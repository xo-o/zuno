import { eq } from "drizzle-orm";
import { userProfile } from "../db/app-schema.js";
import { db } from "../db/index.js";
import { updateTimeZone } from "./notifications.js";
import { isValidTimeZone } from "./time.js";

/** Learners seen within this window are treated as actively online / connected. */
export const ONLINE_WINDOW_MS = 2 * 60_000;

/** Minimum interval between passive last_seen_at writes triggered by API traffic. */
const TOUCH_THROTTLE_MS = 30_000;
const lastTouchedAt = new Map<string, number>();
const lastSyncedTz = new Map<string, string>();

export function isUserOnline(lastSeenAt: Date, nowMs = Date.now()): boolean {
  return nowMs - lastSeenAt.getTime() <= ONLINE_WINDOW_MS;
}

export function formatActiveLabel(lastSeenAt: Date, openToSpeak: boolean, nowMs = Date.now()): string {
  const diffMs = Math.max(0, nowMs - lastSeenAt.getTime());
  if (diffMs <= ONLINE_WINDOW_MS) {
    return openToSpeak ? "Open to speak" : "Online";
  }
  const diffMin = Math.max(1, Math.round(diffMs / 60_000));
  if (diffMin < 60) return `Active ${diffMin}m ago`;
  const diffHours = Math.max(1, Math.round(diffMin / 60));
  if (diffHours < 24) return `Active ${diffHours}h ago`;
  const diffDays = Math.max(1, Math.round(diffHours / 24));
  return `Active ${diffDays}d ago`;
}

/**
 * Passively refreshes `user_profile.last_seen_at` (throttled to every 30s per user) and syncs
 * the learner's IANA timezone when provided via `X-Timezone`.
 */
export function touchPresence(userId: string, timeZone?: string | null): void {
  const now = Date.now();
  const prev = lastTouchedAt.get(userId) ?? 0;
  if (now - prev >= TOUCH_THROTTLE_MS) {
    lastTouchedAt.set(userId, now);
    db.update(userProfile)
      .set({ lastSeenAt: new Date(now) })
      .where(eq(userProfile.userId, userId))
      .catch(() => {});
  }
  const tz = timeZone?.trim();
  if (tz && tz !== lastSyncedTz.get(userId) && isValidTimeZone(tz)) {
    lastSyncedTz.set(userId, tz);
    updateTimeZone(userId, tz).catch(() => {});
  }
}

/** Explicit foreground/background presence heartbeat (`POST /api/me/presence`). */
export async function recordPresenceHeartbeat(
  userId: string,
  status: "online" | "away" = "online",
  timeZone?: string | null,
): Promise<{ online: boolean; lastSeenAt: string }> {
  const now = Date.now();
  const nextSeen = status === "away" ? new Date(now - ONLINE_WINDOW_MS - 60_000) : new Date(now);
  lastTouchedAt.set(userId, now);
  await db.update(userProfile).set({ lastSeenAt: nextSeen }).where(eq(userProfile.userId, userId));

  const tz = timeZone?.trim();
  if (tz && isValidTimeZone(tz)) {
    lastSyncedTz.set(userId, tz);
    await updateTimeZone(userId, tz).catch(() => {});
  }
  return { online: status === "online", lastSeenAt: nextSeen.toISOString() };
}
