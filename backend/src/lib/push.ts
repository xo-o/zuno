import { randomUUID } from "node:crypto";
import { eq, inArray } from "drizzle-orm";
import { cert, getApps, initializeApp, type App } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";
import { device } from "../db/app-schema.js";
import { db } from "../db/index.js";

/** Kinds the Android app maps to notification channels and deep links. */
export type PushType = "reminder" | "schedule" | "progress" | "news";

export type PushMessage = {
  type: PushType;
  title: string;
  body: string;
  /** Deep-link extras: conversationId, scheduleId, topicId, roleplayId… */
  data?: Record<string, string | undefined>;
};

let app: App | null | undefined;

/**
 * Firebase Admin from FIREBASE_SERVICE_ACCOUNT (the service account JSON, raw or base64).
 * Returns null — and push is skipped — when it isn't configured.
 */
function firebase(): App | null {
  if (app !== undefined) return app;
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT?.trim();
  if (!raw) {
    console.warn("FIREBASE_SERVICE_ACCOUNT is not set; push notifications are disabled.");
    return (app = null);
  }
  try {
    const json = raw.startsWith("{") ? raw : Buffer.from(raw, "base64").toString("utf8");
    const account = JSON.parse(json);
    app = getApps()[0] ?? initializeApp({ credential: cert(account) });
    console.log(`Push notifications enabled (Firebase project ${account.project_id})`);
  } catch (err) {
    console.error("FIREBASE_SERVICE_ACCOUNT is invalid; push notifications are disabled:", err);
    app = null;
  }
  return app;
}

export function pushEnabled() {
  return firebase() !== null;
}

const STALE_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument",
]);

export async function registerDevice(
  userId: string,
  input: { pushToken: string; platform: "android"; timezone?: string; appLanguage?: string },
) {
  const values = {
    userId,
    platform: input.platform,
    timezone: input.timezone ?? null,
    appLanguage: input.appLanguage ?? null,
    lastSeenAt: new Date(),
  };
  // A token moves to whoever signed in on the device last.
  await db
    .insert(device)
    .values({ id: randomUUID(), pushToken: input.pushToken, ...values })
    .onConflictDoUpdate({ target: device.pushToken, set: values });
}

/**
 * Sends a data-only, high-priority message to every device of [userId] so the app always builds the
 * notification itself (channel, deep link). Returns how many devices accepted it.
 */
export async function sendToUser(userId: string, message: PushMessage): Promise<number> {
  const firebaseApp = firebase();
  if (!firebaseApp) return 0;
  const devices = await db.select().from(device).where(eq(device.userId, userId));
  if (devices.length === 0) {
    console.log(`Push ${message.type} to user ${userId}: no registered devices`);
    return 0;
  }

  const data: Record<string, string> = { type: message.type, title: message.title, body: message.body };
  for (const [key, value] of Object.entries(message.data ?? {})) if (value) data[key] = value;

  const response = await getMessaging(firebaseApp).sendEachForMulticast({
    tokens: devices.map((d) => d.pushToken),
    data,
    android: { priority: "high", ttl: 6 * 60 * 60 * 1000 },
  });

  const stale: string[] = [];
  response.responses.forEach((result, index) => {
    if (!result.success && result.error && STALE_TOKEN_CODES.has(result.error.code)) stale.push(devices[index].pushToken);
    else if (!result.success) console.warn(`Push to ${devices[index].id} failed:`, result.error?.code, result.error?.message);
  });
  if (stale.length) await db.delete(device).where(inArray(device.pushToken, stale));
  console.log(`Push ${message.type} to user ${userId}: ${response.successCount}/${devices.length} delivered`);
  return response.successCount;
}
