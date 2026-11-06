import "dotenv/config";
import { randomUUID } from "node:crypto";
import { serve } from "@hono/node-server";
import { Hono } from "hono";
import { cors } from "hono/cors";
import { logger } from "hono/logger";
import { AccessToken } from "livekit-server-sdk";
import { auth } from "./lib/auth.js";

const app = new Hono();

// Middlewares
app.use("*", logger());
app.use(
  "*",
  cors({
    origin: (origin) => origin || "*",
    allowHeaders: ["Content-Type", "Authorization"],
    allowMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    exposeHeaders: ["Content-Length"],
    credentials: true,
  })
);

// 1. Better Auth routes (Google OAuth, Magic Links, Sessions)
app.on(["POST", "GET"], "/api/auth/**", (c) => {
  return auth.handler(c.req.raw);
});

// 2. Profile endpoint (Protected by session / bearer token)
app.get("/api/me", async (c) => {
  const session = await auth.api.getSession({ headers: c.req.raw.headers });
  if (!session) {
    return c.json({ error: "Unauthorized" }, 401);
  }
  return c.json({ user: session.user });
});

// 3. LiveKit token generation
type TokenRequest = {
  provider?: string;
  voice?: string;
  language?: string;
};

const TOKEN_TTL = "6h";

app.post("/api/livekit/token", async (c) => {
  const session = await auth.api.getSession({ headers: c.req.raw.headers });
  if (!session) {
    return c.json({ error: "Unauthorized" }, 401);
  }

  const { LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET } = process.env;
  if (!LIVEKIT_URL || !LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) {
    return c.json({ error: "LiveKit is not configured" }, 500);
  }

  const body = (await c.req.json().catch(() => ({}))) as TokenRequest;
  const { user } = session;
  const room = `talkie-${randomUUID().slice(0, 8)}`;

  const token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
    identity: user.id,
    name: user.name || user.email,
    ttl: TOKEN_TTL,
    metadata: JSON.stringify({
      provider: body.provider ?? "breeze",
      voice: body.voice ?? "voc_9f5gtax4x5ac",
      language: body.language ?? "en",
    }),
  });

  token.addGrant({
    room,
    roomJoin: true,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
  });

  return c.json({
    token: await token.toJwt(),
    url: LIVEKIT_URL,
    room,
    identity: user.id,
  });
});

// 4. Digital Asset Links (for Android App Links verification)
app.get("/.well-known/assetlinks.json", (c) => {
  const packageName = process.env.ANDROID_PACKAGE_NAME ?? "com.talkie.ai";
  const fingerprints = (process.env.ANDROID_SHA256_CERT_FINGERPRINTS ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);

  return c.json([
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: {
        namespace: "android_app",
        package_name: packageName,
        sha256_cert_fingerprints: fingerprints,
      },
    },
  ]);
});

// 5. Fallback verify page for Magic Links
app.get("/auth/verify", (c) => {
  const token = c.req.query("token");
  if (!token) {
    return c.html(
      `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Sign in to Zuno</title>
  <style>
    body { margin: 0; background: #0b0b0c; color: #fff; font-family: -apple-system, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
    p { color: #a1a1aa; font-size: 16px; }
  </style>
</head>
<body>
  <p>This sign-in link is invalid or expired. Please request a new one from the app.</p>
</body>
</html>`,
      400
    );
  }

  const appUrl = `zuno://auth/verify?token=${encodeURIComponent(token)}`;
  return c.html(
    `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Sign in to Zuno</title>
  <style>
    body {
      margin: 0;
      padding: 24px;
      background: #0b0b0c;
      color: #ffffff;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 90vh;
    }
    .card {
      background: #161618;
      border: 1px solid #2a2a2d;
      border-radius: 20px;
      padding: 32px;
      max-width: 420px;
      width: 100%;
      text-align: center;
      box-shadow: 0 8px 30px rgba(0, 0, 0, 0.4);
    }
    h1 { margin-top: 0; font-size: 22px; font-weight: 600; }
    p { color: #a1a1aa; font-size: 15px; line-height: 1.5; margin-bottom: 28px; }
    a.button {
      display: inline-block;
      background: #ffffff;
      color: #000000;
      font-weight: 600;
      font-size: 15px;
      padding: 14px 28px;
      border-radius: 9999px;
      text-decoration: none;
      transition: opacity 0.15s ease;
    }
    a.button:hover { opacity: 0.9; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Sign in to Zuno</h1>
    <p>Open this link on the phone where Zuno is installed to finish signing in.</p>
    <a class="button" href="${appUrl}">Open Zuno</a>
  </div>
</body>
</html>`
  );
});

// 6. Health check & Root
app.get("/health", (c) => c.json({ status: "ok" }));
app.get("/", (c) => c.json({ name: "Zuno / Talkie Backend API", status: "running" }));

// Server launch
const port = Number(process.env.PORT) || 3000;
console.log(`Server is running on port ${port}`);

serve({
  fetch: app.fetch,
  port,
});
