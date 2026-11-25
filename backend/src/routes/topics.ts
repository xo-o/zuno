import { randomBytes, randomUUID } from "node:crypto";
import { and, desc, eq, isNull, sql } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { communityReport, conversation, lessonEvaluation, topic, topicLesson } from "../db/app-schema.js";
import { db } from "../db/index.js";
import {
  bindMaterial,
  collectConversationFiles,
  collectTopicFiles,
  materialForTopic,
  materialNotes,
  MAX_TOPIC_MATERIAL,
} from "../lib/attachments.js";
import { findSeedTopic, seedTopicsForLanguage } from "../lib/community-seeds.js";
import { conversationIdsWhere, createConversation, listMessages } from "../lib/conversations.js";
import { sendEmail } from "../lib/email.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { checkTopicCompleted } from "./conversations.js";
import { getProfile, updateProfile, type LearnerProfile } from "../lib/profile.js";
import {
  publicBaseUrl,
  serializeConversation,
  serializeMessages,
  serializeTopicDetail,
  serializeTopicSummary,
  topicShareUrlOf,
} from "../lib/serializers.js";
import {
  analyzeMaterial,
  generateLessonCurriculum,
  generateTopic,
  generateTopicBuilderTurn,
  MAX_LESSONS_PER_TOPIC,
  MIN_LESSONS_PER_TOPIC,
  type GeneratedTopic,
} from "../lib/tutor.js";

const attachmentIds = z.array(z.string().min(1).max(64)).max(MAX_TOPIC_MATERIAL);

const topicCategoryEnum = z.enum(["travel", "food", "work", "daily_life", "grammar", "culture"]);

const draftLessonSchema = z.object({
  title: z.string().trim().min(1).max(60),
  translation: z.string().trim().min(1).max(60),
  objective: z.string().trim().min(1).max(240),
});

const draftTopicSchema = z.object({
  title: z.string().trim().min(1).max(80),
  translation: z.string().trim().min(1).max(80),
  category: topicCategoryEnum.default("daily_life"),
  prompt: z.string().trim().min(2).max(200),
  lessons: z.array(draftLessonSchema).min(MIN_LESSONS_PER_TOPIC).max(MAX_LESSONS_PER_TOPIC),
});

const createSchema = z.object({
  prompt: z.string().trim().min(2).max(200),
  recommendationId: z.string().trim().min(1).max(64).optional(),
  attachmentIds: attachmentIds.default([]),
  /** Sub-topics from /analyze the learner kept selected. */
  parts: z.array(z.string().trim().min(1).max(40)).max(10).default([]),
  /** Optional pre-built draft from the interactive chat builder (`10b`). */
  draft: draftTopicSchema.optional(),
});

const builderTurnSchema = z.object({
  turns: z
    .array(
      z.object({
        role: z.enum(["user", "assistant"]),
        text: z.string().trim().min(1).max(1000),
      }),
    )
    .min(1)
    .max(20),
  attachmentIds: attachmentIds.default([]),
});

const updateTopicSchema = z.object({
  pinned: z.boolean().optional(),
  category: topicCategoryEnum.optional(),
  title: z.string().trim().min(1).max(80).optional(),
});

const shareSchema = z.object({
  visibility: z.enum(["private", "public"]),
});

const reportSchema = z.object({
  reason: z.enum(["wrong_correction", "wrong_language_level", "offensive", "unsafe", "other"]),
  details: z.string().trim().max(1000).optional(),
});

export function authorHandleFor(name?: string | null, email?: string | null) {
  const base = (name?.trim() || email?.split("@")[0] || "learner")
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, ".")
    .replace(/^\.+|\.+$/g, "")
    .slice(0, 18);
  return `@${base || "learner"}`;
}

export async function persistAndWarmTopic(options: {
  userId: string;
  prompt: string;
  generated: GeneratedTopic;
  profile: LearnerProfile;
  trimmedMaterial?: string | null;
  sourceKind?: "user" | "system" | "community";
  sourceTopicId?: string | null;
  authorHandle?: string | null;
}) {
  const topicId = randomUUID();
  const trimmedMaterial = options.trimmedMaterial ?? null;
  const row = await db.transaction(async (tx) => {
    const [created] = await tx
      .insert(topic)
      .values({
        id: topicId,
        userId: options.userId,
        prompt: options.prompt,
        title: options.generated.title,
        translation: options.generated.translation,
        language: options.profile.targetLanguage,
        level: options.profile.level,
        category: options.generated.category ?? "daily_life",
        sourceKind: options.sourceKind ?? "user",
        sourceTopicId: options.sourceTopicId ?? null,
        authorHandle: options.authorHandle ?? null,
        material: trimmedMaterial,
      })
      .returning();
    const lessons = await tx
      .insert(topicLesson)
      .values(
        options.generated.lessons.map((lesson, index) => ({
          id: randomUUID(),
          topicId,
          position: index + 1,
          title: lesson.title,
          translation: lesson.translation,
          objective: lesson.objective,
        })),
      )
      .returning();
    return { ...created, lessons };
  });

  // Pre-generate structured voice curricula in the background so lessons launch instantaneously.
  void (async () => {
    const ordered = [...row.lessons].sort((a, b) => a.position - b.position);
    const totalLessons = ordered.length;
    for (const lesson of ordered) {
      try {
        const curriculum = await generateLessonCurriculum(
          {
            topicTitle: row.title,
            lessonTitle: lesson.title,
            lessonTranslation: lesson.translation,
            objective: lesson.objective,
            position: lesson.position,
            totalLessons,
            material: trimmedMaterial,
          },
          options.profile,
        );
        await db.update(topicLesson).set({ curriculum }).where(eq(topicLesson.id, lesson.id));
      } catch (err) {
        console.warn(`Background curriculum pre-warm skipped for lesson ${lesson.id}:`, err);
      }
    }
  })();

  return row;
}

const analyzeSchema = z.object({
  prompt: z.string().trim().max(200).default(""),
  attachmentIds: attachmentIds.min(1),
});

async function loadTopic(userId: string, id: string) {
  const row = await db.query.topic.findFirst({
    where: and(eq(topic.id, id), eq(topic.userId, userId)),
    with: { lessons: true },
  });
  if (!row) throw new HTTPException(404, { message: "Topic not found" });
  return row;
}

function normalizeSearch(s: string) {
  return s
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

const aiLimit = rateLimit({ windowMs: 60_000, max: 15 });
const reportLimit = rateLimit({ windowMs: 3600_000, max: 10 });

export const topicRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  /** Personal library: returns only the user's own + saved topics, pinned first. */
  .get("/", async (c) => {
    const userId = c.get("user").id;
    const { targetLanguage } = await getProfile(userId);
    const rows = await db.query.topic.findMany({
      where: and(eq(topic.userId, userId), eq(topic.language, targetLanguage)),
      with: { lessons: true },
      orderBy: [desc(topic.updatedAt)],
    });
    const sorted = [...rows].sort((a, b) => {
      if (a.pinnedAt && b.pinnedAt) return b.pinnedAt.getTime() - a.pinnedAt.getTime();
      if (a.pinnedAt) return -1;
      if (b.pinnedAt) return 1;
      return b.updatedAt.getTime() - a.updatedAt.getTime();
    });
    return c.json({ topics: sorted.map((row) => serializeTopicSummary(row, row.lessons)) });
  })
  /** Dedicated Explore view (`08` / `12`): Zuno Official + Public Community topics by category & search query. */
  .get("/discover", async (c) => {
    const userId = c.get("user").id;
    const { targetLanguage, level } = await getProfile(userId);
    const category = (c.req.query("category") ?? "all").toLowerCase();
    const levelFilter = (c.req.query("level") ?? "all").toUpperCase();
    const rawQ = c.req.query("q") ?? "";
    const q = normalizeSearch(rawQ);

    const [publicRows, myRows] = await Promise.all([
      db.query.topic.findMany({
        where: and(eq(topic.language, targetLanguage), eq(topic.visibility, "public")),
        with: { lessons: true },
        orderBy: [desc(topic.learnersCount), desc(topic.publishedAt), desc(topic.updatedAt)],
        limit: 50,
      }),
      db.query.topic.findMany({
        where: and(eq(topic.userId, userId), eq(topic.language, targetLanguage)),
        columns: { id: true, sourceTopicId: true },
      }),
    ]);

    const savedSourceIds = new Set(myRows.map((r) => r.sourceTopicId).filter(Boolean) as string[]);
    const myTopicIds = new Set(myRows.map((r) => r.id));

    const seeds = seedTopicsForLanguage(targetLanguage).map((seed) => ({
      id: seed.id,
      title: seed.generated.title,
      translation: seed.generated.translation,
      language: seed.language,
      category: seed.category,
      sourceKind: seed.sourceKind,
      authorHandle: seed.authorHandle,
      level: seed.level,
      learnersCount: seed.learnersCount,
      totalLessons: seed.generated.lessons.length,
      shareUrl: `${publicBaseUrl()}/t/${seed.id}`,
      alreadySaved: savedSourceIds.has(seed.id),
      lessons: seed.generated.lessons.map((l, idx) => ({
        position: idx + 1,
        title: l.title,
        translation: l.translation,
        objective: l.objective,
      })),
    }));

    const community = publicRows.map((row) => {
      const ordered = [...row.lessons].sort((a, b) => a.position - b.position);
      return {
        id: row.id,
        title: row.title,
        translation: row.translation,
        language: row.language,
        category: row.category ?? "daily_life",
        sourceKind: row.sourceKind === "system" ? ("system" as const) : ("community" as const),
        authorHandle: row.authorHandle ?? "@learner",
        level: row.level || level,
        learnersCount: row.learnersCount ?? 0,
        totalLessons: ordered.length,
        shareUrl: topicShareUrlOf(row),
        alreadySaved: myTopicIds.has(row.id) || savedSourceIds.has(row.id),
        lessons: ordered.map((l) => ({
          position: l.position,
          title: l.title,
          translation: l.translation,
          objective: l.objective,
        })),
      };
    });

    const combined = [...seeds, ...community].filter((item) => {
      const catOk = category === "all" || item.category === category;
      if (!catOk) return false;
      const lvlOk = levelFilter === "ALL" || item.level.toUpperCase() === levelFilter;
      if (!lvlOk) return false;
      if (!q) return true;
      const hay = normalizeSearch(`${item.title} ${item.translation} ${item.authorHandle ?? ""} ${item.category}`);
      return hay.includes(q);
    });

    return c.json({
      official: combined.filter((i) => i.sourceKind === "system"),
      community: combined.filter((i) => i.sourceKind !== "system"),
    });
  })
  /** Preview a public or system topic by ID or shareToken (`09 · Community Preview`). */
  .get("/discover/:idOrToken", async (c) => {
    const userId = c.get("user").id;
    const idOrToken = c.req.param("idOrToken");
    const { level } = await getProfile(userId);

    const seed = findSeedTopic(idOrToken);
    if (seed) {
      const existing = await db.query.topic.findFirst({
        where: and(eq(topic.userId, userId), eq(topic.sourceTopicId, seed.id)),
      });
      return c.json({
        topic: {
          id: seed.id,
          title: seed.generated.title,
          translation: seed.generated.translation,
          language: seed.language,
          category: seed.category,
          sourceKind: seed.sourceKind,
          authorHandle: seed.authorHandle,
          level: seed.level,
          learnersCount: seed.learnersCount,
          totalLessons: seed.generated.lessons.length,
          shareUrl: `${publicBaseUrl()}/t/${seed.id}`,
          alreadySaved: Boolean(existing),
          savedTopicId: existing?.id ?? null,
          lessons: seed.generated.lessons.map((l, idx) => ({
            position: idx + 1,
            title: l.title,
            translation: l.translation,
            objective: l.objective,
          })),
        },
      });
    }

    const row =
      (await db.query.topic.findFirst({
        where: and(eq(topic.id, idOrToken), eq(topic.visibility, "public")),
        with: { lessons: true },
      })) ??
      (await db.query.topic.findFirst({
        where: and(eq(topic.shareToken, idOrToken), eq(topic.visibility, "public")),
        with: { lessons: true },
      }));

    if (!row) throw new HTTPException(404, { message: "Topic not found or private" });
    const existing =
      row.userId === userId
        ? row
        : await db.query.topic.findFirst({
            where: and(eq(topic.userId, userId), eq(topic.sourceTopicId, row.id)),
          });
    const ordered = [...row.lessons].sort((a, b) => a.position - b.position);
    return c.json({
      topic: {
        id: row.id,
        title: row.title,
        translation: row.translation,
        language: row.language,
        category: row.category ?? "daily_life",
        sourceKind: row.sourceKind === "system" ? "system" : "community",
        authorHandle: row.authorHandle ?? "@learner",
        level: row.level || level,
        learnersCount: row.learnersCount ?? 0,
        totalLessons: ordered.length,
        shareUrl: topicShareUrlOf(row),
        alreadySaved: Boolean(existing),
        savedTopicId: existing?.id ?? null,
        lessons: ordered.map((l) => ({
          position: l.position,
          title: l.title,
          translation: l.translation,
          objective: l.objective,
        })),
      },
    });
  })
  /** Interactive conversational Topic Builder turn (`10a` → `10b`) with mid-chat file uploads. */
  .post("/builder/turn", aiLimit, async (c) => {
    const userId = c.get("user").id;
    const body = await parseBody(c, builderTurnSchema);
    const profile = await getProfile(userId);
    const material = await materialForTopic(userId, body.attachmentIds);
    const notes = material.length ? materialNotes(material, 16_000) : null;
    const contextNote =
      material.length > 0
        ? `Read ${material[material.length - 1].fileName} · ${Math.max(2, material[material.length - 1].digest?.items.length || 3)} themes found`
        : null;
    const result = await generateTopicBuilderTurn({ turns: body.turns, materialNotes: notes }, profile);
    return c.json({ ...result, contextNote });
  })
  /** "Found in your material": sub-topics the learner can include or skip before creating the topic. */
  .post("/analyze", aiLimit, async (c) => {
    const userId = c.get("user").id;
    const body = await parseBody(c, analyzeSchema);
    const material = await materialForTopic(userId, body.attachmentIds);
    const parts = await analyzeMaterial(materialNotes(material), body.prompt, await getProfile(userId));
    return c.json({ parts });
  })
  .post("/", aiLimit, async (c) => {
    const currentUser = c.get("user");
    const userId = currentUser.id;
    const { prompt, recommendationId, attachmentIds, parts, draft } = await parseBody(c, createSchema);
    const profile = await getProfile(userId);
    const matchedRec = recommendationId
      ? profile.recommendedTopics.find((r) => r.id === recommendationId)
      : undefined;
    const material = await materialForTopic(userId, attachmentIds);
    const notes = material.length ? materialNotes(material) : null;

    let generated: GeneratedTopic;
    if (draft) {
      generated = {
        title: draft.title,
        translation: draft.translation,
        category: draft.category,
        lessons: draft.lessons,
      };
    } else {
      const rawGenerated = await generateTopic(
        matchedRec ? `${matchedRec.title} (${matchedRec.translation}): ${matchedRec.prompt}` : prompt,
        profile,
        notes ? { notes, focus: parts } : undefined,
      );
      generated = matchedRec
        ? { ...rawGenerated, title: matchedRec.title, translation: matchedRec.translation }
        : rawGenerated;
    }

    const trimmedMaterial = notes ? materialNotes(material, 12_000) : null;
    const row = await persistAndWarmTopic({
      userId,
      prompt: matchedRec?.prompt ?? draft?.prompt ?? prompt,
      generated,
      profile,
      trimmedMaterial,
      sourceKind: "user",
      authorHandle: authorHandleFor(currentUser.name, currentUser.email),
    });
    await bindMaterial(row.id, material);

    if (matchedRec) {
      await updateProfile(userId, {
        recommendedTopics: profile.recommendedTopics.filter((r) => r.id !== matchedRec.id),
      });
    }

    return c.json({ topic: serializeTopicDetail(row, row.lessons) }, 201);
  })
  /** Saves / forks a Zuno Official or Community topic into the user's personal library (`+ Add to my Topics`). */
  .post("/:id/save", aiLimit, async (c) => {
    const userId = c.get("user").id;
    const sourceId = c.req.param("id");
    const profile = await getProfile(userId);

    // Idempotent if already owned or saved
    const alreadyOwned = await db.query.topic.findFirst({
      where: and(eq(topic.id, sourceId), eq(topic.userId, userId)),
      with: { lessons: true },
    });
    if (alreadyOwned) return c.json({ topic: serializeTopicDetail(alreadyOwned, alreadyOwned.lessons) });

    const alreadySaved = await db.query.topic.findFirst({
      where: and(eq(topic.userId, userId), eq(topic.sourceTopicId, sourceId)),
      with: { lessons: true },
    });
    if (alreadySaved) return c.json({ topic: serializeTopicDetail(alreadySaved, alreadySaved.lessons) });

    const seed = findSeedTopic(sourceId);
    if (seed) {
      const saved = await persistAndWarmTopic({
        userId,
        prompt: seed.prompt,
        generated: seed.generated,
        profile: { ...profile, targetLanguage: seed.language as LearnerProfile["targetLanguage"] },
        sourceKind: seed.sourceKind,
        sourceTopicId: seed.id,
        authorHandle: seed.authorHandle,
      });
      return c.json({ topic: serializeTopicDetail(saved, saved.lessons) }, 201);
    }

    const source = await db.query.topic.findFirst({
      where: and(eq(topic.id, sourceId), eq(topic.visibility, "public")),
      with: { lessons: true },
    });
    if (!source) throw new HTTPException(404, { message: "Topic is no longer available" });

    const ordered = [...source.lessons].sort((a, b) => a.position - b.position);
    const topicId = randomUUID();
    const cloned = await db.transaction(async (tx) => {
      const [created] = await tx
        .insert(topic)
        .values({
          id: topicId,
          userId,
          prompt: source.prompt,
          title: source.title,
          translation: source.translation,
          language: source.language,
          level: profile.level,
          category: source.category,
          visibility: "private",
          sourceKind: source.sourceKind === "system" ? "system" : "community",
          sourceTopicId: source.id,
          authorHandle: source.authorHandle ?? "@learner",
          // Never copy private raw material attachments — only generated lesson curriculum
          material: null,
        })
        .returning();
      const sameLevel = (source.level || "B1") === profile.level;
      const lessons = await tx
        .insert(topicLesson)
        .values(
          ordered.map((l) => ({
            id: randomUUID(),
            topicId,
            position: l.position,
            title: l.title,
            translation: l.translation,
            objective: l.objective,
            curriculum: sameLevel ? (l.curriculum ?? null) : null,
          })),
        )
        .returning();
      await tx
        .update(topic)
        .set({ learnersCount: sql`${topic.learnersCount} + 1` })
        .where(eq(topic.id, source.id));
      return { ...created, lessons };
    });

    return c.json({ topic: serializeTopicDetail(cloned, cloned.lessons) }, 201);
  })
  .get("/:id", async (c) => {
    const row = await loadTopic(c.get("user").id, c.req.param("id"));
    return c.json({ topic: serializeTopicDetail(row, row.lessons) });
  })
  /** Pin / unpin, rename, or update category. */
  .patch("/:id", async (c) => {
    const row = await loadTopic(c.get("user").id, c.req.param("id"));
    const body = await parseBody(c, updateTopicSchema);
    const [updated] = await db
      .update(topic)
      .set({
        ...(body.pinned !== undefined && { pinnedAt: body.pinned ? new Date() : null }),
        ...(body.category !== undefined && { category: body.category }),
        ...(body.title !== undefined && { title: body.title }),
      })
      .where(eq(topic.id, row.id))
      .returning();
    return c.json({ topic: serializeTopicDetail(updated, row.lessons) });
  })
  /** Topic settings (`10c` / `11`): Only me vs Public to community. */
  .put("/:id/share", async (c) => {
    const currentUser = c.get("user");
    const row = await loadTopic(currentUser.id, c.req.param("id"));
    if (row.sourceKind !== "user") {
      throw new HTTPException(403, { message: "Only topics you created can change visibility." });
    }
    const { visibility } = await parseBody(c, shareSchema);
    const [updated] = await db
      .update(topic)
      .set({
        visibility,
        ...(visibility === "public" && !row.shareToken && { shareToken: randomBytes(12).toString("base64url") }),
        ...(visibility === "public" && !row.publishedAt && { publishedAt: new Date() }),
        ...(!row.authorHandle && { authorHandle: authorHandleFor(currentUser.name, currentUser.email) }),
      })
      .where(eq(topic.id, row.id))
      .returning();
    return c.json({
      visibility: updated.visibility,
      shareUrl: topicShareUrlOf(updated),
      learnersCount: updated.learnersCount,
      topic: serializeTopicDetail(updated, row.lessons),
    });
  })
  /** Report a saved or discovered community topic (`14`). */
  .post("/:id/report", reportLimit, async (c) => {
    const current = c.get("user");
    const targetId = c.req.param("id");
    const body = await parseBody(c, reportSchema);
    const id = randomUUID();
    await db.insert(communityReport).values({
      id,
      userId: current.id,
      targetKind: "topic",
      targetId,
      reason: body.reason,
      details: body.details || null,
    });
    const to = process.env.REPORTS_EMAIL;
    if (to) {
      sendEmail({
        to,
        subject: `Community topic report: ${body.reason}`,
        heading: `Report on topic ${targetId}`,
        paragraphs: [
          `Reason: ${body.reason}`,
          `Details: ${body.details || "(none)"}`,
          `Reporter: ${current.email} (${current.id})`,
          `Report id: ${id}`,
        ],
      }).catch((err) => console.error("Report email failed:", err));
    }
    return c.json({ id }, 201);
  })
  .delete("/:id", async (c) => {
    const row = await loadTopic(c.get("user").id, c.req.param("id"));
    const removeFiles = await collectConversationFiles(await conversationIdsWhere(eq(conversation.topicId, row.id)));
    const removeMaterial = await collectTopicFiles(row.id);
    await db.delete(topic).where(eq(topic.id, row.id));
    await Promise.all([removeFiles(), removeMaterial()]);
    return c.body(null, 204);
  })
  .post("/:id/reset", async (c) => {
    const row = await loadTopic(c.get("user").id, c.req.param("id"));
    const removeFiles = await collectConversationFiles(await conversationIdsWhere(eq(conversation.topicId, row.id)));
    const lessonIds = row.lessons.map((l) => l.id);
    await db.transaction(async (tx) => {
      await tx.update(topicLesson).set({ completedAt: null }).where(eq(topicLesson.topicId, row.id));
      if (lessonIds.length > 0) {
        for (const lid of lessonIds) {
          await tx.delete(lessonEvaluation).where(eq(lessonEvaluation.lessonId, lid));
        }
      }
      await tx.delete(conversation).where(eq(conversation.topicId, row.id));
    });
    await removeFiles();
    const fresh = await loadTopic(row.userId, row.id);
    return c.json({ topic: serializeTopicDetail(fresh, fresh.lessons) });
  })
  /** Opens the lesson's conversation, creating it (with the tutor's opening line) on first visit. */
  .post("/:id/lessons/:lessonId/start", aiLimit, async (c) => {
    const userId = c.get("user").id;
    const row = await loadTopic(userId, c.req.param("id"));
    const lesson = row.lessons.find((item) => item.id === c.req.param("lessonId"));
    if (!lesson) throw new HTTPException(404, { message: "Lesson not found" });

    let thread = await db.query.conversation.findFirst({
      where: and(eq(conversation.userId, userId), eq(conversation.lessonId, lesson.id)),
      orderBy: [desc(conversation.updatedAt)],
    });
    if (!thread) {
      const profile = await getProfile(userId);
      thread = await createConversation(
        { userId, kind: "lesson", title: lesson.title, language: row.language, topicId: row.id, lessonId: lesson.id },
        profile,
        { withOpening: true },
      );
    }
    const messages = await listMessages(thread.id);
    return c.json({ conversation: serializeConversation(thread), messages: await serializeMessages(messages) });
  })
  .post("/:id/lessons/:lessonId/complete", async (c) => {
    const row = await loadTopic(c.get("user").id, c.req.param("id"));
    const lessonId = c.req.param("lessonId");
    if (!row.lessons.some((item) => item.id === lessonId)) throw new HTTPException(404, { message: "Lesson not found" });
    await db
      .update(topicLesson)
      .set({ completedAt: new Date() })
      .where(and(eq(topicLesson.id, lessonId), eq(topicLesson.topicId, row.id), isNull(topicLesson.completedAt)));
    await db.update(topic).set({ updatedAt: new Date() }).where(eq(topic.id, row.id));
    await checkTopicCompleted(row.id);
    const fresh = await loadTopic(row.userId, row.id);
    return c.json({ topic: serializeTopicDetail(fresh, fresh.lessons) });
  });
