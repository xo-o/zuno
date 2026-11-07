import { randomUUID } from "node:crypto";
import { and, desc, eq, isNull } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { conversation, topic, topicLesson } from "../db/app-schema.js";
import { db } from "../db/index.js";
import {
  bindMaterial,
  collectConversationFiles,
  collectTopicFiles,
  materialForTopic,
  materialNotes,
  MAX_TOPIC_MATERIAL,
} from "../lib/attachments.js";
import { conversationIdsWhere, createConversation, listMessages } from "../lib/conversations.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { getProfile } from "../lib/profile.js";
import {
  serializeConversation,
  serializeMessages,
  serializeTopicDetail,
  serializeTopicSummary,
} from "../lib/serializers.js";
import { analyzeMaterial, generateTopic } from "../lib/tutor.js";

const attachmentIds = z.array(z.string().min(1).max(64)).max(MAX_TOPIC_MATERIAL);

const createSchema = z.object({
  prompt: z.string().trim().min(2).max(200),
  attachmentIds: attachmentIds.default([]),
  /** Sub-topics from /analyze the learner kept selected. */
  parts: z.array(z.string().trim().min(1).max(40)).max(10).default([]),
});

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

const aiLimit = rateLimit({ windowMs: 60_000, max: 10 });

export const topicRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/", async (c) => {
    const rows = await db.query.topic.findMany({
      where: eq(topic.userId, c.get("user").id),
      with: { lessons: true },
      orderBy: [desc(topic.updatedAt)],
    });
    return c.json({ topics: rows.map((row) => serializeTopicSummary(row, row.lessons)) });
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
    const userId = c.get("user").id;
    const { prompt, attachmentIds, parts } = await parseBody(c, createSchema);
    const profile = await getProfile(userId);
    const material = await materialForTopic(userId, attachmentIds);
    const notes = material.length ? materialNotes(material) : null;
    const generated = await generateTopic(prompt, profile, notes ? { notes, focus: parts } : undefined);

    const topicId = randomUUID();
    const row = await db.transaction(async (tx) => {
      const [created] = await tx
        .insert(topic)
        .values({
          id: topicId,
          userId,
          prompt,
          title: generated.title,
          translation: generated.translation,
          language: profile.targetLanguage,
          material: notes ? materialNotes(material, 12_000) : null,
        })
        .returning();
      const lessons = await tx
        .insert(topicLesson)
        .values(
          generated.lessons.map((lesson, index) => ({
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
    await bindMaterial(topicId, material);
    return c.json({ topic: serializeTopicDetail(row, row.lessons) }, 201);
  })
  .get("/:id", async (c) => {
    const row = await loadTopic(c.get("user").id, c.req.param("id"));
    return c.json({ topic: serializeTopicDetail(row, row.lessons) });
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
    await db.transaction(async (tx) => {
      await tx.update(topicLesson).set({ completedAt: null }).where(eq(topicLesson.topicId, row.id));
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
    const fresh = await loadTopic(row.userId, row.id);
    return c.json({ topic: serializeTopicDetail(fresh, fresh.lessons) });
  });
