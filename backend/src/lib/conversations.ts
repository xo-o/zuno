import { randomUUID } from "node:crypto";
import { and, asc, eq, isNull, sql } from "drizzle-orm";
import { conversation, message, roleplay, topic, topicLesson } from "../db/app-schema.js";
import { db } from "../db/index.js";
import type { LearnerProfile } from "./profile.js";
import { generateOpening, generateReply, LESSONS_PER_TOPIC, type ConversationContext } from "./tutor.js";

export type ConversationRow = typeof conversation.$inferSelect;

export const DEFAULT_FREE_TALK_TITLE = "Free Talk";

/** Keeps the tutor's context window bounded on long conversations. */
const HISTORY_LIMIT = 30;

export async function findConversation(userId: string, id: string) {
  return db.query.conversation.findFirst({
    where: and(eq(conversation.id, id), eq(conversation.userId, userId)),
  });
}

export async function listMessages(conversationId: string) {
  return db.query.message.findMany({
    where: eq(message.conversationId, conversationId),
    orderBy: [asc(message.createdAt)],
  });
}

export async function contextFor(row: ConversationRow): Promise<ConversationContext> {
  if (row.kind === "roleplay" && row.roleplayId) {
    const rp = await db.query.roleplay.findFirst({ where: eq(roleplay.id, row.roleplayId) });
    if (rp) return { kind: "roleplay", scenario: rp.scenario, userRole: rp.userRole, aiRole: rp.aiRole, setting: rp.setting };
  }
  if (row.kind === "lesson" && row.lessonId && row.topicId) {
    const [lesson, parent] = await Promise.all([
      db.query.topicLesson.findFirst({ where: eq(topicLesson.id, row.lessonId) }),
      db.query.topic.findFirst({ where: eq(topic.id, row.topicId) }),
    ]);
    if (lesson && parent) {
      return {
        kind: "lesson",
        topicTitle: parent.title,
        lessonTitle: lesson.title,
        objective: lesson.objective,
        position: lesson.position,
        total: LESSONS_PER_TOPIC,
      };
    }
  }
  return { kind: "freetalk" };
}

/** Bumps the conversation and its parent topic/roleplay so they float to the top of "Recent sessions". */
async function touch(row: ConversationRow) {
  const now = sql`now()`;
  await db.update(conversation).set({ updatedAt: now }).where(eq(conversation.id, row.id));
  if (row.topicId) await db.update(topic).set({ updatedAt: now }).where(eq(topic.id, row.topicId));
  if (row.roleplayId) await db.update(roleplay).set({ updatedAt: now }).where(eq(roleplay.id, row.roleplayId));
}

export async function createConversation(
  values: Omit<typeof conversation.$inferInsert, "id">,
  profile: LearnerProfile,
  options: { withOpening: boolean },
) {
  const [row] = await db.insert(conversation).values({ id: randomUUID(), ...values }).returning();
  if (options.withOpening) {
    const opening = await generateOpening(await contextFor(row), profile);
    await db.insert(message).values({
      id: randomUUID(),
      conversationId: row.id,
      role: "assistant",
      text: opening.reply,
      translation: opening.translation,
      glossary: opening.glossary,
    });
  }
  await touch(row);
  return row;
}

/** Stores the learner's turn, asks the tutor for a reply and applies lesson completion. */
export async function sendUserMessage(row: ConversationRow, profile: LearnerProfile, text: string, viaVoice: boolean) {
  const history = await listMessages(row.id);
  const recent = history.slice(-HISTORY_LIMIT);
  const turns = [
    ...recent.map((m) => ({ role: m.role === "user" ? ("user" as const) : ("model" as const), text: m.text })),
    { role: "user" as const, text },
  ];
  // Gemini requires the conversation to start with a user turn.
  if (turns[0]?.role === "model") turns.unshift({ role: "user", text: "(The learner opened the conversation.)" });

  const needsTitle = row.kind === "freetalk" && row.title === DEFAULT_FREE_TALK_TITLE;
  const reply = await generateReply({ context: await contextFor(row), profile, history: turns, needsTitle });

  const [userMessage, assistantMessage] = await db
    .insert(message)
    .values([
      {
        id: randomUUID(),
        conversationId: row.id,
        role: "user",
        text,
        feedback: reply.feedback,
        viaVoice,
        createdAt: sql`now()`,
      },
      {
        id: randomUUID(),
        conversationId: row.id,
        role: "assistant",
        text: reply.reply,
        translation: reply.translation,
        glossary: reply.glossary,
        createdAt: sql`now() + interval '1 millisecond'`,
      },
    ])
    .returning();

  let updated = row;
  const title = reply.title.trim();
  if (needsTitle && title) {
    [updated] = await db.update(conversation).set({ title }).where(eq(conversation.id, row.id)).returning();
  }

  let lessonCompleted = false;
  if (row.kind === "lesson" && row.lessonId && reply.objectiveMet) {
    const done = await db
      .update(topicLesson)
      .set({ completedAt: new Date() })
      .where(and(eq(topicLesson.id, row.lessonId), isNull(topicLesson.completedAt)))
      .returning();
    lessonCompleted = done.length > 0;
  }

  await touch(updated);
  return { userMessage, assistantMessage, conversation: updated, lessonCompleted };
}

/** Saves turns spoken in a realtime voice session (already answered by the voice agent). */
export async function appendTranscript(row: ConversationRow, turns: { role: "user" | "assistant"; text: string }[]) {
  if (turns.length === 0) return [];
  const rows = await db
    .insert(message)
    .values(
      turns.map((turn, index) => ({
        id: randomUUID(),
        conversationId: row.id,
        role: turn.role,
        text: turn.text,
        viaVoice: true,
        createdAt: sql`now() + make_interval(secs => ${index / 1000})`,
      })),
    )
    .returning();
  await touch(row);
  return rows;
}
