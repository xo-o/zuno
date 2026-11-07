import { eq } from "drizzle-orm";
import { Hono } from "hono";
import { z } from "zod";
import { user } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { parseBody, requireAuth, type AuthEnv } from "../lib/http.js";
import { LANGUAGE_CODES, LEVELS, nativeLanguageFor } from "../lib/languages.js";
import { getProfile, updateProfile } from "../lib/profile.js";

const updateSchema = z.object({
  name: z.string().trim().min(1).max(80).optional(),
  targetLanguage: z.enum(LANGUAGE_CODES).optional(),
  level: z.enum(LEVELS).optional(),
});

function serializeUser(row: { id: string; name: string; email: string; image?: string | null }) {
  return { id: row.id, name: row.name, email: row.email, image: row.image ?? null };
}

export const profileRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/", async (c) => {
    const current = c.get("user");
    return c.json({ user: serializeUser(current), profile: await getProfile(current.id) });
  })
  .patch("/", async (c) => {
    const current = c.get("user");
    const body = await parseBody(c, updateSchema);

    let account = serializeUser(current);
    if (body.name) {
      const [row] = await db.update(user).set({ name: body.name }).where(eq(user.id, current.id)).returning();
      account = serializeUser(row);
    }

    const profile = await updateProfile(current.id, {
      ...(body.level && { level: body.level }),
      ...(body.targetLanguage && {
        targetLanguage: body.targetLanguage,
        nativeLanguage: nativeLanguageFor(body.targetLanguage),
      }),
    });
    return c.json({ user: account, profile });
  });
