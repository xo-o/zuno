/**
 * Dev helper: creates (or deletes) a throwaway user with a signed bearer token,
 * so the API can be exercised with curl without going through sign-in.
 *
 *   npx tsx scripts/dev-session.ts create       # prints the bearer token
 *   npx tsx scripts/dev-session.ts magic-link   # prints a magic link token (no email sent)
 *   npx tsx scripts/dev-session.ts delete       # removes the test user and its data
 */
import "dotenv/config";
import { createHash, randomBytes } from "node:crypto";
import { eq } from "drizzle-orm";
import { makeSignature } from "better-auth/crypto";
import { auth } from "../src/lib/auth.js";
import { db } from "../src/db/index.js";
import { user } from "../src/db/auth-schema.js";

const EMAIL = "zuno-dev-test@example.com";

async function main() {
  const mode = process.argv[2];
  if (mode === "delete") {
    await db.delete(user).where(eq(user.email, EMAIL));
    console.log("deleted");
    return;
  }
  const ctx = await auth.$context;
  if (mode === "magic-link") {
    // Mirrors the magic-link plugin with storeToken: "hashed".
    const token = randomBytes(24).toString("base64url");
    const stored = createHash("sha256").update(token).digest("base64url");
    await ctx.internalAdapter.createVerificationValue({
      identifier: `magic-link:${stored}`,
      value: JSON.stringify({ type: "magic-link", email: EMAIL, name: "Noah Carter" }),
      expiresAt: new Date(Date.now() + 15 * 60 * 1000),
    });
    console.log(token);
    return;
  }
  const existing = await ctx.internalAdapter.findUserByEmail(EMAIL);
  const account = existing?.user ?? (await ctx.internalAdapter.createUser({ email: EMAIL, name: "Noah Carter", emailVerified: true }));
  const session = await ctx.internalAdapter.createSession(account.id);
  const signature = await makeSignature(session.token, ctx.secret);
  console.log(`${session.token}.${signature}`);
}

main().then(() => process.exit(0), (err) => {
  console.error(err);
  process.exit(1);
});
