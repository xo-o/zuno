import { addDays, daysInMonth, formatDate, localDateOf, parseDate, parseTime, weekdayOf, zonedTime, type LocalDate } from "./time.js";

export type Repeat = "once" | "daily" | "weekdays" | "weekly" | "monthly";

export type ScheduleRule = {
  repeat: Repeat;
  /** Local "HH:mm". */
  timeOfDay: string;
  /** Local "YYYY-MM-DD" (once). */
  date?: string | null;
  /** 0 = Monday … 6 = Sunday (weekly). */
  weekdays?: number[];
  /** 1–31, clamped to the month's last day (monthly). */
  monthDay?: number | null;
  timezone: string;
  /** Local dates ("YYYY-MM-DD") whose occurrence is skipped. */
  skipDates?: string[];
};

/** Max days ahead to search; covers monthly rules and long skip lists. */
const HORIZON_DAYS = 800;

function occursOn(rule: ScheduleRule, day: LocalDate): boolean {
  switch (rule.repeat) {
    case "once":
      return rule.date === formatDate(day);
    case "daily":
      return true;
    case "weekdays":
      return weekdayOf(day) < 5;
    case "weekly":
      return (rule.weekdays ?? []).includes(weekdayOf(day));
    case "monthly": {
      const target = Math.min(rule.monthDay ?? 1, daysInMonth(day.year, day.month));
      return day.day === target;
    }
  }
}

/** The first occurrence strictly after [after], or null when there is none (a past one-off). */
export function computeNextRun(rule: ScheduleRule, after: Date): Date | null {
  const time = parseTime(rule.timeOfDay);
  if (!time) return null;
  const skip = new Set(rule.skipDates ?? []);

  let day: LocalDate;
  if (rule.repeat === "once") {
    const date = rule.date ? parseDate(rule.date) : null;
    if (!date || skip.has(rule.date!)) return null;
    const at = zonedTime({ ...date, ...time }, rule.timezone);
    return at > after ? at : null;
  }
  if (rule.repeat === "weekly" && !(rule.weekdays ?? []).length) return null;

  // Start a day early so occurrences late in the local day aren't missed across zones.
  day = addDays(localDateOf(after, rule.timezone), -1);
  for (let i = 0; i < HORIZON_DAYS; i++, day = addDays(day, 1)) {
    if (!occursOn(rule, day) || skip.has(formatDate(day))) continue;
    const at = zonedTime({ ...day, ...time }, rule.timezone);
    if (at > after) return at;
  }
  return null;
}

/** Local date ("YYYY-MM-DD") of an occurrence instant, used for "Skip tomorrow only". */
export function occurrenceDate(at: Date, timezone: string) {
  return formatDate(localDateOf(at, timezone));
}
