import { randomUUID } from "node:crypto";
import { and, asc, desc, eq, ne } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { AccessToken } from "livekit-server-sdk";
import { z } from "zod";
import { liveRoom, liveRoomMember } from "../db/app-schema.js";
import { user } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { parseBody, requireAuth, type AuthEnv } from "../lib/http.js";
import { LANGUAGE_CODES, LEVELS } from "../lib/languages.js";
import { ensureSeededLearners } from "../lib/peers.js";
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

function generateRoomCode(): string {
  const num = Math.floor(100 + Math.random() * 900);
  return `ZUNO-${num}`;
}

async function ensureSystemRooms(language: string, level: string) {
  await ensureSeededLearners();
  const existing = await db
    .select()
    .from(liveRoom)
    .where(and(eq(liveRoom.language, language), eq(liveRoom.sourceKind, "system"), ne(liveRoom.status, "ended")))
    .limit(1);
  if (existing.length > 0) return;

  const featuredId = `sys-featured-${language}`;
  const secondaryId = `sys-remote-${language}`;
  const foodId = `pub-food-${language}`;

  await db
    .insert(liveRoom)
    .values([
      {
        id: featuredId,
        hostUserId: "seed:lucia",
        title: "Café culture & morning routines",
        description: "System-guided topic room. Zuno prompts the group with live questions and helpful phrases.",
        language,
        level,
        maxMembers: 4,
        visibility: "public",
        sourceKind: "system",
        status: "live",
        livekitRoom: `zuno-room-${featuredId}`,
        discussionPrompts: [
          "Your go-to morning coffee order and routine",
          "Favorite neighborhood cafés in your city",
          "Comparing café culture across local cities",
        ],
        currentRound: 1,
      },
      {
        id: secondaryId,
        hostUserId: "seed:carlos",
        title: "Remote work vs. office life",
        description: "Compare async collaboration, focus routines, and commute trade-offs.",
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
        currentRound: 1,
      },
      {
        id: foodId,
        hostUserId: "seed:lucia",
        title: "Favorite street food & markets",
        description: "Share local street food favorites and ordering phrases.",
        language,
        level: "B1",
        maxMembers: 4,
        visibility: "public",
        sourceKind: "user",
        status: "lobby",
        livekitRoom: `zuno-room-${foodId}`,
        discussionPrompts: [
          "Your favorite street food dish and why",
          "How you order spicy or custom toppings naturally",
          "Best weekend food market you have visited",
        ],
        currentRound: 1,
      },
    ])
    .onConflictDoNothing();

  await db
    .insert(liveRoomMember)
    .values([
      { roomId: featuredId, userId: "seed:lucia", role: "host", ready: true },
      { roomId: featuredId, userId: "seed:mateo", role: "member", ready: true },
      { roomId: featuredId, userId: "seed:yuki", role: "member", ready: true },
      { roomId: secondaryId, userId: "seed:carlos", role: "host", ready: true },
      { roomId: secondaryId, userId: "seed:priya", role: "member", ready: true },
      { roomId: foodId, userId: "seed:lucia", role: "host", ready: true },
      { roomId: foodId, userId: "seed:mateo", role: "member", ready: true },
    ])
    .onConflictDoNothing();
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
    .where(eq(liveRoomMember.roomId, roomRow.id))
    .orderBy(asc(liveRoomMember.joinedAt));

  const host = members.find((m) => m.role === "host") ?? members[0];
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
    hostUserId: roomRow.hostUserId,
    hostName: host?.name ?? "Zuno",
    isHost: roomRow.hostUserId === viewerId,
    discussionPrompts: roomRow.discussionPrompts,
    currentRound: roomRow.currentRound,
    members: members.map((m) => ({
      id: m.userId,
      name: m.name,
      initial: m.name.trim().slice(0, 1).toUpperCase() || "Z",
      imageUrl: m.image,
      isHost: m.role === "host",
      ready: m.ready,
      isYou: m.userId === viewerId,
    })),
  };
}

async function issueLiveKitTicket(roomRow: typeof liveRoom.$inferSelect, viewer: { id: string; name: string; email: string }) {
  const { LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET } = process.env;
  if (!LIVEKIT_URL || !LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) return null;
  const token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
    identity: viewer.id,
    name: viewer.name || viewer.email,
    ttl: "4h",
    metadata: JSON.stringify({
      mode: "live_room",
      roomId: roomRow.id,
      language: roomRow.language,
      level: roomRow.level,
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
    await ensureSystemRooms(profile.targetLanguage, profile.level);

    const rows = await db
      .select()
      .from(liveRoom)
      .where(and(eq(liveRoom.language, profile.targetLanguage), ne(liveRoom.status, "ended")))
      .orderBy(desc(liveRoom.updatedAt));

    const visible = rows.filter((r) => r.visibility === "public" || r.hostUserId === viewerId);
    const serialized = await Promise.all(visible.map((r) => serializeRoom(r, viewerId)));
    const featured = serialized.find((r) => r.sourceKind === "system" && r.status === "live") ?? serialized[0] ?? null;
    const rooms = serialized.filter((r) => r.id !== featured?.id);
    return c.json({ featured, rooms });
  })
  .post("/", async (c) => {
    const current = c.get("user");
    const profile = await getProfile(current.id);
    const body = await parseBody(c, createRoomSchema);
    const id = randomUUID();
    const code = body.visibility === "private" ? generateRoomCode() : generateRoomCode();
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
        currentRound: 1,
      })
      .returning();

    await db.insert(liveRoomMember).values({
      roomId: id,
      userId: current.id,
      role: "host",
      ready: true,
    });

    return c.json({ room: await serializeRoom(created, current.id) }, 201);
  })
  .post("/join-code", async (c) => {
    const current = c.get("user");
    const { code } = await parseBody(c, z.object({ code: z.string().trim().min(3).max(32) }));
    const normalized = code.toUpperCase();
    const [found] = await db
      .select()
      .from(liveRoom)
      .where(and(eq(liveRoom.code, normalized), ne(liveRoom.status, "ended")))
      .limit(1);
    if (!found) throw new HTTPException(404, { message: "No live room found with that code" });

    await db
      .insert(liveRoomMember)
      .values({ roomId: found.id, userId: current.id, role: "member", ready: true })
      .onConflictDoNothing();

    const livekit = found.status === "live" ? await issueLiveKitTicket(found, current) : null;
    return c.json({ room: await serializeRoom(found, current.id), livekit });
  })
  .get("/:id", async (c) => {
    const viewerId = c.get("user").id;
    const [found] = await db.select().from(liveRoom).where(eq(liveRoom.id, c.req.param("id"))).limit(1);
    if (!found) throw new HTTPException(404, { message: "Live room not found" });
    return c.json({ room: await serializeRoom(found, viewerId) });
  })
  .post("/:id/join", async (c) => {
    const current = c.get("user");
    const [found] = await db.select().from(liveRoom).where(eq(liveRoom.id, c.req.param("id"))).limit(1);
    if (!found || found.status === "ended") throw new HTTPException(404, { message: "Live room not found" });

    const members = await db.select().from(liveRoomMember).where(eq(liveRoomMember.roomId, found.id));
    const alreadyIn = members.some((m) => m.userId === current.id);
    if (!alreadyIn && members.length >= found.maxMembers) {
      throw new HTTPException(409, { message: "This room is full" });
    }
    if (!alreadyIn) {
      await db.insert(liveRoomMember).values({
        roomId: found.id,
        userId: current.id,
        role: found.hostUserId === current.id ? "host" : "member",
        ready: true,
      });
    }
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
    const [updated] = await db
      .update(liveRoom)
      .set({ status: "live" })
      .where(eq(liveRoom.id, found.id))
      .returning();
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
    const nextRound = body.nextQuestion ? ((found.currentRound % maxRounds) + 1) : found.currentRound;
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
    await db.delete(liveRoomMember).where(and(eq(liveRoomMember.roomId, id), eq(liveRoomMember.userId, current.id)));
    return c.body(null, 204);
  })
  .post("/:id/end", async (c) => {
    const current = c.get("user");
    const [found] = await db.select().from(liveRoom).where(eq(liveRoom.id, c.req.param("id"))).limit(1);
    if (!found) return c.body(null, 204);
    if (found.hostUserId === current.id && found.sourceKind === "user") {
      await db.update(liveRoom).set({ status: "ended" }).where(eq(liveRoom.id, found.id));
    } else {
      await db.delete(liveRoomMember).where(and(eq(liveRoomMember.roomId, found.id), eq(liveRoomMember.userId, current.id)));
    }
    return c.body(null, 204);
  });
