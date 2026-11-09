import { and, desc, eq } from "drizzle-orm";
import { conversation, roleplay, schedule, topic, topicLesson } from "../db/app-schema.js";
import { user } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { t } from "./copy.js";
import { sendEmail } from "./email.js";
import { cancelJob, enqueue, pollSoon, registerJob } from "./jobs.js";
import { getNotificationPrefs, unsubscribeUrl } from "./notifications.js";
import { getProfile } from "./profile.js";
import { sendToUser } from "./push.js";
import { computeNextRun, type ScheduleRule } from "./schedules.js";
import { topicProgressLabel } from "./serializers.js";

export type ScheduleRow = typeof schedule.$inferSelect;
type TopicWithLessons = typeof topic.$inferSelect & { lessons: (typeof topicLesson.$inferSelect)[] };
export type ScheduleWithTarget = ScheduleRow & {
  topic: TopicWithLessons | null;
  roleplay: typeof roleplay.$inferSelect | null;
};

export const SCHEDULE_REMIND_JOB = "schedule_remind";
export const SCHEDULE_ADVANCE_JOB = "schedule_advance";

export function ruleOf(row: Pick<ScheduleRow, "repeat" | "timeOfDay" | "date" | "weekdays" | "monthDay" | "timezone" | "skipDates">): ScheduleRule {
  return {
    repeat: row.repeat,
    timeOfDay: row.timeOfDay,
    date: row.date,
    weekdays: row.weekdays,
    monthDay: row.monthDay,
    timezone: row.timezone,
    skipDates: row.skipDates,
  };
}

function jobKeys(id: string, nextRunAt: Date) {
  const at = nextRunAt.toISOString();
  return { remind: `schedule_remind:${id}:${at}`, advance: `schedule_advance:${id}:${at}` };
}

/**
 * Recomputes the next occurrence from now and queues its reminder and its "advance" job. Jobs for an
 * older occurrence are cancelled; any that still fire check nextRunAt and do nothing.
 */
export async function armSchedule(row: ScheduleRow, after = new Date()): Promise<ScheduleRow> {
  if (row.nextRunAt) {
    const old = jobKeys(row.id, row.nextRunAt);
    await Promise.all([cancelJob(old.remind), cancelJob(old.advance)]);
  }
  const nextRunAt = row.active ? computeNextRun(ruleOf(row), after) : null;
  const [updated] = await db.update(schedule).set({ nextRunAt }).where(eq(schedule.id, row.id)).returning();
  if (nextRunAt) {
    const keys = jobKeys(row.id, nextRunAt);
    const payload = { scheduleId: row.id, nextRunAt: nextRunAt.toISOString() };
    const remindAt = new Date(nextRunAt.getTime() - (row.remindBeforeMin ?? 0) * 60_000);
    await enqueue(SCHEDULE_REMIND_JOB, remindAt, payload, keys.remind);
    // Move on to the following occurrence once this session's slot is over.
    await enqueue(SCHEDULE_ADVANCE_JOB, new Date(nextRunAt.getTime() + row.durationMin * 60_000), payload, keys.advance);
    if (remindAt.getTime() - Date.now() < 60_000) pollSoon();
  }
  return updated;
}

export async function loadSchedule(id: string) {
  return db.query.schedule.findFirst({
    where: eq(schedule.id, id),
    with: { topic: { with: { lessons: true } }, roleplay: true },
  }) as Promise<ScheduleWithTarget | undefined>;
}

function nextLesson(row: TopicWithLessons) {
  const sorted = [...row.lessons].sort((a, b) => a.position - b.position);
  return sorted.find((lesson) => !lesson.completedAt) ?? sorted[0];
}

/** Title + "Topic · Lesson 2" / "You: Customer · Zuno: Barista" for lists and notifications. */
export function describeTarget(row: ScheduleWithTarget) {
  if (row.targetKind === "roleplay" && row.roleplay) {
    return {
      title: row.roleplay.title,
      meta: `You: ${row.roleplay.userRole} · Zuno: ${row.roleplay.aiRole}`,
      userRole: row.roleplay.userRole,
      aiRole: row.roleplay.aiRole,
    };
  }
  if (row.topic) {
    const lesson = nextLesson(row.topic);
    const done = row.topic.lessons.length > 0 && row.topic.lessons.every((l) => l.completedAt);
    return {
      title: row.topic.title,
      meta: done ? "Topic · Review" : `Topic · Lesson ${lesson?.position ?? 1}`,
      lessonTitle: lesson?.title ?? row.topic.title,
      progress: topicProgressLabel(row.topic.lessons),
    };
  }
  return { title: "Practice", meta: "" };
}

registerJob(SCHEDULE_REMIND_JOB, async (payload) => {
  const row = await loadSchedule(String(payload.scheduleId));
  if (!row || !row.active || !row.nextRunAt || row.nextRunAt.toISOString() !== payload.nextRunAt) return;
  const nextRunAt = row.nextRunAt;
  const prefs = await getNotificationPrefs(row.userId);
  const profile = await getProfile(row.userId);
  const lang = profile.nativeLanguage;
  const target = describeTarget(row);
  const minutesLeft = Math.round((nextRunAt.getTime() - Date.now()) / 60_000);
  const title =
    minutesLeft >= 1
      ? t(lang, row.targetKind === "roleplay" ? "scheduleSoonRoleplay" : "scheduleSoonTopic", { min: minutesLeft, title: target.title })
      : t(lang, "scheduleNow", { title: target.title });
  const body =
    row.targetKind === "roleplay" && "userRole" in target
      ? t(lang, "scheduleBodyRoleplay", { user: target.userRole ?? "", ai: target.aiRole ?? "" })
      : t(lang, "scheduleBodyTopic", { lesson: "lessonTitle" in target ? (target.lessonTitle ?? target.title) : target.title, min: row.durationMin });

  if (prefs.scheduledSessions) {
    await sendToUser(row.userId, {
      type: "schedule",
      title,
      body,
      data: {
        scheduleId: row.id,
        topicId: row.topicId ?? undefined,
        roleplayId: row.roleplayId ?? undefined,
        startsAt: nextRunAt.toISOString(),
      },
    });
  }
  if (row.notifyEmail) {
    const contact = await db.query.user.findFirst({ where: eq(user.id, row.userId) });
    if (contact?.email) {
      await sendEmail({
        to: contact.email,
        subject: t(lang, "emailScheduleSubject", { title: target.title }),
        heading: title,
        paragraphs: [body],
        footerHtml: `<a href="${unsubscribeUrl(row.userId, "schedules")}" style="color:#71717a;">${t(lang, "emailUnsubscribe")}</a>`,
      });
    }
  }
});

registerJob(SCHEDULE_ADVANCE_JOB, async (payload) => {
  const row = await db.query.schedule.findFirst({ where: eq(schedule.id, String(payload.scheduleId)) });
  if (!row || !row.nextRunAt || row.nextRunAt.toISOString() !== payload.nextRunAt) return;
  const slot = row.nextRunAt;
  if (row.repeat === "once") {
    // A one-off is done once its slot has passed.
    await db.update(schedule).set({ active: false, nextRunAt: null }).where(eq(schedule.id, row.id));
    return;
  }
  await armSchedule(row, new Date(Math.max(Date.now(), slot.getTime())));
});

/**
 * Opens the session a schedule points to: the topic's next unfinished lesson (its conversation,
 * created with the tutor's opening if needed), or the roleplay's latest run.
 */
export async function startScheduledSession(row: ScheduleWithTarget) {
  const { createConversation } = await import("./conversations.js");
  const profile = await getProfile(row.userId);
  if (row.targetKind === "roleplay" && row.roleplay) {
    const latest = await db.query.conversation.findFirst({
      where: and(eq(conversation.roleplayId, row.roleplay.id), eq(conversation.userId, row.userId)),
      orderBy: [desc(conversation.updatedAt)],
    });
    if (latest) return latest;
    return createConversation(
      { userId: row.userId, kind: "roleplay", title: row.roleplay.title, language: row.roleplay.language, roleplayId: row.roleplay.id },
      profile,
      { withOpening: true },
    );
  }
  if (row.topic) {
    const lesson = nextLesson(row.topic);
    if (!lesson) throw new Error("Topic has no lessons");
    const existing = await db.query.conversation.findFirst({
      where: and(eq(conversation.userId, row.userId), eq(conversation.lessonId, lesson.id)),
      orderBy: [desc(conversation.updatedAt)],
    });
    if (existing) return existing;
    return createConversation(
      {
        userId: row.userId,
        kind: "lesson",
        title: lesson.title,
        language: row.topic.language,
        topicId: row.topic.id,
        lessonId: lesson.id,
      },
      profile,
      { withOpening: true },
    );
  }
  throw new Error("Schedule target is gone");
}
