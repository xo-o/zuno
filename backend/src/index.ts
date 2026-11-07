import "dotenv/config";
import { randomUUID } from "node:crypto";
import { serve } from "@hono/node-server";
import { Hono } from "hono";
import { cors } from "hono/cors";
import { logger } from "hono/logger";
import { HTTPException } from "hono/http-exception";
import { AccessToken } from "livekit-server-sdk";
import { describeAttachment, startAttachmentJobs } from "./lib/attachments.js";
import { auth } from "./lib/auth.js";
import { contextFor, findConversation, listMessages } from "./lib/conversations.js";
import { GeminiError } from "./lib/gemini.js";
import { completeFlow, consumeFlow, getFlow, isFlowVerified } from "./lib/magic-link-flows.js";
import { getProfile } from "./lib/profile.js";
import { voiceInstructions } from "./lib/tutor.js";
import { attachmentRoutes } from "./routes/attachments.js";
import { conversationRoutes, messageRoutes, tutorRoutes } from "./routes/conversations.js";
import { profileRoutes } from "./routes/profile.js";
import { roleplayRoutes } from "./routes/roleplays.js";
import { topicRoutes } from "./routes/topics.js";

const app = new Hono();

app.onError((err, c) => {
  if (err instanceof HTTPException) {
    return c.json({ error: err.message }, err.status);
  }
  if (err instanceof GeminiError) {
    console.error("Gemini error:", err.message);
    const status = err.status === 429 ? 503 : 502;
    return c.json({ error: "Zuno is busy right now. Please try again in a moment." }, status);
  }
  console.error("Unhandled error:", err);
  return c.json({ error: "Something went wrong. Please try again." }, 500);
});

// Middlewares
app.use("*", logger());
app.use(
  "*",
  cors({
    origin: (origin) => origin || "*",
    allowHeaders: ["Content-Type", "Authorization"],
    allowMethods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    exposeHeaders: ["Content-Length"],
    credentials: true,
  })
);

// 1. Polling endpoint for Cross-Device Magic Link verification
app.get("/api/auth/magic-link/poll", (c) => {
  const flowId = c.req.query("flowId");
  if (!flowId) {
    return c.json({ error: "Missing flowId" }, 400);
  }

  const flow = getFlow(flowId);
  if (!flow) {
    return c.json({ status: "expired" }, 404);
  }

  if (flow.status === "verified" && flow.sessionToken) {
    consumeFlow(flowId);
    return c.json({
      status: "verified",
      token: flow.sessionToken,
      user: flow.user,
    });
  }

  return c.json({ status: "pending" });
});

// 2. Better Auth routes (Google OAuth, Magic Links, Sessions)
app.all("/api/auth/*", (c) => {
  return auth.handler(c.req.raw);
});

// 2. App API (protected by session / bearer token)
app.route("/api/me", profileRoutes);
app.route("/api/topics", topicRoutes);
app.route("/api/roleplays", roleplayRoutes);
app.route("/api/conversations", conversationRoutes);
app.route("/api/messages", messageRoutes);
app.route("/api/tutor", tutorRoutes);
app.route("/api/attachments", attachmentRoutes);

// 3. LiveKit token generation
type TokenRequest = {
  provider?: string;
  voice?: string;
  language?: string;
  /** When set, the voice agent follows this conversation's roleplay / lesson context. */
  conversationId?: string;
  /** When the conversation ends with a tutor reply, the agent opens the call by speaking it. */
  speakLastReply?: boolean;
};

const TOKEN_TTL = "6h";
const VOICE_HISTORY_TURNS = 12;

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

  const profile = await getProfile(user.id);
  const thread = body.conversationId ? await findConversation(user.id, body.conversationId) : undefined;
  const context = thread ? await contextFor(thread) : { kind: "freetalk" as const };
  const history = thread ? (await listMessages(thread.id)).slice(-VOICE_HISTORY_TURNS) : [];
  const transcript = history
    .map((m) => {
      const files = m.attachments.map(describeAttachment).join("\n");
      return `${m.role === "user" ? "Learner" : "You"}: ${[files, m.text].filter(Boolean).join("\n")}`;
    })
    .join("\n");
  const instructions = transcript
    ? `${voiceInstructions(context, profile)}\n\nConversation so far (continue naturally from here):\n${transcript}`
    : voiceInstructions(context, profile);
  const lastTurn = history.at(-1);
  const opening = body.speakLastReply && lastTurn?.role !== "user" ? lastTurn?.text : undefined;

  const token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
    identity: user.id,
    name: user.name || user.email,
    ttl: TOKEN_TTL,
    metadata: JSON.stringify({
      provider: body.provider ?? "breeze",
      voice: body.voice ?? "voc_9f5gtax4x5ac",
      language: body.language ?? profile.targetLanguage,
      instructions,
      // The agent stays quiet until the learner speaks when the conversation is already under way.
      resume: history.length > 0,
      opening,
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

// 5. Cross-Device & Mobile verify endpoint for Magic Links
app.get("/auth/verify", async (c) => {
  const token = c.req.query("token");
  const flowId = c.req.query("flowId");

  if (!token) {
    return c.html(
      `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Sign in to Zuno</title>
  <style>
    body { margin: 0; background: #0b0b0c; color: #fff; font-family: -apple-system, BlinkMacSystemFont, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
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

  // Attempt to redeem the magic link token server-side
  let sessionResult: any = null;
  try {
    sessionResult = await auth.api.magicLinkVerify({
      query: { token, callbackURL: "" },
      headers: c.req.raw.headers,
    });
  } catch (err: any) {
    // If token was already consumed or invalid, check if the flow was already verified
    if (!flowId || !isFlowVerified(flowId)) {
      console.warn("Magic link token redemption failed:", err?.message || err);
    }
  }

  const sessionToken = sessionResult?.token || sessionResult?.session?.token;
  if (sessionToken && flowId) {
    completeFlow(flowId, sessionToken, sessionResult?.user);
  }

  const verified = Boolean(sessionToken || (flowId && isFlowVerified(flowId)));
  const appUrl = `zuno://auth/verify?token=${encodeURIComponent(token)}`;

  if (verified) {
    return c.html(
      `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Signed In · Zuno</title>
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
      border-radius: 24px;
      padding: 40px 32px;
      max-width: 440px;
      width: 100%;
      text-align: center;
      box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
    }
    .badge {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: rgba(34, 197, 94, 0.15);
      border: 1px solid rgba(34, 197, 94, 0.4);
      color: #22c55e;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 20px;
      font-size: 28px;
    }
    h1 { margin: 0 0 12px; font-size: 24px; font-weight: 700; letter-spacing: -0.02em; }
    p { color: #a1a1aa; font-size: 15px; line-height: 1.6; margin: 0 0 28px; }
    .highlight { color: #ffffff; font-weight: 600; }
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
    .hint { font-size: 12px; color: #71717a; margin-top: 24px; }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">✓</div>
    <h1>You're signed in!</h1>
    <p>Your identity has been verified. The <span class="highlight">Zuno app on your phone</span> is signing in automatically.</p>
    <a class="button" href="${appUrl}">Open Zuno</a>
    <div class="hint">You can safely close this browser window.</div>
  </div>
  <script>
    // Auto-forward if opened in mobile browser
    if (/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)) {
      window.location.href = "${appUrl}";
    }
  </script>
</body>
</html>`
    );
  }

  // Fallback for invalid or expired tokens
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
    }
    h1 { margin-top: 0; font-size: 22px; font-weight: 600; }
    p { color: #a1a1aa; font-size: 15px; line-height: 1.5; margin-bottom: 24px; }
    a.button {
      display: inline-block;
      background: #ffffff;
      color: #000000;
      font-weight: 600;
      font-size: 15px;
      padding: 14px 28px;
      border-radius: 9999px;
      text-decoration: none;
    }
  </style>
</head>
<body>
  <div class="card">
    <h1>Sign in to Zuno</h1>
    <p>Open this link on the phone where Zuno is installed to finish signing in.</p>
    <a class="button" href="${appUrl}">Open Zuno</a>
  </div>
  <script>
    if (/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)) {
      window.location.href = "${appUrl}";
    }
  </script>
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
startAttachmentJobs();
