import assert from "node:assert/strict";
import test from "node:test";
import { computeStreakDays, computeXpDelta, currentUtcWeekWindow, isActiveDay } from "./activity.js";
import { formatActiveLabel, isUserOnline, ONLINE_WINDOW_MS } from "./presence.js";

test("isActiveDay enforces meaningful practice thresholds", () => {
  assert.equal(isActiveDay({ spokenSec: 0, messagesCount: 0, lessonsCompleted: 0, vocabReviewed: 0 }), false);
  assert.equal(isActiveDay({ spokenSec: 29, messagesCount: 1, lessonsCompleted: 0, vocabReviewed: 2 }), false);
  assert.equal(isActiveDay({ spokenSec: 30, messagesCount: 0, lessonsCompleted: 0, vocabReviewed: 0 }), true);
  assert.equal(isActiveDay({ spokenSec: 0, messagesCount: 2, lessonsCompleted: 0, vocabReviewed: 0 }), true);
  assert.equal(isActiveDay({ spokenSec: 0, messagesCount: 0, lessonsCompleted: 1, vocabReviewed: 0 }), true);
  assert.equal(isActiveDay({ spokenSec: 0, messagesCount: 0, lessonsCompleted: 0, vocabReviewed: 3 }), true);
});

test("computeStreakDays counts consecutive days including today or yesterday grace window", () => {
  assert.equal(computeStreakDays([], "2026-11-25"), 0);
  // Active today only
  assert.equal(computeStreakDays(["2026-11-25"], "2026-11-25"), 1);
  // Active 3 consecutive days ending today
  assert.equal(computeStreakDays(["2026-11-23", "2026-11-24", "2026-11-25"], "2026-11-25"), 3);
  // Grace window: not active yet today, but active yesterday and day before -> streak stays 2
  assert.equal(computeStreakDays(["2026-11-23", "2026-11-24"], "2026-11-25"), 2);
  // Missed yesterday -> streak resets to 0
  assert.equal(computeStreakDays(["2026-11-22", "2026-11-23"], "2026-11-25"), 0);
  // Gap in the middle stops counting
  assert.equal(computeStreakDays(["2026-11-20", "2026-11-21", "2026-11-24", "2026-11-25"], "2026-11-25"), 2);
  // Cross-month boundary
  assert.equal(computeStreakDays(["2026-10-30", "2026-10-31", "2026-11-01"], "2026-11-01"), 3);
});

test("computeXpDelta calculates deterministic XP", () => {
  assert.equal(computeXpDelta({ spokenSec: 60, messagesCount: 2, lessonsCompleted: 1, vocabReviewed: 5 }), 10 + 10 + 50 + 40);
  assert.equal(computeXpDelta({ spokenSec: -10, messagesCount: 0 }), 0);
});

test("currentUtcWeekWindow resolves Monday-to-Monday UTC ISO week", () => {
  // Wed Nov 25 2026 -> Monday is Nov 23, next Monday is Nov 30
  const win = currentUtcWeekWindow(new Date("2026-11-25T15:00:00.000Z"));
  assert.equal(win.weekStartDate, "2026-11-23");
  assert.equal(win.resetsAt, "2026-11-30T00:00:00.000Z");

  // Sunday Nov 29 2026 -> still belongs to Nov 23 week
  const sun = currentUtcWeekWindow(new Date("2026-11-29T23:59:00.000Z"));
  assert.equal(sun.weekStartDate, "2026-11-23");
  assert.equal(sun.resetsAt, "2026-11-30T00:00:00.000Z");
});

test("presence online window and activeLabel accuracy", () => {
  const now = Date.UTC(2026, 10, 25, 18, 0, 0);
  assert.equal(isUserOnline(new Date(now - 30_000), now), true);
  assert.equal(isUserOnline(new Date(now - ONLINE_WINDOW_MS), now), true);
  assert.equal(isUserOnline(new Date(now - ONLINE_WINDOW_MS - 1_000), now), false);

  assert.equal(formatActiveLabel(new Date(now - 30_000), true, now), "Open to speak");
  assert.equal(formatActiveLabel(new Date(now - 30_000), false, now), "Online");
  // Stale openToSpeak user must NOT be labeled "Open to speak" when offline!
  assert.equal(formatActiveLabel(new Date(now - 10 * 60_000), true, now), "Active 10m ago");
  assert.equal(formatActiveLabel(new Date(now - 3 * 3_600_000), true, now), "Active 3h ago");
  assert.equal(formatActiveLabel(new Date(now - 48 * 3_600_000), false, now), "Active 2d ago");
});
