import { createHmac, timingSafeEqual } from "node:crypto";
import { and, count, eq, gte, inArray, lt, sql } from "drizzle-orm";
import { chatReminder, conversation, job, message, notificationPrefs, topic } from "../db/app-schema.js";
import { user } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { quote, t } from "./copy.js";
import { sendEmail } from "./email.js";
import { enqueue, registerJob, registerSweep } from "./jobs.js";
import type { AppLanguageCode } from "./languages.js";
import { getProfile } from "./profile.js";
import { sendToUser } from "./push.js";
import { publicBaseUrl } from "./serializers.js";
import { addDays, formatDate, isValidTimeZone, localDateOf, localParts, parseTime, zonedTime } from "./time.js";

export type NotificationPrefs = typeof notificationPrefs.$inferSelect;

export async function getNotificationPrefs(userId: string): Promise<NotificationPrefs> {
  const existing = await db.query.notificationPrefs.findFirst({ where: eq(notificationPrefs.userId, userId) });
  if (existing) return existing;
  await db.insert(notificationPrefs).values({ userId }).onConflictDoNothing();
  return (await db.query.notificationPrefs.findFirst({ where: eq(notificationPrefs.userId, userId) }))!;
}

export async function updateNotificationPrefs(
  userId: string,
  patch: Partial<Omit<NotificationPrefs, "userId" | "createdAt" | "updatedAt">>,
) {
  await getNotificationPrefs(userId);
  if (Object.keys(patch).length) {
    await db.update(notificationPrefs).set(patch).where(eq(notificationPrefs.userId, userId));
  }
  return getNotificationPrefs(userId);
}

/** Devices report their zone; daily reminders and schedules follow the latest one. */
export async function updateTimeZone(userId: string, timezone: string | undefined) {
  if (!timezone || !isValidTimeZone(timezone)) return;
  await getNotificationPrefs(userId);
  await db.update(notificationPrefs).set({ timezone }).where(eq(notificationPrefs.userId, userId));
}

export function serializeNotificationPrefs(prefs: NotificationPrefs) {
  return {
    dailyReminder: prefs.dailyReminder,
    reminderTime: prefs.reminderTime,
    timezone: prefs.timezone,
    scheduledSessions: prefs.scheduledSessions,
    progressMilestones: prefs.progressMilestones,
    newContent: prefs.newContent,
    weeklySummaryEmail: prefs.weeklySummaryEmail,
    productNewsEmail: prefs.productNewsEmail,
  };
}

/** Language name in the learner's app language ("español" → "Spanish" for an English UI). */
export function displayLanguage(code: string, appLanguage: AppLanguageCode) {
  try {
    const name = new Intl.DisplayNames([appLanguage], { type: "language" }).of(code);
    if (name) return name;
  } catch {
    // Fall through to the code.
  }
  return code;
}

// ── Email unsubscribe ─────────────────────────────────────────────────────────

export type EmailKind = "weekly" | "news" | "schedules";

function secret() {
  return process.env.BETTER_AUTH_SECRET || "zuno-dev-secret";
}

export function unsubscribeUrl(userId: string, kind: EmailKind) {
  const payload = Buffer.from(`${userId}:${kind}`).toString("base64url");
  const signature = createHmac("sha256", secret()).update(payload).digest("base64url");
  return `${publicBaseUrl()}/api/email/unsubscribe?token=${payload}.${signature}`;
}

export function verifyUnsubscribe(token: string): { userId: string; kind: EmailKind } | null {
  const [payload, signature] = token.split(".");
  if (!payload || !signature) return null;
  const expected = createHmac("sha256", secret()).update(payload).digest();
  const given = Buffer.from(signature, "base64url");
  if (given.length !== expected.length || !timingSafeEqual(given, expected)) return null;
  const [userId, kind] = Buffer.from(payload, "base64url").toString("utf8").split(":");
  if (!userId || !["weekly", "news", "schedules"].includes(kind)) return null;
  return { userId, kind: kind as EmailKind };
}

export async function applyUnsubscribe(userId: string, kind: EmailKind) {
  if (kind === "weekly") await updateNotificationPrefs(userId, { weeklySummaryEmail: false });
  else if (kind === "news") await updateNotificationPrefs(userId, { productNewsEmail: false });
  else await db.execute(sql`update schedule set notify_email = false where user_id = ${userId}`);
}

async function userContact(userId: string) {
  const [row] = await db.select({ email: user.email, name: user.name }).from(user).where(eq(user.id, userId));
  return row;
}

// ── "Remind me to continue" ───────────────────────────────────────────────────

export const CHAT_REMINDER_JOB = "chat_reminder";

registerJob(CHAT_REMINDER_JOB, async (payload) => {
  const reminder = await db.query.chatReminder.findFirst({
    where: eq(chatReminder.id, String(payload.reminderId)),
    with: { conversation: true },
  });
  if (!reminder || reminder.status !== "pending") return;
  const profile = await getProfile(reminder.userId);
  const [last] = await db
    .select({ text: message.text })
    .from(message)
    .where(and(eq(message.conversationId, reminder.conversationId), eq(message.role, "assistant")))
    .orderBy(sql`${message.createdAt} desc`)
    .limit(1);
  const lang = profile.nativeLanguage;
  await sendToUser(reminder.userId, {
    type: "reminder",
    title: t(lang, "reminderTitle", { title: reminder.conversation.title }),
    body: last?.text ? t(lang, "reminderBody", { line: quote(last.text) }) : t(lang, "reminderBodyEmpty"),
    data: { conversationId: reminder.conversationId, reminderId: reminder.id },
  });
  await db.update(chatReminder).set({ status: "sent" }).where(eq(chatReminder.id, reminder.id));
});

// ── Progress milestones ───────────────────────────────────────────────────────

export const TOPIC_COMPLETED_JOB = "topic_completed";

registerJob(TOPIC_COMPLETED_JOB, async (payload) => {
  const row = await db.query.topic.findFirst({ where: eq(topic.id, String(payload.topicId)), with: { lessons: true } });
  if (!row) return;
  const prefs = await getNotificationPrefs(row.userId);
  if (!prefs.progressMilestones) return;
  const profile = await getProfile(row.userId);
  await sendToUser(row.userId, {
    type: "progress",
    title: t(profile.nativeLanguage, "topicDoneTitle", { title: row.title }),
    body: t(profile.nativeLanguage, "topicDoneBody", { count: row.lessons.length }),
    data: { topicId: row.id },
  });
});

/** Queues the "Topic complete" push once per topic (re-completing after a reset doesn't repeat it). */
export async function onTopicCompleted(topicId: string) {
  await enqueue(TOPIC_COMPLETED_JOB, new Date(), { topicId }, `topic_completed:${topicId}`);
}

// ── Daily practice reminder ───────────────────────────────────────────────────

export const DAILY_REMINDER_JOB = "daily_reminder";
/** The reminder still goes out if the backend was down at the exact minute, within this window. */
const DAILY_WINDOW_MIN = 60;

async function practicedSince(userId: string, since: Date) {
  const [row] = await db
    .select({ n: count() })
    .from(message)
    .innerJoin(conversation, eq(message.conversationId, conversation.id))
    .where(and(eq(conversation.userId, userId), eq(message.role, "user"), gte(message.createdAt, since)));
  return (row?.n ?? 0) > 0;
}

registerSweep(async () => {
  const now = new Date();
  const rows = await db
    .select({ userId: notificationPrefs.userId, reminderTime: notificationPrefs.reminderTime, timezone: notificationPrefs.timezone })
    .from(notificationPrefs)
    .where(eq(notificationPrefs.dailyReminder, true));
  for (const row of rows) {
    const time = parseTime(row.reminderTime);
    if (!time || !isValidTimeZone(row.timezone)) continue;
    const today = localDateOf(now, row.timezone);
    const at = zonedTime({ ...today, ...time }, row.timezone);
    const minutesLate = (now.getTime() - at.getTime()) / 60_000;
    if (minutesLate < 0 || minutesLate > DAILY_WINDOW_MIN) continue;
    await enqueue(DAILY_REMINDER_JOB, now, { userId: row.userId, date: formatDate(today) }, `daily:${row.userId}:${formatDate(today)}`);
  }
});

registerJob(DAILY_REMINDER_JOB, async (payload) => {
  const userId = String(payload.userId);
  const prefs = await getNotificationPrefs(userId);
  if (!prefs.dailyReminder) return;
  const today = localDateOf(new Date(), prefs.timezone);
  const midnight = zonedTime({ ...today, hour: 0, minute: 0 }, prefs.timezone);
  if (await practicedSince(userId, midnight)) return;
  const profile = await getProfile(userId);
  const lang = profile.nativeLanguage;
  await sendToUser(userId, {
    type: "progress",
    title: t(lang, "dailyTitle", { language: displayLanguage(profile.targetLanguage, lang) }),
    body: t(lang, "dailyBody", { goal: profile.dailyGoalMin }),
    data: {},
  });
});

// ── Weekly summary email (Sunday evening, local time) ─────────────────────────

export const WEEKLY_SUMMARY_JOB = "weekly_summary";
const WEEKLY_HOUR = 18;

registerSweep(async () => {
  const now = new Date();
  const rows = await db
    .select({ userId: notificationPrefs.userId, timezone: notificationPrefs.timezone })
    .from(notificationPrefs)
    .where(eq(notificationPrefs.weeklySummaryEmail, true));
  for (const row of rows) {
    if (!isValidTimeZone(row.timezone)) continue;
    const local = localParts(now, row.timezone);
    if (local.weekday !== 6 || local.hour !== WEEKLY_HOUR) continue;
    const date = formatDate(local);
    await enqueue(WEEKLY_SUMMARY_JOB, now, { userId: row.userId, date }, `weekly:${row.userId}:${date}`);
  }
});

registerJob(WEEKLY_SUMMARY_JOB, async (payload) => {
  const userId = String(payload.userId);
  const prefs = await getNotificationPrefs(userId);
  if (!prefs.weeklySummaryEmail) return;
  const contact = await userContact(userId);
  if (!contact?.email) return;
  const profile = await getProfile(userId);
  const lang = profile.nativeLanguage;

  const today = localDateOf(new Date(), prefs.timezone);
  const since = zonedTime({ ...addDays(today, -6), hour: 0, minute: 0 }, prefs.timezone);
  const [stats] = await db
    .select({
      messages: count(message.id),
      sessions: sql<number>`count(distinct ${message.conversationId})`,
      voice: sql<number>`count(*) filter (where ${message.viaVoice})`,
    })
    .from(message)
    .innerJoin(conversation, eq(message.conversationId, conversation.id))
    .where(and(eq(conversation.userId, userId), eq(message.role, "user"), gte(message.createdAt, since)));
  const messages = Number(stats?.messages ?? 0);
  const sessions = Number(stats?.sessions ?? 0);
  // Rough practice time: ~1 minute per typed turn, ~30 s per spoken turn.
  const minutes = Math.max(1, Math.round(messages - Number(stats?.voice ?? 0) * 0.5));
  const language = displayLanguage(profile.targetLanguage, lang);

  await sendEmail({
    to: contact.email,
    subject: t(lang, "weeklySubject"),
    heading: t(lang, "weeklySubject"),
    paragraphs: [messages ? t(lang, "weeklyBody", { language, sessions, messages, minutes }) : t(lang, "weeklyIdle", { language })],
    footerHtml: `<a href="${unsubscribeUrl(userId, "weekly")}" style="color:#71717a;">${t(lang, "emailUnsubscribe")}</a>`,
    headers: { "List-Unsubscribe": `<${unsubscribeUrl(userId, "weekly")}>` },
  });
});

// ── Housekeeping ──────────────────────────────────────────────────────────────

let lastCleanup = 0;
registerSweep(async () => {
  if (Date.now() - lastCleanup < 6 * 60 * 60 * 1000) return;
  lastCleanup = Date.now();
  const cutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
  await db.delete(job).where(and(inArray(job.status, ["done", "cancelled", "failed"]), lt(job.createdAt, cutoff)));
});
