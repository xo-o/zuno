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
  const limiter = createLimiter(options);
  return createMiddleware<AuthEnv>(async (c, next) => {
    limiter(c, c.get("user").id);
    await next();
  });
}

/** The same fixed-window limiter for any key (IP, email…); the returned check throws 429 when exceeded. */
export function createLimiter(options: { windowMs: number; max: number; message?: string }) {
  const hits = new Map<string, { count: number; resetAt: number }>();
  setInterval(() => {
    const now = Date.now();
    for (const [key, entry] of hits) if (entry.resetAt <= now) hits.delete(key);
  }, options.windowMs).unref?.();

  return (c: Context, key: string) => {
    const now = Date.now();
    const entry = hits.get(key);
    if (!entry || entry.resetAt <= now) {
      hits.set(key, { count: 1, resetAt: now + options.windowMs });
    } else if (++entry.count > options.max) {
      c.header("Retry-After", String(Math.ceil((entry.resetAt - now) / 1000)));
      throw new HTTPException(429, { message: options.message ?? "Too many requests. Please slow down." });
    }
  };
}

/** Caller IP for public rate limits: the first X-Forwarded-For hop set by the proxy, else the socket address. */
export function clientIp(c: Context) {
  const forwarded = c.req.header("x-forwarded-for")?.split(",")[0]?.trim();
  if (forwarded) return forwarded;
  const real = c.req.header("x-real-ip")?.trim();
  if (real) return real;
  const incoming = (c.env as { incoming?: { socket?: { remoteAddress?: string } } } | undefined)?.incoming;
  return incoming?.socket?.remoteAddress ?? "unknown";
}
