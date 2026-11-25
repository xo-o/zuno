import test from "node:test";
import assert from "node:assert/strict";
import type { LearnerProfile } from "./profile.js";
import {
  cefrGuidelines,
  MAX_LESSONS_PER_TOPIC,
  MIN_LESSONS_PER_TOPIC,
  topicBuilderTurnSchema,
  topicSchema,
  voiceInstructions,
} from "./tutor.js";

function makeLessons(count: number) {
  return Array.from({ length: count }, (_, idx) => ({
    title: `Lección ${idx + 1}`,
    translation: `Lesson ${idx + 1}`,
    objective: `Practice scenario step ${idx + 1}.`,
  }));
}

function makeProfile(overrides: Partial<LearnerProfile> = {}): LearnerProfile {
  return {
    targetLanguage: "es",
    nativeLanguage: "en",
    level: "B1",
    accent: "es-419",
    tutorId: "sofia",
    tutorVoice: "warm",
    teachingStyle: "balanced",
    strictMode: false,
    handsFree: true,
    suggestReplies: true,
    showTranslations: true,
    teachInTarget: false,
    dailyGoalMin: 15,
    focus: "conversation",
    interests: [],
    goalNote: null,
    onboardingCompletedAt: null,
    recommendedTopics: [],
    openToSpeak: true,
    allowCallInvites: true,
    whoCanMessage: "partners",
    plan: "free",
    planExpiresAt: null,
    ...overrides,
  };
}

test("tutor: topic lesson bounds allow 3 to 15 lessons and reject out-of-range counts", () => {
  assert.equal(MIN_LESSONS_PER_TOPIC, 3);
  assert.equal(MAX_LESSONS_PER_TOPIC, 15);

  for (const validCount of [3, 5, 8, 12, 15]) {
    const topicRes = topicSchema.safeParse({
      title: "Viaje a Lima",
      translation: "Trip to Lima",
      category: "travel",
      lessons: makeLessons(validCount),
    });
    assert.equal(topicRes.success, true, `expected ${validCount} lessons to be valid`);

    const builderRes = topicBuilderTurnSchema.safeParse({
      reply: `Built a ${validCount}-lesson plan.`,
      ready: true,
      draft: {
        title: "Viaje a Lima",
        translation: "Trip to Lima",
        category: "travel",
        prompt: "Essential phrases for Lima",
        lessons: makeLessons(validCount),
      },
    });
    assert.equal(builderRes.success, true, `expected builder draft with ${validCount} lessons to be valid`);
  }

  for (const invalidCount of [0, 2, 16]) {
    const topicRes = topicSchema.safeParse({
      title: "Viaje a Lima",
      translation: "Trip to Lima",
      category: "travel",
      lessons: makeLessons(invalidCount),
    });
    assert.equal(topicRes.success, false, `expected ${invalidCount} lessons to fail`);

    const builderRes = topicBuilderTurnSchema.safeParse({
      reply: "Draft",
      ready: true,
      draft: {
        title: "Viaje a Lima",
        translation: "Trip to Lima",
        category: "travel",
        prompt: "Essential phrases for Lima",
        lessons: makeLessons(invalidCount),
      },
    });
    assert.equal(builderRes.success, false, `expected builder draft with ${invalidCount} lessons to fail`);
  }
});

test("tutor: cefrGuidelines returns distinct guidance per level", () => {
  const a1 = cefrGuidelines("A1");
  const b1 = cefrGuidelines("B1");
  const c1 = cefrGuidelines("C1");

  assert.match(a1, /CEFR A1/i);
  assert.match(a1, /zero idioms/i);
  assert.match(b1, /CEFR B1/i);
  assert.match(c1, /CEFR C1/i);
  assert.notEqual(a1, b1);
  assert.notEqual(b1, c1);
});

test("tutor: voiceInstructions injects CEFR guidelines for Free Talk, Roleplay, and Lesson", () => {
  const a1FreeTalk = voiceInstructions({ kind: "freetalk" }, makeProfile({ level: "A1", teachInTarget: false }));
  assert.match(a1FreeTalk, /CEFR A1/);

  const c1Immersion = voiceInstructions({ kind: "freetalk" }, makeProfile({ level: "C1", teachInTarget: true }));
  assert.match(c1Immersion, /CEFR C1/);

  const levelCheck = voiceInstructions({ kind: "levelcheck" }, makeProfile({ level: "C1" }));
  assert.doesNotMatch(levelCheck, /CEFR C1 \(Advanced\)/);
});
