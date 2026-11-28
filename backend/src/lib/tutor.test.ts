import test from "node:test";
import assert from "node:assert/strict";
import type { LearnerProfile } from "./profile.js";
import {
  cefrGuidelines,
  goalsTurnSchema,
  levelTurnSchema,
  MAX_LESSONS_PER_TOPIC,
  MIN_LESSONS_PER_TOPIC,
  nameTurnSchema,
  parseOnboardingTurn,
  topicBuilderTurnSchema,
  topicSchema,
  tutorSystem,
  voiceInstructions,
} from "./tutor.js";
import { aiFriendDirective, CHATS_FRIEND_ORDER, TUTOR_CATALOG } from "./voices.js";

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

test("tutor: onboarding turn schemas support correctedName and empty inputs return localized retry prompts", async () => {
  assert.equal(nameTurnSchema.safeParse({ valid: true, name: "Pablito", retryPrompt: "" }).success, true);
  assert.equal(levelTurnSchema.safeParse({ valid: false, level: "A1", correctedName: "Pablituuu", retryPrompt: "¡Hola, Pablituuu!" }).success, true);
  assert.equal(
    goalsTurnSchema.safeParse({
      valid: false,
      focus: "conversation",
      interests: [],
      goalNote: "",
      correctedName: "Pablituuu",
      retryPrompt: "¡Hola, Pablituuu! ¿Cuál es tu objetivo?",
    }).success,
    true,
  );

  const emptyNameEs = await parseOnboardingTurn({ step: "name", answer: "   ", targetLanguage: "en", nativeLanguage: "es" });
  assert.equal(emptyNameEs.valid, false);
  assert.match(emptyNameEs.retryPrompt, /llame/i);

  const emptyLevelEs = await parseOnboardingTurn({ step: "level", answer: "", targetLanguage: "en", nativeLanguage: "es" });
  assert.equal(emptyLevelEs.valid, false);
  assert.match(emptyLevelEs.retryPrompt, /English/i);

  const emptyGoalsEs = await parseOnboardingTurn({ step: "goals", answer: "", chips: [], targetLanguage: "en", nativeLanguage: "es" });
  assert.equal(emptyGoalsEs.valid, false);
  assert.match(emptyGoalsEs.retryPrompt, /aprendiendo/i);
});

test("tutor: AI Friend personas adapt across CEFR levels and any target language (Chinese, Italian, Spanish)", () => {
  assert.deepEqual([...CHATS_FRIEND_ORDER], ["sofia", "mateo", "valeria", "claire", "lucas", "ethan"]);

  // Chinese A1 with Ethan: relaxed without obscure slang + automatic romanization
  const zhA1 = makeProfile({ targetLanguage: "zh", accent: "zh-CN", level: "A1", tutorId: "sofia" });
  const ethanZhA1 = tutorSystem({ kind: "freetalk" }, zhA1, "ethan");
  assert.match(ethanZhA1, /You are Ethan/i);
  assert.match(ethanZhA1, /Persona \(Ethan · Relaxed\)/);
  assert.match(ethanZhA1, /zero obscure slang/i);
  assert.match(ethanZhA1, /Chinese/i);
  assert.match(ethanZhA1, /romanisation/i);
  assert.doesNotMatch(ethanZhA1, /Madrid|Spanish/i);

  // Italian C1 with Ethan: authentic colloquialisms + C1 calibration
  const itC1 = makeProfile({ targetLanguage: "it", accent: "it-IT", level: "C1", tutorId: "sofia" });
  const ethanItC1 = voiceInstructions({ kind: "freetalk" }, itC1, "ethan");
  assert.match(ethanItC1, /You are Ethan/i);
  assert.match(ethanItC1, /Persona \(Ethan · Relaxed\)/);
  assert.match(ethanItC1, /colloquialisms/i);
  assert.match(ethanItC1, /Italian/i);
  assert.match(ethanItC1, /CEFR C1/);

  // Italian B2 with Mateo: language-agnostic banter without hardcoded Madrid/Spanish
  const itB2 = makeProfile({ targetLanguage: "it", accent: "it-IT", level: "B2", tutorId: "sofia" });
  const mateoItB2 = tutorSystem({ kind: "freetalk" }, itB2, "mateo");
  assert.match(mateoItB2, /You are Mateo/i);
  assert.match(mateoItB2, /Persona \(Mateo · Energetic\)/);
  assert.match(mateoItB2, /Italian/i);
  assert.doesNotMatch(mateoItB2, /Madrid/i);

  // Strict scoping: without tutorIdOverride (Home Free Talk / Topics / Roleplays), user's default Sofía stays active with no Chats Persona override
  const defaultFreeTalk = tutorSystem({ kind: "freetalk" }, itB2);
  assert.match(defaultFreeTalk, /You are Sofía/i);
  assert.doesNotMatch(defaultFreeTalk, /Persona \(/);

  // Valeria slow pace metadata & compact prompt length across all 6 personas
  assert.equal(TUTOR_CATALOG.valeria.pace, "slow");
  assert.equal(TUTOR_CATALOG.mateo.pace, "normal");
  for (const id of CHATS_FRIEND_ORDER) {
    const dir = aiFriendDirective(id, "B1", "Italian");
    assert.ok(dir && dir.length < 260, `directive for ${id} should stay compact for zero latency overhead`);
  }
});
