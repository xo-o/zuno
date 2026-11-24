export const TUTOR_IDS = ["sofia", "valeria", "claire", "mateo", "lucas", "ethan"] as const;
export const TUTOR_VOICES = ["warm", "calm", "crisp", "energetic", "steady", "casual"] as const;

export type TutorId = (typeof TUTOR_IDS)[number];
export type TutorVoice = (typeof TUTOR_VOICES)[number];

export type TutorSpec = {
  id: TutorId;
  name: string;
  voicePreset: TutorVoice;
  voiceId: string;
  gender: "female" | "male";
  tagline: string;
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
  },
  valeria: {
    id: "valeria",
    name: "Valeria",
    voicePreset: "calm",
    voiceId: "EXAVITQu4vr4xnSDxMaL", // Sarah - Mature, Reassuring, Confident
    gender: "female",
    tagline: "Calm & patient",
  },
  claire: {
    id: "claire",
    name: "Claire",
    voicePreset: "crisp",
    voiceId: "Xb7hH8MSUJpSbSDYk0k2", // Alice - Clear, Engaging Educator
    gender: "female",
    tagline: "Crisp & clear",
  },
  mateo: {
    id: "mateo",
    name: "Mateo",
    voicePreset: "energetic",
    voiceId: "TX3LPaxmHKxFdv7VOQHJ", // Liam - Energetic
    gender: "male",
    tagline: "Energetic & upbeat",
  },
  lucas: {
    id: "lucas",
    name: "Lucas",
    voicePreset: "steady",
    voiceId: "cjVigY5qzO86Huf0OWal", // Eric - Smooth, Trustworthy
    gender: "male",
    tagline: "Steady & articulate",
  },
  ethan: {
    id: "ethan",
    name: "Ethan",
    voicePreset: "casual",
    voiceId: "bIHbv24MWmeRgasZH58o", // Will - Relaxed Optimist
    gender: "male",
    tagline: "Casual & relaxed",
  },
};

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
