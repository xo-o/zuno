import { randomUUID } from "node:crypto";
import { eq } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { account, user } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { deleteAccount, R2_PREFIX } from "../lib/account.js";
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
import { generateTopic, generateTopicRecommendations, parseOnboardingTurn } from "../lib/tutor.js";
import { serializeTopicDetail } from "../lib/serializers.js";
import { persistAndWarmTopic } from "./topics.js";
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
  interests: z.array(z.string().trim().min(1).max(60)).max(10).optional(),
  goalNote: z.string().trim().max(160).optional(),
});

const onboardingTurnSchema = z.object({
  step: z.enum(["name", "level", "goals"]),
  answer: z.string().max(400).default(""),
  chips: z.array(z.string().trim().min(1).max(60)).max(8).optional(),
  targetLanguage: z.enum(LANGUAGE_CODES).optional(),
  nativeLanguage: z.enum(APP_LANGUAGE_CODES).optional(),
});

const onboardingSchema = z.object({
  name: z.string().trim().min(1).max(80).optional(),
  targetLanguage: z.enum(LANGUAGE_CODES),
  nativeLanguage: z.enum(APP_LANGUAGE_CODES).optional(),
  accent: z.string().trim().min(1).max(40).optional(),
  level: z.enum(LEVELS),
  focus: z.enum(FOCUSES),
  interests: z.array(z.string().trim().min(1).max(60)).max(10).default([]),
  goalNote: z.string().trim().max(160).optional(),
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
  /** Conversational onboarding turn parser (Name / Level / Goals). */
  .post("/onboarding/turn", async (c) => {
    const current = c.get("user");
    const body = await parseBody(c, onboardingTurnSchema);
    const existing = await getProfile(current.id);
    const result = await parseOnboardingTurn({
      step: body.step,
      answer: body.answer,
      chips: body.chips,
      targetLanguage: body.targetLanguage ?? existing.targetLanguage,
      nativeLanguage: body.nativeLanguage ?? existing.nativeLanguage,
    });
    return c.json(result);
  })
  /** Completes onboarding (`5e` -> `5f`): persists profile, creates Starter 5-lesson topic, and generates Recommended Next. */
  .post("/onboarding", async (c) => {
    const current = c.get("user");
    const { name, ...patch } = await parseBody(c, onboardingSchema);

    if (name) {
      await db.update(user).set({ name }).where(eq(user.id, current.id));
    }

    let updatedProfile;
    try {
      updatedProfile = await updateProfile(current.id, {
        ...patch,
        goalNote: patch.goalNote || null,
        onboardingCompletedAt: new Date(),
        markLevelChecked: true,
      });
    } catch (err) {
      if (err instanceof InvalidAccentError) throw new HTTPException(400, { message: err.message });
      throw err;
    }

    const starterPrompt = patch.goalNote
      ? `Starter foundational topic tailored to: ${patch.goalNote}`
      : patch.interests.length > 0
        ? `Starter foundational topic introducing myself and ${patch.interests.slice(0, 2).join(" & ")}`
        : `Starter foundational topic for ${patch.focus} conversations and introducing myself`;

    const [generatedStarter, recBundle] = await Promise.all([
      generateTopic(starterPrompt, updatedProfile),
      generateTopicRecommendations(updatedProfile),
    ]);

    // Filter out any recommendation whose title duplicates the starter topic, keeping 2–3 crisp next steps.
    const starterTitleKey = generatedStarter.title.trim().toLowerCase();
    const dedupedRecs = recBundle.topics
      .filter((r) => r.title.trim().toLowerCase() !== starterTitleKey)
      .slice(0, 3);

    const [starterRow] = await Promise.all([
      persistAndWarmTopic({
        userId: current.id,
        prompt: starterPrompt,
        generated: generatedStarter,
        profile: updatedProfile,
      }),
      updateProfile(current.id, { recommendedTopics: dedupedRecs }),
    ]);

    const [row] = await db.select().from(user).where(eq(user.id, current.id));
    return c.json({
      ...(await profileResponse(row ?? current)),
      starterTopic: serializeTopicDetail(starterRow, starterRow.lessons),
      recommendedTopics: dedupedRecs,
      pathSubtitle: recBundle.pathSubtitle,
    });
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
    const deleted = await deleteAccount(c.get("user").id);
    if (!deleted) return c.body(null, 204);
    const { user: row, profile } = deleted;

    sendEmail({
      to: row.email,
      subject: t(profile.nativeLanguage, "accountDeletedSubject"),
      heading: t(profile.nativeLanguage, "accountDeletedSubject"),
      paragraphs: [t(profile.nativeLanguage, "accountDeletedBody")],
    }).catch((err) => console.error("Account deletion email failed:", err));
    return c.body(null, 204);
  });
