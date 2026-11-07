import type { Context } from "hono";
import { createMiddleware } from "hono/factory";
import { HTTPException } from "hono/http-exception";
import type { z } from "zod";
import { auth } from "./auth.js";

export type AuthEnv = {
  Variables: {
    user: typeof auth.$Infer.Session.user;
  };
};

/** Rejects requests without a valid Better Auth session (cookie or bearer token). */
export const requireAuth = createMiddleware<AuthEnv>(async (c, next) => {
  const session = await auth.api.getSession({ headers: c.req.raw.headers });
  if (!session) throw new HTTPException(401, { message: "Unauthorized" });
  c.set("user", session.user);
  await next();
});

export async function parseBody<T extends z.ZodType>(c: Context, schema: T): Promise<z.infer<T>> {
  const raw = await c.req.json().catch(() => {
    throw new HTTPException(400, { message: "Request body must be valid JSON" });
  });
  const result = schema.safeParse(raw);
  if (!result.success) {
    const issue = result.error.issues[0];
    throw new HTTPException(400, { message: `${issue?.path.join(".") || "body"}: ${issue?.message ?? "invalid"}` });
  }
  return result.data;
}

/**
 * Fixed-window, in-memory limiter for the AI endpoints. Good enough for a single
 * instance; move to Redis if the backend is scaled horizontally.
 */
export function rateLimit(options: { windowMs: number; max: number }) {
  const hits = new Map<string, { count: number; resetAt: number }>();
  setInterval(() => {
    const now = Date.now();
    for (const [key, entry] of hits) if (entry.resetAt <= now) hits.delete(key);
  }, options.windowMs).unref?.();

  return createMiddleware<AuthEnv>(async (c, next) => {
    const key = c.get("user").id;
    const now = Date.now();
    const entry = hits.get(key);
    if (!entry || entry.resetAt <= now) {
      hits.set(key, { count: 1, resetAt: now + options.windowMs });
    } else if (++entry.count > options.max) {
      c.header("Retry-After", String(Math.ceil((entry.resetAt - now) / 1000)));
      throw new HTTPException(429, { message: "Too many requests. Please slow down." });
    }
    await next();
  });
}
