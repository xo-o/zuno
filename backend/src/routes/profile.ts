import { randomUUID } from "node:crypto";
import { eq } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { attachment } from "../db/app-schema.js";
import { account, user } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { t } from "../lib/copy.js";
import { sendEmail } from "../lib/email.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { APP_LANGUAGE_CODES, LANGUAGE_CODES, LEVELS } from "../lib/languages.js";
import { getNotificationPrefs, serializeNotificationPrefs, updateNotificationPrefs } from "../lib/notifications.js";
import {
  DAILY_GOALS,
  FOCUSES,
  getProfile,
  InvalidAccentError,
  languageProgress,
  TEACHING_STYLES,
  TUTOR_VOICES,
  updateProfile,
} from "../lib/profile.js";
import { deleteObjects, headObject, presignDownload, presignUpload } from "../lib/storage.js";
import { isValidTimeZone } from "../lib/time.js";

const updateSchema = z.object({
  name: z.string().trim().min(1).max(80).optional(),
  targetLanguage: z.enum(LANGUAGE_CODES).optional(),
  nativeLanguage: z.enum(APP_LANGUAGE_CODES).optional(),
  level: z.enum(LEVELS).optional(),
  accent: z.string().trim().min(1).max(40).optional(),
  tutorVoice: z.enum(TUTOR_VOICES).optional(),
  teachingStyle: z.enum(TEACHING_STYLES).optional(),
  strictMode: z.boolean().optional(),
  handsFree: z.boolean().optional(),
  suggestReplies: z.boolean().optional(),
  showTranslations: z.boolean().optional(),
  teachInTarget: z.boolean().optional(),
  dailyGoalMin: z
    .number()
    .int()
    .refine((value) => (DAILY_GOALS as readonly number[]).includes(value), "Unsupported daily goal")
    .optional(),
  focus: z.enum(FOCUSES).optional(),
});

const notificationsSchema = z.object({
  dailyReminder: z.boolean().optional(),
  reminderTime: z
    .string()
    .regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Use HH:mm")
    .optional(),
  timezone: z
    .string()
    .max(64)
    .refine(isValidTimeZone, "Unknown time zone")
    .optional(),
  scheduledSessions: z.boolean().optional(),
  progressMilestones: z.boolean().optional(),
  newContent: z.boolean().optional(),
  weeklySummaryEmail: z.boolean().optional(),
  productNewsEmail: z.boolean().optional(),
});

const AVATAR_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
const AVATAR_MAX_BYTES = 5 * 1024 * 1024;
const avatarSchema = z.object({
  mimeType: z.enum(AVATAR_TYPES),
  sizeBytes: z.number().int().positive().max(AVATAR_MAX_BYTES, "Photos can be up to 5 MB"),
});
const avatarCompleteSchema = z.object({ key: z.string().min(1).max(200) });

/** Avatars uploaded in the app live in the private bucket ("r2:<key>") and are served with signed URLs. */
const R2_PREFIX = "r2:";

async function imageUrl(image: string | null | undefined) {
  if (!image) return null;
  if (!image.startsWith(R2_PREFIX)) return image;
  try {
    return await presignDownload(image.slice(R2_PREFIX.length));
  } catch {
    return null;
  }
}

async function serializeUser(row: { id: string; name: string; email: string; image?: string | null }) {
  return { id: row.id, name: row.name, email: row.email, image: await imageUrl(row.image) };
}

async function profileResponse(current: { id: string; name: string; email: string; image?: string | null }) {
  const profile = await getProfile(current.id);
  const [languages, providers] = await Promise.all([
    languageProgress(current.id, profile),
    db.select({ providerId: account.providerId }).from(account).where(eq(account.userId, current.id)),
  ]);
  return {
    user: await serializeUser(current),
    profile,
    languages,
    // "Signed in with Google" in Settings · Account.
    providers: [...new Set(providers.map((p) => p.providerId))],
  };
}

const deleteLimit = rateLimit({ windowMs: 60 * 60_000, max: 5 });

export const profileRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/", async (c) => {
    const [row] = await db.select().from(user).where(eq(user.id, c.get("user").id));
    return c.json(await profileResponse(row ?? c.get("user")));
  })
  .patch("/", async (c) => {
    const current = c.get("user");
    const { name, ...patch } = await parseBody(c, updateSchema);

    if (name) await db.update(user).set({ name }).where(eq(user.id, current.id));
    try {
      await updateProfile(current.id, patch);
    } catch (err) {
      if (err instanceof InvalidAccentError) throw new HTTPException(400, { message: err.message });
      throw err;
    }
    const [row] = await db.select().from(user).where(eq(user.id, current.id));
    return c.json(await profileResponse(row));
  })
  .get("/notifications", async (c) => {
    return c.json({ notifications: serializeNotificationPrefs(await getNotificationPrefs(c.get("user").id)) });
  })
  .patch("/notifications", async (c) => {
    const patch = await parseBody(c, notificationsSchema);
    return c.json({ notifications: serializeNotificationPrefs(await updateNotificationPrefs(c.get("user").id, patch)) });
  })
  /** Change photo: presigned PUT to R2, then /avatar/complete. */
  .post("/avatar", async (c) => {
    const userId = c.get("user").id;
    const body = await parseBody(c, avatarSchema);
    const key = `avatars/${userId}/${randomUUID()}`;
    return c.json({ key, uploadUrl: await presignUpload(key, body.mimeType) });
  })
  .post("/avatar/complete", async (c) => {
    const current = c.get("user");
    const { key } = await parseBody(c, avatarCompleteSchema);
    if (!key.startsWith(`avatars/${current.id}/`)) throw new HTTPException(400, { message: "Invalid upload" });
    const head = await headObject(key);
    if (!head || head.size === 0) throw new HTTPException(409, { message: "Upload not found. Please try again." });
    if (head.size > AVATAR_MAX_BYTES) {
      await deleteObjects([key]);
      throw new HTTPException(413, { message: "Photos can be up to 5 MB" });
    }
    const [previous] = await db.select({ image: user.image }).from(user).where(eq(user.id, current.id));
    const [row] = await db.update(user).set({ image: `${R2_PREFIX}${key}` }).where(eq(user.id, current.id)).returning();
    if (previous?.image?.startsWith(R2_PREFIX)) await deleteObjects([previous.image.slice(R2_PREFIX.length)]);
    return c.json({ user: await serializeUser(row) });
  })
  .delete("/avatar", async (c) => {
    const current = c.get("user");
    const [previous] = await db.select({ image: user.image }).from(user).where(eq(user.id, current.id));
    const [row] = await db.update(user).set({ image: null }).where(eq(user.id, current.id)).returning();
    if (previous?.image?.startsWith(R2_PREFIX)) await deleteObjects([previous.image.slice(R2_PREFIX.length)]);
    return c.json({ user: await serializeUser(row) });
  })
  /**
   * Delete account: removes the learner's files from R2, then the user row — every app table, session
   * and linked account cascades from it — and confirms by email.
   */
  .delete("/", deleteLimit, async (c) => {
    const current = c.get("user");
    const [row] = await db.select().from(user).where(eq(user.id, current.id));
    if (!row) return c.body(null, 204);
    const profile = await getProfile(current.id);
    const files = await db.select({ key: attachment.storageKey }).from(attachment).where(eq(attachment.userId, current.id));
    const keys = files.map((f) => f.key).filter(Boolean);
    if (row.image?.startsWith(R2_PREFIX)) keys.push(row.image.slice(R2_PREFIX.length));

    await db.delete(user).where(eq(user.id, current.id));
    await deleteObjects(keys);

    sendEmail({
      to: row.email,
      subject: t(profile.nativeLanguage, "accountDeletedSubject"),
      heading: t(profile.nativeLanguage, "accountDeletedSubject"),
      paragraphs: [t(profile.nativeLanguage, "accountDeletedBody")],
    }).catch((err) => console.error("Account deletion email failed:", err));
    return c.body(null, 204);
  });
