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

/**
 * Curated AI Tutors with strict 1:1 avatar-to-voice mapping.
 * Every Cartesia Sonic-3.6 voice below speaks natively across 23+ locales so each tutor
 * keeps their exact vocal identity across `nativeLanguage` and `targetLanguage`.
 */
export const TUTOR_CATALOG: Record<TutorId, TutorSpec> = {
  sofia: {
    id: "sofia",
    name: "Sofía",
    voicePreset: "warm",
    voiceId: "ec1e269e-9ca0-402f-8a18-58e0e022355a", // Ariana - Kind Friend
    gender: "female",
    tagline: "Warm & friendly",
  },
  valeria: {
    id: "valeria",
    name: "Valeria",
    voicePreset: "calm",
    voiceId: "ae823354-f9be-4aef-8543-f569644136b4", // Mariana - Nurturing Guide
    gender: "female",
    tagline: "Calm & patient",
  },
  claire: {
    id: "claire",
    name: "Claire",
    voicePreset: "crisp",
    voiceId: "f786b574-daa5-4673-aa0c-cbe3e8534c02", // Katie - Friendly Fixer
    gender: "female",
    tagline: "Crisp & clear",
  },
  mateo: {
    id: "mateo",
    name: "Mateo",
    voicePreset: "energetic",
    voiceId: "4df027cb-2920-4a1f-8c34-f21529d5c3fe", // Carson - Friendly Support
    gender: "male",
    tagline: "Energetic & upbeat",
  },
  lucas: {
    id: "lucas",
    name: "Lucas",
    voicePreset: "steady",
    voiceId: "8c254787-4eb4-4577-bd3d-fb3c273baea2", // Rowan - Steady Guide
    gender: "male",
    tagline: "Steady & articulate",
  },
  ethan: {
    id: "ethan",
    name: "Ethan",
    voicePreset: "casual",
    voiceId: "30894953-bcce-41fe-892c-15ce19c843ff", // Parker - Supportive Pal
    gender: "male",
    tagline: "Casual & relaxed",
  },
};

export const CARTESIA_MULTILINGUAL_VOICES: Record<TutorVoice, string> = {
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

export function cartesiaVoiceFor(tutorVoiceOrId: string | null | undefined, tutorId?: string | null): string {
  return resolveTutor(tutorId ?? tutorVoiceOrId, tutorVoiceOrId).voiceId;
}

/** Explicit language codes accepted by Cartesia `sonic-3` / `sonic-3.6`. */
export const CARTESIA_SONIC3_LANGS = new Set([
  "ar",
  "bg",
  "bn",
  "cs",
  "da",
  "de",
  "el",
  "en",
  "es",
  "fi",
  "fr",
  "gu",
  "he",
  "hi",
  "hr",
  "hu",
  "id",
  "it",
  "ja",
  "ka",
  "kn",
  "ko",
  "ml",
  "mr",
  "ms",
  "nl",
  "no",
  "or",
  "pa",
  "pl",
  "pt",
  "ro",
  "ru",
  "sk",
  "sv",
  "ta",
  "te",
  "th",
  "tl",
  "tr",
  "uk",
  "vi",
  "zh",
]);

/**
 * Returns the explicit `sonic-3` language code when supported, or `undefined`
 * so Cartesia auto-detects scripts such as Persian (`fa`) or Urdu (`ur`).
 */
export function cartesiaLanguage(code: string | null | undefined): string | undefined {
  if (!code) return undefined;
  const normalized = code.trim().toLowerCase().split("-")[0];
  return CARTESIA_SONIC3_LANGS.has(normalized) ? normalized : undefined;
}
