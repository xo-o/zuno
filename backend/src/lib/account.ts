import { createHash, randomBytes, randomUUID } from "node:crypto";
import { and, count, eq, gt, lt, or, sql } from "drizzle-orm";
import { attachment, conversation, roleplay, topic } from "../db/app-schema.js";
import { user, verification } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { getProfile } from "./profile.js";
import { deleteObjects } from "./storage.js";

/** Avatars uploaded in the app live in the private bucket ("r2:<key>") and are served with signed URLs. */
export const R2_PREFIX = "r2:";

/**
 * Deletes an account: removes the learner's files from R2, then the user row — every app table, session
 * and linked account cascades from it. Returns the deleted user and their profile (for the confirmation
 * email), or undefined when the user no longer exists.
 */
export async function deleteAccount(userId: string) {
  const [row] = await db.select().from(user).where(eq(user.id, userId));
  if (!row) return undefined;
  const profile = await getProfile(userId);
  const files = await db.select({ key: attachment.storageKey }).from(attachment).where(eq(attachment.userId, userId));
  const keys = files.map((f) => f.key).filter(Boolean);
  if (row.image?.startsWith(R2_PREFIX)) keys.push(row.image.slice(R2_PREFIX.length));

  await db.delete(user).where(eq(user.id, userId));
  await deleteObjects(keys);
  return { user: row, profile };
}

// Deletion links requested from the website reuse Better Auth's `verification` table: the identifier
// holds only a SHA-256 of the token, the value holds the user id, and redeeming deletes the row.
const DELETION_PREFIX = "account-deletion:";
export const DELETION_TOKEN_TTL_MS = 30 * 60_000;

function deletionIdentifier(token: string) {
  return DELETION_PREFIX + createHash("sha256").update(token).digest("hex");
}

export async function findUserByEmail(email: string) {
  const [row] = await db.select().from(user).where(sql`lower(${user.email}) = ${email.toLowerCase()}`).limit(1);
  return row;
}

/** Issues a single-use deletion token for [userId]; earlier tokens for the user (and expired ones) are removed. */
export async function createDeletionToken(userId: string) {
  const token = randomBytes(32).toString("base64url");
  await db
    .delete(verification)
    .where(
      and(
        sql`${verification.identifier} like ${`${DELETION_PREFIX}%`}`,
        or(eq(verification.value, userId), lt(verification.expiresAt, new Date())),
      ),
    );
  await db.insert(verification).values({
    id: randomUUID(),
    identifier: deletionIdentifier(token),
    value: userId,
    expiresAt: new Date(Date.now() + DELETION_TOKEN_TTL_MS),
  });
  return token;
}

/** The user a valid, unexpired deletion token belongs to. */
export async function findDeletionTokenUser(token: string) {
  if (!token) return undefined;
  const [row] = await db
    .select({ user })
    .from(verification)
    .innerJoin(user, eq(user.id, verification.value))
    .where(and(eq(verification.identifier, deletionIdentifier(token)), gt(verification.expiresAt, new Date())))
    .limit(1);
  return row?.user;
}

/** Atomically redeems a deletion token; returns the user id only for the first caller. */
export async function consumeDeletionToken(token: string) {
  if (!token) return undefined;
  const [row] = await db
    .delete(verification)
    .where(and(eq(verification.identifier, deletionIdentifier(token)), gt(verification.expiresAt, new Date())))
    .returning({ userId: verification.value });
  return row?.userId;
}

export async function accountCounts(userId: string) {
  const [[chats], [topics], [roleplays]] = await Promise.all([
    db.select({ n: count() }).from(conversation).where(eq(conversation.userId, userId)),
    db.select({ n: count() }).from(topic).where(eq(topic.userId, userId)),
    db.select({ n: count() }).from(roleplay).where(eq(roleplay.userId, userId)),
  ]);
  return { chats: chats?.n ?? 0, topics: topics?.n ?? 0, roleplays: roleplays?.n ?? 0 };
}
