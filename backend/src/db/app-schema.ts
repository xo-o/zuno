import { relations } from "drizzle-orm";
import { boolean, index, integer, jsonb, pgTable, text, timestamp, uniqueIndex } from "drizzle-orm/pg-core";
import { user } from "./auth-schema.js";

/** Learning preferences. One row per user, created lazily on first read. */
export const userProfile = pgTable("user_profile", {
  userId: text("user_id")
    .primaryKey()
    .references(() => user.id, { onDelete: "cascade" }),
  targetLanguage: text("target_language").default("es").notNull(),
  nativeLanguage: text("native_language").default("en").notNull(),
  level: text("level").default("B1").notNull(),
  createdAt: timestamp("created_at").defaultNow().notNull(),
  updatedAt: timestamp("updated_at")
    .defaultNow()
    .$onUpdate(() => new Date())
    .notNull(),
});

export const topic = pgTable(
  "topic",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    prompt: text("prompt").notNull(),
    title: text("title").notNull(),
    translation: text("translation").notNull(),
    language: text("language").notNull(),
    /** Digest of the learning material the topic was built from; lesson tutors use it as context. */
    material: text("material"),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at")
      .defaultNow()
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [index("topic_userId_idx").on(table.userId)],
);

export const topicLesson = pgTable(
  "topic_lesson",
  {
    id: text("id").primaryKey(),
    topicId: text("topic_id")
      .notNull()
      .references(() => topic.id, { onDelete: "cascade" }),
    position: integer("position").notNull(),
    title: text("title").notNull(),
    translation: text("translation").notNull(),
    objective: text("objective").notNull(),
    completedAt: timestamp("completed_at"),
  },
  (table) => [uniqueIndex("topic_lesson_topic_position_idx").on(table.topicId, table.position)],
);

export const roleplay = pgTable(
  "roleplay",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    title: text("title").notNull(),
    scenario: text("scenario").notNull(),
    userRole: text("user_role").notNull(),
    aiRole: text("ai_role").notNull(),
    setting: text("setting").notNull(),
    language: text("language").notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at")
      .defaultNow()
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [index("roleplay_userId_idx").on(table.userId)],
);

/** A conversation thread: Free Talk, a roleplay run, or a topic lesson. */
export const conversation = pgTable(
  "conversation",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    kind: text("kind", { enum: ["freetalk", "roleplay", "lesson"] }).notNull(),
    title: text("title").notNull(),
    language: text("language").notNull(),
    roleplayId: text("roleplay_id").references(() => roleplay.id, { onDelete: "cascade" }),
    topicId: text("topic_id").references(() => topic.id, { onDelete: "cascade" }),
    lessonId: text("lesson_id").references(() => topicLesson.id, { onDelete: "cascade" }),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at").defaultNow().notNull(),
  },
  (table) => [
    index("conversation_userId_updatedAt_idx").on(table.userId, table.updatedAt),
    index("conversation_lessonId_idx").on(table.lessonId),
  ],
);

export const message = pgTable(
  "message",
  {
    id: text("id").primaryKey(),
    conversationId: text("conversation_id")
      .notNull()
      .references(() => conversation.id, { onDelete: "cascade" }),
    role: text("role", { enum: ["user", "assistant"] }).notNull(),
    text: text("text").notNull(),
    translation: text("translation"),
    feedback: text("feedback"),
    glossary: jsonb("glossary").$type<string[]>(),
    explanation: text("explanation"),
    viaVoice: boolean("via_voice").default(false).notNull(),
    /** Short "Read menu photo · 4 items found" line shown above replies to messages with attachments. */
    contextNote: text("context_note"),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [index("message_conversationId_createdAt_idx").on(table.conversationId, table.createdAt)],
);

/** What Zuno read from an attachment once, reused as text context on later turns. */
export type AttachmentDigest = {
  label: string;
  summary: string;
  items: string[];
  text: string;
  pageCount?: number;
  sectionCount?: number;
};

/**
 * A file the learner uploaded to R2 (or a pasted link). Created unbound while uploading, then
 * linked to the user message (and its conversation) when sent, or to a topic as learning material.
 */
export const attachment = pgTable(
  "attachment",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    conversationId: text("conversation_id").references(() => conversation.id, { onDelete: "cascade" }),
    messageId: text("message_id").references(() => message.id, { onDelete: "cascade" }),
    topicId: text("topic_id").references(() => topic.id, { onDelete: "cascade" }),
    kind: text("kind", { enum: ["image", "document", "link"] }).notNull(),
    mimeType: text("mime_type").notNull(),
    /** Page title for links. */
    fileName: text("file_name").notNull(),
    sizeBytes: integer("size_bytes").notNull(),
    /** Empty for links, which have nothing stored in R2. */
    storageKey: text("storage_key").notNull(),
    url: text("url"),
    width: integer("width"),
    height: integer("height"),
    status: text("status", { enum: ["pending", "processing", "ready", "failed"] })
      .default("pending")
      .notNull(),
    digest: jsonb("digest").$type<AttachmentDigest>(),
    error: text("error"),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [
    index("attachment_messageId_idx").on(table.messageId),
    index("attachment_conversationId_idx").on(table.conversationId),
    index("attachment_topicId_idx").on(table.topicId),
    index("attachment_userId_status_createdAt_idx").on(table.userId, table.status, table.createdAt),
  ],
);

export const topicRelations = relations(topic, ({ many }) => ({
  lessons: many(topicLesson),
}));

export const topicLessonRelations = relations(topicLesson, ({ one }) => ({
  topic: one(topic, { fields: [topicLesson.topicId], references: [topic.id] }),
}));

export const conversationRelations = relations(conversation, ({ many, one }) => ({
  messages: many(message),
  roleplay: one(roleplay, { fields: [conversation.roleplayId], references: [roleplay.id] }),
  topic: one(topic, { fields: [conversation.topicId], references: [topic.id] }),
  lesson: one(topicLesson, { fields: [conversation.lessonId], references: [topicLesson.id] }),
}));

export const messageRelations = relations(message, ({ one, many }) => ({
  conversation: one(conversation, { fields: [message.conversationId], references: [conversation.id] }),
  attachments: many(attachment),
}));

export const attachmentRelations = relations(attachment, ({ one }) => ({
  message: one(message, { fields: [attachment.messageId], references: [message.id] }),
}));
