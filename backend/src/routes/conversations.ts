import { and, desc, eq, exists } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { conversation, message, roleplay, topic } from "../db/app-schema.js";
import { db } from "../db/index.js";
import {
  appendTranscript,
  createConversation,
  DEFAULT_FREE_TALK_TITLE,
  findConversation,
  listMessages,
  sendUserMessage,
} from "../lib/conversations.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { getProfile } from "../lib/profile.js";
import { serializeConversation, serializeMessage, topicProgressLabel } from "../lib/serializers.js";
import { explainGrammar, generateTypingHint } from "../lib/tutor.js";

const createSchema = z.object({ kind: z.literal("freetalk").default("freetalk") });
const messageSchema = z.object({
  text: z.string().trim().min(1).max(1000),
  viaVoice: z.boolean().optional(),
});
const transcriptSchema = z.object({
  turns: z
    .array(z.object({ role: z.enum(["user", "assistant"]), text: z.string().trim().min(1).max(2000) }))
    .max(200),
});
const hintSchema = z.object({ text: z.string().trim().min(1).max(300) });

async function requireConversation(userId: string, id: string) {
  const row = await findConversation(userId, id);
  if (!row) throw new HTTPException(404, { message: "Conversation not found" });
  return row;
}

const chatLimit = rateLimit({ windowMs: 60_000, max: 30 });
const hintLimit = rateLimit({ windowMs: 60_000, max: 40 });

export const conversationRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  /**
   * Drawer "Recent sessions": Free Talk threads plus one row per topic and roleplay,
   * ordered by latest activity.
   */
  .get("/recent", async (c) => {
    const userId = c.get("user").id;
    const limit = Math.min(Number(c.req.query("limit")) || 20, 50);
    const [freeTalks, topics, roleplays] = await Promise.all([
      db.query.conversation.findMany({
        // Skip Free Talk threads that were opened but never used.
        where: and(
          eq(conversation.userId, userId),
          eq(conversation.kind, "freetalk"),
          exists(db.select({ id: message.id }).from(message).where(eq(message.conversationId, conversation.id))),
        ),
        orderBy: [desc(conversation.updatedAt)],
        limit,
      }),
      db.query.topic.findMany({
        where: eq(topic.userId, userId),
        with: { lessons: true },
        orderBy: [desc(topic.updatedAt)],
        limit,
      }),
      db.query.roleplay.findMany({ where: eq(roleplay.userId, userId), orderBy: [desc(roleplay.updatedAt)], limit }),
    ]);

    const sessions = [
      ...freeTalks.map((row) => ({ kind: "freetalk", id: row.id, title: row.title, meta: "Free Talk", at: row.updatedAt })),
      ...topics.map((row) => ({
        kind: "topic",
        id: row.id,
        title: row.title,
        meta: `Topic · ${topicProgressLabel(row.lessons)}`,
        at: row.updatedAt,
      })),
      ...roleplays.map((row) => ({ kind: "roleplay", id: row.id, title: row.title, meta: "Roleplay", at: row.updatedAt })),
    ]
      .sort((a, b) => b.at.getTime() - a.at.getTime())
      .slice(0, limit)
      .map(({ at, ...rest }) => ({ ...rest, updatedAt: at.toISOString() }));

    return c.json({ sessions });
  })
  .post("/", async (c) => {
    const userId = c.get("user").id;
    await parseBody(c, createSchema);
    const profile = await getProfile(userId);
    const row = await createConversation(
      { userId, kind: "freetalk", title: DEFAULT_FREE_TALK_TITLE, language: profile.targetLanguage },
      profile,
      { withOpening: false },
    );
    return c.json({ conversation: serializeConversation(row), messages: [] }, 201);
  })
  .get("/:id", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    const messages = await listMessages(row.id);
    return c.json({ conversation: serializeConversation(row), messages: messages.map(serializeMessage) });
  })
  .delete("/:id", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    await db.delete(conversation).where(eq(conversation.id, row.id));
    return c.body(null, 204);
  })
  .post("/:id/messages", chatLimit, async (c) => {
    const userId = c.get("user").id;
    const row = await requireConversation(userId, c.req.param("id"));
    const body = await parseBody(c, messageSchema);
    const result = await sendUserMessage(row, await getProfile(userId), body.text, body.viaVoice ?? false);
    return c.json({
      conversation: serializeConversation(result.conversation),
      userMessage: serializeMessage(result.userMessage),
      reply: serializeMessage(result.assistantMessage),
      lessonCompleted: result.lessonCompleted,
    });
  })
  .post("/:id/transcript", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    const { turns } = await parseBody(c, transcriptSchema);
    const saved = await appendTranscript(row, turns);
    return c.json({ messages: saved.map(serializeMessage) });
  });

export const messageRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .post("/:id/explain", chatLimit, async (c) => {
    const userId = c.get("user").id;
    const row = await db.query.message.findFirst({
      where: eq(message.id, c.req.param("id")),
      with: { conversation: true },
    });
    if (!row || row.conversation.userId !== userId) throw new HTTPException(404, { message: "Message not found" });
    if (row.explanation) return c.json({ explanation: row.explanation });

    const explanation = await explainGrammar(row.text, await getProfile(userId));
    await db.update(message).set({ explanation }).where(eq(message.id, row.id));
    return c.json({ explanation });
  });

export const tutorRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .post("/hint", hintLimit, async (c) => {
    const userId = c.get("user").id;
    const { text } = await parseBody(c, hintSchema);
    return c.json({ hint: await generateTypingHint(text, await getProfile(userId)) });
  });
