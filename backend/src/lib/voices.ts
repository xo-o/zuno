import type { TutorVoice } from "./profile.js";

/**
 * Curated Cartesia Sonic-3 multilingual voices.
 * Every voice below natively speaks across 23+ locales so Zuno keeps the exact same
 * vocal identity and tone when switching between `nativeLanguage` and `targetLanguage`.
 */
export const CARTESIA_MULTILINGUAL_VOICES: Record<TutorVoice, string> = {
  warm: "ec1e269e-9ca0-402f-8a18-58e0e022355a", // Ariana - Kind Friend
  calm: "ae823354-f9be-4aef-8543-f569644136b4", // Mariana - Nurturing Guide
  energetic: "4df027cb-2920-4a1f-8c34-f21529d5c3fe", // Carson - Friendly Support
};

/** Explicit language codes accepted by Cartesia `sonic-3` (`2025-04-16`). */
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

export function cartesiaVoiceFor(tutorVoice: string | null | undefined): string {
  if (tutorVoice === "calm" || tutorVoice === "energetic" || tutorVoice === "warm") {
    return CARTESIA_MULTILINGUAL_VOICES[tutorVoice];
  }
  return CARTESIA_MULTILINGUAL_VOICES.warm;
}

/**
 * Returns the explicit `sonic-3` language code when supported, or `undefined`
 * so Cartesia auto-detects scripts such as Persian (`fa`) or Urdu (`ur`).
 */
export function cartesiaLanguage(code: string | null | undefined): string | undefined {
  if (!code) return undefined;
  const normalized = code.trim().toLowerCase().split("-")[0];
  return CARTESIA_SONIC3_LANGS.has(normalized) ? normalized : undefined;
}
