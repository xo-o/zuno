export type Accent = { id: string; label: string };

type LanguageInfo = {
  englishName: string;
  nativeName: string;
  accents: Accent[];
  rtl?: boolean;
  /** Non-Latin script: beginners get romanisation hints. */
  romanize?: boolean;
};

const STANDARD: Accent[] = [{ id: "standard", label: "Standard" }];

/**
 * Languages a learner can study (Settings · Learning language). Only languages the whole voice pipeline
 * supports are listed — Deepgram Nova-3 speech-to-text and Breeze TTS 2 multilingual — so Swahili (no
 * Nova-3) and Cantonese (no Breeze voice) are left out. The first accent is the default.
 */
export const LANGUAGES = {
  ar: {
    englishName: "Arabic",
    nativeName: "العربية",
    accents: [
      { id: "msa", label: "Modern Standard" },
      { id: "egyptian", label: "Egyptian" },
      { id: "levantine", label: "Levantine" },
      { id: "gulf", label: "Gulf" },
    ],
    rtl: true,
    romanize: true,
  },
  bn: {
    englishName: "Bengali",
    nativeName: "বাংলা",
    accents: [
      { id: "bangladesh", label: "Bangladesh" },
      { id: "india", label: "India (West Bengal)" },
    ],
    romanize: true,
  },
  zh: {
    englishName: "Chinese (Mandarin)",
    nativeName: "中文（普通话）",
    accents: [
      { id: "mainland", label: "Mainland" },
      { id: "taiwan", label: "Taiwan" },
    ],
    romanize: true,
  },
  cs: { englishName: "Czech", nativeName: "Čeština", accents: STANDARD },
  da: { englishName: "Danish", nativeName: "Dansk", accents: STANDARD },
  nl: {
    englishName: "Dutch",
    nativeName: "Nederlands",
    accents: [
      { id: "netherlands", label: "Netherlands" },
      { id: "belgium", label: "Belgium (Flemish)" },
    ],
  },
  en: {
    englishName: "English",
    nativeName: "English",
    accents: [
      { id: "us", label: "US" },
      { id: "uk", label: "UK" },
      { id: "australia", label: "Australia" },
    ],
  },
  fi: { englishName: "Finnish", nativeName: "Suomi", accents: STANDARD },
  fr: {
    englishName: "French",
    nativeName: "Français",
    accents: [
      { id: "france", label: "France" },
      { id: "canada", label: "Canada" },
    ],
  },
  de: {
    englishName: "German",
    nativeName: "Deutsch",
    accents: [
      { id: "germany", label: "Germany" },
      { id: "austria", label: "Austria" },
      { id: "switzerland", label: "Switzerland" },
    ],
  },
  el: { englishName: "Greek", nativeName: "Ελληνικά", accents: STANDARD, romanize: true },
  he: { englishName: "Hebrew", nativeName: "עברית", accents: STANDARD, rtl: true, romanize: true },
  hi: { englishName: "Hindi", nativeName: "हिन्दी", accents: STANDARD, romanize: true },
  hu: { englishName: "Hungarian", nativeName: "Magyar", accents: STANDARD },
  id: { englishName: "Indonesian", nativeName: "Bahasa Indonesia", accents: STANDARD },
  it: { englishName: "Italian", nativeName: "Italiano", accents: STANDARD },
  ja: { englishName: "Japanese", nativeName: "日本語", accents: [{ id: "tokyo", label: "Standard (Tokyo)" }], romanize: true },
  ko: { englishName: "Korean", nativeName: "한국어", accents: [{ id: "seoul", label: "Standard (Seoul)" }], romanize: true },
  ms: { englishName: "Malay", nativeName: "Bahasa Melayu", accents: [{ id: "malaysia", label: "Malaysia" }] },
  no: { englishName: "Norwegian", nativeName: "Norsk", accents: [{ id: "bokmal", label: "Bokmål" }] },
  fa: { englishName: "Persian", nativeName: "فارسی", accents: [{ id: "iran", label: "Iran" }], rtl: true, romanize: true },
  pl: { englishName: "Polish", nativeName: "Polski", accents: STANDARD },
  pt: {
    englishName: "Portuguese",
    nativeName: "Português",
    accents: [
      { id: "brazil", label: "Brazil" },
      { id: "portugal", label: "Portugal" },
    ],
  },
  ro: { englishName: "Romanian", nativeName: "Română", accents: STANDARD },
  ru: { englishName: "Russian", nativeName: "Русский", accents: STANDARD, romanize: true },
  es: {
    englishName: "Spanish",
    nativeName: "Español",
    accents: [
      { id: "latam", label: "Latin America" },
      { id: "spain", label: "Spain" },
    ],
  },
  sv: { englishName: "Swedish", nativeName: "Svenska", accents: STANDARD },
  tl: { englishName: "Tagalog", nativeName: "Tagalog", accents: [{ id: "philippines", label: "Philippines" }] },
  th: { englishName: "Thai", nativeName: "ไทย", accents: STANDARD, romanize: true },
  tr: { englishName: "Turkish", nativeName: "Türkçe", accents: STANDARD },
  uk: { englishName: "Ukrainian", nativeName: "Українська", accents: STANDARD, romanize: true },
  ur: { englishName: "Urdu", nativeName: "اردو", accents: [{ id: "pakistan", label: "Pakistan" }], rtl: true, romanize: true },
  vi: {
    englishName: "Vietnamese",
    nativeName: "Tiếng Việt",
    accents: [
      { id: "north", label: "Northern (Hanoi)" },
      { id: "south", label: "Southern (Saigon)" },
    ],
  },
} satisfies Record<string, LanguageInfo>;

export type LanguageCode = keyof typeof LANGUAGES;
export const LANGUAGE_CODES = Object.keys(LANGUAGES) as [LanguageCode, ...LanguageCode[]];

export const APP_LANGUAGE_CODES = ["en", "es", "fr", "de", "it", "pt", "nl", "pl"] as const;
export type AppLanguageCode = (typeof APP_LANGUAGE_CODES)[number];

export const LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"] as const;
export type Level = (typeof LEVELS)[number];

/** English name of any learning or app language, for prompts. */
export function languageName(code: string): string {
  return (LANGUAGES as Record<string, LanguageInfo>)[code]?.englishName ?? code;
}

export function languageInfo(code: LanguageCode): LanguageInfo {
  return LANGUAGES[code];
}

export function defaultAccent(code: LanguageCode): string {
  return LANGUAGES[code].accents[0].id;
}

export function isAccentOf(code: LanguageCode, accent: string): boolean {
  return LANGUAGES[code].accents.some((item) => item.id === accent);
}

export function accentLabel(code: LanguageCode, accent: string | null | undefined): string {
  const accents: Accent[] = LANGUAGES[code].accents;
  return (accents.find((item) => item.id === accent) ?? accents[0]).label;
}

/** Public list for GET /api/languages, A→Z by English name. */
export function languageList() {
  return LANGUAGE_CODES.map((code) => {
    const info: LanguageInfo = LANGUAGES[code];
    return {
      code,
      englishName: info.englishName,
      nativeName: info.nativeName,
      accents: info.accents,
      defaultAccent: info.accents[0].id,
      rtl: info.rtl ?? false,
    };
  }).sort((a, b) => a.englishName.localeCompare(b.englishName));
}

export function isLanguageCode(value: unknown): value is LanguageCode {
  return typeof value === "string" && Object.hasOwn(LANGUAGES, value);
}

export function isAppLanguageCode(value: unknown): value is AppLanguageCode {
  return typeof value === "string" && (APP_LANGUAGE_CODES as readonly string[]).includes(value);
}
