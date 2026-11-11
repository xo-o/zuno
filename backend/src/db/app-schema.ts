import { relations } from "drizzle-orm";
import { boolean, date, index, integer, jsonb, pgTable, primaryKey, text, timestamp, uniqueIndex } from "drizzle-orm/pg-core";
import { user } from "./auth-schema.js";

/**
 * Learning preferences. One row per user, created lazily on first read. `level` and `accent` are the
 * current target language's values (denormalised from user_language_progress for the tutor).
 */
export const userProfile = pgTable("user_profile", {
  userId: text("user_id")
    .primaryKey()
    .references(() => user.id, { onDelete: "cascade" }),
  targetLanguage: text("target_language").default("es").notNull(),
  nativeLanguage: text("native_language").default("en").notNull(),
  level: text("level").default("B1").notNull(),
  accent: text("accent"),
  // Preferences & learning goals (Settings · Preferences / Learning)
  tutorVoice: text("tutor_voice").default("warm").notNull(),
  teachingStyle: text("teaching_style").default("balanced").notNull(),
  strictMode: boolean("strict_mode").default(false).notNull(),
  handsFree: boolean("hands_free").default(true).notNull(),
  suggestReplies: boolean("suggest_replies").default(true).notNull(),
  showTranslations: boolean("show_translations").default(true).notNull(),
  teachInTarget: boolean("teach_in_target").default(false).notNull(),
  dailyGoalMin: integer("daily_goal_min").default(15).notNull(),
  focus: text("focus").default("conversation").notNull(),
  plan: text("plan", { enum: ["free", "pro"] }).default("free").notNull(),
  planExpiresAt: timestamp("plan_expires_at"),
  createdAt: timestamp("created_at").defaultNow().notNull(),
  updatedAt: timestamp("updated_at")
    .defaultNow()
    .$onUpdate(() => new Date())
    .notNull(),
});

/** Level and accent per language the learner has studied, so switching back restores them. */
export const userLanguageProgress = pgTable(
  "user_language_progress",
  {
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    language: text("language").notNull(),
    level: text("level").default("B1").notNull(),
    accent: text("accent"),
    /** Null until a level check (or a manual level pick) has set the level for this language. */
    levelCheckedAt: timestamp("level_checked_at"),
    lastActiveAt: timestamp("last_active_at").defaultNow().notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at")
      .defaultNow()
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [primaryKey({ columns: [table.userId, table.language] })],
);

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

export type LessonCurriculum = {
  targetPhrases: Array<{
    id: string;
    setupNative?: string;
    phrase: string;
    translation: string;
    focusWord: string;
    syllables: string;
    pronunciationCue: string;
    chunks: string[];
  }>;
  buildingBlocks: Array<{
    promptNative: string;
    newWord: string;
    meaning: string;
    stemHint: string;
    expectedTarget: string;
  }>;
  recallPrompts: Array<{
    situationNative: string;
    firstWordHint: string;
    expectedTargets: string[];
  }>;
  roleplay: {
    aiRole: string;
    setting: string;
    goals: Array<{
      id: string;
      label: string;
      description: string;
    }>;
  };
};

export type CoachNoteItem = {
  category: string;
  correction: string;
  ttsSampleText: string;
  kind: "grammar" | "pronunciation";
};

export type PhraseResultItem = {
  phrase: string;
  status: "mastered" | "practice";
};

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
    curriculum: jsonb("curriculum").$type<LessonCurriculum>(),
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
    pinnedAt: timestamp("pinned_at"),
    visibility: text("visibility", { enum: ["private", "public"] }).default("private").notNull(),
    /** Public link id (/s/:token). Kept when the chat goes back to private, so re-sharing reuses the link. */
    shareToken: text("share_token").unique(),
    /** Set once the learner renames the chat, so the tutor stops auto-titling it. */
    titleEdited: boolean("title_edited").default(false).notNull(),
    /** A "Find my level" Free Talk: the tutor assesses the learner's CEFR level. */
    levelCheck: boolean("level_check").default(false).notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at").defaultNow().notNull(),
  },
  (table) => [
    index("conversation_userId_updatedAt_idx").on(table.userId, table.updatedAt),
    index("conversation_lessonId_idx").on(table.lessonId),
  ],
);

export const chatReport = pgTable(
  "chat_report",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    conversationId: text("conversation_id")
      .notNull()
      .references(() => conversation.id, { onDelete: "cascade" }),
    reason: text("reason", {
      enum: ["wrong_correction", "wrong_language_level", "offensive", "unsafe", "other"],
    }).notNull(),
    details: text("details"),
    status: text("status").default("open").notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [index("chat_report_conversationId_idx").on(table.conversationId)],
);

/** A signed-in app install that can receive push notifications (FCM). */
export const device = pgTable(
  "device",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    platform: text("platform", { enum: ["android"] }).default("android").notNull(),
    pushToken: text("push_token").notNull().unique(),
    appLanguage: text("app_language"),
    /** IANA zone, e.g. "Europe/Madrid". */
    timezone: text("timezone"),
    lastSeenAt: timestamp("last_seen_at").defaultNow().notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at")
      .defaultNow()
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [index("device_userId_idx").on(table.userId)],
);

/**
 * Durable background jobs, polled with FOR UPDATE SKIP LOCKED so several backend instances can share
 * the queue. [dedupeKey] makes enqueueing idempotent (e.g. one daily reminder per user per day).
 */
export const job = pgTable(
  "job",
  {
    id: text("id").primaryKey(),
    type: text("type").notNull(),
    runAt: timestamp("run_at").notNull(),
    payload: jsonb("payload").$type<Record<string, unknown>>().default({}).notNull(),
    status: text("status", { enum: ["pending", "running", "done", "failed", "cancelled"] })
      .default("pending")
      .notNull(),
    attempts: integer("attempts").default(0).notNull(),
    lockedAt: timestamp("locked_at"),
    lastError: text("last_error"),
    dedupeKey: text("dedupe_key").unique(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [index("job_status_runAt_idx").on(table.status, table.runAt)],
);

/** "Remind me to continue": a push that reopens the chat as the learner left it. */
export const chatReminder = pgTable(
  "chat_reminder",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    conversationId: text("conversation_id")
      .notNull()
      .references(() => conversation.id, { onDelete: "cascade" }),
    remindAt: timestamp("remind_at").notNull(),
    status: text("status", { enum: ["pending", "sent", "cancelled"] }).default("pending").notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [index("chat_reminder_userId_status_idx").on(table.userId, table.status)],
);

/** Unsent composer text per conversation, so a chat reopens exactly as left on any device. */
export const conversationDraft = pgTable("conversation_draft", {
  conversationId: text("conversation_id")
    .primaryKey()
    .references(() => conversation.id, { onDelete: "cascade" }),
  text: text("text").default("").notNull(),
  attachmentIds: jsonb("attachment_ids").$type<string[]>().default([]).notNull(),
  updatedAt: timestamp("updated_at")
    .defaultNow()
    .$onUpdate(() => new Date())
    .notNull(),
});

export const notificationPrefs = pgTable("notification_prefs", {
  userId: text("user_id")
    .primaryKey()
    .references(() => user.id, { onDelete: "cascade" }),
  dailyReminder: boolean("daily_reminder").default(true).notNull(),
  /** Local time "HH:mm". */
  reminderTime: text("reminder_time").default("19:00").notNull(),
  timezone: text("timezone").default("UTC").notNull(),
  scheduledSessions: boolean("scheduled_sessions").default(true).notNull(),
  progressMilestones: boolean("progress_milestones").default(true).notNull(),
  newContent: boolean("new_content").default(false).notNull(),
  weeklySummaryEmail: boolean("weekly_summary_email").default(true).notNull(),
  productNewsEmail: boolean("product_news_email").default(false).notNull(),
  createdAt: timestamp("created_at").defaultNow().notNull(),
  updatedAt: timestamp("updated_at")
    .defaultNow()
    .$onUpdate(() => new Date())
    .notNull(),
});

/** A planned practice session (topic lesson or roleplay) with a repeat rule, evaluated in [timezone]. */
export const schedule = pgTable(
  "schedule",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    targetKind: text("target_kind", { enum: ["topic", "roleplay"] }).notNull(),
    topicId: text("topic_id").references(() => topic.id, { onDelete: "cascade" }),
    roleplayId: text("roleplay_id").references(() => roleplay.id, { onDelete: "cascade" }),
    repeat: text("repeat", { enum: ["once", "daily", "weekdays", "weekly", "monthly"] }).notNull(),
    /** Local "HH:mm". */
    timeOfDay: text("time_of_day").notNull(),
    /** Local date (once). */
    date: date("date", { mode: "string" }),
    /** 0 = Monday … 6 = Sunday (weekly). */
    weekdays: jsonb("weekdays").$type<number[]>().default([]).notNull(),
    /** 1–31, clamped to the month's last day (monthly). */
    monthDay: integer("month_day"),
    timezone: text("timezone").notNull(),
    durationMin: integer("duration_min").default(15).notNull(),
    active: boolean("active").default(true).notNull(),
    remindBeforeMin: integer("remind_before_min").default(10),
    notifyEmail: boolean("notify_email").default(false).notNull(),
    skipDates: jsonb("skip_dates").$type<string[]>().default([]).notNull(),
    /** Next occurrence; null once a one-off has run or nothing is left. */
    nextRunAt: timestamp("next_run_at"),
    language: text("language").notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at")
      .defaultNow()
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [index("schedule_userId_idx").on(table.userId)],
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

export const topicLessonRelations = relations(topicLesson, ({ one, many }) => ({
  topic: one(topic, { fields: [topicLesson.topicId], references: [topic.id] }),
  evaluations: many(lessonEvaluation),
}));

export const lessonEvaluation = pgTable(
  "lesson_evaluation",
  {
    id: text("id").primaryKey(),
    lessonId: text("lesson_id")
      .notNull()
      .references(() => topicLesson.id, { onDelete: "cascade" }),
    conversationId: text("conversation_id").references(() => conversation.id, { onDelete: "set null" }),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    durationSec: integer("duration_sec").notNull(),
    pronunciationScore: integer("pronunciation_score").notNull(),
    goalsCompleted: integer("goals_completed").notNull(),
    goalsTotal: integer("goals_total").notNull(),
    fluencyLabel: text("fluency_label", { enum: ["Developing", "Steady", "Natural", "Fluent"] }).notNull(),
    coachNotes: jsonb("coach_notes").$type<CoachNoteItem[]>().default([]).notNull(),
    phraseResults: jsonb("phrase_results").$type<PhraseResultItem[]>().default([]).notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [
    index("lesson_evaluation_lessonId_idx").on(table.lessonId, table.createdAt),
    index("lesson_evaluation_userId_idx").on(table.userId, table.createdAt),
  ],
);

export const lessonEvaluationRelations = relations(lessonEvaluation, ({ one }) => ({
  lesson: one(topicLesson, { fields: [lessonEvaluation.lessonId], references: [topicLesson.id] }),
  conversation: one(conversation, { fields: [lessonEvaluation.conversationId], references: [conversation.id] }),
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

export const scheduleRelations = relations(schedule, ({ one }) => ({
  topic: one(topic, { fields: [schedule.topicId], references: [topic.id] }),
  roleplay: one(roleplay, { fields: [schedule.roleplayId], references: [roleplay.id] }),
}));

export const chatReminderRelations = relations(chatReminder, ({ one }) => ({
  conversation: one(conversation, { fields: [chatReminder.conversationId], references: [conversation.id] }),
}));
