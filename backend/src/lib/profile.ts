import { eq } from "drizzle-orm";
import { userProfile } from "../db/app-schema.js";
import { db } from "../db/index.js";
import { isLanguageCode, LEVELS, type LanguageCode, type Level } from "./languages.js";

export type LearnerProfile = {
  targetLanguage: LanguageCode;
  nativeLanguage: LanguageCode;
  level: Level;
};

function normalize(row: typeof userProfile.$inferSelect): LearnerProfile {
  return {
    targetLanguage: isLanguageCode(row.targetLanguage) ? row.targetLanguage : "es",
    nativeLanguage: isLanguageCode(row.nativeLanguage) ? row.nativeLanguage : "en",
    level: (LEVELS as readonly string[]).includes(row.level) ? (row.level as Level) : "B1",
  };
}

/** Returns the learner's profile, creating the default (Spanish · B1) on first access. */
export async function getProfile(userId: string): Promise<LearnerProfile> {
  const existing = await db.query.userProfile.findFirst({ where: eq(userProfile.userId, userId) });
  if (existing) return normalize(existing);

  const [created] = await db.insert(userProfile).values({ userId }).onConflictDoNothing().returning();
  if (created) return normalize(created);

  const row = await db.query.userProfile.findFirst({ where: eq(userProfile.userId, userId) });
  return normalize(row!);
}

export async function updateProfile(userId: string, patch: Partial<LearnerProfile>): Promise<LearnerProfile> {
  await getProfile(userId);
  const [row] = await db.update(userProfile).set(patch).where(eq(userProfile.userId, userId)).returning();
  return normalize(row);
}
