import { randomUUID } from "node:crypto";
import { and, asc, desc, eq, ne, or, sql } from "drizzle-orm";
import { AccessToken } from "livekit-server-sdk";
import { z } from "zod";
import {
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
import { generateJson } from "./gemini.js";
import { quote, t } from "./copy.js";
import { languageName, type LanguageCode } from "./languages.js";
import { displayLanguage } from "./notifications.js";
import { getProfile, type LearnerProfile } from "./profile.js";
import { sendToUser } from "./push.js";
import { elevenLabsVoiceFor, resolveTutor } from "./voices.js";

const SEED_PREFIX = "seed:";

type SeedLearnerSpec = {
  id: string;
  name: string;
  email: string;
  level: "A2" | "B1" | "B2";
  nativeLanguage: string;
  tutorVoice: "warm" | "calm" | "energetic";
  openToSpeak: boolean;
  streakDays: number;
  sessionsCount: number;
  interests: string[];
  activeLabel: string;
  starterPartner: boolean;
};

const SEED_LEARNERS: SeedLearnerSpec[] = [
  {
    id: "seed:lucia",
    name: "Lucía M.",
    email: "lucia.seed@heyzuno.app",
    level: "B1",
    nativeLanguage: "en",
    tutorVoice: "warm",
    openToSpeak: true,
    streakDays: 18,
    sessionsCount: 34,
    interests: ["Travel", "Café", "Culture"],
    activeLabel: "Open to speak",
    starterPartner: false,
  },
  {
    id: "seed:priya",
    name: "Priya S.",
    email: "priya.seed@heyzuno.app",
    level: "B2",
    nativeLanguage: "en",
    tutorVoice: "calm",
    openToSpeak: true,
    streakDays: 21,
    sessionsCount: 48,
    interests: ["Travel", "Food", "Cinema"],
    activeLabel: "Open to speak",
    starterPartner: true,
  },
  {
    id: "seed:carlos",
    name: "Carlos Vega",
    email: "carlos.seed@heyzuno.app",
    level: "B2",
    nativeLanguage: "pt",
    tutorVoice: "energetic",
    openToSpeak: true,
    streakDays: 14,
    sessionsCount: 42,
    interests: ["Travel", "Daily life", "Tech"],
    activeLabel: "Open to speak",
    starterPartner: false,
  },
  {
    id: "seed:yuki",
    name: "Yuki T.",
    email: "yuki.seed@heyzuno.app",
    level: "A2",
    nativeLanguage: "ja",
    tutorVoice: "calm",
    openToSpeak: true,
    streakDays: 9,
    sessionsCount: 16,
    interests: ["Daily life", "Music"],
    activeLabel: "Open to speak",
    starterPartner: false,
  },
  {
    id: "seed:mateo",
    name: "Mateo R.",
    email: "mateo.seed@heyzuno.app",
    level: "B1",
    nativeLanguage: "en",
    tutorVoice: "energetic",
    openToSpeak: false,
    streakDays: 11,
    sessionsCount: 25,
    interests: ["Work", "Sports"],
    activeLabel: "Active 2h ago",
    starterPartner: true,
  },
];

const SEED_BY_ID = new Map(SEED_LEARNERS.map((s) => [s.id, s]));

export function isSeedUser(userId: string) {
  return userId.startsWith(SEED_PREFIX);
}

let seededOnce = false;

export async function ensureSeededLearners() {
  if (seededOnce) return;
  for (const spec of SEED_LEARNERS) {
    await db
      .insert(user)
      .values({
        id: spec.id,
        name: spec.name,
        email: spec.email,
        emailVerified: true,
      })
      .onConflictDoNothing();
    await db
      .insert(userProfile)
      .values({
        userId: spec.id,
        targetLanguage: "es",
        nativeLanguage: spec.nativeLanguage,
        level: spec.level,
        tutorVoice: spec.tutorVoice,
        interests: spec.interests,
        openToSpeak: spec.openToSpeak,
        onboardingCompletedAt: new Date(),
      })
      .onConflictDoNothing();
  }
  seededOnce = true;
}

const seededUsers = new Set<string>();

/** Seeds realistic starter 1:1 threads & added partners on first visit for a new learner. */
async function ensureStarterPeerData(userId: string, language: string) {
  if (seededUsers.has(`${userId}:${language}`)) return;
  await ensureSeededLearners();
  const [existingConvs, existingPartners] = await Promise.all([
    db
      .select({ id: peerConversation.id })
      .from(peerConversation)
      .where(or(eq(peerConversation.userAId, userId), eq(peerConversation.userBId, userId)))
      .limit(1),
    db.select({ partnerUserId: learnerPartner.partnerUserId }).from(learnerPartner).where(eq(learnerPartner.userId, userId)).limit(1),
  ]);
  if (existingConvs.length > 0 || existingPartners.length > 0) {
    seededUsers.add(`${userId}:${language}`);
    return;
  }

  for (const spec of SEED_LEARNERS.filter((s) => s.starterPartner)) {
    await db.insert(learnerPartner).values({ userId, partnerUserId: spec.id }).onConflictDoNothing();
  }

  const now = Date.now();
  const luciaConvId = randomUUID();
  const priyaConvId = randomUUID();
  const mateoConvId = randomUUID();

  const luciaGreeting =
    language === "es"
      ? "¡Hola! ¿Tienes 5 minutos para practicar antes de la clase?"
      : `Hi! Do you have 5 minutes to practice ${languageName(language as LanguageCode)} together?`;

  await db.insert(peerConversation).values([
    {
      id: luciaConvId,
      userAId: userId,
      userBId: "seed:lucia",
      language,
      lastMessageText: luciaGreeting,
      lastMessageAt: new Date(now - 14 * 60_000),
      unreadCountA: 1,
      unreadCountB: 0,
    },
    {
      id: priyaConvId,
      userAId: userId,
      userBId: "seed:priya",
      language,
      lastMessageText: "Voice practice · 8 min",
      lastMessageAt: new Date(now - 24 * 60 * 60_000),
      unreadCountA: 0,
      unreadCountB: 0,
    },
    {
      id: mateoConvId,
      userAId: userId,
      userBId: "seed:mateo",
      language,
      lastMessageText: language === "es" ? "Nos vemos el jueves en la sala" : "See you Thursday in the practice room!",
      lastMessageAt: new Date(now - 48 * 60 * 60_000),
      unreadCountA: 0,
      unreadCountB: 0,
    },
  ]);

  const luciaCallId = randomUUID();
  await db.insert(peerCallRoom).values({
    id: luciaCallId,
    peerConversationId: luciaConvId,
    hostUserId: "seed:lucia",
    guestUserId: userId,
    livekitRoom: `peer-${luciaCallId.slice(0, 12)}`,
    status: "waiting",
    cameraEnabled: false,
  });

  await db.insert(peerMessage).values([
    {
      id: randomUUID(),
      peerConversationId: luciaConvId,
      senderId: "seed:lucia",
      kind: "text",
      text: luciaGreeting,
      createdAt: new Date(now - 16 * 60_000),
    },
    {
      id: randomUUID(),
      peerConversationId: luciaConvId,
      senderId: "seed:lucia",
      kind: "call_invite",
      text: "Lucía M. is inviting you to talk",
      callRoomId: luciaCallId,
      metadata: { cameraEnabled: false, status: "waiting" },
      createdAt: new Date(now - 14 * 60_000),
    },
    {
      id: randomUUID(),
      peerConversationId: priyaConvId,
      senderId: "seed:priya",
      kind: "call_summary",
      text: "Voice practice · 8 min",
      metadata: { durationSec: 480, isVideo: false },
      createdAt: new Date(now - 24 * 60 * 60_000),
    },
    {
      id: randomUUID(),
      peerConversationId: mateoConvId,
      senderId: "seed:mateo",
      kind: "text",
      text: language === "es" ? "Nos vemos el jueves en la sala" : "See you Thursday in the practice room!",
      createdAt: new Date(now - 48 * 60 * 60_000),
    },
  ]);

  seededUsers.add(`${userId}:${language}`);
}

export function shortName(fullName: string) {
  const parts = fullName.trim().split(/\s+/);
  if (parts.length <= 1) return parts[0] || "Learner";
  return `${parts[0]} ${parts[1][0].toUpperCase()}.`;
}

function serializeLearner(
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
) {
  const seed = SEED_BY_ID.get(row.id);
  const diffMin = Math.max(0, Math.round((Date.now() - row.lastSeenAt.getTime()) / 60_000));
  const activeLabel = seed
    ? seed.activeLabel
    : row.openToSpeak
      ? "Open to speak"
      : diffMin < 5
        ? "Online"
        : diffMin < 60
          ? `Active ${diffMin}m ago`
          : `Active ${Math.max(1, Math.round(diffMin / 60))}h ago`;
  return {
    id: row.id,
    name: row.name,
    shortName: row.name.split(" ")[0] || row.name,
    initial: (row.name.trim()[0] || "L").toUpperCase(),
    image: row.image ?? null,
    language: targetLanguage,
    languageName: languageName(targetLanguage as LanguageCode),
    level: seed?.level ?? row.level,
    nativeLanguage: languageName((seed?.nativeLanguage ?? row.nativeLanguage) as any) || "English",
    streakDays: seed?.streakDays ?? 7,
    sessionsCount: seed?.sessionsCount ?? 12,
    interests: seed?.interests ?? (row.interests.length > 0 ? row.interests : ["Conversation", "Travel"]),
    openToSpeak: row.openToSpeak,
    isPartner,
    activeLabel,
    isSeeded: Boolean(seed),
  };
}

export async function loadLearner(partnerId: string, viewerLanguage: string, isPartner: boolean) {
  await ensureSeededLearners();
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
  return serializeLearner(
    {
      id: row.id,
      name: row.name,
      image: row.image,
      level: row.level ?? "B1",
      nativeLanguage: row.nativeLanguage ?? "en",
      interests: row.interests ?? [],
      openToSpeak: row.openToSpeak ?? true,
      lastSeenAt: row.lastSeenAt ?? new Date(),
    },
    viewerLanguage,
    isPartner,
  );
}

export async function getChatsOverview(userId: string) {
  const profile = await getProfile(userId);
  await db.update(userProfile).set({ lastSeenAt: new Date() }).where(eq(userProfile.userId, userId));
  await ensureStarterPeerData(userId, profile.targetLanguage);

  const [viewerRow] = await db
    .select({ openToSpeak: userProfile.openToSpeak })
    .from(userProfile)
    .where(eq(userProfile.userId, userId));

  const partnerRows = await db
    .select({ partnerUserId: learnerPartner.partnerUserId })
    .from(learnerPartner)
    .where(eq(learnerPartner.userId, userId));
  const partnerIds = new Set(partnerRows.map((r) => r.partnerUserId));

  // Learners open to speak: real users learning this language (openToSpeak=true) + seeded learners.
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
        or(
          and(eq(userProfile.targetLanguage, profile.targetLanguage), eq(userProfile.openToSpeak, true)),
          sql`${user.id} like 'seed:%'`,
          partnerRows.length > 0
            ? sql`${user.id} in (${sql.join(
                partnerRows.map((p) => sql`${p.partnerUserId}`),
                sql`, `,
              )})`
            : sql`false`,
        ),
      ),
    );

  const learnersMap = new Map(
    learnerRows.map((row) => [
      row.id,
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
      ),
    ]),
  );

  const openSpeakers = Array.from(learnersMap.values())
    .filter((l) => l.openToSpeak)
    .sort((a, b) => Number(a.isSeeded) - Number(b.isSeeded));

  const partners = Array.from(learnersMap.values()).filter((l) => l.isPartner);

  // AI Tutor conversations for current targetLanguage
  const aiConvs = await db.query.conversation.findMany({
    where: and(eq(conversation.userId, userId), eq(conversation.language, profile.targetLanguage)),
    orderBy: [desc(conversation.updatedAt)],
    limit: 30,
    with: {
      messages: {
        orderBy: [desc(message.createdAt)],
        limit: 1,
      },
    },
  });

  // Peer conversations for this user
  const peerConvs = await db
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
    .limit(30);

  const activeTutor = resolveTutor(profile.tutorId, profile.tutorVoice);
  const aiItems = aiConvs
    .filter((row) => row.messages.length > 0 || row.kind === "freetalk")
    .map((row) => {
      const lastMsg = row.messages[0];
      const isVoice = Boolean(lastMsg?.viaVoice);
      const snippet = lastMsg
        ? lastMsg.kind === "call_summary"
          ? lastMsg.text
          : `${lastMsg.role === "assistant" ? `${activeTutor.name} · ` : "You · "}${lastMsg.text}`
        : "Message or call anytime";
      return {
        id: row.id,
        kind: "ai" as const,
        conversationId: row.id,
        peerConversationId: null as string | null,
        partnerId: null as string | null,
        title: row.title,
        badge: isVoice ? "Voice" : row.kind === "roleplay" ? "Roleplay" : row.kind === "lesson" ? "Lesson" : "Text",
        isVoice,
        openToSpeak: false,
        snippet,
        unread: false,
        pinned: row.pinnedAt !== null,
        updatedAt: (lastMsg?.createdAt ?? row.updatedAt).toISOString(),
        avatarInitial: "S",
        avatarUrl: null as string | null,
      };
    });

  const peerItems = await Promise.all(
    peerConvs.map(async (row) => {
      const isA = row.userAId === userId;
      const partnerId = isA ? row.userBId : row.userAId;
      const partner = learnersMap.get(partnerId) ?? (await loadLearner(partnerId, profile.targetLanguage, partnerIds.has(partnerId)));
      const unread = (isA ? row.unreadCountA : row.unreadCountB) > 0;
      return {
        id: `peer:${row.id}`,
        kind: "peer" as const,
        conversationId: null as string | null,
        peerConversationId: row.id,
        partnerId,
        title: partner?.name ?? "Learner",
        badge: partner?.openToSpeak ? "Open to speak" : null,
        isVoice: false,
        openToSpeak: partner?.openToSpeak ?? false,
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
  await ensureSeededLearners();
  if (add) {
    await db.insert(learnerPartner).values({ userId, partnerUserId: partnerId }).onConflictDoNothing();
  } else {
    await db
      .delete(learnerPartner)
      .where(and(eq(learnerPartner.userId, userId), eq(learnerPartner.partnerUserId, partnerId)));
  }
}

export async function findOrCreatePeerConversation(userId: string, partnerId: string) {
  const profile = await getProfile(userId);
  await ensureSeededLearners();
  const [userAId, userBId] = userId < partnerId ? [userId, partnerId] : [partnerId, userId];

  const existing = await db.query.peerConversation.findFirst({
    where: and(
      eq(peerConversation.userAId, userAId),
      eq(peerConversation.userBId, userBId),
      eq(peerConversation.language, profile.targetLanguage),
    ),
  });

  if (existing) {
    // Un-delete for caller if previously deleted
    if ((existing.userAId === userId && existing.deletedByUserA) || (existing.userBId === userId && existing.deletedByUserB)) {
      await db
        .update(peerConversation)
        .set(existing.userAId === userId ? { deletedByUserA: false } : { deletedByUserB: false })
        .where(eq(peerConversation.id, existing.id));
    }
    return existing;
  }

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

const peerReplySchema = z.object({
  reply: z.string().min(1).max(320),
});

async function triggerSeededReply(
  conv: typeof peerConversation.$inferSelect,
  partnerSpec: SeedLearnerSpec,
  learnerMessage: string,
) {
  try {
    const recent = await db
      .select()
      .from(peerMessage)
      .where(and(eq(peerMessage.peerConversationId, conv.id), eq(peerMessage.kind, "text")))
      .orderBy(desc(peerMessage.createdAt))
      .limit(8);
    const historyLines = recent
      .reverse()
      .map((m) => `${m.senderId === partnerSpec.id ? partnerSpec.name : "Partner"}: ${m.text}`)
      .join("\n");

    const targetLang = languageName(conv.language as LanguageCode);
    let replyText = "";
    try {
      const res = await generateJson({
        system: [
          `You are ${partnerSpec.name}, a friendly language learner practicing ${targetLang} at CEFR ${partnerSpec.level}.`,
          `Your native language is ${partnerSpec.nativeLanguage} and your interests are ${partnerSpec.interests.join(", ")}.`,
          `Reply naturally to your practice partner's message in ${targetLang} (1-2 short, warm conversational sentences). Do not act like a teacher or AI; talk like a peer learner.`,
        ].join(" "),
        contents: `Recent chat:\n${historyLines}\n\nLatest message from your partner: "${learnerMessage}"`,
        schema: peerReplySchema,
        temperature: 0.8,
        timeoutMs: 12_000,
      });
      replyText = res.reply.trim();
    } catch {
      replyText =
        conv.language === "es"
          ? "¡Qué bien! Yo también estoy practicando hoy. ¿Hablamos un momento por voz?"
          : "Sounds great! I am practicing right now too. Want to hop on a quick voice call?";
    }

    const isPartnerA = conv.userAId === partnerSpec.id;
    const recipientId = isPartnerA ? conv.userBId : conv.userAId;
    const replyMsgId = randomUUID();
    await db.insert(peerMessage).values({
      id: replyMsgId,
      peerConversationId: conv.id,
      senderId: partnerSpec.id,
      kind: "text",
      text: replyText,
    });
    const [updatedConv] = await db
      .update(peerConversation)
      .set({
        lastMessageText: replyText,
        lastMessageAt: new Date(),
        ...(isPartnerA
          ? { unreadCountB: sql`${peerConversation.unreadCountB} + 1` }
          : { unreadCountA: sql`${peerConversation.unreadCountA} + 1` }),
      })
      .where(eq(peerConversation.id, conv.id))
      .returning();

    if (!isSeedUser(recipientId) && !(updatedConv && isRecipientMuted(updatedConv, recipientId))) {
      sendToUser(recipientId, {
        type: "peer_message",
        title: partnerSpec.name,
        body: quote(replyText, 120),
        data: { peerConversationId: conv.id, partnerId: partnerSpec.id, messageId: replyMsgId },
      }).catch(() => {});
    }
  } catch (err) {
    console.warn("Seeded learner reply failed:", err);
  }
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

  const seed = SEED_BY_ID.get(partnerId);
  if (seed) {
    setTimeout(() => void triggerSeededReply(conv, seed, text), 700);
  } else if (!isRecipientMuted(conv, partnerId)) {
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
    .set(isA ? { deletedByUserA: true } : { deletedByUserB: true })
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
  const seed = SEED_BY_ID.get(params.partner.id);
  const metadata = seed
    ? {
        mode: "peer_partner",
        provider: "elevenlabs",
        voice: elevenLabsVoiceFor(seed.tutorVoice),
        tutorVoice: seed.tutorVoice,
        language: params.callerProfile.targetLanguage,
        targetLanguage: params.callerProfile.targetLanguage,
        nativeLanguage: params.callerProfile.nativeLanguage,
        handsFree: true,
        partnerId: seed.id,
        partnerName: seed.name,
        partnerLevel: seed.level,
        partnerInterests: seed.interests,
      }
    : {
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
  const [callerProfile, callerRow, partner] = await Promise.all([
    getProfile(userId),
    db.select({ name: user.name, email: user.email }).from(user).where(eq(user.id, userId)).then((r) => r[0]),
    loadLearner(partnerId, conv.language, false),
  ]);
  if (!partner) return null;

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

  if (!isSeedUser(partnerId) && !isRecipientMuted(conv, partnerId)) {
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
  if (!isSeedUser(partnerId)) {
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
