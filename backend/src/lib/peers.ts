import { randomUUID } from "node:crypto";
import { and, asc, desc, eq, gte, inArray, isNotNull, ne, or, sql } from "drizzle-orm";
import { HTTPException } from "hono/http-exception";
import { AccessToken } from "livekit-server-sdk";
import { z } from "zod";
import {
  blockedLearner,
  conversation,
  learnerPartner,
  message,
  peerCallRoom,
  peerConversation,
  peerMessage,
  peerReport,
  userProfile,
} from "../db/app-schema.js";
import { user } from "../db/auth-schema.js";
import { db } from "../db/index.js";
import { getBatchLearnerStats, recordPracticeActivity } from "./activity.js";
import { resolveUserImageUrl } from "./avatars.js";
import { quote, t } from "./copy.js";
import { generateJson } from "./gemini.js";
import { languageName, type LanguageCode } from "./languages.js";
import { displayLanguage } from "./notifications.js";
import { formatActiveLabel, isUserOnline, ONLINE_WINDOW_MS } from "./presence.js";
import { getProfile, type LearnerProfile } from "./profile.js";
import { sendToUser } from "./push.js";
import { resolveTutor } from "./voices.js";

export function shortName(fullName: string) {
  const parts = fullName.trim().split(/\s+/);
  if (parts.length <= 1) return parts[0] || "Learner";
  return `${parts[0]} ${parts[1][0].toUpperCase()}.`;
}

async function getBlockedUserIds(userId: string): Promise<Set<string>> {
  const rows = await db
    .select({
      userId: blockedLearner.userId,
      blockedUserId: blockedLearner.blockedUserId,
    })
    .from(blockedLearner)
    .where(or(eq(blockedLearner.userId, userId), eq(blockedLearner.blockedUserId, userId)));
  const blocked = new Set<string>();
  for (const r of rows) {
    if (r.userId === userId) blocked.add(r.blockedUserId);
    if (r.blockedUserId === userId) blocked.add(r.userId);
  }
  return blocked;
}

async function serializeLearner(
  row: {
    id: string;
    name: string;
    image: string | null;
    level: string;
    nativeLanguage: string;
    interests: string[];
    openToSpeak: boolean;
    lastSeenAt: Date;
  },
  targetLanguage: string,
  isPartner: boolean,
  stats?: { streakDays: number; sessionsCount: number },
) {
  const online = isUserOnline(row.lastSeenAt);
  const activeLabel = formatActiveLabel(row.lastSeenAt, row.openToSpeak);
  const image = await resolveUserImageUrl(row.image);
  return {
    id: row.id,
    name: row.name,
    shortName: row.name.split(" ")[0] || row.name,
    initial: (row.name.trim()[0] || "L").toUpperCase(),
    image,
    language: targetLanguage,
    languageName: languageName(targetLanguage as LanguageCode),
    level: row.level,
    nativeLanguage: languageName(row.nativeLanguage as any) || "English",
    streakDays: stats?.streakDays ?? 0,
    sessionsCount: stats?.sessionsCount ?? 0,
    interests: row.interests,
    openToSpeak: row.openToSpeak,
    online,
    isPartner,
    activeLabel,
    isSeeded: false,
  };
}

export async function loadLearner(partnerId: string, viewerLanguage: string, isPartner: boolean) {
  const [row] = await db
    .select({
      id: user.id,
      name: user.name,
      image: user.image,
      level: userProfile.level,
      nativeLanguage: userProfile.nativeLanguage,
      interests: userProfile.interests,
      openToSpeak: userProfile.openToSpeak,
      lastSeenAt: userProfile.lastSeenAt,
    })
    .from(user)
    .leftJoin(userProfile, eq(userProfile.userId, user.id))
    .where(eq(user.id, partnerId));

  if (!row) return null;
  const statsMap = await getBatchLearnerStats([row.id]);
  return serializeLearner(
    {
      id: row.id,
      name: row.name,
      image: row.image,
      level: row.level ?? "B1",
      nativeLanguage: row.nativeLanguage ?? "en",
      interests: row.interests ?? [],
      openToSpeak: row.openToSpeak ?? false,
      lastSeenAt: row.lastSeenAt ?? new Date(0),
    },
    viewerLanguage,
    isPartner,
    statsMap.get(row.id),
  );
}

async function assertNotBlocked(userId: string, otherUserId: string) {
  const blocked = await getBlockedUserIds(userId);
  if (blocked.has(otherUserId)) {
    throw new HTTPException(403, { message: "This learner is unavailable." });
  }
}

async function assertCanMessage(senderId: string, recipientId: string, hasExistingThread = false) {
  await assertNotBlocked(senderId, recipientId);
  const [recipientProfile] = await db
    .select({
      whoCanMessage: userProfile.whoCanMessage,
      openToSpeak: userProfile.openToSpeak,
      lastSeenAt: userProfile.lastSeenAt,
    })
    .from(userProfile)
    .where(eq(userProfile.userId, recipientId));

  if (!recipientProfile) throw new HTTPException(404, { message: "Learner not found" });
  if (recipientProfile.whoCanMessage === "nobody") {
    throw new HTTPException(403, { message: "This learner isn't accepting new messages right now." });
  }
  if (recipientProfile.whoCanMessage === "partners" && !hasExistingThread) {
    const isCurrentlyOpen = recipientProfile.openToSpeak && isUserOnline(recipientProfile.lastSeenAt);
    if (!isCurrentlyOpen) {
      const [partnerLink] = await db
        .select({ userId: learnerPartner.userId })
        .from(learnerPartner)
        .where(
          or(
            and(eq(learnerPartner.userId, recipientId), eq(learnerPartner.partnerUserId, senderId)),
            and(eq(learnerPartner.userId, senderId), eq(learnerPartner.partnerUserId, recipientId)),
          ),
        )
        .limit(1);
      if (!partnerLink) {
        throw new HTTPException(403, { message: "Only partners can message this learner while they're offline." });
      }
    }
  }
}

export async function getChatsOverview(userId: string) {
  const profile = await getProfile(userId);
  const now = new Date();
  const onlineCutoff = new Date(now.getTime() - ONLINE_WINDOW_MS);

  await db.update(userProfile).set({ lastSeenAt: now }).where(eq(userProfile.userId, userId));

  const [[viewerRow], partnerRows, blockedIds] = await Promise.all([
    db.select({ openToSpeak: userProfile.openToSpeak }).from(userProfile).where(eq(userProfile.userId, userId)),
    db
      .select({ partnerUserId: learnerPartner.partnerUserId })
      .from(learnerPartner)
      .where(eq(learnerPartner.userId, userId)),
    getBlockedUserIds(userId),
  ]);

  const partnerIds = new Set(partnerRows.map((r) => r.partnerUserId).filter((id) => !blockedIds.has(id)));
  const partnerIdList = [...partnerIds];

  // Real connected learners open to speak right now (within ONLINE_WINDOW_MS) + viewer's saved partners.
  const learnerRows = await db
    .select({
      id: user.id,
      name: user.name,
      image: user.image,
      level: userProfile.level,
      nativeLanguage: userProfile.nativeLanguage,
      interests: userProfile.interests,
      openToSpeak: userProfile.openToSpeak,
      lastSeenAt: userProfile.lastSeenAt,
      targetLanguage: userProfile.targetLanguage,
    })
    .from(user)
    .innerJoin(userProfile, eq(userProfile.userId, user.id))
    .where(
      and(
        ne(user.id, userId),
        sql`${user.id} not like 'seed:%'`,
        or(
          and(
            eq(userProfile.targetLanguage, profile.targetLanguage),
            eq(userProfile.openToSpeak, true),
            isNotNull(userProfile.onboardingCompletedAt),
            gte(userProfile.lastSeenAt, onlineCutoff),
          ),
          partnerIdList.length > 0 ? inArray(user.id, partnerIdList) : sql`false`,
        ),
      ),
    )
    .orderBy(desc(userProfile.lastSeenAt))
    .limit(60);

  const visibleRows = learnerRows.filter((r) => !blockedIds.has(r.id));
  const statsMap = await getBatchLearnerStats(visibleRows.map((r) => r.id), now);

  const serializedLearners = await Promise.all(
    visibleRows.map((row) =>
      serializeLearner(
        {
          id: row.id,
          name: row.name,
          image: row.image,
          level: row.level,
          nativeLanguage: row.nativeLanguage,
          interests: row.interests,
          openToSpeak: row.openToSpeak,
          lastSeenAt: row.lastSeenAt,
        },
        profile.targetLanguage,
        partnerIds.has(row.id),
        statsMap.get(row.id),
      ),
    ),
  );

  const learnersMap = new Map(serializedLearners.map((l) => [l.id, l]));

  const openSpeakers = serializedLearners.filter((l) => l.openToSpeak && l.online);
  const partners = serializedLearners.filter((l) => l.isPartner);

  // AI Tutor conversations for current targetLanguage
  const [aiConvs, peerConvs] = await Promise.all([
    db.query.conversation.findMany({
      where: and(eq(conversation.userId, userId), eq(conversation.language, profile.targetLanguage)),
      orderBy: [desc(conversation.updatedAt)],
      limit: 30,
      with: {
        messages: {
          orderBy: [desc(message.createdAt)],
          limit: 1,
        },
      },
    }),
    db
      .select()
      .from(peerConversation)
      .where(
        and(
          eq(peerConversation.language, profile.targetLanguage),
          or(
            and(eq(peerConversation.userAId, userId), eq(peerConversation.deletedByUserA, false)),
            and(eq(peerConversation.userBId, userId), eq(peerConversation.deletedByUserB, false)),
          ),
        ),
      )
      .orderBy(desc(peerConversation.lastMessageAt))
      .limit(30),
  ]);

  const activeTutor = resolveTutor(profile.tutorId, profile.tutorVoice);
  const aiItems = aiConvs
    .filter((row) => row.messages.length > 0 || row.kind === "freetalk")
    .map((row) => {
      const rowTutor = row.tutorId ? resolveTutor(row.tutorId) : activeTutor;
      const lastMsg = row.messages[0];
      const isVoice = Boolean(lastMsg?.viaVoice);
      const hasTopicTitle = Boolean(row.title && row.title !== "Free Talk");
      const snippet = lastMsg
        ? lastMsg.kind === "call_summary"
          ? lastMsg.text
          : hasTopicTitle
            ? `${row.title} · ${lastMsg.text}`
            : lastMsg.text
        : hasTopicTitle
          ? `${row.title} · Message or call anytime`
          : "Message or call anytime";
      return {
        id: row.id,
        kind: "ai" as const,
        conversationId: row.id,
        peerConversationId: null as string | null,
        partnerId: null as string | null,
        tutorId: rowTutor.id,
        tutorName: rowTutor.name,
        conversationTitle: row.title,
        title: rowTutor.name,
        badge: isVoice ? "Voice" : row.kind === "roleplay" ? "Roleplay" : row.kind === "lesson" ? "Lesson" : "Text",
        isVoice,
        openToSpeak: false,
        snippet,
        unread: row.unreadCount > 0,
        pinned: row.pinnedAt !== null,
        updatedAt: (lastMsg?.createdAt ?? row.updatedAt).toISOString(),
        avatarInitial: rowTutor.name.slice(0, 1).toUpperCase() || "Z",
        avatarUrl: null as string | null,
      };
    });

  const visiblePeerConvs = peerConvs.filter((row) => {
    const partnerId = row.userAId === userId ? row.userBId : row.userAId;
    return !blockedIds.has(partnerId);
  });

  const peerItems = await Promise.all(
    visiblePeerConvs.map(async (row) => {
      const isA = row.userAId === userId;
      const partnerId = isA ? row.userBId : row.userAId;
      const partner = learnersMap.get(partnerId) ?? (await loadLearner(partnerId, profile.targetLanguage, partnerIds.has(partnerId)));
      const muted = isA ? row.mutedByUserA : row.mutedByUserB;
      const unread = !muted && (isA ? row.unreadCountA : row.unreadCountB) > 0;
      const partnerOnlineAndOpen = Boolean(partner?.online && partner?.openToSpeak);
      return {
        id: `peer:${row.id}`,
        kind: "peer" as const,
        conversationId: null as string | null,
        peerConversationId: row.id,
        partnerId,
        title: partner?.name ?? "Learner",
        badge: partnerOnlineAndOpen ? "Open to speak" : null,
        isVoice: false,
        openToSpeak: partnerOnlineAndOpen,
        snippet: row.lastMessageText || "Tap to start chatting",
        unread,
        pinned: false,
        updatedAt: row.lastMessageAt.toISOString(),
        avatarInitial: partner?.initial ?? "L",
        avatarUrl: partner?.image ?? null,
      };
    }),
  );

  const unified = [...aiItems, ...peerItems].sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
  );

  return {
    openToSpeak: viewerRow?.openToSpeak ?? true,
    language: profile.targetLanguage,
    languageName: languageName(profile.targetLanguage),
    level: profile.level,
    unreadCount: unified.filter((c) => c.unread).length,
    onlineSpeakersCount: openSpeakers.length,
    openSpeakers,
    partners,
    conversations: unified,
  };
}

export async function setOpenToSpeak(userId: string, openToSpeak: boolean) {
  await getProfile(userId);
  await db
    .update(userProfile)
    .set({ openToSpeak, lastSeenAt: new Date() })
    .where(eq(userProfile.userId, userId));
  return openToSpeak;
}

export async function setPartner(userId: string, partnerId: string, add: boolean) {
  if (userId === partnerId) return;
  if (add) {
    await assertNotBlocked(userId, partnerId);
    const [exists] = await db.select({ id: user.id }).from(user).where(eq(user.id, partnerId)).limit(1);
    if (!exists) throw new HTTPException(404, { message: "Learner not found" });
    await db.insert(learnerPartner).values({ userId, partnerUserId: partnerId }).onConflictDoNothing();
  } else {
    await db
      .delete(learnerPartner)
      .where(and(eq(learnerPartner.userId, userId), eq(learnerPartner.partnerUserId, partnerId)));
  }
}

export async function findOrCreatePeerConversation(userId: string, partnerId: string) {
  const profile = await getProfile(userId);
  await assertNotBlocked(userId, partnerId);
  const [userAId, userBId] = userId < partnerId ? [userId, partnerId] : [partnerId, userId];

  const existing = await db.query.peerConversation.findFirst({
    where: and(
      eq(peerConversation.userAId, userAId),
      eq(peerConversation.userBId, userBId),
      eq(peerConversation.language, profile.targetLanguage),
    ),
  });

  if (existing) {
    if ((existing.userAId === userId && existing.deletedByUserA) || (existing.userBId === userId && existing.deletedByUserB)) {
      await db
        .update(peerConversation)
        .set(existing.userAId === userId ? { deletedByUserA: false } : { deletedByUserB: false })
        .where(eq(peerConversation.id, existing.id));
    }
    return existing;
  }

  await assertCanMessage(userId, partnerId, false);

  const [created] = await db
    .insert(peerConversation)
    .values({
      id: randomUUID(),
      userAId,
      userBId,
      language: profile.targetLanguage,
      lastMessageText: null,
      lastMessageAt: new Date(),
    })
    .returning();

  return created;
}

export async function markPeerConversationRead(userId: string, peerConversationId: string): Promise<boolean> {
  const conv = await db.query.peerConversation.findFirst({
    where: and(
      eq(peerConversation.id, peerConversationId),
      or(eq(peerConversation.userAId, userId), eq(peerConversation.userBId, userId)),
    ),
  });
  if (!conv) return false;
  const isA = conv.userAId === userId;
  if ((isA && conv.unreadCountA > 0) || (!isA && conv.unreadCountB > 0)) {
    await db
      .update(peerConversation)
      .set(isA ? { unreadCountA: 0 } : { unreadCountB: 0 })
      .where(eq(peerConversation.id, conv.id));
  }
  return true;
}

export async function getPeerConversationDetail(userId: string, peerConversationId: string) {
  const conv = await db.query.peerConversation.findFirst({
    where: and(
      eq(peerConversation.id, peerConversationId),
      or(eq(peerConversation.userAId, userId), eq(peerConversation.userBId, userId)),
    ),
  });
  if (!conv) return null;

  const isA = conv.userAId === userId;
  const partnerId = isA ? conv.userBId : conv.userAId;
  const muted = isA ? conv.mutedByUserA : conv.mutedByUserB;

  if ((isA && conv.unreadCountA > 0) || (!isA && conv.unreadCountB > 0)) {
    await db
      .update(peerConversation)
      .set(isA ? { unreadCountA: 0 } : { unreadCountB: 0 })
      .where(eq(peerConversation.id, conv.id));
  }

  const [partnerRow, partnerLink, msgs, activeCall] = await Promise.all([
    loadLearner(partnerId, conv.language, false),
    db.query.learnerPartner.findFirst({
      where: and(eq(learnerPartner.userId, userId), eq(learnerPartner.partnerUserId, partnerId)),
    }),
    db
      .select()
      .from(peerMessage)
      .where(eq(peerMessage.peerConversationId, conv.id))
      .orderBy(asc(peerMessage.createdAt)),
    db.query.peerCallRoom.findFirst({
      where: and(
        eq(peerCallRoom.peerConversationId, conv.id),
        or(eq(peerCallRoom.status, "waiting"), eq(peerCallRoom.status, "active")),
      ),
      orderBy: [desc(peerCallRoom.createdAt)],
    }),
  ]);

  const partner = partnerRow ? { ...partnerRow, isPartner: Boolean(partnerLink) } : null;

  return {
    conversation: {
      id: conv.id,
      language: conv.language,
      muted,
      partnerId,
      updatedAt: conv.updatedAt.toISOString(),
    },
    partner,
    messages: msgs.map((m) => serializePeerMessage(m, userId)),
    activeCall: activeCall ? serializePeerCall(activeCall) : null,
  };
}

function serializePeerMessage(m: typeof peerMessage.$inferSelect, viewerId: string) {
  return {
    id: m.id,
    peerConversationId: m.peerConversationId,
    senderId: m.senderId,
    isMine: m.senderId === viewerId,
    kind: m.kind,
    text: m.text,
    callRoomId: m.callRoomId ?? null,
    metadata: m.metadata ?? null,
    createdAt: m.createdAt.toISOString(),
  };
}

function serializePeerCall(c: typeof peerCallRoom.$inferSelect) {
  return {
    id: c.id,
    peerConversationId: c.peerConversationId,
    hostUserId: c.hostUserId,
    guestUserId: c.guestUserId,
    livekitRoom: c.livekitRoom,
    status: c.status,
    cameraEnabled: c.cameraEnabled,
    startedAt: c.startedAt?.toISOString() ?? null,
    endedAt: c.endedAt?.toISOString() ?? null,
    durationSec: c.durationSec,
    createdAt: c.createdAt.toISOString(),
  };
}

function isRecipientMuted(conv: typeof peerConversation.$inferSelect, recipientId: string): boolean {
  return conv.userAId === recipientId ? conv.mutedByUserA : conv.mutedByUserB;
}

export async function sendPeerMessage(userId: string, peerConversationId: string, text: string) {
  const conv = await db.query.peerConversation.findFirst({
    where: and(
      eq(peerConversation.id, peerConversationId),
      or(eq(peerConversation.userAId, userId), eq(peerConversation.userBId, userId)),
    ),
  });
  if (!conv) return null;

  const isA = conv.userAId === userId;
  const partnerId = isA ? conv.userBId : conv.userAId;
  await assertCanMessage(userId, partnerId, true);

  const [saved] = await db
    .insert(peerMessage)
    .values({
      id: randomUUID(),
      peerConversationId: conv.id,
      senderId: userId,
      kind: "text",
      text,
    })
    .returning();

  await db
    .update(peerConversation)
    .set({
      lastMessageText: text,
      lastMessageAt: new Date(),
      deletedByUserA: false,
      deletedByUserB: false,
      ...(isA
        ? { unreadCountB: sql`${peerConversation.unreadCountB} + 1` }
        : { unreadCountA: sql`${peerConversation.unreadCountA} + 1` }),
    })
    .where(eq(peerConversation.id, conv.id));

  await recordPracticeActivity(userId, conv.language, { messagesCount: 1 }).catch(() => {});

  if (!isRecipientMuted(conv, partnerId)) {
    const [[sender], recipientProfile] = await Promise.all([
      db.select({ name: user.name }).from(user).where(eq(user.id, userId)),
      getProfile(partnerId),
    ]);
    sendToUser(partnerId, {
      type: "peer_message",
      title: sender?.name?.trim() || t(recipientProfile.nativeLanguage, "peerMessageNewTitle"),
      body: quote(text, 120),
      data: { peerConversationId: conv.id, partnerId: userId, messageId: saved.id },
    }).catch(() => {});
  }

  return serializePeerMessage(saved, userId);
}

export async function updatePeerConversation(userId: string, peerConversationId: string, muted: boolean) {
  const conv = await db.query.peerConversation.findFirst({
    where: and(
      eq(peerConversation.id, peerConversationId),
      or(eq(peerConversation.userAId, userId), eq(peerConversation.userBId, userId)),
    ),
  });
  if (!conv) return null;
  const isA = conv.userAId === userId;
  await db
    .update(peerConversation)
    .set(isA ? { mutedByUserA: muted } : { mutedByUserB: muted })
    .where(eq(peerConversation.id, conv.id));
  return { id: conv.id, muted };
}

export async function deletePeerConversation(userId: string, peerConversationId: string) {
  const conv = await db.query.peerConversation.findFirst({
    where: and(
      eq(peerConversation.id, peerConversationId),
      or(eq(peerConversation.userAId, userId), eq(peerConversation.userBId, userId)),
    ),
  });
  if (!conv) return false;
  const isA = conv.userAId === userId;
  await db
    .update(peerConversation)
    .set(isA ? { deletedByUserA: true, unreadCountA: 0 } : { deletedByUserB: true, unreadCountB: 0 })
    .where(eq(peerConversation.id, conv.id));
  return true;
}

export async function reportPeerUser(userId: string, peerConversationId: string, reason: string, details?: string) {
  const conv = await db.query.peerConversation.findFirst({
    where: and(
      eq(peerConversation.id, peerConversationId),
      or(eq(peerConversation.userAId, userId), eq(peerConversation.userBId, userId)),
    ),
  });
  if (!conv) return null;
  const partnerId = conv.userAId === userId ? conv.userBId : conv.userAId;
  const id = randomUUID();
  await db.insert(peerReport).values({
    id,
    reporterId: userId,
    reportedUserId: partnerId,
    peerConversationId: conv.id,
    reason,
    details: details?.trim() || null,
  });
  return { id };
}

// ── LiveKit Peer Call Rooms ──────────────────────────────────────────────────

async function mintPeerCallToken(params: {
  userId: string;
  userName: string;
  livekitRoom: string;
  partner: NonNullable<Awaited<ReturnType<typeof loadLearner>>>;
  callerProfile: LearnerProfile;
}) {
  const { LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET } = process.env;
  if (!LIVEKIT_URL || !LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) {
    throw new Error("LiveKit is not configured");
  }
  const metadata = {
    mode: "peer_human_only",
    language: params.callerProfile.targetLanguage,
    targetLanguage: params.callerProfile.targetLanguage,
    nativeLanguage: params.callerProfile.nativeLanguage,
    partnerId: params.partner.id,
  };

  const token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
    identity: params.userId,
    name: params.userName,
    ttl: "6h",
    metadata: JSON.stringify(metadata),
  });
  token.addGrant({
    room: params.livekitRoom,
    roomJoin: true,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
  });

  return {
    token: await token.toJwt(),
    url: LIVEKIT_URL,
    room: params.livekitRoom,
    identity: params.userId,
  };
}

export async function startPeerCall(userId: string, peerConversationId: string, cameraEnabled: boolean) {
  const conv = await db.query.peerConversation.findFirst({
    where: and(
      eq(peerConversation.id, peerConversationId),
      or(eq(peerConversation.userAId, userId), eq(peerConversation.userBId, userId)),
    ),
  });
  if (!conv) return null;

  const partnerId = conv.userAId === userId ? conv.userBId : conv.userAId;
  await assertNotBlocked(userId, partnerId);

  const [[recipientPrefs], callerProfile, callerRow, partner] = await Promise.all([
    db.select({ allowCallInvites: userProfile.allowCallInvites }).from(userProfile).where(eq(userProfile.userId, partnerId)),
    getProfile(userId),
    db.select({ name: user.name, email: user.email }).from(user).where(eq(user.id, userId)).then((r) => r[0]),
    loadLearner(partnerId, conv.language, false),
  ]);
  if (!partner) return null;
  if (recipientPrefs && !recipientPrefs.allowCallInvites) {
    throw new HTTPException(403, { message: "This learner has call invites turned off." });
  }

  // Mark older waiting calls as missed
  await db
    .update(peerCallRoom)
    .set({ status: "missed", endedAt: new Date() })
    .where(and(eq(peerCallRoom.peerConversationId, conv.id), eq(peerCallRoom.status, "waiting")));

  await db
    .update(peerMessage)
    .set({ kind: "missed_invite", text: "Missed invite to talk" })
    .where(and(eq(peerMessage.peerConversationId, conv.id), eq(peerMessage.kind, "call_invite")));

  const callId = randomUUID();
  const livekitRoom = `peer-${callId.slice(0, 12)}`;
  const [call] = await db
    .insert(peerCallRoom)
    .values({
      id: callId,
      peerConversationId: conv.id,
      hostUserId: userId,
      guestUserId: partnerId,
      livekitRoom,
      status: "waiting",
      cameraEnabled,
    })
    .returning();

  const callerName = callerRow?.name || callerRow?.email || "Learner";
  const [inviteMsg] = await db
    .insert(peerMessage)
    .values({
      id: randomUUID(),
      peerConversationId: conv.id,
      senderId: userId,
      kind: "call_invite",
      text: `${shortName(callerName)} invited you to talk`,
      callRoomId: call.id,
      metadata: { cameraEnabled, status: "waiting" },
    })
    .returning();

  const isA = conv.userAId === userId;
  await db
    .update(peerConversation)
    .set({
      lastMessageText: "Voice call invite",
      lastMessageAt: new Date(),
      deletedByUserA: false,
      deletedByUserB: false,
      ...(isA
        ? { unreadCountB: sql`${peerConversation.unreadCountB} + 1` }
        : { unreadCountA: sql`${peerConversation.unreadCountA} + 1` }),
    })
    .where(eq(peerConversation.id, conv.id));

  if (!isRecipientMuted(conv, partnerId)) {
    const recipientProfile = await getProfile(partnerId);
    const lang = recipientProfile.nativeLanguage;
    sendToUser(partnerId, {
      type: "peer_invite",
      title: t(lang, "peerInviteTitle", { name: shortName(callerName) }),
      body: t(lang, "peerInviteBody", {
        language: displayLanguage(conv.language, lang),
        level: callerProfile.level,
      }),
      data: { peerConversationId: conv.id, partnerId: userId, callId: call.id },
    }).catch(() => {});
  }

  const livekit = await mintPeerCallToken({
    userId,
    userName: callerName,
    livekitRoom,
    partner,
    callerProfile,
  });

  return {
    call: serializePeerCall(call),
    livekit,
    message: serializePeerMessage(inviteMsg, userId),
  };
}

export async function joinPeerCall(userId: string, callId: string) {
  const call = await db.query.peerCallRoom.findFirst({
    where: and(
      eq(peerCallRoom.id, callId),
      or(eq(peerCallRoom.hostUserId, userId), eq(peerCallRoom.guestUserId, userId)),
    ),
  });
  if (!call) return null;

  const conv = await db.query.peerConversation.findFirst({ where: eq(peerConversation.id, call.peerConversationId) });
  if (!conv) return null;

  const partnerId = call.hostUserId === userId ? call.guestUserId : call.hostUserId;
  const [callerProfile, userRow, partner] = await Promise.all([
    getProfile(userId),
    db.select({ name: user.name, email: user.email }).from(user).where(eq(user.id, userId)).then((r) => r[0]),
    loadLearner(partnerId, conv.language, false),
  ]);
  if (!partner) return null;

  let updatedCall = call;
  if (call.status === "waiting") {
    const [u] = await db
      .update(peerCallRoom)
      .set({ status: "active", startedAt: new Date() })
      .where(eq(peerCallRoom.id, call.id))
      .returning();
    if (u) updatedCall = u;
  }

  const livekit = await mintPeerCallToken({
    userId,
    userName: userRow?.name || userRow?.email || "Learner",
    livekitRoom: call.livekitRoom,
    partner,
    callerProfile,
  });

  return { call: serializePeerCall(updatedCall), livekit };
}

export async function declinePeerCall(userId: string, callId: string) {
  const call = await db.query.peerCallRoom.findFirst({
    where: and(
      eq(peerCallRoom.id, callId),
      or(eq(peerCallRoom.hostUserId, userId), eq(peerCallRoom.guestUserId, userId)),
    ),
  });
  if (!call) return null;

  const [updated] = await db
    .update(peerCallRoom)
    .set({ status: "declined", endedAt: new Date() })
    .where(eq(peerCallRoom.id, call.id))
    .returning();

  await db
    .update(peerMessage)
    .set({
      kind: "missed_invite",
      text: "Missed invite to talk",
      metadata: { status: "declined" },
    })
    .where(eq(peerMessage.callRoomId, call.id));

  return serializePeerCall(updated);
}

export async function pingPeerCall(userId: string, callId: string) {
  const call = await db.query.peerCallRoom.findFirst({
    where: and(
      eq(peerCallRoom.id, callId),
      or(eq(peerCallRoom.hostUserId, userId), eq(peerCallRoom.guestUserId, userId)),
    ),
  });
  if (!call) return null;

  const partnerId = call.hostUserId === userId ? call.guestUserId : call.hostUserId;
  const [conv, [sender], recipientProfile] = await Promise.all([
    db.query.peerConversation.findFirst({ where: eq(peerConversation.id, call.peerConversationId) }),
    db.select({ name: user.name }).from(user).where(eq(user.id, userId)),
    getProfile(partnerId),
  ]);
  if (!conv || !isRecipientMuted(conv, partnerId)) {
    const lang = recipientProfile.nativeLanguage;
    sendToUser(partnerId, {
      type: "peer_invite",
      title: t(lang, "peerPingTitle", { name: shortName(sender?.name || "Partner") }),
      body: t(lang, "peerPingBody"),
      data: { peerConversationId: call.peerConversationId, partnerId: userId, callId: call.id },
    }).catch(() => {});
  }
  return { ok: true, status: call.status };
}

export async function endPeerCall(userId: string, callId: string, durationSec: number, isVideo: boolean) {
  const call = await db.query.peerCallRoom.findFirst({
    where: and(
      eq(peerCallRoom.id, callId),
      or(eq(peerCallRoom.hostUserId, userId), eq(peerCallRoom.guestUserId, userId)),
    ),
  });
  if (!call) return null;

  const safeDur = Math.max(0, durationSec);
  const [updated] = await db
    .update(peerCallRoom)
    .set({ status: "ended", endedAt: new Date(), durationSec: safeDur })
    .where(eq(peerCallRoom.id, call.id))
    .returning();

  const conv = await db.query.peerConversation.findFirst({ where: eq(peerConversation.id, call.peerConversationId) });
  if (conv && safeDur > 0) {
    await recordPracticeActivity(userId, conv.language, { spokenSec: safeDur }).catch(() => {});
  }

  const mins = Math.max(1, Math.round(safeDur / 60));
  const summaryLabel = `${isVideo ? "Video" : "Voice"} practice · ${mins} min`;

  // Convert pending call_invite into call_summary (or insert summary)
  const existingInvites = await db
    .select()
    .from(peerMessage)
    .where(and(eq(peerMessage.callRoomId, call.id), eq(peerMessage.kind, "call_invite")));

  let summaryMsg;
  if (existingInvites.length > 0) {
    const [u] = await db
      .update(peerMessage)
      .set({
        kind: "call_summary",
        text: summaryLabel,
        metadata: { durationSec: safeDur, isVideo },
      })
      .where(eq(peerMessage.id, existingInvites[0].id))
      .returning();
    summaryMsg = u;
  } else {
    const [inserted] = await db
      .insert(peerMessage)
      .values({
        id: randomUUID(),
        peerConversationId: call.peerConversationId,
        senderId: userId,
        kind: "call_summary",
        text: summaryLabel,
        callRoomId: call.id,
        metadata: { durationSec: safeDur, isVideo },
      })
      .returning();
    summaryMsg = inserted;
  }

  await db
    .update(peerConversation)
    .set({ lastMessageText: summaryLabel, lastMessageAt: new Date() })
    .where(eq(peerConversation.id, call.peerConversationId));

  return {
    call: serializePeerCall(updated),
    summaryMessage: serializePeerMessage(summaryMsg, userId),
  };
}

// ── Coach Prompts for Live Calls ─────────────────────────────────────────────

const coachPromptItemSchema = z.object({
  phrase: z.string().min(1).max(140),
  translation: z.string().min(1).max(160),
});

const coachPromptsSchema = z.object({
  answers: z.array(coachPromptItemSchema).length(2),
  followUps: z.array(coachPromptItemSchema).length(2),
  questions: z.array(coachPromptItemSchema).length(2),
});

function fallbackCoachPrompts(targetLanguage: string, partnerName?: string | null) {
  if (targetLanguage === "es") {
    return {
      answers: [
        { phrase: "Normalmente tomo un café por la mañana.", translation: "I usually have a coffee in the morning." },
        { phrase: "Hoy prefiero practicar conversación cotidiana.", translation: "Today I prefer to practice everyday conversation." },
      ],
      followUps: partnerName
        ? [
            { phrase: `¡Hola ${partnerName.split(" ")[0]}! ¿Qué tal tu día?`, translation: `Hi ${partnerName.split(" ")[0]}! How is your day going?` },
            { phrase: "¿Sobre qué tema practicamos hoy?", translation: "What topic should we practice today?" },
          ]
        : [
            { phrase: "¿Y tú qué sueles pedir aquí?", translation: "And what do you usually order here?" },
            { phrase: "¿Tienen leche de avena hoy?", translation: "Do you have oat milk today?" },
          ],
      questions: [
        { phrase: "¿Cómo se dice esto de forma más natural?", translation: "How do you say this more naturally?" },
        { phrase: "¿Puedes repetir eso un poco más despacio?", translation: "Could you repeat that a little slower?" },
      ],
    };
  }
  return {
    answers: [
      { phrase: "I usually start my morning with coffee.", translation: "Useful everyday opener" },
      { phrase: "I'd like to practice natural conversation today.", translation: "State your practice goal" },
    ],
    followUps: [
      { phrase: "How has your day been going so far?", translation: "Keep the conversation warm" },
      { phrase: "What topic should we practice next?", translation: "Steer the conversation" },
    ],
    questions: [
      { phrase: "How would a native speaker say that?", translation: "Ask for a natural phrasing" },
      { phrase: "Could you say that a bit more slowly?", translation: "Slow down the pace" },
    ],
  };
}

export async function generateCoachPrompts(params: {
  mode?: "freetalk" | "peer";
  targetLanguage: string;
  nativeLanguage: string;
  level: string;
  tutorName?: string;
  contextLabel?: string;
  partnerName?: string | null;
  lastLine?: string | null;
}) {
  const target = languageName(params.targetLanguage as LanguageCode);
  const native = languageName(params.nativeLanguage as any) || "English";
  const isFreeTalk = (params.mode ?? (params.partnerName ? "peer" : "freetalk")) === "freetalk" && !params.partnerName;
  try {
    return await generateJson({
      system: isFreeTalk
        ? [
            `You generate live Free Talk coaching cards for a ${native} speaker learning ${target} (CEFR ${params.level}).`,
            `In Free Talk, the learner chats freely in ${native} or ${target}, asks how to say or pronounce ${target} words, and tries out ${target} phrases.`,
            `Return 2 concise, natural ${target} phrases (suited to ${params.level}) for each tab so tapping a card lets them hear and practice authentic ${target} pronunciation:`,
            `"answers": 2 natural ${target} expressions they can try saying right now;`,
            `"followUps": 2 fun everyday topic starters in ${target} (travel, food, hobbies, culture);`,
            `"questions": 2 practical ${target} phrases for asking how to pronounce/say something or slowing down.`,
            `Every "translation" must be a crisp ${native} translation.`,
          ].join(" ")
        : [
            `You generate live conversation coaching prompts for a learner speaking ${target} (CEFR ${params.level}).`,
            `Return 2 concise, natural ${target} phrases for each tab: "answers" (ways to respond), "followUps" (natural follow-up lines), and "questions" (questions to ask the other speaker).`,
            `Every "translation" must be a crisp ${native} translation.`,
          ].join(" "),
      contents: [
        params.contextLabel ? `Context: ${params.contextLabel}` : "",
        params.partnerName ? `Speaking with: ${params.partnerName}` : `Speaking with: ${params.tutorName || "Sofía"} (AI Personal Coach — Free Talk)`,
        params.lastLine ? `Last line heard: "${params.lastLine}"` : "",
      ]
        .filter(Boolean)
        .join("\n"),
      schema: coachPromptsSchema,
      temperature: 0.7,
      timeoutMs: 8_000,
    });
  } catch {
    return fallbackCoachPrompts(params.targetLanguage, params.partnerName);
  }
}
