export const LANGUAGE_CODES = ["es", "en"] as const;
export type LanguageCode = (typeof LANGUAGE_CODES)[number];

export const LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"] as const;
export type Level = (typeof LEVELS)[number];

export const LANGUAGE_NAMES: Record<LanguageCode, string> = {
  es: "Spanish",
  en: "English",
};

/** Until more languages ship, the native language is always the other supported one. */
export function nativeLanguageFor(target: LanguageCode): LanguageCode {
  return target === "es" ? "en" : "es";
}

export function isLanguageCode(value: unknown): value is LanguageCode {
  return typeof value === "string" && (LANGUAGE_CODES as readonly string[]).includes(value);
}
