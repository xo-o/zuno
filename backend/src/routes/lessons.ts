import { randomUUID } from "node:crypto";
import { and, desc, eq, inArray, isNull } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { AccessToken } from "livekit-server-sdk";
import { z } from "zod";
import {
  conversation,
  lessonEvaluation,
  topic,
  topicLesson,
  type LessonCurriculum,
} from "../db/app-schema.js";
import { db } from "../db/index.js";
import { createConversation } from "../lib/conversations.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { languageName } from "../lib/languages.js";
import { getProfile } from "../lib/profile.js";
import { serializeLessonEvaluation, serializeTopicDetail } from "../lib/serializers.js";
import { generateLessonCurriculum } from "../lib/tutor.js";
import { cartesiaVoiceFor } from "../lib/voices.js";
import { checkTopicCompleted } from "./conversations.js";

const TOKEN_TTL = "6h";

const completeEvaluationSchema = z.object({
  conversationId: z.string().min(1).max(64).optional(),
  durationSec: z.number().int().min(1).max(7200).default(300),
  pronunciationScore: z.number().int().min(0).max(100).default(90),
  goalsCompleted: z.number().int().min(0).max(20).default(5),
  goalsTotal: z.number().int().min(1).max(20).default(5),
  fluencyLabel: z.enum(["Developing", "Steady", "Natural", "Fluent"]).default("Natural"),
  coachNotes: z
    .array(
      z.object({
        category: z.string().min(1).max(80),
        correction: z.string().min(1).max(200),
        ttsSampleText: z.string().min(1).max(200),
        kind: z.enum(["grammar", "pronunciation"]),
      }),
    )
    .max(6)
    .default([]),
  phraseResults: z
    .array(
      z.object({
        phrase: z.string().min(1).max(200),
        status: z.enum(["mastered", "practice"]),
      }),
    )
    .max(10)
    .default([]),
});

async function loadLessonWithTopic(userId: string, lessonId: string) {
  const lesson = await db.query.topicLesson.findFirst({
    where: eq(topicLesson.id, lessonId),
  });
  if (!lesson) throw new HTTPException(404, { message: "Lesson not found" });

  const parent = await db.query.topic.findFirst({
    where: and(eq(topic.id, lesson.topicId), eq(topic.userId, userId)),
    with: { lessons: true },
  });
  if (!parent) throw new HTTPException(404, { message: "Lesson not found" });

  const sortedLessons = [...parent.lessons].sort((a, b) => a.position - b.position);
  return { lesson, parent, sortedLessons };
}

function computeNextLessonId(sortedLessons: (typeof topicLesson.$inferSelect)[], currentLessonId: string): string | null {
  const currentIdx = sortedLessons.findIndex((l) => l.id === currentLessonId);
  const afterCurrentIncomplete = sortedLessons.slice(currentIdx + 1).find((l) => !l.completedAt);
  if (afterCurrentIncomplete) return afterCurrentIncomplete.id;
  const anyIncomplete = sortedLessons.find((l) => !l.completedAt && l.id !== currentLessonId);
  if (anyIncomplete) return anyIncomplete.id;
  const immediateNext = sortedLessons[currentIdx + 1];
  return immediateNext ? immediateNext.id : null;
}

const sessionLimit = rateLimit({ windowMs: 60_000, max: 15 });

export const lessonRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  /** Starts an interactive 03g voice lesson session and returns a LiveKit token with full curriculum metadata. */
  .post("/:lessonId/session", sessionLimit, async (c) => {
    const { LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET } = process.env;
    if (!LIVEKIT_URL || !LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) {
      throw new HTTPException(500, { message: "LiveKit is not configured" });
    }

    const user = c.get("user");
    const { lesson, parent, sortedLessons } = await loadLessonWithTopic(user.id, c.req.param("lessonId"));
    const profile = await getProfile(user.id);

    let baseCurriculum: LessonCurriculum | null = lesson.curriculum ?? null;
    if (!baseCurriculum) {
      baseCurriculum = await generateLessonCurriculum(
        {
          topicTitle: parent.title,
          lessonTitle: lesson.title,
          lessonTranslation: lesson.translation,
          objective: lesson.objective,
          position: lesson.position,
          material: parent.material,
        },
        profile,
      );
      await db.update(topicLesson).set({ curriculum: baseCurriculum }).where(eq(topicLesson.id, lesson.id));
    }

    // Spaced-repetition injection: pull up to 1 "practice" phrase from earlier lessons in this topic into active_recall.
    const earlierLessonIds = sortedLessons.filter((l) => l.position < lesson.position).map((l) => l.id);
    let sessionCurriculum: LessonCurriculum = structuredClone(baseCurriculum);
    if (earlierLessonIds.length > 0) {
      const priorEvals = await db.query.lessonEvaluation.findMany({
        where: and(eq(lessonEvaluation.userId, user.id), inArray(lessonEvaluation.lessonId, earlierLessonIds)),
        orderBy: [desc(lessonEvaluation.createdAt)],
        limit: 5,
      });
      const reviewItem = priorEvals
        .flatMap((ev) => ev.phraseResults ?? [])
        .find((pr) => pr.status === "practice");
      const reviewPhrase = reviewItem?.phrase;
      if (reviewPhrase && !sessionCurriculum.recallPrompts.some((r) => r.expectedTargets.includes(reviewPhrase))) {
        const firstWords = reviewPhrase.split(/\s+/).slice(0, 2).join(" ") + "…";
        const cueLangName = languageName(parent.language || profile.targetLanguage);
        // Find English translation from earlier lesson curriculum to prompt in native language without speaking Spanish in English voice
        const priorLessonWithPhrase = sortedLessons.find((l) =>
          l.curriculum?.targetPhrases.some((tp) => tp.phrase.toLowerCase() === reviewPhrase.toLowerCase()),
        );
        const phraseMeta = priorLessonWithPhrase?.curriculum?.targetPhrases.find(
          (tp) => tp.phrase.toLowerCase() === reviewPhrase.toLowerCase(),
        );
        const situationPrompt = phraseMeta?.translation
          ? `Quick review from earlier: how do you say "${phraseMeta.translation}" in ${cueLangName}?`
          : `Quick review from earlier: express this concept in ${cueLangName}.`;

        sessionCurriculum.recallPrompts.push({
          situationNative: situationPrompt,
          firstWordHint: firstWords,
          expectedTargets: [reviewPhrase],
        });
      }
    }

    let thread = await db.query.conversation.findFirst({
      where: and(eq(conversation.userId, user.id), eq(conversation.lessonId, lesson.id)),
      orderBy: [desc(conversation.updatedAt)],
    });
    if (!thread) {
      thread = await createConversation(
        {
          userId: user.id,
          kind: "lesson",
          title: lesson.title,
          language: parent.language || profile.targetLanguage,
          topicId: parent.id,
          lessonId: lesson.id,
        },
        profile,
        { withOpening: false },
      );
    }

    const room = `zuno-les-${randomUUID().slice(0, 8)}`;
    const voice = cartesiaVoiceFor(profile.tutorVoice);
    const targetLanguage = parent.language || profile.targetLanguage;

    const token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
      identity: user.id,
      name: user.name || user.email,
      ttl: TOKEN_TTL,
      metadata: JSON.stringify({
        mode: "lesson",
        provider: "cartesia",
        voice,
        tutorVoice: profile.tutorVoice,
        lessonId: lesson.id,
        topicId: parent.id,
        conversationId: thread.id,
        position: lesson.position,
        totalLessons: sortedLessons.length,
        topicTitle: parent.title,
        lessonTitle: lesson.title,
        lessonTranslation: lesson.translation,
        objective: lesson.objective,
        curriculum: sessionCurriculum,
        nativeLanguage: profile.nativeLanguage,
        language: targetLanguage,
        targetLanguage,
        targetLanguageName: languageName(targetLanguage),
        nativeLanguageName: languageName(profile.nativeLanguage),
        accent: profile.accent,
        level: profile.level,
        teachInTarget: profile.teachInTarget,
        handsFree: profile.handsFree,
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
      conversationId: thread.id,
      topicId: parent.id,
      lessonId: lesson.id,
      position: lesson.position,
      totalLessons: sortedLessons.length,
      lessonTitle: lesson.title,
      lessonTranslation: lesson.translation,
      curriculum: sessionCurriculum,
    });
  })
  /** Persists 04 · Evaluation results, marks the lesson complete, and returns updated topic state + nextLessonId. */
  .post("/:lessonId/complete", async (c) => {
    const user = c.get("user");
    const { lesson, parent, sortedLessons } = await loadLessonWithTopic(user.id, c.req.param("lessonId"));
    const body = await parseBody(c, completeEvaluationSchema);

    let validConversationId: string | null = null;
    if (body.conversationId) {
      const existingConv = await db.query.conversation.findFirst({
        where: and(eq(conversation.id, body.conversationId), eq(conversation.userId, user.id)),
      });
      if (existingConv) validConversationId = existingConv.id;
    }

    const [evalRow] = await db
      .insert(lessonEvaluation)
      .values({
        id: randomUUID(),
        lessonId: lesson.id,
        conversationId: validConversationId,
        userId: user.id,
        durationSec: body.durationSec,
        pronunciationScore: body.pronunciationScore,
        goalsCompleted: body.goalsCompleted,
        goalsTotal: Math.max(body.goalsTotal, body.goalsCompleted, 1),
        fluencyLabel: body.fluencyLabel,
        coachNotes: body.coachNotes,
        phraseResults: body.phraseResults,
      })
      .returning();

    await db
      .update(topicLesson)
      .set({ completedAt: new Date() })
      .where(and(eq(topicLesson.id, lesson.id), isNull(topicLesson.completedAt)));
    await db.update(topic).set({ updatedAt: new Date() }).where(eq(topic.id, parent.id));
    await checkTopicCompleted(parent.id);

    const freshParent = await db.query.topic.findFirst({
      where: eq(topic.id, parent.id),
      with: { lessons: true },
    });
    const freshSorted = [...(freshParent?.lessons ?? sortedLessons)].sort((a, b) => a.position - b.position);
    const nextLessonId = computeNextLessonId(freshSorted, lesson.id);

    return c.json({
      evaluation: serializeLessonEvaluation(evalRow, lesson, freshSorted.length, nextLessonId),
      topic: freshParent ? serializeTopicDetail(freshParent, freshSorted) : null,
      nextLessonId,
    });
  })
  /** Loads the latest saved 04 · Evaluation for a completed lesson (or synthesizes a summary for legacy done lessons). */
  .get("/:lessonId/evaluation", async (c) => {
    const user = c.get("user");
    const { lesson, sortedLessons } = await loadLessonWithTopic(user.id, c.req.param("lessonId"));
    const nextLessonId = computeNextLessonId(sortedLessons, lesson.id);

    const latest = await db.query.lessonEvaluation.findFirst({
      where: and(eq(lessonEvaluation.lessonId, lesson.id), eq(lessonEvaluation.userId, user.id)),
      orderBy: [desc(lessonEvaluation.createdAt)],
    });

    if (latest) {
      return c.json({
        evaluation: serializeLessonEvaluation(latest, lesson, sortedLessons.length, nextLessonId),
        nextLessonId,
      });
    }

    if (!lesson.completedAt) {
      throw new HTTPException(404, { message: "No evaluation found for this lesson yet" });
    }

    // Legacy lesson marked complete before 03g evaluations existed: build a clean summary from its curriculum/title.
    const fallbackPhrases =
      lesson.curriculum?.targetPhrases.map((tp) => ({
        phrase: tp.phrase,
        status: "mastered" as const,
      })) ?? [{ phrase: lesson.title, status: "mastered" as const }];
    const fallbackNotes = lesson.curriculum?.targetPhrases[0]
      ? [
          {
            category: "Pronunciation · Stress",
            correction: `${lesson.curriculum.targetPhrases[0].syllables} — ${lesson.curriculum.targetPhrases[0].pronunciationCue}`,
            ttsSampleText: lesson.curriculum.targetPhrases[0].focusWord,
            kind: "pronunciation" as const,
          },
        ]
      : [];

    return c.json({
      evaluation: {
        id: `legacy-${lesson.id}`,
        lessonId: lesson.id,
        topicId: lesson.topicId,
        lessonPosition: lesson.position,
        totalLessons: sortedLessons.length,
        lessonTitle: lesson.title,
        lessonTranslation: lesson.translation,
        durationSec: 300,
        pronunciationScore: 90,
        goalsCompleted: 5,
        goalsTotal: 5,
        fluencyLabel: "Natural",
        coachNotes: fallbackNotes,
        phraseResults: fallbackPhrases,
        nextLessonId,
        createdAt: lesson.completedAt.toISOString(),
      },
      nextLessonId,
    });
  });
