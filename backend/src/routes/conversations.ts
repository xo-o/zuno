import { randomBytes, randomUUID } from "node:crypto";
import { and, desc, eq, exists, isNotNull, sql } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { chatReminder, chatReport, conversation, conversationDraft, message, roleplay, topic } from "../db/app-schema.js";
import { db } from "../db/index.js";
import {
  appendTranscript,
  createConversation,
  DEFAULT_FREE_TALK_TITLE,
  findConversation,
  listMessages,
  sendUserMessage,
} from "../lib/conversations.js";
import { attachmentsForSend, collectConversationFiles, MAX_ATTACHMENTS_PER_MESSAGE } from "../lib/attachments.js";
import { sendEmail } from "../lib/email.js";
import { cancelJob, enqueue, pollSoon } from "../lib/jobs.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { CHAT_REMINDER_JOB, onTopicCompleted } from "../lib/notifications.js";
import { getProfile } from "../lib/profile.js";
import { serializeConversation, serializeMessage, serializeMessages, shareUrlOf, topicProgressLabel } from "../lib/serializers.js";
import { explainGrammar, generateTypingHint } from "../lib/tutor.js";

const createSchema = z.object({
  kind: z.literal("freetalk").default("freetalk"),
  /** "Find my level": the tutor runs a short level check and sets the learner's level. */
  levelCheck: z.boolean().default(false),
});
const messageSchema = z
  .object({
    text: z.string().trim().max(1000).default(""),
    viaVoice: z.boolean().optional(),
    attachmentIds: z.array(z.string().min(1).max(64)).max(MAX_ATTACHMENTS_PER_MESSAGE).default([]),
  })
  .refine((body) => body.text.length > 0 || body.attachmentIds.length > 0, {
    message: "Write a message or attach a file",
    path: ["text"],
  });
const transcriptSchema = z.object({
  turns: z
    .array(z.object({ role: z.enum(["user", "assistant"]), text: z.string().trim().min(1).max(2000) }))
    .max(200),
});
const hintSchema = z.object({ text: z.string().trim().min(1).max(300) });
const updateSchema = z
  .object({
    title: z.string().trim().min(1).max(80).optional(),
    pinned: z.boolean().optional(),
  })
  .refine((body) => body.title !== undefined || body.pinned !== undefined, { message: "Nothing to update" });
const shareSchema = z.object({ visibility: z.enum(["private", "public"]) });
const reportSchema = z.object({
  reason: z.enum(["wrong_correction", "wrong_language_level", "offensive", "unsafe", "other"]),
  details: z.string().trim().max(1000).optional(),
});
const reminderSchema = z.object({ remindAt: z.iso.datetime({ offset: true }) });
const draftSchema = z.object({
  text: z.string().max(4000).default(""),
  attachmentIds: z.array(z.string().min(1).max(64)).max(MAX_ATTACHMENTS_PER_MESSAGE).default([]),
});

async function requireConversation(userId: string, id: string) {
  const row = await findConversation(userId, id);
  if (!row) throw new HTTPException(404, { message: "Conversation not found" });
  return row;
}

const chatLimit = rateLimit({ windowMs: 60_000, max: 30 });
const hintLimit = rateLimit({ windowMs: 60_000, max: 40 });
const reportLimit = rateLimit({ windowMs: 60 * 60_000, max: 10 });

/** Latest pin time among a topic's or roleplay's conversations (pinning a lesson pins its topic row). */
function latestPin(rows: { pinnedAt: Date | null }[]) {
  return rows.reduce<Date | null>((latest, row) => (row.pinnedAt && (!latest || row.pinnedAt > latest) ? row.pinnedAt : latest), null);
}

export const conversationRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  /**
   * Drawer sessions for the current learning language: Free Talk threads plus one row per topic and
   * roleplay. Pinned rows come first (latest pin first), then the rest by latest activity.
   */
  .get("/recent", async (c) => {
    const userId = c.get("user").id;
    const limit = Math.min(Number(c.req.query("limit")) || 20, 50);
    const { targetLanguage } = await getProfile(userId);
    const [freeTalks, topics, roleplays, pinnedChildren] = await Promise.all([
      db.query.conversation.findMany({
        // Skip Free Talk threads that were opened but never used.
        where: and(
          eq(conversation.userId, userId),
          eq(conversation.kind, "freetalk"),
          eq(conversation.language, targetLanguage),
          exists(db.select({ id: message.id }).from(message).where(eq(message.conversationId, conversation.id))),
        ),
        orderBy: [desc(conversation.updatedAt)],
        limit: limit + 20,
      }),
      db.query.topic.findMany({
        where: and(eq(topic.userId, userId), eq(topic.language, targetLanguage)),
        with: { lessons: true },
        orderBy: [desc(topic.updatedAt)],
        limit: limit + 20,
      }),
      db.query.roleplay.findMany({
        where: and(eq(roleplay.userId, userId), eq(roleplay.language, targetLanguage)),
        orderBy: [desc(roleplay.updatedAt)],
        limit: limit + 20,
      }),
      db
        .select({ topicId: conversation.topicId, roleplayId: conversation.roleplayId, id: conversation.id, pinnedAt: conversation.pinnedAt })
        .from(conversation)
        .where(and(eq(conversation.userId, userId), isNotNull(conversation.pinnedAt), sql`${conversation.kind} <> 'freetalk'`)),
    ]);

    const sessions = [
      ...freeTalks.map((row) => ({
        kind: "freetalk",
        id: row.id,
        conversationId: row.id,
        title: row.title,
        meta: "Free Talk",
        at: row.updatedAt,
        pinnedAt: row.pinnedAt,
      })),
      ...topics.map((row) => {
        const pins = pinnedChildren.filter((child) => child.topicId === row.id);
        return {
          kind: "topic",
          id: row.id,
          conversationId: pins[0]?.id ?? null,
          title: row.title,
          meta: `Topic · ${topicProgressLabel(row.lessons)}`,
          at: row.updatedAt,
          pinnedAt: latestPin(pins),
        };
      }),
      ...roleplays.map((row) => {
        const pins = pinnedChildren.filter((child) => child.roleplayId === row.id);
        return {
          kind: "roleplay",
          id: row.id,
          conversationId: pins[0]?.id ?? null,
          title: row.title,
          meta: "Roleplay",
          at: row.updatedAt,
          pinnedAt: latestPin(pins),
        };
      }),
    ];
    const pinned = sessions
      .filter((s) => s.pinnedAt)
      .sort((a, b) => b.pinnedAt!.getTime() - a.pinnedAt!.getTime());
    const rest = sessions
      .filter((s) => !s.pinnedAt)
      .sort((a, b) => b.at.getTime() - a.at.getTime())
      .slice(0, limit);

    return c.json({
      sessions: [...pinned, ...rest].map(({ at, pinnedAt, ...row }) => ({
        ...row,
        pinned: pinnedAt !== null,
        pinnedAt: pinnedAt?.toISOString() ?? null,
        updatedAt: at.toISOString(),
      })),
    });
  })
  .post("/", async (c) => {
    const userId = c.get("user").id;
    const body = await parseBody(c, createSchema);
    const profile = await getProfile(userId);
    const row = await createConversation(
      {
        userId,
        kind: "freetalk",
        title: body.levelCheck ? "Level check" : DEFAULT_FREE_TALK_TITLE,
        titleEdited: body.levelCheck,
        levelCheck: body.levelCheck,
        language: profile.targetLanguage,
      },
      profile,
      { withOpening: false },
    );
    return c.json({ conversation: serializeConversation(row), messages: [] }, 201);
  })
  .get("/:id", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    const messages = await listMessages(row.id);
    return c.json({ conversation: serializeConversation(row), messages: await serializeMessages(messages) });
  })
  /** Rename and/or pin. A rename stops the tutor from auto-titling the chat. */
  .patch("/:id", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    const body = await parseBody(c, updateSchema);
    const [updated] = await db
      .update(conversation)
      .set({
        ...(body.title !== undefined && { title: body.title, titleEdited: true }),
        ...(body.pinned !== undefined && { pinnedAt: body.pinned ? new Date() : null }),
      })
      .where(eq(conversation.id, row.id))
      .returning();
    return c.json({ conversation: serializeConversation(updated) });
  })
  .delete("/:id", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    const removeFiles = await collectConversationFiles([row.id]);
    await db.delete(conversation).where(eq(conversation.id, row.id));
    await removeFiles();
    return c.body(null, 204);
  })
  /** Only me / Public access. The first public share mints the link; going private keeps it but hides the page. */
  .put("/:id/share", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    const { visibility } = await parseBody(c, shareSchema);
    const [updated] = await db
      .update(conversation)
      .set({ visibility, ...(visibility === "public" && !row.shareToken && { shareToken: randomBytes(18).toString("base64url") }) })
      .where(eq(conversation.id, row.id))
      .returning();
    return c.json({ visibility: updated.visibility, shareUrl: shareUrlOf(updated) });
  })
  .post("/:id/report", reportLimit, async (c) => {
    const current = c.get("user");
    const row = await requireConversation(current.id, c.req.param("id"));
    const body = await parseBody(c, reportSchema);
    const id = randomUUID();
    await db.insert(chatReport).values({
      id,
      userId: current.id,
      conversationId: row.id,
      reason: body.reason,
      details: body.details || null,
    });
    const to = process.env.REPORTS_EMAIL;
    if (to) {
      sendEmail({
        to,
        subject: `Chat report: ${body.reason}`,
        heading: `Report on “${row.title}”`,
        paragraphs: [
          `Reason: ${body.reason}`,
          `Details: ${body.details || "(none)"}`,
          `Conversation: ${row.id} (${row.kind}, ${row.language})`,
          `Reporter: ${current.email} (${current.id})`,
          `Report id: ${id}`,
        ],
      }).catch((err) => console.error("Report email failed:", err));
    }
    return c.json({ id }, 201);
  })
  /** "Remind me to continue": a push at [remindAt] that reopens this chat. */
  .post("/:id/reminders", async (c) => {
    const userId = c.get("user").id;
    const row = await requireConversation(userId, c.req.param("id"));
    const { remindAt } = await parseBody(c, reminderSchema);
    const at = new Date(remindAt);
    if (at.getTime() < Date.now() - 60_000) throw new HTTPException(400, { message: "Pick a time in the future" });
    // One pending reminder per chat: a new one replaces the old.
    const previous = await db
      .update(chatReminder)
      .set({ status: "cancelled" })
      .where(and(eq(chatReminder.conversationId, row.id), eq(chatReminder.status, "pending")))
      .returning({ id: chatReminder.id });
    await Promise.all(previous.map((p) => cancelJob(`reminder:${p.id}`)));
    const id = randomUUID();
    await db.insert(chatReminder).values({ id, userId, conversationId: row.id, remindAt: at });
    await enqueue(CHAT_REMINDER_JOB, at, { reminderId: id }, `reminder:${id}`);
    if (at.getTime() - Date.now() < 60_000) pollSoon();
    return c.json({ id, remindAt: at.toISOString() }, 201);
  })
  .get("/:id/draft", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    const draft = await db.query.conversationDraft.findFirst({ where: eq(conversationDraft.conversationId, row.id) });
    return c.json({ text: draft?.text ?? "", attachmentIds: draft?.attachmentIds ?? [], updatedAt: draft?.updatedAt.toISOString() ?? null });
  })
  .put("/:id/draft", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    const body = await parseBody(c, draftSchema);
    if (!body.text.trim() && body.attachmentIds.length === 0) {
      await db.delete(conversationDraft).where(eq(conversationDraft.conversationId, row.id));
    } else {
      await db
        .insert(conversationDraft)
        .values({ conversationId: row.id, text: body.text, attachmentIds: body.attachmentIds })
        .onConflictDoUpdate({ target: conversationDraft.conversationId, set: { text: body.text, attachmentIds: body.attachmentIds } });
    }
    return c.body(null, 204);
  })
  .post("/:id/messages", chatLimit, async (c) => {
    const userId = c.get("user").id;
    const row = await requireConversation(userId, c.req.param("id"));
    const body = await parseBody(c, messageSchema);
    const attachments = await attachmentsForSend(userId, body.attachmentIds);
    const result = await sendUserMessage(row, await getProfile(userId), body.text, body.viaVoice ?? false, attachments);
    if (result.lessonCompleted && row.topicId) await checkTopicCompleted(row.topicId);
    await db.delete(conversationDraft).where(eq(conversationDraft.conversationId, row.id));
    return c.json({
      conversation: serializeConversation(result.conversation),
      userMessage: await serializeMessage(result.userMessage),
      reply: await serializeMessage(result.assistantMessage),
      lessonCompleted: result.lessonCompleted,
      levelAssessed: result.levelAssessed,
    });
  })
  .post("/:id/transcript", async (c) => {
    const row = await requireConversation(c.get("user").id, c.req.param("id"));
    const { turns } = await parseBody(c, transcriptSchema);
    const saved = await appendTranscript(row, turns);
    return c.json({ messages: await serializeMessages(saved) });
  });

/** Queues the "Topic complete" milestone once every lesson is done. */
export async function checkTopicCompleted(topicId: string) {
  const row = await db.query.topic.findFirst({ where: eq(topic.id, topicId), with: { lessons: true } });
  if (row && row.lessons.length > 0 && row.lessons.every((lesson) => lesson.completedAt)) await onTopicCompleted(row.id);
}

export const reminderRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/", async (c) => {
    const userId = c.get("user").id;
    const status = c.req.query("status");
    const rows = await db.query.chatReminder.findMany({
      where: and(
        eq(chatReminder.userId, userId),
        status === "pending" || status === "sent" || status === "cancelled" ? eq(chatReminder.status, status) : undefined,
      ),
      with: { conversation: true },
      orderBy: [desc(chatReminder.remindAt)],
      limit: 50,
    });
    return c.json({
      reminders: rows.map((row) => ({
        id: row.id,
        conversationId: row.conversationId,
        title: row.conversation.title,
        remindAt: row.remindAt.toISOString(),
        status: row.status,
      })),
    });
  })
  /** Undo. */
  .delete("/:id", async (c) => {
    const userId = c.get("user").id;
    const [row] = await db
      .update(chatReminder)
      .set({ status: "cancelled" })
      .where(and(eq(chatReminder.id, c.req.param("id")), eq(chatReminder.userId, userId), eq(chatReminder.status, "pending")))
      .returning();
    if (row) await cancelJob(`reminder:${row.id}`);
    return c.body(null, 204);
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
    const profile = await getProfile(userId);
    // "Suggest a reply" off: no live tips while typing.
    if (!profile.suggestReplies) return c.json({ hint: null });
    return c.json({ hint: await generateTypingHint(text, profile) });
  });

