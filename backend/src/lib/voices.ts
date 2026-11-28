export const TUTOR_IDS = ["sofia", "valeria", "claire", "mateo", "lucas", "ethan"] as const;
export const CHATS_FRIEND_ORDER: readonly TutorId[] = ["sofia", "mateo", "valeria", "claire", "lucas", "ethan"] as const;
export const TUTOR_VOICES = ["warm", "calm", "crisp", "energetic", "steady", "casual"] as const;

export type TutorId = (typeof TUTOR_IDS)[number];
export type TutorVoice = (typeof TUTOR_VOICES)[number];
export type LevelBand = "beginner" | "intermediate" | "advanced";

export type TutorSpec = {
  id: TutorId;
  name: string;
  voicePreset: TutorVoice;
  voiceId: string;
  gender: "female" | "male";
  tagline: string;
  traitLabel: string;
  chatSubtitle: string;
  pace: "slow" | "normal";
};

export const DEFAULT_ELEVENLABS_MODEL = "eleven_v4_turbo";
export const FALLBACK_PREMADE_VOICE_ID = "hpp4J3VqNfWAUOO0d1Us"; // Bella

/**
 * Curated AI Tutors with strict 1:1 avatar-to-voice mapping.
 * Powered by ElevenLabs `eleven_v4_turbo` (90+ languages with automatic code-switching) so each tutor
 * speaks both `nativeLanguage` and `targetLanguage` naturally in a single pass.
 */
export const TUTOR_CATALOG: Record<TutorId, TutorSpec> = {
  sofia: {
    id: "sofia",
    name: "Sofía",
    voicePreset: "warm",
    voiceId: "DODLEQrClDo8wCz460ld", // Lauren - Friendly, Comforting and Soft
    gender: "female",
    tagline: "Warm & friendly",
    traitLabel: "Warm",
    chatSubtitle: "Everyday chat & travel",
    pace: "normal",
  },
  valeria: {
    id: "valeria",
    name: "Valeria",
    voicePreset: "calm",
    voiceId: "EXAVITQu4vr4xnSDxMaL", // Sarah - Mature, Reassuring, Confident
    gender: "female",
    tagline: "Calm & patient",
    traitLabel: "Patient",
    chatSubtitle: "Slow pace & gentle tips",
    pace: "slow",
  },
  claire: {
    id: "claire",
    name: "Claire",
    voicePreset: "crisp",
    voiceId: "Xb7hH8MSUJpSbSDYk0k2", // Alice - Clear, Engaging Educator
    gender: "female",
    tagline: "Crisp & clear",
    traitLabel: "Crisp",
    chatSubtitle: "Work, tech & interviews",
    pace: "normal",
  },
  mateo: {
    id: "mateo",
    name: "Mateo",
    voicePreset: "energetic",
    voiceId: "TX3LPaxmHKxFdv7VOQHJ", // Liam - Energetic
    gender: "male",
    tagline: "Energetic & upbeat",
    traitLabel: "Energetic",
    chatSubtitle: "Sports, banter & city life",
    pace: "normal",
  },
  lucas: {
    id: "lucas",
    name: "Lucas",
    voicePreset: "steady",
    voiceId: "cjVigY5qzO86Huf0OWal", // Eric - Smooth, Trustworthy
    gender: "male",
    tagline: "Steady & articulate",
    traitLabel: "Thoughtful",
    chatSubtitle: "News, culture & stories",
    pace: "normal",
  },
  ethan: {
    id: "ethan",
    name: "Ethan",
    voicePreset: "casual",
    voiceId: "bIHbv24MWmeRgasZH58o", // Will - Relaxed Optimist
    gender: "male",
    tagline: "Casual & relaxed",
    traitLabel: "Relaxed",
    chatSubtitle: "Casual slang & daily life",
    pace: "normal",
  },
};

export function levelBandFor(level?: string | null): LevelBand {
  const norm = (level ?? "B1").trim().toUpperCase();
  if (norm === "A1" || norm === "A2") return "beginner";
  if (norm === "C1" || norm === "C2") return "advanced";
  return "intermediate";
}

/**
 * Compact, language-agnostic AI Friend directives indexed by (tutorId, levelBand).
 * O(1) in-memory lookup with ~25–35 tokens so CEFR + personality adaptation adds zero latency.
 */
const AI_FRIEND_DIRECTIVES: Record<TutorId, Record<LevelBand, string>> = {
  sofia: {
    beginner: "Warm, encouraging everyday & travel vibe; stick to simple high-frequency daily phrases and gentle questions.",
    intermediate: "Warm, approachable everyday & travel friend; share natural trip and daily-life follow-ups.",
    advanced: "Warm, worldly everyday & travel companion; chat with rich local nuance and effortless flow.",
  },
  mateo: {
    beginner: "Upbeat, playful city & sports energy; keep banter short, clear, and free of tricky idioms.",
    intermediate: "Upbeat, lively banter about sports, weekend plans, and city life with playful follow-ups.",
    advanced: "Fast, witty city & sports banter with authentic local energy, humor, and idiomatic punch.",
  },
  valeria: {
    beginner: "Unhurried, extra-patient tone; use very short clear sentences, pause-friendly phrasing, and reassuring tips.",
    intermediate: "Calm, patient pacing with supportive, natural phrasing tips that build speaking confidence.",
    advanced: "Composed, thoughtful tone; match advanced vocabulary while offering crisp, polished phrasing refinements.",
  },
  claire: {
    beginner: "Crisp, organized tone focused on simple work introductions, schedules, and clear basics.",
    intermediate: "Sharp, structured partner for work updates, tech discussions, and practical interview answers.",
    advanced: "Executive, articulate tone for nuanced work trade-offs, technical depth, and persuasive negotiation.",
  },
  lucas: {
    beginner: "Steady, curious tone; chat about films, food, and everyday culture using short, clear sentences.",
    intermediate: "Thoughtful, articulate storyteller; explore news, culture, and opinions with engaging questions.",
    advanced: "Reflective, eloquent conversationalist for cultural commentary, current affairs, and abstract debate.",
  },
  ethan: {
    beginner: "Laid-back, friendly daily-life vibe; use simple approachable casual phrasing with zero obscure slang.",
    intermediate: "Relaxed, conversational friend using common everyday informal expressions and daily-life humor.",
    advanced: "Easygoing, authentic friend weaving in natural local colloquialisms, casual slang, and effortless register.",
  },
};

export function aiFriendDirective(
  tutorId: string | null | undefined,
  level: string | null | undefined,
  varietyName: string,
): string | null {
  if (!tutorId) return null;
  const spec = resolveTutor(tutorId);
  const band = levelBandFor(level);
  return `Persona (${spec.name} · ${spec.traitLabel}): ${AI_FRIEND_DIRECTIVES[spec.id][band]} Adapt naturally to ${varietyName} culture and strictly respect CEFR ${level || "B1"} complexity.`;
}

export const ELEVENLABS_VOICES: Record<TutorVoice, string> = {
  warm: TUTOR_CATALOG.sofia.voiceId,
  calm: TUTOR_CATALOG.valeria.voiceId,
  crisp: TUTOR_CATALOG.claire.voiceId,
  energetic: TUTOR_CATALOG.mateo.voiceId,
  steady: TUTOR_CATALOG.lucas.voiceId,
  casual: TUTOR_CATALOG.ethan.voiceId,
};

const VOICE_PRESET_TO_TUTOR_ID: Record<TutorVoice, TutorId> = {
  warm: "sofia",
  calm: "valeria",
  crisp: "claire",
  energetic: "mateo",
  steady: "lucas",
  casual: "ethan",
};

export function resolveTutor(tutorId?: string | null, tutorVoice?: string | null): TutorSpec {
  if (tutorId) {
    const normId = tutorId.trim().toLowerCase();
    if (normId === "elena") return TUTOR_CATALOG.sofia;
    if ((TUTOR_IDS as readonly string[]).includes(normId)) {
      return TUTOR_CATALOG[normId as TutorId];
    }
  }
  if (tutorVoice) {
    const normVoice = tutorVoice.trim().toLowerCase();
    if ((TUTOR_VOICES as readonly string[]).includes(normVoice)) {
      return TUTOR_CATALOG[VOICE_PRESET_TO_TUTOR_ID[normVoice as TutorVoice]];
    }
  }
  return TUTOR_CATALOG.sofia;
}

export function elevenLabsVoiceFor(tutorVoiceOrId: string | null | undefined, tutorId?: string | null): string {
  return resolveTutor(tutorId ?? tutorVoiceOrId, tutorVoiceOrId).voiceId;
}
