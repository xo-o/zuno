import { and, desc, eq, sql } from "drizzle-orm";
import { userLanguageProgress, userProfile } from "../db/app-schema.js";
import { db } from "../db/index.js";
import {
  defaultAccent,
  isAccentOf,
  isAppLanguageCode,
  isLanguageCode,
  LEVELS,
  type AppLanguageCode,
  type LanguageCode,
  type Level,
} from "./languages.js";

export const TUTOR_VOICES = ["warm", "calm", "energetic"] as const;
export const TEACHING_STYLES = ["balanced", "gentle", "immersive"] as const;
export const FOCUSES = ["conversation", "travel", "work", "exams"] as const;
export const DAILY_GOALS = [5, 10, 15, 20, 30] as const;

export type TutorVoice = (typeof TUTOR_VOICES)[number];
export type TeachingStyle = (typeof TEACHING_STYLES)[number];
export type Focus = (typeof FOCUSES)[number];

export type Preferences = {
  tutorVoice: TutorVoice;
  teachingStyle: TeachingStyle;
  strictMode: boolean;
  handsFree: boolean;
  suggestReplies: boolean;
  showTranslations: boolean;
  teachInTarget: boolean;
  dailyGoalMin: number;
  focus: Focus;
};

export type LearnerProfile = Preferences & {
  targetLanguage: LanguageCode;
  nativeLanguage: AppLanguageCode;
  level: Level;
  accent: string;
  /** False until the level for the current language has been checked or picked. */
  levelChecked: boolean;
  plan: "free" | "pro";
  planExpiresAt: string | null;
};

type ProfileRow = typeof userProfile.$inferSelect;

function oneOf<T extends string>(values: readonly T[], value: string, fallback: T): T {
  return (values as readonly string[]).includes(value) ? (value as T) : fallback;
}

function normalize(row: ProfileRow, levelChecked: boolean): LearnerProfile {
  const targetLanguage = isLanguageCode(row.targetLanguage) ? row.targetLanguage : "es";
  return {
    targetLanguage,
    nativeLanguage: isAppLanguageCode(row.nativeLanguage) ? row.nativeLanguage : "en",
    level: oneOf(LEVELS, row.level, "B1"),
    accent: row.accent && isAccentOf(targetLanguage, row.accent) ? row.accent : defaultAccent(targetLanguage),
    levelChecked,
    tutorVoice: oneOf(TUTOR_VOICES, row.tutorVoice, "warm"),
    teachingStyle: oneOf(TEACHING_STYLES, row.teachingStyle, "balanced"),
    strictMode: row.strictMode,
    handsFree: row.handsFree,
    suggestReplies: row.suggestReplies,
    showTranslations: row.showTranslations,
    teachInTarget: row.teachInTarget,
    dailyGoalMin: row.dailyGoalMin,
    focus: oneOf(FOCUSES, row.focus, "conversation"),
    plan: row.plan === "pro" && (!row.planExpiresAt || row.planExpiresAt > new Date()) ? "pro" : "free",
    planExpiresAt: row.planExpiresAt?.toISOString() ?? null,
  };
}

async function loadRow(userId: string): Promise<ProfileRow> {
  const existing = await db.query.userProfile.findFirst({ where: eq(userProfile.userId, userId) });
  if (existing) return existing;
  await db.insert(userProfile).values({ userId }).onConflictDoNothing();
  return (await db.query.userProfile.findFirst({ where: eq(userProfile.userId, userId) }))!;
}

/**
 * Progress row for the profile's current language. Profiles created before per-language progress
 * existed are backfilled here, counting their level as already checked.
 */
async function currentProgress(row: ProfileRow) {
  const existing = await db.query.userLanguageProgress.findFirst({
    where: and(eq(userLanguageProgress.userId, row.userId), eq(userLanguageProgress.language, row.targetLanguage)),
  });
  if (existing) return existing;
  const [created] = await db
    .insert(userLanguageProgress)
    .values({
      userId: row.userId,
      language: row.targetLanguage,
      level: row.level,
      accent: row.accent,
      levelCheckedAt: new Date(),
    })
    .onConflictDoNothing()
    .returning();
  return (
    created ??
    (await db.query.userLanguageProgress.findFirst({
      where: and(eq(userLanguageProgress.userId, row.userId), eq(userLanguageProgress.language, row.targetLanguage)),
    }))!
  );
}

/** Returns the learner's profile, creating the default (Spanish · B1) on first access. */
export async function getProfile(userId: string): Promise<LearnerProfile> {
  const row = await loadRow(userId);
  const progress = await currentProgress(row);
  return normalize(row, progress.levelCheckedAt !== null);
}

export type ProfilePatch = Partial<Preferences> & {
  targetLanguage?: LanguageCode;
  nativeLanguage?: AppLanguageCode;
  level?: Level;
  accent?: string;
  /** The level came from a level check (or an explicit pick), so the language counts as checked. */
  markLevelChecked?: boolean;
};

export class InvalidAccentError extends Error {}

/**
 * Applies a profile change. Switching the target language stores the old language's level and accent
 * and restores the new one's (or starts it unchecked at B1 with its default accent).
 */
export async function updateProfile(userId: string, patch: ProfilePatch): Promise<LearnerProfile> {
  const row = await loadRow(userId);
  await currentProgress(row);
  const { targetLanguage, level, accent, markLevelChecked, ...rest } = patch;

  const language = targetLanguage ?? (isLanguageCode(row.targetLanguage) ? row.targetLanguage : "es");
  if (accent !== undefined && !isAccentOf(language, accent)) throw new InvalidAccentError("Unknown accent for this language");

  const values: Partial<typeof userProfile.$inferInsert> = { ...rest };

  if (targetLanguage && targetLanguage !== row.targetLanguage) {
    // Remember where the learner left the previous language.
    await db
      .update(userLanguageProgress)
      .set({ level: row.level, accent: row.accent, lastActiveAt: sql`now()` })
      .where(and(eq(userLanguageProgress.userId, userId), eq(userLanguageProgress.language, row.targetLanguage)));
    await db
      .insert(userLanguageProgress)
      .values({ userId, language: targetLanguage, level: "B1", accent: defaultAccent(targetLanguage) })
      .onConflictDoNothing();
    const next = (await db.query.userLanguageProgress.findFirst({
      where: and(eq(userLanguageProgress.userId, userId), eq(userLanguageProgress.language, targetLanguage)),
    }))!;
    values.targetLanguage = targetLanguage;
    values.level = next.level;
    values.accent = next.accent ?? defaultAccent(targetLanguage);
    await db
      .update(userLanguageProgress)
      .set({ lastActiveAt: sql`now()` })
      .where(and(eq(userLanguageProgress.userId, userId), eq(userLanguageProgress.language, targetLanguage)));
  }

  if (level) values.level = level;
  if (accent !== undefined) values.accent = accent;

  if (Object.keys(values).length) {
    await db.update(userProfile).set(values).where(eq(userProfile.userId, userId));
  }

  // Write level and accent through to the current language's progress.
  if (level || accent !== undefined || markLevelChecked) {
    await db
      .update(userLanguageProgress)
      .set({
        ...(level && { level }),
        ...(accent !== undefined && { accent }),
        ...((markLevelChecked || level) && { levelCheckedAt: sql`coalesce(${userLanguageProgress.levelCheckedAt}, now())` }),
      })
      .where(and(eq(userLanguageProgress.userId, userId), eq(userLanguageProgress.language, language)));
  }

  return getProfile(userId);
}

/** Every language the learner has studied, most recent first. */
export async function languageProgress(userId: string, current: LearnerProfile) {
  const rows = await db.query.userLanguageProgress.findMany({
    where: eq(userLanguageProgress.userId, userId),
    orderBy: [desc(userLanguageProgress.lastActiveAt)],
  });
  return rows
    .filter((row) => isLanguageCode(row.language))
    .map((row) => {
      const isCurrent = row.language === current.targetLanguage;
      return {
        language: row.language,
        level: isCurrent ? current.level : row.level,
        accent: isCurrent ? current.accent : (row.accent ?? defaultAccent(row.language as LanguageCode)),
        levelChecked: isCurrent ? current.levelChecked : row.levelCheckedAt !== null,
        lastActiveAt: row.lastActiveAt.toISOString(),
      };
    });
}
