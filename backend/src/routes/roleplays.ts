import { randomBytes, randomUUID } from "node:crypto";
import { and, desc, eq, sql } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { communityReport, conversation, roleplay } from "../db/app-schema.js";
import { db } from "../db/index.js";
import {
  collectConversationFiles,
  materialForTopic,
  materialNotes,
  MAX_TOPIC_MATERIAL,
} from "../lib/attachments.js";
import { findSeedRoleplay, seedRoleplaysForLanguage } from "../lib/community-seeds.js";
import { conversationIdsWhere, createConversation, listMessages } from "../lib/conversations.js";
import { sendEmail } from "../lib/email.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { getProfile } from "../lib/profile.js";
import {
  publicBaseUrl,
  roleplayShareUrlOf,
  serializeConversation,
  serializeMessages,
  serializeRoleplay,
} from "../lib/serializers.js";
import { generateRoleplay, generateRoleplayBuilderTurn, type GeneratedRoleplay } from "../lib/tutor.js";
import { authorHandleFor } from "./topics.js";

const attachmentIds = z.array(z.string().min(1).max(64)).max(MAX_TOPIC_MATERIAL);

const roleplayCategoryEnum = z.enum(["travel", "food", "work", "housing", "health", "social"]);

const draftRoleplaySchema = z.object({
  title: z.string().trim().min(1).max(60),
  scenario: z.string().trim().min(1).max(120),
  userRole: z.string().trim().min(1).max(40),
  aiRole: z.string().trim().min(1).max(40),
  setting: z.string().trim().min(1).max(400),
  category: roleplayCategoryEnum.default("social"),
});

const createSchema = z.object({
  scenario: z.string().trim().min(2).max(120),
  userRole: z.string().trim().max(40).optional(),
  aiRole: z.string().trim().max(40).optional(),
  attachmentIds: attachmentIds.default([]),
  draft: draftRoleplaySchema.optional(),
  /** When true (Roleplays · 3b -> 3c), saves the roleplay without immediately opening a conversation. */
  skipInitialRun: z.boolean().default(false),
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

const updateRoleplaySchema = z.object({
  pinned: z.boolean().optional(),
  category: roleplayCategoryEnum.optional(),
});

const shareSchema = z.object({
  visibility: z.enum(["private", "public"]),
});

const reportSchema = z.object({
  reason: z.enum(["wrong_correction", "wrong_language_level", "offensive", "unsafe", "other"]),
  details: z.string().trim().max(1000).optional(),
});

function normalizeSearch(s: string) {
  return s
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

async function loadRoleplay(userId: string, id: string) {
  const row = await db.query.roleplay.findFirst({ where: and(eq(roleplay.id, id), eq(roleplay.userId, userId)) });
  if (!row) throw new HTTPException(404, { message: "Roleplay not found" });
  return row;
}

async function startRun(row: typeof roleplay.$inferSelect) {
  const profile = await getProfile(row.userId);
  const thread = await createConversation(
    { userId: row.userId, kind: "roleplay", title: row.title, language: row.language, roleplayId: row.id },
    profile,
    { withOpening: true },
  );
  if (row.sourceRoleplayId) {
    await db
      .update(roleplay)
      .set({ practicedCount: sql`${roleplay.practicedCount} + 1` })
      .where(eq(roleplay.id, row.sourceRoleplayId));
  }
  const messages = await listMessages(thread.id);
  return { conversation: serializeConversation(thread), messages: await serializeMessages(messages) };
}

const aiLimit = rateLimit({ windowMs: 60_000, max: 15 });
const reportLimit = rateLimit({ windowMs: 3600_000, max: 10 });

export const roleplayRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  /** Personal library: returns only the user's own + saved roleplays, pinned first. */
  .get("/", async (c) => {
    const userId = c.get("user").id;
    const { targetLanguage } = await getProfile(userId);
    const rows = await db.query.roleplay.findMany({
      where: and(eq(roleplay.userId, userId), eq(roleplay.language, targetLanguage)),
      orderBy: [desc(roleplay.updatedAt)],
    });
    const sorted = [...rows].sort((a, b) => {
      if (a.pinnedAt && b.pinnedAt) return b.pinnedAt.getTime() - a.pinnedAt.getTime();
      if (a.pinnedAt) return -1;
      if (b.pinnedAt) return 1;
      return b.updatedAt.getTime() - a.updatedAt.getTime();
    });
    return c.json({ roleplays: sorted.map(serializeRoleplay) });
  })
  /** Dedicated Explore roleplays view (`Roleplays · 5`, `9`): Zuno Official + Community roleplays. */
  .get("/discover", async (c) => {
    const userId = c.get("user").id;
    const { targetLanguage, level } = await getProfile(userId);
    const category = (c.req.query("category") ?? "all").toLowerCase();
    const rawQ = c.req.query("q") ?? "";
    const q = normalizeSearch(rawQ);

    const [publicRows, myRows] = await Promise.all([
      db.query.roleplay.findMany({
        where: and(eq(roleplay.language, targetLanguage), eq(roleplay.visibility, "public")),
        orderBy: [desc(roleplay.practicedCount), desc(roleplay.publishedAt), desc(roleplay.updatedAt)],
        limit: 50,
      }),
      db.query.roleplay.findMany({
        where: and(eq(roleplay.userId, userId), eq(roleplay.language, targetLanguage)),
        columns: { id: true, sourceRoleplayId: true },
      }),
    ]);

    const savedSourceIds = new Set(myRows.map((r) => r.sourceRoleplayId).filter(Boolean) as string[]);
    const myRoleplayIds = new Set(myRows.map((r) => r.id));

    const seeds = seedRoleplaysForLanguage(targetLanguage).map((seed) => ({
      id: seed.id,
      title: seed.generated.title,
      scenario: seed.generated.scenario,
      userRole: seed.generated.userRole,
      aiRole: seed.generated.aiRole,
      setting: seed.generated.setting,
      language: seed.language,
      category: seed.category,
      sourceKind: seed.sourceKind,
      authorHandle: seed.authorHandle,
      level: seed.level,
      practicedCount: seed.practicedCount,
      shareUrl: `${publicBaseUrl()}/r/${seed.id}`,
      alreadySaved: savedSourceIds.has(seed.id),
    }));

    const community = publicRows.map((row) => ({
      id: row.id,
      title: row.title,
      scenario: row.scenario,
      userRole: row.userRole,
      aiRole: row.aiRole,
      setting: row.setting,
      language: row.language,
      category: row.category ?? "social",
      sourceKind: row.sourceKind === "system" ? ("system" as const) : ("community" as const),
      authorHandle: row.authorHandle ?? "@learner",
      level,
      practicedCount: row.practicedCount ?? 0,
      shareUrl: roleplayShareUrlOf(row),
      alreadySaved: myRoleplayIds.has(row.id) || savedSourceIds.has(row.id),
    }));

    const combined = [...seeds, ...community].filter((item) => {
      const catOk = category === "all" || item.category === category;
      if (!catOk) return false;
      if (!q) return true;
      const hay = normalizeSearch(
        `${item.title} ${item.scenario} ${item.userRole} ${item.aiRole} ${item.setting} ${item.authorHandle ?? ""} ${item.category}`,
      );
      return hay.includes(q);
    });

    return c.json({
      official: combined.filter((i) => i.sourceKind === "system"),
      community: combined.filter((i) => i.sourceKind !== "system"),
    });
  })
  /** Preview a public or system roleplay by ID or shareToken (`Roleplays · 6 · Community Preview`). */
  .get("/discover/:idOrToken", async (c) => {
    const userId = c.get("user").id;
    const idOrToken = c.req.param("idOrToken");
    const { level } = await getProfile(userId);

    const seed = findSeedRoleplay(idOrToken);
    if (seed) {
      const existing = await db.query.roleplay.findFirst({
        where: and(eq(roleplay.userId, userId), eq(roleplay.sourceRoleplayId, seed.id)),
      });
      return c.json({
        roleplay: {
          id: seed.id,
          title: seed.generated.title,
          scenario: seed.generated.scenario,
          userRole: seed.generated.userRole,
          aiRole: seed.generated.aiRole,
          setting: seed.generated.setting,
          language: seed.language,
          category: seed.category,
          sourceKind: seed.sourceKind,
          authorHandle: seed.authorHandle,
          level: seed.level,
          practicedCount: seed.practicedCount,
          shareUrl: `${publicBaseUrl()}/r/${seed.id}`,
          alreadySaved: Boolean(existing),
          savedRoleplayId: existing?.id ?? null,
        },
      });
    }

    const row =
      (await db.query.roleplay.findFirst({
        where: and(eq(roleplay.id, idOrToken), eq(roleplay.visibility, "public")),
      })) ??
      (await db.query.roleplay.findFirst({
        where: and(eq(roleplay.shareToken, idOrToken), eq(roleplay.visibility, "public")),
      }));

    if (!row) throw new HTTPException(404, { message: "Roleplay not found or private" });
    const existing =
      row.userId === userId
        ? row
        : await db.query.roleplay.findFirst({
            where: and(eq(roleplay.userId, userId), eq(roleplay.sourceRoleplayId, row.id)),
          });
    return c.json({
      roleplay: {
        id: row.id,
        title: row.title,
        scenario: row.scenario,
        userRole: row.userRole,
        aiRole: row.aiRole,
        setting: row.setting,
        language: row.language,
        category: row.category ?? "social",
        sourceKind: row.sourceKind === "system" ? "system" : "community",
        authorHandle: row.authorHandle ?? "@learner",
        level,
        practicedCount: row.practicedCount ?? 0,
        shareUrl: roleplayShareUrlOf(row),
        alreadySaved: Boolean(existing),
        savedRoleplayId: existing?.id ?? null,
      },
    });
  })
  /** Interactive conversational Roleplay Builder turn (`Roleplays · 3a` → `3b`) with mid-chat file uploads. */
  .post("/builder/turn", aiLimit, async (c) => {
    const userId = c.get("user").id;
    const body = await parseBody(c, builderTurnSchema);
    const profile = await getProfile(userId);
    const material = await materialForTopic(userId, body.attachmentIds);
    const notes = material.length ? materialNotes(material, 16_000) : null;
    const contextNote =
      material.length > 0
        ? `Read ${material[material.length - 1].fileName} · ${Math.max(2, material[material.length - 1].digest?.items.length || 3)} key skills extracted`
        : null;
    const result = await generateRoleplayBuilderTurn({ turns: body.turns, materialNotes: notes }, profile);
    return c.json({ ...result, contextNote });
  })
  /** Creates the roleplay; if `skipInitialRun` is true (`Roleplays · 3c`), returns without opening a conversation yet. */
  .post("/", aiLimit, async (c) => {
    const currentUser = c.get("user");
    const userId = currentUser.id;
    const body = await parseBody(c, createSchema);
    const profile = await getProfile(userId);
    const material = await materialForTopic(userId, body.attachmentIds);
    const notes = material.length ? materialNotes(material, 12_000) : null;

    let generated: GeneratedRoleplay;
    if (body.draft) {
      generated = body.draft;
    } else {
      generated = await generateRoleplay(
        { scenario: body.scenario, userRole: body.userRole, aiRole: body.aiRole, notes },
        profile,
      );
    }

    const [row] = await db
      .insert(roleplay)
      .values({
        id: randomUUID(),
        userId,
        language: profile.targetLanguage,
        ...generated,
        userRole: body.userRole || generated.userRole,
        aiRole: body.aiRole || generated.aiRole,
        sourceKind: "user",
        authorHandle: authorHandleFor(currentUser.name, currentUser.email),
      })
      .returning();

    if (body.skipInitialRun) {
      return c.json({ roleplay: serializeRoleplay(row), conversation: null, messages: [] }, 201);
    }
    return c.json({ roleplay: serializeRoleplay(row), ...(await startRun(row)) }, 201);
  })
  /** Saves / forks a Zuno Official or Community roleplay into the user's personal library. */
  .post("/:id/save", aiLimit, async (c) => {
    const userId = c.get("user").id;
    const sourceId = c.req.param("id");

    const alreadyOwned = await db.query.roleplay.findFirst({
      where: and(eq(roleplay.id, sourceId), eq(roleplay.userId, userId)),
    });
    if (alreadyOwned) return c.json({ roleplay: serializeRoleplay(alreadyOwned) });

    const alreadySaved = await db.query.roleplay.findFirst({
      where: and(eq(roleplay.userId, userId), eq(roleplay.sourceRoleplayId, sourceId)),
    });
    if (alreadySaved) return c.json({ roleplay: serializeRoleplay(alreadySaved) });

    const seed = findSeedRoleplay(sourceId);
    if (seed) {
      const [created] = await db
        .insert(roleplay)
        .values({
          id: randomUUID(),
          userId,
          title: seed.generated.title,
          scenario: seed.generated.scenario,
          userRole: seed.generated.userRole,
          aiRole: seed.generated.aiRole,
          setting: seed.generated.setting,
          language: seed.language,
          category: seed.category,
          visibility: "private",
          sourceKind: seed.sourceKind,
          sourceRoleplayId: seed.id,
          authorHandle: seed.authorHandle,
        })
        .returning();
      return c.json({ roleplay: serializeRoleplay(created) }, 201);
    }

    const source = await db.query.roleplay.findFirst({
      where: and(eq(roleplay.id, sourceId), eq(roleplay.visibility, "public")),
    });
    if (!source) throw new HTTPException(404, { message: "Roleplay is no longer available" });

    const cloned = await db.transaction(async (tx) => {
      const [created] = await tx
        .insert(roleplay)
        .values({
          id: randomUUID(),
          userId,
          title: source.title,
          scenario: source.scenario,
          userRole: source.userRole,
          aiRole: source.aiRole,
          setting: source.setting,
          language: source.language,
          category: source.category,
          visibility: "private",
          sourceKind: source.sourceKind === "system" ? "system" : "community",
          sourceRoleplayId: source.id,
          authorHandle: source.authorHandle ?? "@learner",
        })
        .returning();
      await tx
        .update(roleplay)
        .set({ practicedCount: sql`${roleplay.practicedCount} + 1` })
        .where(eq(roleplay.id, source.id));
      return created;
    });

    return c.json({ roleplay: serializeRoleplay(cloned) }, 201);
  })
  .get("/:id", async (c) => {
    const row = await loadRoleplay(c.get("user").id, c.req.param("id"));
    return c.json({ roleplay: serializeRoleplay(row) });
  })
  /** Pin / unpin or update category (Roleplays are immutable in content after creation). */
  .patch("/:id", async (c) => {
    const row = await loadRoleplay(c.get("user").id, c.req.param("id"));
    const body = await parseBody(c, updateRoleplaySchema);
    const [updated] = await db
      .update(roleplay)
      .set({
        ...(body.pinned !== undefined && { pinnedAt: body.pinned ? new Date() : null }),
        ...(body.category !== undefined && { category: body.category }),
      })
      .where(eq(roleplay.id, row.id))
      .returning();
    return c.json({ roleplay: serializeRoleplay(updated) });
  })
  /** Roleplay settings (`Roleplays · 3c` / `7`): Only me vs Public to community. */
  .put("/:id/share", async (c) => {
    const currentUser = c.get("user");
    const row = await loadRoleplay(currentUser.id, c.req.param("id"));
    if (row.sourceKind !== "user") {
      throw new HTTPException(403, { message: "Only roleplays you created can change visibility." });
    }
    const { visibility } = await parseBody(c, shareSchema);
    const [updated] = await db
      .update(roleplay)
      .set({
        visibility,
        ...(visibility === "public" && !row.shareToken && { shareToken: randomBytes(12).toString("base64url") }),
        ...(visibility === "public" && !row.publishedAt && { publishedAt: new Date() }),
        ...(!row.authorHandle && { authorHandle: authorHandleFor(currentUser.name, currentUser.email) }),
      })
      .where(eq(roleplay.id, row.id))
      .returning();
    return c.json({
      visibility: updated.visibility,
      shareUrl: roleplayShareUrlOf(updated),
      practicedCount: updated.practicedCount,
      roleplay: serializeRoleplay(updated),
    });
  })
  /** Report a saved or discovered community roleplay (`Roleplays · 10`). */
  .post("/:id/report", reportLimit, async (c) => {
    const current = c.get("user");
    const targetId = c.req.param("id");
    const body = await parseBody(c, reportSchema);
    const id = randomUUID();
    await db.insert(communityReport).values({
      id,
      userId: current.id,
      targetKind: "roleplay",
      targetId,
      reason: body.reason,
      details: body.details || null,
    });
    const to = process.env.REPORTS_EMAIL;
    if (to) {
      sendEmail({
        to,
        subject: `Community roleplay report: ${body.reason}`,
        heading: `Report on roleplay ${targetId}`,
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
    const row = await loadRoleplay(c.get("user").id, c.req.param("id"));
    const removeFiles = await collectConversationFiles(await conversationIdsWhere(eq(conversation.roleplayId, row.id)));
    await db.delete(roleplay).where(eq(roleplay.id, row.id));
    await removeFiles();
    return c.body(null, 204);
  })
  /** Resumes the latest run of this roleplay, or starts one if there is none. */
  .post("/:id/open", aiLimit, async (c) => {
    const row = await loadRoleplay(c.get("user").id, c.req.param("id"));
    const latest = await db.query.conversation.findFirst({
      where: and(eq(conversation.roleplayId, row.id), eq(conversation.userId, row.userId)),
      orderBy: [desc(conversation.updatedAt)],
    });
    if (!latest) return c.json(await startRun(row));
    const messages = await listMessages(latest.id);
    return c.json({ conversation: serializeConversation(latest), messages: await serializeMessages(messages) });
  })
  .post("/:id/restart", aiLimit, async (c) => {
    const row = await loadRoleplay(c.get("user").id, c.req.param("id"));
    return c.json(await startRun(row), 201);
  });
