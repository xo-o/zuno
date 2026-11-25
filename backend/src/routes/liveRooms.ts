import { randomUUID } from "node:crypto";
import { and, asc, desc, eq, lt, ne, sql } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { AccessToken, RoomServiceClient } from "livekit-server-sdk";
import { z } from "zod";
import { liveRoom, liveRoomMember } from "../db/app-schema.js";
import { user } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { recordPracticeActivity } from "../lib/activity.js";
import { resolveUserImageUrl } from "../lib/avatars.js";
import { parseBody, requireAuth, type AuthEnv } from "../lib/http.js";
import { LANGUAGE_CODES, LEVELS } from "../lib/languages.js";
import { getProfile } from "../lib/profile.js";

const createRoomSchema = z.object({
  title: z.string().trim().min(2).max(120),
  description: z.string().trim().max(300).optional(),
  language: z.enum(LANGUAGE_CODES).optional(),
  level: z.enum(LEVELS).optional(),
  maxMembers: z.number().int().min(2).max(4).optional().default(4),
  visibility: z.enum(["public", "private"]).optional().default("public"),
});

const patchRoomSchema = z.object({
  visibility: z.enum(["public", "private"]).optional(),
  nextQuestion: z.boolean().optional(),
});

const endOrLeaveSchema = z.object({
  durationSec: z.number().int().min(0).max(36_000).optional().default(0),
});

const MEMBER_STALE_MS = 60_000;
const EMPTY_USER_LIVE_GRACE_MS = 60_000;
const EMPTY_USER_LOBBY_TTL_MS = 10 * 60_000;
const RECONCILE_CACHE_MS = 5_000;

let lastReconciledAt = 0;

function livekitRoomService(): RoomServiceClient | null {
  const { LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET } = process.env;
  if (!LIVEKIT_URL || !LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) return null;
  const httpUrl = LIVEKIT_URL.replace(/^wss:/i, "https:").replace(/^ws:/i, "http:");
  return new RoomServiceClient(httpUrl, LIVEKIT_API_KEY, LIVEKIT_API_SECRET);
}

function generateRoomCode(): string {
  const num = Math.floor(100 + Math.random() * 900);
  return `ZUNO-${num}`;
}

function starterPhrasesForLanguage(language: string): string[] {
  switch (language) {
    case "es":
      return ["Siempre pido…", "En mi opinión…", "¿Y tú qué opinas?"];
    case "fr":
      return ["Je prends toujours…", "À mon avis…", "Et toi, tu en penses quoi ?"];
    case "de":
      return ["Ich bestelle immer…", "Meiner Meinung nach…", "Was denkst du darüber?"];
    case "it":
      return ["Di solito prendo…", "Secondo me…", "Tu cosa ne pensi?"];
    case "pt":
      return ["Eu sempre peço…", "Na minha opinião…", "E você, o que acha?"];
    case "nl":
      return ["Ik bestel meestal…", "Volgens mij…", "Wat vind jij ervan?"];
    case "pl":
      return ["Zazwyczaj zamawiam…", "Moim zdaniem…", "A ty co o tym myślisz?"];
    default:
      return ["I usually go for…", "In my experience…", "What do you think?"];
  }
}

async function ensureSystemRooms(language: string) {
  const existing = await db
    .select({ id: liveRoom.id })
    .from(liveRoom)
    .where(and(eq(liveRoom.language, language), eq(liveRoom.sourceKind, "system"), ne(liveRoom.status, "ended")))
    .limit(1);
  if (existing.length > 0) return;

  const featuredId = `sys-featured-${language}`;
  const secondaryId = `sys-remote-${language}`;
  const starters = starterPhrasesForLanguage(language);

  await db
    .insert(liveRoom)
    .values([
      {
        id: featuredId,
        hostUserId: null,
        title: "Café culture & morning routines",
        description: "Zuno-guided speaking room. Jump in anytime with fellow learners — live prompts keep the conversation flowing.",
        language,
        level: "B1",
        maxMembers: 4,
        visibility: "public",
        sourceKind: "system",
        status: "live",
        livekitRoom: `zuno-room-${featuredId}`,
        discussionPrompts: [
          "Your go-to morning coffee order and routine",
          "Favorite neighborhood cafés in your city",
          "Comparing café culture across different cities",
        ],
        starterPhrases: starters,
        currentRound: 1,
      },
      {
        id: secondaryId,
        hostUserId: null,
        title: "Remote work vs. office life",
        description: "Compare async collaboration, focus routines, and commute trade-offs with other learners.",
        language,
        level: "B2",
        maxMembers: 4,
        visibility: "public",
        sourceKind: "system",
        status: "live",
        livekitRoom: `zuno-room-${secondaryId}`,
        discussionPrompts: [
          "What part of your daily work setup matters most?",
          "How do you stay connected with teammates remotely?",
          "Ideal balance between deep work and office days",
        ],
        starterPhrases: starters,
        currentRound: 1,
      },
    ])
    .onConflictDoNothing();
}

/**
 * Reconciles `live_room_member` and user-created `live_room` rows against member heartbeats
 * (`last_seen_at`) and active LiveKit participants so stale or disconnected sessions never linger.
 */
export async function reconcileLiveRooms(force = false): Promise<void> {
  const now = Date.now();
  if (!force && now - lastReconciledAt < RECONCILE_CACHE_MS) return;
  lastReconciledAt = now;

  try {
    const staleCutoff = new Date(now - MEMBER_STALE_MS);
    const staleMembers = await db
      .select({
        roomId: liveRoomMember.roomId,
        userId: liveRoomMember.userId,
        livekitRoom: liveRoom.livekitRoom,
      })
      .from(liveRoomMember)
      .innerJoin(liveRoom, eq(liveRoomMember.roomId, liveRoom.id))
      .where(and(ne(liveRoom.status, "ended"), lt(liveRoomMember.lastSeenAt, staleCutoff)));

    if (staleMembers.length > 0) {
      const svc = livekitRoomService();
      const connectedByRoom = new Map<string, Set<string>>();
      if (svc) {
        const distinctLkRooms = [...new Set(staleMembers.map((m) => m.livekitRoom))];
        await Promise.all(
          distinctLkRooms.map(async (lkRoom) => {
            try {
              const participants = await svc.listParticipants(lkRoom);
              connectedByRoom.set(
                lkRoom,
                new Set(participants.map((p) => p.identity).filter((id) => !id.startsWith("agent-"))),
              );
            } catch {
              connectedByRoom.set(lkRoom, new Set());
            }
          }),
        );
      }

      for (const m of staleMembers) {
        const activeIdentities = connectedByRoom.get(m.livekitRoom);
        if (activeIdentities?.has(m.userId)) {
          // Participant is genuinely still connected in LiveKit; refresh lastSeenAt.
          await db
            .update(liveRoomMember)
            .set({ lastSeenAt: new Date(now) })
            .where(and(eq(liveRoomMember.roomId, m.roomId), eq(liveRoomMember.userId, m.userId)));
        } else {
          await db
            .delete(liveRoomMember)
            .where(and(eq(liveRoomMember.roomId, m.roomId), eq(liveRoomMember.userId, m.userId)));
        }
      }
    }

    // Auto-end user-created rooms that have been empty beyond their grace period.
    const liveGraceCutoff = new Date(now - EMPTY_USER_LIVE_GRACE_MS);
    const lobbyGraceCutoff = new Date(now - EMPTY_USER_LOBBY_TTL_MS);

    await db.execute(sql`
      UPDATE "live_room" r
      SET "status" = 'ended', "ended_at" = now(), "updated_at" = now()
      WHERE r."source_kind" = 'user'
        AND r."status" <> 'ended'
        AND NOT EXISTS (
          SELECT 1 FROM "live_room_member" m WHERE m."room_id" = r."id"
        )
        AND (
          (r."status" = 'live' AND COALESCE(r."started_at", r."updated_at") < ${liveGraceCutoff})
          OR (r."status" = 'lobby' AND r."updated_at" < ${lobbyGraceCutoff})
        );
    `);
  } catch (err) {
    console.warn("Live room reconciliation warning:", err);
  }
}

async function serializeRoom(roomRow: typeof liveRoom.$inferSelect, viewerId: string) {
  const members = await db
    .select({
      userId: liveRoomMember.userId,
      role: liveRoomMember.role,
      ready: liveRoomMember.ready,
      name: user.name,
      image: user.image,
    })
    .from(liveRoomMember)
    .innerJoin(user, eq(liveRoomMember.userId, user.id))
    .where(and(eq(liveRoomMember.roomId, roomRow.id), sql`${liveRoomMember.userId} not like 'seed:%'`))
    .orderBy(asc(liveRoomMember.joinedAt));

  const resolvedMembers = await Promise.all(
    members.map(async (m) => ({
      id: m.userId,
      name: m.name,
      initial: m.name.trim().slice(0, 1).toUpperCase() || "Z",
      imageUrl: await resolveUserImageUrl(m.image),
      isHost: m.role === "host",
      ready: m.ready,
      isYou: m.userId === viewerId,
    })),
  );

  const host = resolvedMembers.find((m) => m.isHost) ?? resolvedMembers[0];
  const starters =
    Array.isArray(roomRow.starterPhrases) && roomRow.starterPhrases.length > 0
      ? roomRow.starterPhrases
      : starterPhrasesForLanguage(roomRow.language);

  return {
    id: roomRow.id,
    title: roomRow.title,
    description: roomRow.description,
    language: roomRow.language,
    level: roomRow.level,
    maxMembers: roomRow.maxMembers,
    visibility: roomRow.visibility,
    code: roomRow.code,
    sourceKind: roomRow.sourceKind,
    status: roomRow.status,
    hostUserId: roomRow.hostUserId ?? "",
    hostName: roomRow.sourceKind === "system" ? "Zuno" : (host?.name ?? "Host"),
    isHost: roomRow.hostUserId === viewerId,
    discussionPrompts: roomRow.discussionPrompts,
    starterPhrases: starters,
    currentRound: roomRow.currentRound,
    members: resolvedMembers,
  };
}

async function issueLiveKitTicket(roomRow: typeof liveRoom.$inferSelect, viewer: { id: string; name: string; email: string }) {
  const { LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET } = process.env;
  if (!LIVEKIT_URL || !LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) return null;
  const profile = await getProfile(viewer.id).catch(() => null);
  const token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
    identity: viewer.id,
    name: viewer.name || viewer.email,
    ttl: "4h",
    metadata: JSON.stringify({
      mode: "live_room",
      roomId: roomRow.id,
      language: roomRow.language,
      targetLanguage: roomRow.language,
      nativeLanguage: profile?.nativeLanguage ?? "en",
      level: roomRow.level,
      sourceKind: roomRow.sourceKind,
      discussionPrompts: roomRow.discussionPrompts,
      currentRound: roomRow.currentRound,
    }),
  });
  token.addGrant({
    room: roomRow.livekitRoom,
    roomJoin: true,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
  });
  return {
    token: await token.toJwt(),
    url: LIVEKIT_URL,
    room: roomRow.livekitRoom,
    identity: viewer.id,
  };
}

export const liveRoomRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/", async (c) => {
    const viewerId = c.get("user").id;
    const profile = await getProfile(viewerId);
    await ensureSystemRooms(profile.targetLanguage);
    await reconcileLiveRooms();

    const rows = await db
      .select()
      .from(liveRoom)
      .where(and(eq(liveRoom.language, profile.targetLanguage), ne(liveRoom.status, "ended")))
      .orderBy(desc(liveRoom.updatedAt));

    const visible = rows.filter((r) => r.visibility === "public" || r.hostUserId === viewerId);
    const serialized = await Promise.all(visible.map((r) => serializeRoom(r, viewerId)));

    // Sort active rooms with real humans first, then curated system rooms, then lobby rooms.
    serialized.sort((a, b) => {
      if (b.members.length !== a.members.length) return b.members.length - a.members.length;
      if (a.sourceKind !== b.sourceKind) return a.sourceKind === "system" ? -1 : 1;
      return 0;
    });

    const featured = serialized.find((r) => r.sourceKind === "system" && r.status === "live") ?? serialized[0] ?? null;
    const rooms = serialized.filter((r) => r.id !== featured?.id);
    // Only count rooms that currently have real connected participants as "active".
    const activeRoomsCount = serialized.filter((r) => r.status === "live" && r.members.length > 0).length;

    return c.json({ featured, rooms, activeRoomsCount });
  })
  .post("/", async (c) => {
    const current = c.get("user");
    const profile = await getProfile(current.id);
    const body = await parseBody(c, createRoomSchema);
    const id = randomUUID();
    const code = generateRoomCode();
    const lang = body.language ?? profile.targetLanguage;
    const lvl = body.level ?? profile.level;

    const [created] = await db
      .insert(liveRoom)
      .values({
        id,
        hostUserId: current.id,
        title: body.title,
        description: body.description ?? `Live ${lvl} speaking room on ${body.title}.`,
        language: lang,
        level: lvl,
        maxMembers: body.maxMembers,
        visibility: body.visibility,
        code,
        sourceKind: "user",
        status: "lobby",
        livekitRoom: `zuno-room-${id}`,
        discussionPrompts: [
          `What's your take on ${body.title.toLowerCase()}?`,
          "Share a real example or experience from your week.",
          "Ask another member a follow-up question.",
        ],
        starterPhrases: starterPhrasesForLanguage(lang),
        currentRound: 1,
      })
      .returning();

    await db.insert(liveRoomMember).values({
      roomId: id,
      userId: current.id,
      role: "host",
      ready: true,
      lastSeenAt: new Date(),
    });

    return c.json({ room: await serializeRoom(created, current.id) }, 201);
  })
  .post("/join-code", async (c) => {
    const current = c.get("user");
    const { code } = await parseBody(c, z.object({ code: z.string().trim().min(3).max(32) }));
    const normalized = code.toUpperCase();
    await reconcileLiveRooms();
    const [found] = await db
      .select()
      .from(liveRoom)
      .where(and(eq(liveRoom.code, normalized), ne(liveRoom.status, "ended")))
      .limit(1);
    if (!found) throw new HTTPException(404, { message: "No live room found with that code" });

    const members = await db.select().from(liveRoomMember).where(eq(liveRoomMember.roomId, found.id));
    const alreadyIn = members.some((m) => m.userId === current.id);
    if (!alreadyIn && members.length >= found.maxMembers) {
      throw new HTTPException(409, { message: "This room is full" });
    }

    await db
      .insert(liveRoomMember)
      .values({ roomId: found.id, userId: current.id, role: "member", ready: true, lastSeenAt: new Date() })
      .onConflictDoUpdate({
        target: [liveRoomMember.roomId, liveRoomMember.userId],
        set: { lastSeenAt: new Date(), ready: true },
      });

    const livekit = found.status === "live" ? await issueLiveKitTicket(found, current) : null;
    return c.json({ room: await serializeRoom(found, current.id), livekit });
  })
  .get("/:id", async (c) => {
    const viewerId = c.get("user").id;
    await reconcileLiveRooms();
    const [found] = await db.select().from(liveRoom).where(eq(liveRoom.id, c.req.param("id"))).limit(1);
    if (!found) throw new HTTPException(404, { message: "Live room not found" });
    return c.json({ room: await serializeRoom(found, viewerId) });
  })
  .post("/:id/ping", async (c) => {
    const current = c.get("user");
    const id = c.req.param("id");
    await db
      .update(liveRoomMember)
      .set({ lastSeenAt: new Date() })
      .where(and(eq(liveRoomMember.roomId, id), eq(liveRoomMember.userId, current.id)));
    return c.body(null, 204);
  })
  .post("/:id/join", async (c) => {
    const current = c.get("user");
    await reconcileLiveRooms();
    const [found] = await db.select().from(liveRoom).where(eq(liveRoom.id, c.req.param("id"))).limit(1);
    if (!found || found.status === "ended") throw new HTTPException(404, { message: "Live room not found" });

    const members = await db.select().from(liveRoomMember).where(eq(liveRoomMember.roomId, found.id));
    const alreadyIn = members.some((m) => m.userId === current.id);
    if (!alreadyIn && members.length >= found.maxMembers) {
      throw new HTTPException(409, { message: "This room is full" });
    }
    await db
      .insert(liveRoomMember)
      .values({
        roomId: found.id,
        userId: current.id,
        role: found.hostUserId === current.id ? "host" : "member",
        ready: true,
        lastSeenAt: new Date(),
      })
      .onConflictDoUpdate({
        target: [liveRoomMember.roomId, liveRoomMember.userId],
        set: { lastSeenAt: new Date(), ready: true },
      });

    const livekit = found.status === "live" || found.sourceKind === "system" ? await issueLiveKitTicket(found, current) : null;
    return c.json({ room: await serializeRoom(found, current.id), livekit });
  })
  .post("/:id/start", async (c) => {
    const current = c.get("user");
    const [found] = await db.select().from(liveRoom).where(eq(liveRoom.id, c.req.param("id"))).limit(1);
    if (!found) throw new HTTPException(404, { message: "Live room not found" });
    if (found.hostUserId !== current.id && found.sourceKind !== "system") {
      throw new HTTPException(403, { message: "Only the host can start the room" });
    }
    const now = new Date();
    const [updated] = await db
      .update(liveRoom)
      .set({ status: "live", startedAt: found.startedAt ?? now })
      .where(eq(liveRoom.id, found.id))
      .returning();
    await db
      .update(liveRoomMember)
      .set({ lastSeenAt: now })
      .where(and(eq(liveRoomMember.roomId, found.id), eq(liveRoomMember.userId, current.id)));
    const livekit = await issueLiveKitTicket(updated, current);
    return c.json({ room: await serializeRoom(updated, current.id), livekit });
  })
  .patch("/:id", async (c) => {
    const current = c.get("user");
    const body = await parseBody(c, patchRoomSchema);
    const [found] = await db.select().from(liveRoom).where(eq(liveRoom.id, c.req.param("id"))).limit(1);
    if (!found) throw new HTTPException(404, { message: "Live room not found" });
    if (found.hostUserId !== current.id && found.sourceKind !== "system") {
      throw new HTTPException(403, { message: "Only the host can update this room" });
    }
    const maxRounds = Math.max(found.discussionPrompts.length, 1);
    const nextRound = body.nextQuestion ? (found.currentRound % maxRounds) + 1 : found.currentRound;
    const [updated] = await db
      .update(liveRoom)
      .set({
        ...(body.visibility !== undefined && { visibility: body.visibility }),
        ...(body.nextQuestion && { currentRound: nextRound }),
      })
      .where(eq(liveRoom.id, found.id))
      .returning();
    return c.json({ room: await serializeRoom(updated, current.id) });
  })
  .post("/:id/leave", async (c) => {
    const current = c.get("user");
    const id = c.req.param("id");
    const raw = await c.req.json().catch(() => ({}));
    const parsed = endOrLeaveSchema.safeParse(raw);
    const durationSec = parsed.success ? parsed.data.durationSec : 0;

    const [found] = await db.select().from(liveRoom).where(eq(liveRoom.id, id)).limit(1);
    await db.delete(liveRoomMember).where(and(eq(liveRoomMember.roomId, id), eq(liveRoomMember.userId, current.id)));
    if (found && durationSec > 0) {
      await recordPracticeActivity(current.id, found.language, { spokenSec: durationSec }).catch(() => {});
    }
    if (found && found.sourceKind === "user") {
      const remaining = await db
        .select({ userId: liveRoomMember.userId })
        .from(liveRoomMember)
        .where(eq(liveRoomMember.roomId, id))
        .limit(1);
      if (remaining.length === 0) {
        await db.update(liveRoom).set({ status: "ended", endedAt: new Date() }).where(eq(liveRoom.id, id));
      }
    }
    return c.body(null, 204);
  })
  .post("/:id/end", async (c) => {
    const current = c.get("user");
    const raw = await c.req.json().catch(() => ({}));
    const parsed = endOrLeaveSchema.safeParse(raw);
    const durationSec = parsed.success ? parsed.data.durationSec : 0;

    const [found] = await db.select().from(liveRoom).where(eq(liveRoom.id, c.req.param("id"))).limit(1);
    if (!found) return c.body(null, 204);

    await db.delete(liveRoomMember).where(and(eq(liveRoomMember.roomId, found.id), eq(liveRoomMember.userId, current.id)));
    if (durationSec > 0) {
      await recordPracticeActivity(current.id, found.language, { spokenSec: durationSec }).catch(() => {});
    }
    if (found.hostUserId === current.id && found.sourceKind === "user") {
      await db.delete(liveRoomMember).where(eq(liveRoomMember.roomId, found.id));
      await db.update(liveRoom).set({ status: "ended", endedAt: new Date() }).where(eq(liveRoom.id, found.id));
    }
    return c.body(null, 204);
  });
