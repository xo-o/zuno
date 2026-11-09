import { randomUUID } from "node:crypto";
import { and, desc, eq, sql } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { roleplay, schedule, topic } from "../db/app-schema.js";
import { db } from "../db/index.js";
import { listMessages } from "../lib/conversations.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { enqueue, pollSoon } from "../lib/jobs.js";
import { getNotificationPrefs } from "../lib/notifications.js";
import { getProfile } from "../lib/profile.js";
import {
  armSchedule,
  describeTarget,
  loadSchedule,
  SCHEDULE_REMIND_JOB,
  startScheduledSession,
  type ScheduleWithTarget,
} from "../lib/schedule-service.js";
import { occurrenceDate } from "../lib/schedules.js";
import { serializeConversation, serializeMessages, topicProgressLabel } from "../lib/serializers.js";
import { isValidTimeZone, parseDate } from "../lib/time.js";

const TOPIC_MINUTES = 15;
const ROLEPLAY_MINUTES = 10;

const ruleFields = {
  repeat: z.enum(["once", "daily", "weekdays", "weekly", "monthly"]),
  timeOfDay: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Use HH:mm"),
  date: z
    .string()
    .refine((value) => parseDate(value) !== null, "Use YYYY-MM-DD")
    .nullable()
    .optional(),
  weekdays: z.array(z.number().int().min(0).max(6)).max(7).optional(),
  monthDay: z.number().int().min(1).max(31).nullable().optional(),
  timezone: z.string().max(64).refine(isValidTimeZone, "Unknown time zone").optional(),
  durationMin: z.number().int().min(5).max(120).optional(),
  active: z.boolean().optional(),
  remindBeforeMin: z.number().int().min(0).max(24 * 60).nullable().optional(),
  notifyEmail: z.boolean().optional(),
};

const createSchema = z
  .object({
    targetKind: z.enum(["topic", "roleplay"]),
    topicId: z.string().min(1).max(64).optional(),
    roleplayId: z.string().min(1).max(64).optional(),
    ...ruleFields,
  })
  .refine((body) => (body.targetKind === "topic" ? !!body.topicId : !!body.roleplayId), {
    message: "Pick a topic or roleplay",
    path: ["targetKind"],
  });

const updateSchema = z.object(ruleFields).partial();
const snoozeSchema = z.object({ minutes: z.number().int().min(1).max(120).default(10) });

function validateRule(rule: { repeat: string; date?: string | null; weekdays?: number[]; monthDay?: number | null }) {
  if (rule.repeat === "once" && !rule.date) throw new HTTPException(400, { message: "Pick a date" });
  if (rule.repeat === "weekly" && !rule.weekdays?.length) throw new HTTPException(400, { message: "Pick at least one day" });
  if (rule.repeat === "monthly" && !rule.monthDay) throw new HTTPException(400, { message: "Pick a day of the month" });
}

function serializeSchedule(row: ScheduleWithTarget) {
  const target = describeTarget(row);
  return {
    id: row.id,
    targetKind: row.targetKind,
    topicId: row.topicId,
    roleplayId: row.roleplayId,
    title: target.title,
    meta: target.meta,
    repeat: row.repeat,
    timeOfDay: row.timeOfDay,
    date: row.date,
    weekdays: row.weekdays,
    monthDay: row.monthDay,
    timezone: row.timezone,
    durationMin: row.durationMin,
    active: row.active,
    remindBeforeMin: row.remindBeforeMin,
    notifyEmail: row.notifyEmail,
    skipDates: row.skipDates,
    nextRunAt: row.nextRunAt?.toISOString() ?? null,
    language: row.language,
  };
}

async function requireSchedule(userId: string, id: string) {
  const row = await loadSchedule(id);
  if (!row || row.userId !== userId) throw new HTTPException(404, { message: "Schedule not found" });
  return row;
}

const startLimit = rateLimit({ windowMs: 60_000, max: 10 });

export const scheduleRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/", async (c) => {
    const userId = c.get("user").id;
    const { targetLanguage } = await getProfile(userId);
    const rows = (await db.query.schedule.findMany({
      where: and(eq(schedule.userId, userId), eq(schedule.language, targetLanguage)),
      with: { topic: { with: { lessons: true } }, roleplay: true },
      orderBy: [sql`${schedule.nextRunAt} asc nulls last`, desc(schedule.createdAt)],
    })) as ScheduleWithTarget[];
    return c.json({ schedules: rows.map(serializeSchedule) });
  })
  /** Step 1 of New schedule: topics (with their next lesson) and roleplays in the current language. */
  .get("/targets", async (c) => {
    const userId = c.get("user").id;
    const { targetLanguage } = await getProfile(userId);
    const [topics, roleplays] = await Promise.all([
      db.query.topic.findMany({
        where: and(eq(topic.userId, userId), eq(topic.language, targetLanguage)),
        with: { lessons: true },
        orderBy: [desc(topic.updatedAt)],
      }),
      db.query.roleplay.findMany({
        where: and(eq(roleplay.userId, userId), eq(roleplay.language, targetLanguage)),
        orderBy: [desc(roleplay.updatedAt)],
      }),
    ]);
    return c.json({
      topics: topics.map((row) => {
        const lessons = [...row.lessons].sort((a, b) => a.position - b.position);
        const next = lessons.find((lesson) => !lesson.completedAt);
        return {
          id: row.id,
          title: row.title,
          totalLessons: lessons.length,
          nextLesson: next ? { position: next.position, title: next.title } : null,
          completed: !next && lessons.length > 0,
          progress: topicProgressLabel(row.lessons),
          durationMin: TOPIC_MINUTES,
        };
      }),
      roleplays: roleplays.map((row) => ({
        id: row.id,
        title: row.title,
        userRole: row.userRole,
        aiRole: row.aiRole,
        durationMin: ROLEPLAY_MINUTES,
      })),
    });
  })
  .post("/", async (c) => {
    const userId = c.get("user").id;
    const body = await parseBody(c, createSchema);
    validateRule(body);
    const profile = await getProfile(userId);
    const target =
      body.targetKind === "topic"
        ? await db.query.topic.findFirst({ where: and(eq(topic.id, body.topicId!), eq(topic.userId, userId)) })
        : await db.query.roleplay.findFirst({ where: and(eq(roleplay.id, body.roleplayId!), eq(roleplay.userId, userId)) });
    if (!target) throw new HTTPException(404, { message: body.targetKind === "topic" ? "Topic not found" : "Roleplay not found" });
    const timezone = body.timezone ?? (await getNotificationPrefs(userId)).timezone;

    const [row] = await db
      .insert(schedule)
      .values({
        id: randomUUID(),
        userId,
        targetKind: body.targetKind,
        topicId: body.targetKind === "topic" ? target.id : null,
        roleplayId: body.targetKind === "roleplay" ? target.id : null,
        repeat: body.repeat,
        timeOfDay: body.timeOfDay,
        date: body.repeat === "once" ? body.date : null,
        weekdays: body.repeat === "weekly" ? [...new Set(body.weekdays)].sort() : [],
        monthDay: body.repeat === "monthly" ? body.monthDay : null,
        timezone,
        durationMin: body.durationMin ?? (body.targetKind === "topic" ? TOPIC_MINUTES : ROLEPLAY_MINUTES),
        active: body.active ?? true,
        remindBeforeMin: body.remindBeforeMin === undefined ? 10 : body.remindBeforeMin,
        notifyEmail: body.notifyEmail ?? false,
        language: target.language || profile.targetLanguage,
      })
      .returning();
    const armed = await armSchedule(row);
    if (armed.repeat === "once" && !armed.nextRunAt) {
      await db.delete(schedule).where(eq(schedule.id, armed.id));
      throw new HTTPException(400, { message: "Pick a time in the future" });
    }
    return c.json({ schedule: serializeSchedule((await loadSchedule(row.id))!) }, 201);
  })
  .patch("/:id", async (c) => {
    const userId = c.get("user").id;
    const current = await requireSchedule(userId, c.req.param("id"));
    const body = await parseBody(c, updateSchema);
    const repeat = body.repeat ?? current.repeat;
    const merged = {
      repeat,
      date: body.date !== undefined ? body.date : current.date,
      weekdays: body.weekdays ?? current.weekdays,
      monthDay: body.monthDay !== undefined ? body.monthDay : current.monthDay,
    };
    validateRule(merged);
    const ruleChanged =
      body.repeat !== undefined || body.timeOfDay !== undefined || body.date !== undefined || body.weekdays !== undefined ||
      body.monthDay !== undefined || body.timezone !== undefined;
    const [row] = await db
      .update(schedule)
      .set({
        ...body,
        date: repeat === "once" ? merged.date : null,
        weekdays: repeat === "weekly" ? [...new Set(merged.weekdays)].sort() : [],
        monthDay: repeat === "monthly" ? merged.monthDay : null,
        // A new time or repeat rule starts fresh: earlier skips no longer apply.
        ...(ruleChanged && { skipDates: [] }),
      })
      .where(eq(schedule.id, current.id))
      .returning();
    await armSchedule(row);
    return c.json({ schedule: serializeSchedule((await loadSchedule(row.id))!) });
  })
  .delete("/:id", async (c) => {
    const row = await requireSchedule(c.get("user").id, c.req.param("id"));
    await armSchedule({ ...row, active: false });
    await db.delete(schedule).where(eq(schedule.id, row.id));
    return c.body(null, 204);
  })
  /** "Skip tomorrow only": skips the next occurrence; the rule continues after it. */
  .post("/:id/skip-next", async (c) => {
    const row = await requireSchedule(c.get("user").id, c.req.param("id"));
    if (!row.nextRunAt) throw new HTTPException(409, { message: "Nothing to skip" });
    const skipDates = [...new Set([...row.skipDates, occurrenceDate(row.nextRunAt, row.timezone)])].sort().slice(-60);
    const [updated] = await db.update(schedule).set({ skipDates }).where(eq(schedule.id, row.id)).returning();
    const armed = await armSchedule(updated);
    if (armed.repeat === "once") await db.update(schedule).set({ active: false }).where(eq(schedule.id, row.id));
    return c.json({ schedule: serializeSchedule((await loadSchedule(row.id))!) });
  })
  /** Notification "Snooze": reminds again in a few minutes without changing the schedule. */
  .post("/:id/snooze", async (c) => {
    const row = await requireSchedule(c.get("user").id, c.req.param("id"));
    const { minutes } = await parseBody(c, snoozeSchema);
    if (!row.nextRunAt) throw new HTTPException(409, { message: "Nothing to snooze" });
    const at = new Date(Date.now() + minutes * 60_000);
    await enqueue(SCHEDULE_REMIND_JOB, at, { scheduleId: row.id, nextRunAt: row.nextRunAt.toISOString(), snoozed: true });
    if (minutes <= 1) pollSoon();
    return c.json({ remindAt: at.toISOString() });
  })
  /** Start now: opens the next lesson (or the roleplay) and returns its conversation. */
  .post("/:id/start", startLimit, async (c) => {
    const row = await requireSchedule(c.get("user").id, c.req.param("id"));
    const thread = await startScheduledSession(row);
    const messages = await listMessages(thread.id);
    return c.json({ conversationId: thread.id, conversation: serializeConversation(thread), messages: await serializeMessages(messages) });
  });

