import { and, desc, eq, gte, inArray, sql } from "drizzle-orm";
import {
  conversation,
  lessonEvaluation,
  notificationPrefs,
  peerCallRoom,
  topic,
  topicLesson,
  userDailyActivity,
  vocabPhrase,
} from "../db/app-schema.js";
import { user } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { resolveUserImageUrl } from "./avatars.js";
import type { LearnerProfile } from "./profile.js";
import { addDays, formatDate, isValidTimeZone, localDateOf, localParts, parseDate, weekdayOf, zonedTime } from "./time.js";

export type DailyActivityDelta = {
  spokenSec?: number;
  messagesCount?: number;
  lessonsCompleted?: number;
  vocabReviewed?: number;
};

export type DailyActivityRowLike = {
  spokenSec: number;
  messagesCount: number;
  lessonsCompleted: number;
  vocabReviewed: number;
};

/** Pure predicate: whether a day's accumulated activity counts as an active streak day. */
export function isActiveDay(row: DailyActivityRowLike): boolean {
  return row.spokenSec >= 30 || row.messagesCount >= 2 || row.lessonsCompleted >= 1 || row.vocabReviewed >= 3;
}

/** Deterministic XP formula shared across live recording and historical backfill. */
export function computeXpDelta(delta: DailyActivityDelta): number {
  const spoken = Math.max(0, Math.round(delta.spokenSec ?? 0));
  const msgs = Math.max(0, Math.round(delta.messagesCount ?? 0));
  const lessons = Math.max(0, Math.round(delta.lessonsCompleted ?? 0));
  const vocab = Math.max(0, Math.round(delta.vocabReviewed ?? 0));
  return Math.round(spoken / 6) + msgs * 5 + lessons * 50 + vocab * 8;
}

/**
 * Computes consecutive active streak days ending at [todayLocalDate], with a 1-day grace window
 * (if today isn't active yet, yesterday's streak stays intact until local midnight).
 */
export function computeStreakDays(activeDates: Iterable<string>, todayLocalDate: string): number {
  const set = new Set<string>();
  for (const d of activeDates) set.add(d);
  const today = parseDate(todayLocalDate);
  if (!today) return 0;

  let cursor = set.has(todayLocalDate) ? today : addDays(today, -1);
  if (!set.has(formatDate(cursor))) return 0;

  let streak = 0;
  while (set.has(formatDate(cursor))) {
    streak += 1;
    cursor = addDays(cursor, -1);
  }
  return streak;
}

/** Returns ISO-week Monday YYYY-MM-DD and next-Monday reset timestamp in UTC. */
export function currentUtcWeekWindow(now = new Date()): { weekStartDate: string; resetsAt: string } {
  const utcToday = { year: now.getUTCFullYear(), month: now.getUTCMonth() + 1, day: now.getUTCDate() };
  const wd = weekdayOf(utcToday); // 0 = Mon .. 6 = Sun
  const monday = addDays(utcToday, -wd);
  const nextMonday = addDays(monday, 7);
  return {
    weekStartDate: formatDate(monday),
    resetsAt: new Date(Date.UTC(nextMonday.year, nextMonday.month - 1, nextMonday.day, 0, 0, 0)).toISOString(),
  };
}

async function resolveUserTimezone(userId: string): Promise<string> {
  const row = await db.query.notificationPrefs.findFirst({
    where: eq(notificationPrefs.userId, userId),
    columns: { timezone: true },
  });
  return row?.timezone && isValidTimeZone(row.timezone) ? row.timezone : "UTC";
}

/** Atomically records learner practice into `user_daily_activity` in their local calendar date. */
export async function recordPracticeActivity(
  userId: string,
  language: string,
  delta: DailyActivityDelta,
  now = new Date(),
): Promise<void> {
  const spokenSec = Math.max(0, Math.round(delta.spokenSec ?? 0));
  const messagesCount = Math.max(0, Math.round(delta.messagesCount ?? 0));
  const lessonsCompleted = Math.max(0, Math.round(delta.lessonsCompleted ?? 0));
  const vocabReviewed = Math.max(0, Math.round(delta.vocabReviewed ?? 0));
  if (spokenSec === 0 && messagesCount === 0 && lessonsCompleted === 0 && vocabReviewed === 0) return;

  const xp = computeXpDelta({ spokenSec, messagesCount, lessonsCompleted, vocabReviewed });
  const tz = await resolveUserTimezone(userId);
  const localDate = formatDate(localDateOf(now, tz));

  await db
    .insert(userDailyActivity)
    .values({
      userId,
      localDate,
      language,
      spokenSec,
      messagesCount,
      lessonsCompleted,
      vocabReviewed,
      xp,
    })
    .onConflictDoUpdate({
      target: [userDailyActivity.userId, userDailyActivity.localDate, userDailyActivity.language],
      set: {
        spokenSec: sql`${userDailyActivity.spokenSec} + ${spokenSec}`,
        messagesCount: sql`${userDailyActivity.messagesCount} + ${messagesCount}`,
        lessonsCompleted: sql`${userDailyActivity.lessonsCompleted} + ${lessonsCompleted}`,
        vocabReviewed: sql`${userDailyActivity.vocabReviewed} + ${vocabReviewed}`,
        xp: sql`${userDailyActivity.xp} + ${xp}`,
        updatedAt: new Date(),
      },
    });
}

export type UserStatsSummary = {
  streakDays: number;
  dailyMinutesDone: number;
  activeDaysTotal: number;
  weeklyXp: number;
  leagueRank: number | null;
};

/** Lightweight summary returned inside `GET /api/me` for Home & Drawer badges. */
export async function getUserSummaryStats(
  userId: string,
  targetLanguage: string,
  now = new Date(),
): Promise<UserStatsSummary> {
  const tz = await resolveUserTimezone(userId);
  const todayStr = formatDate(localDateOf(now, tz));
  const { weekStartDate } = currentUtcWeekWindow(now);

  // Aggregate across all languages per localDate for overall streak, plus today's spoken minutes in targetLanguage.
  const [userRows, weeklyTotals] = await Promise.all([
    db
      .select({
        localDate: userDailyActivity.localDate,
        language: userDailyActivity.language,
        spokenSec: userDailyActivity.spokenSec,
        messagesCount: userDailyActivity.messagesCount,
        lessonsCompleted: userDailyActivity.lessonsCompleted,
        vocabReviewed: userDailyActivity.vocabReviewed,
        xp: userDailyActivity.xp,
      })
      .from(userDailyActivity)
      .where(eq(userDailyActivity.userId, userId)),
    db
      .select({
        userId: userDailyActivity.userId,
        totalXp: sql<number>`coalesce(sum(${userDailyActivity.xp}), 0)::int`,
        totalSpokenSec: sql<number>`coalesce(sum(${userDailyActivity.spokenSec}), 0)::int`,
      })
      .from(userDailyActivity)
      .where(and(eq(userDailyActivity.language, targetLanguage), gte(userDailyActivity.localDate, weekStartDate)))
      .groupBy(userDailyActivity.userId)
      .orderBy(
        desc(sql`coalesce(sum(${userDailyActivity.xp}), 0)`),
        desc(sql`coalesce(sum(${userDailyActivity.spokenSec}), 0)`),
      ),
  ]);

  const byDate = new Map<string, DailyActivityRowLike>();
  let todaySpokenSec = 0;
  let weeklyXp = 0;
  for (const row of userRows) {
    const existing = byDate.get(row.localDate) ?? { spokenSec: 0, messagesCount: 0, lessonsCompleted: 0, vocabReviewed: 0 };
    existing.spokenSec += row.spokenSec;
    existing.messagesCount += row.messagesCount;
    existing.lessonsCompleted += row.lessonsCompleted;
    existing.vocabReviewed += row.vocabReviewed;
    byDate.set(row.localDate, existing);

    if (row.localDate === todayStr) {
      todaySpokenSec += row.spokenSec;
    }
    if (row.language === targetLanguage && row.localDate >= weekStartDate) {
      weeklyXp += row.xp;
    }
  }

  const activeDates: string[] = [];
  for (const [dateStr, agg] of byDate) {
    if (isActiveDay(agg)) activeDates.push(dateStr);
  }

  const streakDays = computeStreakDays(activeDates, todayStr);
  const rankIdx = weeklyTotals.findIndex((r) => r.userId === userId && r.totalXp > 0);
  const leagueRank = rankIdx >= 0 ? rankIdx + 1 : null;

  return {
    streakDays,
    dailyMinutesDone: Math.floor(todaySpokenSec / 60),
    activeDaysTotal: activeDates.length,
    weeklyXp,
    leagueRank,
  };
}

/** Batch real stats (`streakDays`, `sessionsCount`) for peer learner cards so nothing is hardcoded. */
export async function getBatchLearnerStats(
  userIds: string[],
  now = new Date(),
): Promise<Map<string, { streakDays: number; sessionsCount: number }>> {
  const result = new Map<string, { streakDays: number; sessionsCount: number }>();
  const uniqueIds = [...new Set(userIds.filter(Boolean))];
  if (uniqueIds.length === 0) return result;

  for (const id of uniqueIds) result.set(id, { streakDays: 0, sessionsCount: 0 });

  const [activityRows, tzRows, convCounts, peerCallCounts] = await Promise.all([
    db
      .select({
        userId: userDailyActivity.userId,
        localDate: userDailyActivity.localDate,
        spokenSec: userDailyActivity.spokenSec,
        messagesCount: userDailyActivity.messagesCount,
        lessonsCompleted: userDailyActivity.lessonsCompleted,
        vocabReviewed: userDailyActivity.vocabReviewed,
      })
      .from(userDailyActivity)
      .where(inArray(userDailyActivity.userId, uniqueIds)),
    db
      .select({ userId: notificationPrefs.userId, timezone: notificationPrefs.timezone })
      .from(notificationPrefs)
      .where(inArray(notificationPrefs.userId, uniqueIds)),
    db
      .select({
        userId: conversation.userId,
        n: sql<number>`count(*)::int`,
      })
      .from(conversation)
      .where(inArray(conversation.userId, uniqueIds))
      .groupBy(conversation.userId),
    db
      .select({
        hostUserId: peerCallRoom.hostUserId,
        guestUserId: peerCallRoom.guestUserId,
      })
      .from(peerCallRoom)
      .where(
        and(
          eq(peerCallRoom.status, "ended"),
          sql`(${peerCallRoom.hostUserId} in (${sql.join(
            uniqueIds.map((id) => sql`${id}`),
            sql`, `,
          )}) OR ${peerCallRoom.guestUserId} in (${sql.join(
            uniqueIds.map((id) => sql`${id}`),
            sql`, `,
          )}))`,
        ),
      ),
  ]);

  const tzMap = new Map<string, string>();
  for (const r of tzRows) {
    if (r.timezone && isValidTimeZone(r.timezone)) tzMap.set(r.userId, r.timezone);
  }

  const datesByUser = new Map<string, Map<string, DailyActivityRowLike>>();
  for (const r of activityRows) {
    let userMap = datesByUser.get(r.userId);
    if (!userMap) {
      userMap = new Map();
      datesByUser.set(r.userId, userMap);
    }
    const existing = userMap.get(r.localDate) ?? { spokenSec: 0, messagesCount: 0, lessonsCompleted: 0, vocabReviewed: 0 };
    existing.spokenSec += r.spokenSec;
    existing.messagesCount += r.messagesCount;
    existing.lessonsCompleted += r.lessonsCompleted;
    existing.vocabReviewed += r.vocabReviewed;
    userMap.set(r.localDate, existing);
  }

  const sessionsMap = new Map<string, number>();
  for (const r of convCounts) sessionsMap.set(r.userId, (sessionsMap.get(r.userId) ?? 0) + r.n);
  for (const c of peerCallCounts) {
    if (result.has(c.hostUserId)) sessionsMap.set(c.hostUserId, (sessionsMap.get(c.hostUserId) ?? 0) + 1);
    if (result.has(c.guestUserId)) sessionsMap.set(c.guestUserId, (sessionsMap.get(c.guestUserId) ?? 0) + 1);
  }

  for (const id of uniqueIds) {
    const tz = tzMap.get(id) ?? "UTC";
    const todayStr = formatDate(localDateOf(now, tz));
    const userDates = datesByUser.get(id);
    const activeDates: string[] = [];
    if (userDates) {
      for (const [d, agg] of userDates) {
        if (isActiveDay(agg)) activeDates.push(d);
      }
    }
    result.set(id, {
      streakDays: computeStreakDays(activeDates, todayStr),
      sessionsCount: sessionsMap.get(id) ?? 0,
    });
  }

  return result;
}

const FLUENCY_SCORES: Record<string, number> = {
  Developing: 62,
  Steady: 76,
  Natural: 88,
  Fluent: 96,
};

const MONTH_SHORT = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const DAY_LETTERS = ["M", "T", "W", "T", "F", "S", "S"];

/** Full real progress overview backing `13 · Progress` (`GET /api/me/progress`). */
export async function getProgressOverview(userId: string, profile: LearnerProfile, now = new Date()) {
  const tz = await resolveUserTimezone(userId);
  const todayLocal = localDateOf(now, tz);
  const todayStr = formatDate(todayLocal);
  const localNow = localParts(now, tz);
  const tomorrowMidnight = zonedTime({ ...addDays(todayLocal, 1), hour: 0, minute: 0 }, tz);
  const hoursUntilReset = Math.max(1, Math.ceil((tomorrowMidnight.getTime() - now.getTime()) / 3_600_000));

  const lang = profile.targetLanguage;
  const thirtyDaysAgo = new Date(now.getTime() - 30 * 86_400_000);

  const [allActivity, vocabRows, evalRows, topicLessonRows] = await Promise.all([
    db
      .select()
      .from(userDailyActivity)
      .where(eq(userDailyActivity.userId, userId)),
    db
      .select({ bucket: vocabPhrase.bucket, dueAt: vocabPhrase.dueAt })
      .from(vocabPhrase)
      .where(and(eq(vocabPhrase.userId, userId), eq(vocabPhrase.language, lang))),
    db
      .select({
        pronunciationScore: lessonEvaluation.pronunciationScore,
        fluencyLabel: lessonEvaluation.fluencyLabel,
        goalsCompleted: lessonEvaluation.goalsCompleted,
        goalsTotal: lessonEvaluation.goalsTotal,
        coachNotes: lessonEvaluation.coachNotes,
        createdAt: lessonEvaluation.createdAt,
      })
      .from(lessonEvaluation)
      .innerJoin(topicLesson, eq(lessonEvaluation.lessonId, topicLesson.id))
      .innerJoin(topic, eq(topicLesson.topicId, topic.id))
      .where(and(eq(lessonEvaluation.userId, userId), eq(topic.language, lang), gte(lessonEvaluation.createdAt, thirtyDaysAgo)))
      .orderBy(desc(lessonEvaluation.createdAt))
      .limit(30),
    db
      .select({ completedAt: topicLesson.completedAt })
      .from(topicLesson)
      .innerJoin(topic, eq(topicLesson.topicId, topic.id))
      .where(and(eq(topic.userId, userId), eq(topic.language, lang))),
  ]);

  // Aggregate per localDate across all languages for streak/heatmap, and per targetLanguage for spoken minutes.
  const overallByDate = new Map<string, DailyActivityRowLike & { xp: number }>();
  const langByDate = new Map<string, DailyActivityRowLike & { xp: number }>();
  let totalSpokenSecLang = 0;

  for (const row of allActivity) {
    const overall = overallByDate.get(row.localDate) ?? { spokenSec: 0, messagesCount: 0, lessonsCompleted: 0, vocabReviewed: 0, xp: 0 };
    overall.spokenSec += row.spokenSec;
    overall.messagesCount += row.messagesCount;
    overall.lessonsCompleted += row.lessonsCompleted;
    overall.vocabReviewed += row.vocabReviewed;
    overall.xp += row.xp;
    overallByDate.set(row.localDate, overall);

    if (row.language === lang) {
      totalSpokenSecLang += row.spokenSec;
      const cur = langByDate.get(row.localDate) ?? { spokenSec: 0, messagesCount: 0, lessonsCompleted: 0, vocabReviewed: 0, xp: 0 };
      cur.spokenSec += row.spokenSec;
      cur.messagesCount += row.messagesCount;
      cur.lessonsCompleted += row.lessonsCompleted;
      cur.vocabReviewed += row.vocabReviewed;
      cur.xp += row.xp;
      langByDate.set(row.localDate, cur);
    }
  }

  const activeDates: string[] = [];
  for (const [d, agg] of overallByDate) {
    if (isActiveDay(agg)) activeDates.push(d);
  }
  const streakDays = computeStreakDays(activeDates, todayStr);
  const totalSpokenMinutes = Math.round(totalSpokenSecLang / 60);
  const wordsCount = vocabRows.length;

  // CEFR progress toward next level: combines completed lessons + spoken minutes (capped at 99% until level check/change).
  const completedLessonsCount = topicLessonRows.filter((r) => r.completedAt !== null).length;
  const cefrProgressPercent = Math.min(99, Math.round(completedLessonsCount * 8 + totalSpokenMinutes * 0.4));

  // Today's real daily goals
  const todayAgg = langByDate.get(todayStr) ?? { spokenSec: 0, messagesCount: 0, lessonsCompleted: 0, vocabReviewed: 0, xp: 0 };
  const dailyGoalTargetMin = Math.max(5, profile.dailyGoalMin);
  const todayMinutes = Math.floor(todayAgg.spokenSec / 60);

  const dailyGoals = [
    {
      id: "speak",
      title: `Speak for ${dailyGoalTargetMin} minutes`,
      current: todayMinutes,
      target: dailyGoalTargetMin,
      unit: "m",
    },
    {
      id: "vocab",
      title: "Review 5 saved phrases",
      current: todayAgg.vocabReviewed,
      target: 5,
      unit: "",
    },
    {
      id: "lessons",
      title: "Complete 1 lesson or drill",
      current: todayAgg.lessonsCompleted,
      target: 1,
      unit: "",
    },
  ];

  // Skill balance from real last-30-day evaluations & vocabulary mastery
  let skills: {
    hasData: boolean;
    pronunciation: number;
    listening: number;
    vocabulary: number;
    fluency: number;
    grammar: number;
    wordStress: number;
    focusArea: string | null;
    focusScore: number | null;
  };

  if (evalRows.length === 0) {
    skills = {
      hasData: false,
      pronunciation: 0,
      listening: 0,
      vocabulary: 0,
      fluency: 0,
      grammar: 0,
      wordStress: 0,
      focusArea: null,
      focusScore: null,
    };
  } else {
    const avgPron = Math.round(evalRows.reduce((acc, r) => acc + r.pronunciationScore, 0) / evalRows.length);
    const avgFluency = Math.round(evalRows.reduce((acc, r) => acc + (FLUENCY_SCORES[r.fluencyLabel] ?? 80), 0) / evalRows.length);
    const goalRate = Math.round(
      (evalRows.reduce((acc, r) => acc + (r.goalsTotal > 0 ? r.goalsCompleted / r.goalsTotal : 1), 0) / evalRows.length) * 100,
    );
    const grammarNotes = evalRows.reduce((acc, r) => acc + (r.coachNotes?.filter((n) => n.kind === "grammar").length ?? 0), 0);
    const pronNotes = evalRows.reduce((acc, r) => acc + (r.coachNotes?.filter((n) => n.kind === "pronunciation").length ?? 0), 0);
    const grammar = Math.max(40, Math.min(98, Math.round(92 - (grammarNotes / evalRows.length) * 12)));
    const wordStress = Math.max(40, Math.min(98, Math.round(avgPron - (pronNotes / evalRows.length) * 6)));
    const masteredVocab = vocabRows.filter((v) => v.bucket === "mastered").length;
    const vocabulary = vocabRows.length > 0 ? Math.max(50, Math.min(98, Math.round(60 + (masteredVocab / vocabRows.length) * 38))) : goalRate;
    const listening = Math.max(50, Math.min(98, goalRate));

    const entries: Array<[string, number]> = [
      ["Pronunciation", avgPron],
      ["Listening", listening],
      ["Vocabulary", vocabulary],
      ["Fluency", avgFluency],
      ["Grammar", grammar],
      ["Word stress", wordStress],
    ];
    const weakest = entries.reduce((min, cur) => (cur[1] < min[1] ? cur : min), entries[0]);
    skills = {
      hasData: true,
      pronunciation: avgPron,
      listening,
      vocabulary,
      fluency: avgFluency,
      grammar,
      wordStress,
      focusArea: weakest[0],
      focusScore: weakest[1],
    };
  }

  // Current week Mon..Sun bars in learner's local timezone
  const todayWeekday = localNow.weekday; // 0=Mon .. 6=Sun
  const weekMonday = addDays(todayLocal, -todayWeekday);
  let weeklyXp = 0;
  let weeklySpokenSec = 0;
  const weeklyBars = Array.from({ length: 7 }, (_, i) => {
    const d = addDays(weekMonday, i);
    const key = formatDate(d);
    const agg = langByDate.get(key);
    const mins = agg ? Math.round(agg.spokenSec / 60) : 0;
    const xp = agg?.xp ?? 0;
    weeklyXp += xp;
    weeklySpokenSec += agg?.spokenSec ?? 0;
    return {
      dayLabel: DAY_LETTERS[i],
      date: key,
      minutes: mins,
      xp,
      isToday: i === todayWeekday,
    };
  });
  const daysElapsedThisWeek = Math.max(1, todayWeekday + 1);
  const avgMinutesPerDay = Math.round(weeklySpokenSec / 60 / daysElapsedThisWeek);

  // Last 12 weeks (84 days) consistency heatmap ending today
  const heatmapDays: Array<{ date: string; level: number; minutes: number }> = [];
  let activeDaysLast12Weeks = 0;
  const monthNames: string[] = [];
  for (let offset = 83; offset >= 0; offset--) {
    const d = addDays(todayLocal, -offset);
    const key = formatDate(d);
    const mName = MONTH_SHORT[d.month - 1];
    if (monthNames[monthNames.length - 1] !== mName) monthNames.push(mName);

    const agg = overallByDate.get(key);
    const mins = agg ? Math.round(agg.spokenSec / 60) : 0;
    const active = agg ? isActiveDay(agg) : false;
    if (active) activeDaysLast12Weeks += 1;

    let intensity = 0;
    if (agg) {
      if (mins >= dailyGoalTargetMin || agg.lessonsCompleted >= 2) intensity = 3;
      else if (mins >= Math.max(5, Math.floor(dailyGoalTargetMin / 2)) || agg.lessonsCompleted >= 1) intensity = 2;
      else if (active || agg.messagesCount > 0 || agg.vocabReviewed > 0 || agg.spokenSec > 0) intensity = 1;
    }
    heatmapDays.push({ date: key, level: intensity, minutes: mins });
  }

  return {
    language: lang,
    level: profile.level,
    cefrProgressPercent,
    streakDays,
    totalSpokenMinutes,
    wordsCount,
    hoursUntilReset,
    dailyGoals,
    skills,
    weeklyXp,
    avgMinutesPerDay,
    weeklyBars,
    activeDaysLast12Weeks,
    heatmapMonths: monthNames.slice(-3),
    heatmapDays,
  };
}

/** Real weekly speaking league for learners studying [targetLanguage] (`GET /api/community/league`). */
export async function getWeeklyLeague(userId: string, targetLanguage: string, now = new Date()) {
  const { weekStartDate, resetsAt } = currentUtcWeekWindow(now);

  const rows = await db
    .select({
      userId: userDailyActivity.userId,
      name: user.name,
      image: user.image,
      xp: sql<number>`coalesce(sum(${userDailyActivity.xp}), 0)::int`,
      spokenSec: sql<number>`coalesce(sum(${userDailyActivity.spokenSec}), 0)::int`,
    })
    .from(userDailyActivity)
    .innerJoin(user, eq(userDailyActivity.userId, user.id))
    .where(and(eq(userDailyActivity.language, targetLanguage), gte(userDailyActivity.localDate, weekStartDate)))
    .groupBy(userDailyActivity.userId, user.name, user.image)
    .having(sql`coalesce(sum(${userDailyActivity.xp}), 0) > 0`)
    .orderBy(
      desc(sql`coalesce(sum(${userDailyActivity.xp}), 0)`),
      desc(sql`coalesce(sum(${userDailyActivity.spokenSec}), 0)`),
    )
    .limit(50);

  const standings = await Promise.all(
    rows.map(async (r, index) => {
      const cleanName = (r.name || "Learner").trim();
      const parts = cleanName.split(/\s+/);
      const displayName = parts.length <= 1 ? parts[0] : `${parts[0]} ${parts[1][0].toUpperCase()}.`;
      return {
        rank: index + 1,
        userId: r.userId,
        name: displayName,
        initial: (cleanName[0] || "L").toUpperCase(),
        imageUrl: await resolveUserImageUrl(r.image),
        minutesSpoken: Math.round(r.spokenSec / 60),
        xp: r.xp,
        isYou: r.userId === userId,
      };
    }),
  );

  const viewerRow = standings.find((s) => s.isYou) ?? null;

  return {
    language: targetLanguage,
    weekStartDate,
    resetsAt,
    myRank: viewerRow?.rank ?? null,
    myXp: viewerRow?.xp ?? 0,
    myMinutesSpoken: viewerRow?.minutesSpoken ?? 0,
    totalParticipants: standings.length,
    standings: standings.slice(0, 20),
  };
}
