import { and, eq } from "drizzle-orm";
import { Hono } from "hono";
import { z } from "zod";
import { device } from "../db/app-schema.js";
import { db } from "../db/index.js";
import { parseBody, requireAuth, type AuthEnv } from "../lib/http.js";
import { APP_LANGUAGE_CODES } from "../lib/languages.js";
import { updateTimeZone } from "../lib/notifications.js";
import { registerDevice } from "../lib/push.js";

const registerSchema = z.object({
  pushToken: z.string().trim().min(10).max(4096),
  platform: z.literal("android").default("android"),
  timezone: z.string().max(64).optional(),
  appLanguage: z.enum(APP_LANGUAGE_CODES).optional(),
});

export const deviceRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  /** Upserts this install's FCM token (called after sign-in and whenever FCM rotates the token). */
  .post("/", async (c) => {
    const userId = c.get("user").id;
    const body = await parseBody(c, registerSchema);
    await registerDevice(userId, body);
    await updateTimeZone(userId, body.timezone);
    return c.body(null, 204);
  })
  /** Sign-out: this device stops receiving pushes for the account. */
  .delete("/:pushToken", async (c) => {
    await db
      .delete(device)
      .where(and(eq(device.pushToken, decodeURIComponent(c.req.param("pushToken"))), eq(device.userId, c.get("user").id)));
    return c.body(null, 204);
  });
