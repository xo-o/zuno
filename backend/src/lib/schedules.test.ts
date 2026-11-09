import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { computeNextRun, type ScheduleRule } from "./schedules.js";
import { zonedTime } from "./time.js";

const base = { timeOfDay: "09:00", timezone: "Europe/Madrid" } as const;

function iso(date: Date | null) {
  return date?.toISOString() ?? null;
}

describe("computeNextRun", () => {
  it("once: returns the date if still ahead, else null", () => {
    const rule: ScheduleRule = { ...base, repeat: "once", date: "2026-11-08" };
    assert.equal(iso(computeNextRun(rule, new Date("2026-11-08T07:00:00Z"))), "2026-11-08T08:00:00.000Z");
    assert.equal(computeNextRun(rule, new Date("2026-11-08T08:00:00Z")), null);
  });

  it("once: a skipped date never runs", () => {
    const rule: ScheduleRule = { ...base, repeat: "once", date: "2026-11-08", skipDates: ["2026-11-08"] };
    assert.equal(computeNextRun(rule, new Date("2026-11-01T00:00:00Z")), null);
  });

  it("daily: today when ahead, tomorrow otherwise", () => {
    const rule: ScheduleRule = { ...base, repeat: "daily" };
    assert.equal(iso(computeNextRun(rule, new Date("2026-11-08T07:59:00Z"))), "2026-11-08T08:00:00.000Z");
    assert.equal(iso(computeNextRun(rule, new Date("2026-11-08T08:00:00Z"))), "2026-11-09T08:00:00.000Z");
  });

  it("daily: honours skip dates", () => {
    const rule: ScheduleRule = { ...base, repeat: "daily", skipDates: ["2026-11-09"] };
    assert.equal(iso(computeNextRun(rule, new Date("2026-11-08T09:00:00Z"))), "2026-11-10T08:00:00.000Z");
  });

  it("weekdays: skips the weekend", () => {
    // 2026-11-06 is a Friday.
    const rule: ScheduleRule = { ...base, repeat: "weekdays" };
    assert.equal(iso(computeNextRun(rule, new Date("2026-11-06T09:00:00Z"))), "2026-11-09T08:00:00.000Z");
  });

  it("weekly: picks the next selected weekday (0 = Monday)", () => {
    const rule: ScheduleRule = { ...base, repeat: "weekly", weekdays: [0, 2, 4] };
    // Sunday 2026-11-08 → Monday 2026-11-09
    assert.equal(iso(computeNextRun(rule, new Date("2026-11-08T12:00:00Z"))), "2026-11-09T08:00:00.000Z");
    // Monday after 9:00 → Wednesday
    assert.equal(iso(computeNextRun(rule, new Date("2026-11-09T08:30:00Z"))), "2026-11-11T08:00:00.000Z");
  });

  it("weekly: no days selected means no occurrence", () => {
    assert.equal(computeNextRun({ ...base, repeat: "weekly", weekdays: [] }, new Date()), null);
  });

  it("monthly: clamps day 31 to the end of shorter months", () => {
    const rule: ScheduleRule = { ...base, repeat: "monthly", monthDay: 31 };
    assert.equal(iso(computeNextRun(rule, new Date("2026-11-01T00:00:00Z"))), "2026-11-30T08:00:00.000Z");
    assert.equal(iso(computeNextRun(rule, new Date("2027-02-01T00:00:00Z"))), "2027-02-28T08:00:00.000Z");
  });

  it("monthly: rolls over to next month once this month's day passed", () => {
    const rule: ScheduleRule = { ...base, repeat: "monthly", monthDay: 7 };
    assert.equal(iso(computeNextRun(rule, new Date("2026-11-08T00:00:00Z"))), "2026-12-07T08:00:00.000Z");
  });

  it("keeps the local time across a DST change", () => {
    // Madrid leaves summer time on 2026-10-25: 09:00 is 07:00Z before and 08:00Z after.
    const rule: ScheduleRule = { ...base, repeat: "daily" };
    assert.equal(iso(computeNextRun(rule, new Date("2026-10-24T08:00:00Z"))), "2026-10-25T08:00:00.000Z");
    assert.equal(iso(computeNextRun(rule, new Date("2026-10-23T12:00:00Z"))), "2026-10-24T07:00:00.000Z");
  });

  it("works for zones far from UTC", () => {
    const rule: ScheduleRule = { repeat: "daily", timeOfDay: "23:30", timezone: "Pacific/Auckland" };
    const next = computeNextRun(rule, new Date("2026-11-08T00:00:00Z"));
    assert.equal(iso(next), "2026-11-08T10:30:00.000Z");
  });
});

describe("zonedTime", () => {
  it("resolves a time skipped by spring-forward to after the gap", () => {
    // New York jumps from 02:00 to 03:00 on 2026-03-08.
    const at = zonedTime({ year: 2026, month: 3, day: 8, hour: 2, minute: 30 }, "America/New_York");
    assert.equal(at.toISOString(), "2026-03-08T07:30:00.000Z");
  });
});
