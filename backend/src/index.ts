import "dotenv/config";
import { randomUUID } from "node:crypto";
import { serve } from "@hono/node-server";
import { Hono } from "hono";
import { cors } from "hono/cors";
import { logger } from "hono/logger";
import { HTTPException } from "hono/http-exception";
import { AccessToken } from "livekit-server-sdk";
import { makeSignature } from "better-auth/crypto";
import { describeAttachment, startAttachmentJobs } from "./lib/attachments.js";
import { auth } from "./lib/auth.js";
import { and, eq } from "drizzle-orm";
import { conversation, roleplay, topic } from "./db/app-schema.js";
import { db, ensureRuntimeSchema } from "./db/index.js";
import { findSeedRoleplay, findSeedTopic } from "./lib/community-seeds.js";
import { contextFor, findConversation, listMessages } from "./lib/conversations.js";
import { GeminiError } from "./lib/gemini.js";
import { startJobRunner } from "./lib/jobs.js";
import { languageList, languageName } from "./lib/languages.js";
import { applyUnsubscribe, verifyUnsubscribe } from "./lib/notifications.js";
import "./lib/schedule-service.js";
import { publicBaseUrl } from "./lib/serializers.js";
import {
  renderMessagePage,
  renderNotFoundPage,
  renderRoleplaySharePage,
  renderSharePage,
  renderTopicSharePage,
} from "./lib/share-page.js";
import { completeFlow, consumeFlow, getFlow } from "./lib/magic-link-flows.js";
import { renderVerifyLandingPage, renderVerifyResultPage } from "./lib/verify-page.js";
import { getProfile, type LearnerProfile } from "./lib/profile.js";
import { variety, voiceInstructions } from "./lib/tutor.js";
import { resolveTutor, TUTOR_CATALOG } from "./lib/voices.js";
import { accountDeletionRoutes } from "./routes/account.js";
import { attachmentRoutes } from "./routes/attachments.js";
import { chatRoutes } from "./routes/chats.js";
import { contactRoutes } from "./routes/contact.js";
import { conversationRoutes, messageRoutes, reminderRoutes, tutorRoutes } from "./routes/conversations.js";
import { deviceRoutes } from "./routes/devices.js";
import { lessonRoutes } from "./routes/lessons.js";
import { liveRoomRoutes } from "./routes/liveRooms.js";
import { profileRoutes } from "./routes/profile.js";
import { ensureRoleplayGoals, roleplayRoutes } from "./routes/roleplays.js";
import { scheduleRoutes } from "./routes/schedules.js";
import { topicRoutes } from "./routes/topics.js";
import { ttsRoutes } from "./routes/tts.js";
import { vocabularyRoutes } from "./routes/vocabulary.js";

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
    // The app sends bearer tokens and needs no CORS; only Zuno's own web origins may call from a browser.
    origin: (process.env.CORS_ORIGINS ?? "https://heyzuno.app")
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean),
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

  const flow = consumeFlow(flowId);
  if (!flow) {
    return c.json({ status: "expired" }, 404);
  }

  if (flow.status === "verified" && flow.sessionToken) {
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
app.route("/api/lessons", lessonRoutes);
app.route("/api/tts", ttsRoutes);
app.route("/api/roleplays", roleplayRoutes);
app.route("/api/conversations", conversationRoutes);
app.route("/api/chats", chatRoutes);
app.route("/api/messages", messageRoutes);
app.route("/api/tutor", tutorRoutes);
app.route("/api/attachments", attachmentRoutes);
app.route("/api/devices", deviceRoutes);
app.route("/api/reminders", reminderRoutes);
app.route("/api/schedules", scheduleRoutes);
app.route("/api/vocabulary", vocabularyRoutes);
app.route("/api/live-rooms", liveRoomRoutes);

// Public endpoints for the website (heyzuno.app): account deletion by email link and the contact form.
app.route("/api/account/deletion", accountDeletionRoutes);
app.route("/api/contact", contactRoutes);

/** Learning languages (Settings · Learning language). Public so the app can cache it before sign-in. */
app.get("/api/languages", (c) => {
  c.header("Cache-Control", "public, max-age=3600");
  return c.json({ languages: languageList() });
});

/** One-click unsubscribe from summary / news / schedule emails. */
app.on(["GET", "POST"], "/api/email/unsubscribe", async (c) => {
  const target = verifyUnsubscribe(c.req.query("token") ?? "");
  if (!target) return c.html(renderMessagePage("Link not valid", "This unsubscribe link is invalid or incomplete."), 400);
  await applyUnsubscribe(target.userId, target.kind);
  return c.html(renderMessagePage("You're unsubscribed", "You won't get these emails anymore. You can turn them back on in Zuno's notification settings."));
});

/** Read-only page for a chat shared with "Public access". */
app.get("/s/:token", async (c) => {
  const row = await db.query.conversation.findFirst({ where: eq(conversation.shareToken, c.req.param("token")) });
  if (!row || row.visibility !== "public") return c.html(renderNotFoundPage(), 404);
  const messages = await listMessages(row.id);
  c.header("Cache-Control", "no-store");
  return c.html(
    renderSharePage({
      title: row.title,
      kind: row.kind,
      language: row.language,
      messages,
      url: `${publicBaseUrl()}/s/${row.shareToken}`,
    }),
  );
});

/** Public preview page for a shared or official Topic (`/t/:token`). */
app.get("/t/:token", async (c) => {
  const token = c.req.param("token");
  c.header("Cache-Control", "no-store");
  const seed = findSeedTopic(token);
  if (seed) {
    return c.html(
      renderTopicSharePage({
        title: seed.generated.title,
        translation: seed.generated.translation,
        language: seed.language,
        authorHandle: seed.authorHandle,
        lessons: seed.generated.lessons.map((l, idx) => ({ position: idx + 1, ...l })),
        url: `${publicBaseUrl()}/t/${seed.id}`,
        appDeepLink: `zuno://topic-preview/${seed.id}`,
      }),
    );
  }
  const row =
    (await db.query.topic.findFirst({
      where: and(eq(topic.shareToken, token), eq(topic.visibility, "public")),
      with: { lessons: true },
    })) ??
    (await db.query.topic.findFirst({
      where: and(eq(topic.id, token), eq(topic.visibility, "public")),
      with: { lessons: true },
    }));
  if (!row) return c.html(renderNotFoundPage(), 404);
  const ordered = [...row.lessons].sort((a, b) => a.position - b.position);
  return c.html(
    renderTopicSharePage({
      title: row.title,
      translation: row.translation,
      language: row.language,
      authorHandle: row.authorHandle,
      lessons: ordered.map((l) => ({
        position: l.position,
        title: l.title,
        translation: l.translation,
        objective: l.objective,
      })),
      url: `${publicBaseUrl()}/t/${row.shareToken ?? row.id}`,
      appDeepLink: `zuno://topic-preview/${row.shareToken ?? row.id}`,
    }),
  );
});

/** Public preview page for a shared or official Roleplay (`/r/:token`). */
app.get("/r/:token", async (c) => {
  const token = c.req.param("token");
  c.header("Cache-Control", "no-store");
  const seed = findSeedRoleplay(token);
  if (seed) {
    return c.html(
      renderRoleplaySharePage({
        title: seed.generated.title,
        scenario: seed.generated.scenario,
        userRole: seed.generated.userRole,
        aiRole: seed.generated.aiRole,
        setting: seed.generated.setting,
        language: seed.language,
        authorHandle: seed.authorHandle,
        url: `${publicBaseUrl()}/r/${seed.id}`,
        appDeepLink: `zuno://roleplay-preview/${seed.id}`,
      }),
    );
  }
  const row =
    (await db.query.roleplay.findFirst({
      where: and(eq(roleplay.shareToken, token), eq(roleplay.visibility, "public")),
    })) ??
    (await db.query.roleplay.findFirst({
      where: and(eq(roleplay.id, token), eq(roleplay.visibility, "public")),
    }));
  if (!row) return c.html(renderNotFoundPage(), 404);
  return c.html(
    renderRoleplaySharePage({
      title: row.title,
      scenario: row.scenario,
      userRole: row.userRole,
      aiRole: row.aiRole,
      setting: row.setting,
      language: row.language,
      authorHandle: row.authorHandle,
      url: `${publicBaseUrl()}/r/${row.shareToken ?? row.id}`,
      appDeepLink: `zuno://roleplay-preview/${row.shareToken ?? row.id}`,
    }),
  );
});

// 3. LiveKit token generation
type TokenRequest = {
  language?: string;
  /** "conversation" (default) or "onboarding" (`5b`–`5d` guided setup). */
  mode?: "conversation" | "onboarding";
  /** When set, the voice agent follows this conversation's roleplay / lesson context. */
  conversationId?: string;
  /** When the conversation ends with a tutor reply, the agent opens the call by speaking it. */
  speakLastReply?: boolean;
  /** Initial onboarding state when `mode === "onboarding"`. */
  onboarding?: {
    step?: "name" | "level" | "goals";
    targetLanguage?: string;
    nativeLanguage?: string;
    accent?: string;
    name?: string;
    level?: string;
  };
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
  const room = `zuno-${randomUUID().slice(0, 8)}`;

  const profile = await getProfile(user.id);
  const tutor = resolveTutor(profile.tutorId, profile.tutorVoice);
  const voice = tutor.voiceId;

  if (body.mode === "onboarding") {
    const ob = body.onboarding ?? {};
    const nativeLanguage = ob.nativeLanguage || profile.nativeLanguage || "en";
    const targetLanguage = ob.targetLanguage || body.language || profile.targetLanguage;
    const accent = ob.accent || profile.accent;
    const onboardingTutor = TUTOR_CATALOG.sofia;
    const token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
      identity: user.id,
      name: ob.name || user.name || user.email,
      ttl: TOKEN_TTL,
      metadata: JSON.stringify({
        mode: "onboarding",
        provider: "elevenlabs",
        voice: onboardingTutor.voiceId,
        tutorId: onboardingTutor.id,
        tutorName: onboardingTutor.name,
        // Onboarding conducts STT + TTS in the learner's native/app language.
        language: nativeLanguage,
        targetLanguage,
        nativeLanguage,
        accent,
        tutorVoice: onboardingTutor.voicePreset,
        handsFree: true,
        onboarding: {
          step: ob.step ?? "name",
          name: ob.name ?? "",
          level: ob.level ?? "",
        },
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
  }

  const thread = body.conversationId ? await findConversation(user.id, body.conversationId) : undefined;
  const targetLanguage = thread?.language || body.language || profile.targetLanguage;
  const effectiveProfile =
    targetLanguage !== profile.targetLanguage
      ? { ...profile, targetLanguage: targetLanguage as LearnerProfile["targetLanguage"] }
      : profile;

  const context = thread ? await contextFor(thread) : { kind: "freetalk" as const };
  const history = thread ? (await listMessages(thread.id)).slice(-VOICE_HISTORY_TURNS) : [];
  const transcript = history
    .map((m) => {
      const files = m.attachments.map(describeAttachment).join("\n");
      return `${m.role === "user" ? "Learner" : "You"}: ${[files, m.text].filter(Boolean).join("\n")}`;
    })
    .join("\n");
  const lastTurn = history.at(-1);
  const opening = body.speakLastReply && lastTurn?.role !== "user" ? lastTurn?.text : undefined;

  if (thread?.kind === "roleplay" && thread.roleplayId) {
    const rpRow = await db.query.roleplay.findFirst({
      where: and(eq(roleplay.id, thread.roleplayId), eq(roleplay.userId, user.id)),
    });
    if (rpRow) {
      const goals = await ensureRoleplayGoals(rpRow, effectiveProfile);
      const rpToken = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
        identity: user.id,
        name: user.name || user.email,
        ttl: TOKEN_TTL,
        metadata: JSON.stringify({
          mode: "roleplay",
          provider: "elevenlabs",
          voice,
          tutorId: tutor.id,
          tutorName: tutor.name,
          tutorVoice: tutor.voicePreset,
          roleplayId: rpRow.id,
          conversationId: thread.id,
          language: targetLanguage,
          targetLanguage,
          targetLanguageName: languageName(targetLanguage),
          varietyName: variety(effectiveProfile),
          nativeLanguage: profile.nativeLanguage,
          nativeLanguageName: languageName(profile.nativeLanguage),
          accent: effectiveProfile.accent,
          level: effectiveProfile.level,
          teachInTarget: effectiveProfile.teachInTarget,
          teachingStyle: effectiveProfile.teachingStyle,
          strictMode: effectiveProfile.strictMode,
          handsFree: effectiveProfile.handsFree,
          resume: history.length > 0,
          opening,
          historyTranscript: transcript,
          roleplay: {
            id: rpRow.id,
            title: rpRow.title,
            scenario: rpRow.scenario,
            userRole: rpRow.userRole,
            aiRole: rpRow.aiRole,
            setting: rpRow.setting,
            goals,
          },
        }),
      });
      rpToken.addGrant({
        room,
        roomJoin: true,
        canPublish: true,
        canSubscribe: true,
        canPublishData: true,
      });
      return c.json({
        token: await rpToken.toJwt(),
        url: LIVEKIT_URL,
        room,
        identity: user.id,
      });
    }
  }

  const instructions = transcript
    ? `${voiceInstructions(context, effectiveProfile)}\n\nConversation so far (continue naturally from here):\n${transcript}`
    : voiceInstructions(context, effectiveProfile);

  const token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
    identity: user.id,
    name: user.name || user.email,
    ttl: TOKEN_TTL,
    metadata: JSON.stringify({
      mode: "conversation",
      conversationKind: context.kind,
      provider: "elevenlabs",
      voice,
      tutorId: tutor.id,
      tutorName: tutor.name,
      language: targetLanguage,
      targetLanguage,
      targetLanguageName: languageName(targetLanguage),
      varietyName: variety(effectiveProfile),
      nativeLanguage: profile.nativeLanguage,
      nativeLanguageName: languageName(profile.nativeLanguage),
      accent: effectiveProfile.accent,
      level: effectiveProfile.level,
      teachInTarget: effectiveProfile.teachInTarget,
      tutorVoice: tutor.voicePreset,
      handsFree: profile.handsFree,
      instructions,
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
  const packageName = process.env.ANDROID_PACKAGE_NAME ?? "com.zuno.ai";
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

// 5. Magic link landing page. GET never redeems the token (see renderVerifyLandingPage);
// confirming a waiting phone's sign-in from another device is an explicit POST.
const noStore = { "Cache-Control": "no-store", "Referrer-Policy": "no-referrer" };

app.get("/auth/verify", (c) => {
  const token = c.req.query("token");
  if (!token) {
    return c.html(
      renderVerifyResultPage("Link not valid", "This sign-in link is invalid or incomplete. Request a new one from the app.", {
        variant: "error",
        badge: "Invalid",
        actionLabel: "Back to Zuno app",
        actionUrl: "zuno://auth",
      }),
      400,
      noStore,
    );
  }
  const flowId = c.req.query("flowId");
  const flow = flowId ? getFlow(flowId) : undefined;
  const pending = flow?.status === "pending";
  return c.html(
    renderVerifyLandingPage({ token, flowId: pending ? flowId : undefined, email: pending ? flow?.email : undefined }),
    200,
    noStore,
  );
});

app.post("/auth/verify", async (c) => {
  const form = await c.req.parseBody();
  const token = typeof form.token === "string" ? form.token : "";
  const flowId = typeof form.flowId === "string" ? form.flowId : "";
  const flow = flowId ? getFlow(flowId) : undefined;
  if (!token || flow?.status !== "pending") {
    return c.html(
      renderVerifyResultPage("This link has expired", "This sign-in request is no longer waiting. Request a new link from the Zuno app.", {
        variant: "error",
        badge: "Expired",
        actionLabel: "Back to Zuno app",
        actionUrl: "zuno://auth",
      }),
      400,
      noStore,
    );
  }

  let result: Awaited<ReturnType<typeof auth.api.magicLinkVerify>> | undefined;
  try {
    result = await auth.api.magicLinkVerify({ query: { token, callbackURL: "" }, headers: c.req.raw.headers });
  } catch (err: any) {
    console.warn("Magic link token redemption failed:", err?.message || err);
  }
  const session = result as { token?: string; user?: { email?: string } } | undefined;
  if (!session?.token) {
    return c.html(
      renderVerifyResultPage("This link has expired", "Sign-in links work once and expire after 15 minutes. Request a new one from the Zuno app.", {
        variant: "error",
        badge: "Expired",
        actionLabel: "Back to Zuno app",
        actionUrl: "zuno://auth",
      }),
      400,
      noStore,
    );
  }
  if (session.user?.email?.toLowerCase() !== flow.email.toLowerCase()) {
    return c.html(
      renderVerifyResultPage("Link not valid", "This link doesn't match the sign-in request. Request a new one from the Zuno app.", {
        variant: "error",
        badge: "Invalid",
        actionLabel: "Back to Zuno app",
        actionUrl: "zuno://auth",
      }),
      400,
      noStore,
    );
  }

  // The bearer plugin requires signed tokens, the same form it returns in `set-auth-token`.
  const { secret } = await auth.$context;
  completeFlow(flowId, `${session.token}.${await makeSignature(session.token, secret)}`, session.user);
  return c.html(
    renderVerifyResultPage("You’re signed in", "Zuno on your phone is signing in now. You can safely close this window.", {
      variant: "success",
      badge: "Verified",
      email: flow.email,
    }),
    200,
    noStore,
  );
});

// 6. Health check & Root
app.get("/health", (c) => c.json({ status: "ok" }));
app.get("/", (c) => c.json({ name: "Zuno Backend API", status: "running" }));

// Server launch
const port = Number(process.env.PORT) || 3000;
console.log(`Server is running on port ${port}`);

serve({
  fetch: app.fetch,
  port,
});
void ensureRuntimeSchema();
startAttachmentJobs();
// The queue only runs in production (or with RUN_JOBS=1), so a local dev server pointed at the same
// database doesn't claim jobs it can't deliver (e.g. without Firebase credentials).
const runJobs = process.env.RUN_JOBS === "1" || (process.env.NODE_ENV === "production" && process.env.DISABLE_JOBS !== "1");
if (runJobs) startJobRunner();
