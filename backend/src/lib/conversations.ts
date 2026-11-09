import { randomUUID } from "node:crypto";
import { and, asc, eq, inArray, isNull, sql, type SQL } from "drizzle-orm";
import { attachment, conversation, message, roleplay, topic, topicLesson } from "../db/app-schema.js";
import { db } from "../db/index.js";
import {
  describeAttachment,
  filesForTurn,
  loadImageFiles,
  recentImages,
  type AttachmentRow,
} from "./attachments.js";
import type { GeminiTurn } from "./gemini.js";
import { updateProfile, type LearnerProfile } from "./profile.js";
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
    with: { attachments: { orderBy: [asc(attachment.createdAt)] } },
  });
}

export type MessageWithAttachments = Awaited<ReturnType<typeof listMessages>>[number];

export async function conversationIdsWhere(where: SQL | undefined) {
  const rows = await db.select({ id: conversation.id }).from(conversation).where(where);
  return rows.map((row) => row.id);
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
        material: parent.material,
      };
    }
  }
  return row.levelCheck ? { kind: "levelcheck" } : { kind: "freetalk" };
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
      translation: opening.translation.trim() || null,
      glossary: opening.glossary,
    });
  }
  await touch(row);
  return row;
}

/**
 * Builds the tutor's view of the conversation. Files shared earlier appear as their digest
 * text; the most recent images are resent as pixels; new files are sent in full.
 */
async function buildTurns(recent: MessageWithAttachments[], text: string, attachments: AttachmentRow[]) {
  const resent = await loadImageFiles(recentImages(recent.flatMap((m) => m.attachments)));
  const turns: GeminiTurn[] = recent.map((m) => {
    const notes = m.attachments.map(describeAttachment);
    return {
      role: m.role === "user" ? "user" : "model",
      text: [...notes, m.text].filter(Boolean).join("\n") || "(sent an attachment)",
      files: m.attachments.map((a) => resent.get(a.id)).filter((file) => file !== undefined),
    };
  });
  const current = await filesForTurn(attachments);
  turns.push({
    role: "user",
    text: [...current.notes, text || "(The learner shared this without a message.)"].join("\n"),
    files: current.files,
  });
  // Gemini requires the conversation to start with a user turn.
  if (turns[0]?.role === "model") turns.unshift({ role: "user", text: "(The learner opened the conversation.)" });
  return turns;
}

/** Stores the learner's turn, asks the tutor for a reply and applies lesson completion. */
export async function sendUserMessage(
  row: ConversationRow,
  profile: LearnerProfile,
  text: string,
  viaVoice: boolean,
  attachments: AttachmentRow[] = [],
) {
  const history = await listMessages(row.id);
  const turns = await buildTurns(history.slice(-HISTORY_LIMIT), text, attachments);

  const needsTitle = row.kind === "freetalk" && !row.titleEdited && row.title === DEFAULT_FREE_TALK_TITLE;
  const reply = await generateReply({
    context: await contextFor(row),
    profile,
    history: turns,
    needsTitle,
    withAttachments: attachments.length > 0,
  });

  const { userMessage, assistantMessage, linked } = await db.transaction(async (tx) => {
    const [userMessage, assistantMessage] = await tx
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
          translation: reply.translation.trim() || null,
          glossary: reply.glossary,
          contextNote: attachments.length ? reply.contextNote.trim() || null : null,
          createdAt: sql`now() + interval '1 millisecond'`,
        },
      ])
      .returning();
    const linked = attachments.length
      ? await tx
          .update(attachment)
          .set({ messageId: userMessage.id, conversationId: row.id })
          .where(
            and(
              inArray(
                attachment.id,
                attachments.map((a) => a.id),
              ),
              isNull(attachment.messageId),
            ),
          )
          .returning()
      : [];
    return { userMessage, assistantMessage, linked };
  });

  let updated = row;
  const title = reply.title.trim();
  if (needsTitle && title) {
    const [titled] = await db
      .update(conversation)
      .set({ title })
      .where(and(eq(conversation.id, row.id), eq(conversation.titleEdited, false)))
      .returning();
    updated = titled ?? row;
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

  // A level check ends once the tutor has assessed the learner: that becomes their level for this language.
  let levelAssessed: string | null = null;
  if (row.levelCheck && reply.assessedLevel && row.language === profile.targetLanguage) {
    await updateProfile(row.userId, { level: reply.assessedLevel, markLevelChecked: true });
    levelAssessed = reply.assessedLevel;
  }

  await touch(updated);
  const ordered = attachments.map((a) => linked.find((l) => l.id === a.id) ?? a);
  return {
    userMessage: { ...userMessage, attachments: ordered },
    assistantMessage: { ...assistantMessage, attachments: [] as AttachmentRow[] },
    conversation: updated,
    lessonCompleted,
    levelAssessed,
  };
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
