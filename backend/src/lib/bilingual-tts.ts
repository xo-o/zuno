import { cartesiaLanguage } from "./voices.js";

export type SpeechSegment = {
  text: string;
  language?: string;
  speed?: number;
};

const XML_LANG_TAG_RE = /<lang(?:\s+code)?\s*=\s*["']?([a-zA-Z_-]+)["']?\s*>([\s\S]*?)<\/lang>/gi;
const BRACKET_LANG_TAG_RE = /\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[\/\1\]/gi;
const COMBINED_LANG_TAG_RE =
  /(?:<lang(?:\s+code)?\s*=\s*["']?([a-zA-Z_-]+)["']?\s*>([\s\S]*?)<\/lang>|\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[\/\3\])/gi;
const STRAY_TAG_TOKEN_RE =
  /(?:<\/?lang(?:\s+code\s*=\s*["']?[a-zA-Z_-]*["']?|\s*=\s*["']?[a-zA-Z_-]*["']?)?\s*>|\[\/?[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?\])/gi;

const NON_LATIN_CHAR_RE =
  /[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]/;

const NON_LATIN_LANGS = new Set([
  "ar", "bn", "el", "gu", "he", "hi", "ja", "ka", "kn", "ko", "ml",
  "mr", "or", "pa", "ru", "ta", "te", "th", "uk", "ur", "fa", "zh",
]);

/**
 * Strips `<lang code="en">...</lang>`, `<lang="en">...</lang>`, and `[en]...[/en]` tags for UI display.
 */
export function stripLangTags(text: string): string {
  return text
    .replace(XML_LANG_TAG_RE, "$2")
    .replace(BRACKET_LANG_TAG_RE, "$2")
    .replace(STRAY_TAG_TOKEN_RE, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}

/**
 * Strips Latin transliterations in parentheses following non-Latin scripts
 * (e.g. "こんにちは (konnichiwa)" -> "こんにちは") so TTS engines don't pronounce both.
 */
export function stripRomanization(text: string): string {
  return text
    .replace(
      /([\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF])\s*\([A-Za-z0-9\s,.'’\-–—]+\)/g,
      "$1",
    )
    .trim();
}

/**
 * Strips parenthetical grammar or clarification notes like "(plural)", "(formal)", "(feminine)".
 */
export function stripParentheticalNotes(text: string): string {
  return text.replace(/\s*\([A-Za-z0-9\s,.'’\-–—/]+\)/g, "").trim();
}

/**
 * Ensures text sent to Cartesia has clean terminal punctuation, eliminating buffer-delay artifacts.
 */
export function ensureTerminalPunct(text: string, defaultPunct = "."): string {
  let cleaned = text.trim();
  if (!cleaned) return "";
  cleaned = cleaned.replace(/[:\-–—]+$/, "").trim();
  cleaned = cleaned.replace(/\.{3,}$|…$/, "").trim();
  if (!cleaned) return "";
  if (!".?!;:¡¿。？！".includes(cleaned[cleaned.length - 1])) {
    cleaned += defaultPunct;
  }
  return cleaned;
}

/**
 * Resolves a language code and learner accent into an explicit Cartesia regional locale for sonic-3.6.
 */
export function resolveCartesiaLocale(
  language: string | null | undefined,
  accent?: string | null,
  tutorVoice?: string | null,
): string {
  const lang = cartesiaLanguage(language) || "es";
  const acc = (accent || "").toLowerCase().trim();

  if (lang === "es") {
    if (acc === "spain" || acc === "castilian") return "es-ES";
    if (tutorVoice === "calm") return "es-CO";
    return "es-MX";
  }

  if (lang === "en") {
    if (acc === "uk" || acc === "british") return "en-GB";
    if (acc === "au" || acc === "australia" || acc === "australian") return "en-AU";
    return "en-US";
  }

  if (lang === "fr") return acc === "canada" || acc === "canadian" ? "fr-CA" : "fr-FR";
  if (lang === "pt") return acc === "portugal" || acc === "european" ? "pt-PT" : "pt-BR";
  if (lang === "de") return acc === "switzerland" || acc === "swiss" ? "de-CH" : "de-DE";
  if (lang === "nl") return acc === "belgium" || acc === "flemish" ? "nl-BE" : "nl-NL";
  if (lang === "it") return "it-IT";
  if (lang === "ja") return "ja-JP";
  if (lang === "zh") return acc === "taiwan" || acc === "taiwanese" ? "zh-TW" : "zh-CN";
  if (lang === "ko") return "ko-KR";
  if (lang === "ru") return "ru-RU";
  if (lang === "hi") return "hi-IN";
  if (lang === "pl") return "pl-PL";
  if (lang === "tr") return "tr-TR";
  if (lang === "sv") return "sv-SE";
  if (lang === "ar") return "ar-SA";
  if (lang === "id") return "id-ID";
  if (lang === "vi") return "vi-VN";

  return lang;
}

const LEAD_IN_REGEX =
  /^(repeat after me|say after me|listen and repeat|to say this|to say|you can say|try saying|try asking|in english|that means|it means|say|listen|ask):?\s+/i;

const FUNCTION_WORDS: Record<string, Set<string>> = {
  en: new Set([
    "the", "a", "an", "to", "in", "for", "of", "and", "or", "is", "are", "was", "were",
    "say", "after", "repeat", "me", "listen", "this", "that", "you", "your", "with",
    "now", "ask", "if", "please", "means", "mean", "word", "phrase", "try", "can",
    "how", "what", "when", "where", "why", "here", "sure",
  ]),
  es: new Set([
    "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "en", "por", "para",
    "con", "y", "o", "es", "son", "está", "están", "este", "esta", "estos", "estas", "va",
    "al", "cómo", "dónde", "qué", "quién", "cuánto", "cuántos", "disculpe", "gracias",
    "hola", "buenos", "buenas", "quiero", "puedo", "tiene", "tienes", "muy", "pero", "sí",
  ]),
  fr: new Set([
    "le", "la", "les", "un", "une", "des", "du", "de", "en", "pour", "avec", "et", "ou",
    "est", "sont", "ce", "cette", "ces", "comment", "où", "s'il", "vous", "plaît", "merci",
    "bonjour", "salut", "je", "tu", "nous", "oui", "non", "très", "mais",
  ]),
  de: new Set([
    "der", "die", "das", "den", "dem", "ein", "eine", "einen", "einem", "einer", "und", "oder",
    "in", "mit", "für", "zu", "ist", "sind", "bitte", "danke", "wie", "wo", "was", "ich",
    "du", "sie", "wir", "nicht", "auch", "auf", "haben", "möchte",
  ]),
  it: new Set([
    "il", "lo", "la", "i", "gli", "le", "un", "uno", "una", "di", "da", "in", "con", "su",
    "per", "tra", "fra", "e", "o", "è", "sono", "questo", "questa", "grazie", "ciao",
    "buongiorno", "vorrei", "dove", "come", "che", "non",
  ]),
  pt: new Set([
    "o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "em", "no", "na",
    "por", "para", "com", "e", "ou", "é", "são", "este", "esta", "obrigado", "obrigada",
    "olá", "bom", "boa", "você", "eu", "não", "muito", "onde", "como", "quero",
  ]),
};

const DISTINCT_LANG_MARKERS: Record<string, RegExp> = {
  es: /[¿¡ñÑ]/,
  de: /[ßäöüÄÖÜ]/,
  fr: /[œæçÇ]|(?:\b(?:qu'|c'|j'|n'|s'|d'|l'))/i,
  pt: /[ãõçÃÕÇ]/,
};

function scoreLanguageMatch(words: string[], lang: string): number {
  const normLang = lang.toLowerCase().split("-")[0];
  const dict = FUNCTION_WORDS[normLang];
  if (!dict) return 0;
  let matches = 0;
  for (const w of words) {
    const clean = w.toLowerCase().replace(/^[^\p{L}]+|[^\p{L}]+$/gu, "");
    if (dict.has(clean)) matches++;
  }
  return matches;
}

function hasDistinctMarkers(text: string, lang: string): boolean {
  const norm = lang.toLowerCase().split("-")[0];
  const pat = DISTINCT_LANG_MARKERS[norm];
  return pat ? pat.test(text) : false;
}

export function detectClauseLanguage(
  clauseText: string,
  nativeLanguage = "en",
  targetLanguage = "es",
  defaultLanguage?: string,
): string {
  const native = nativeLanguage.toLowerCase().split("-")[0];
  const target = targetLanguage.toLowerCase().split("-")[0];
  const def = (defaultLanguage ?? target).toLowerCase().split("-")[0];

  if (native === target || !clauseText.trim()) return def;

  const hasNonLatin = NON_LATIN_CHAR_RE.test(clauseText);
  const targetNonLatin = NON_LATIN_LANGS.has(target);
  const nativeNonLatin = NON_LATIN_LANGS.has(native);

  if (targetNonLatin && !nativeNonLatin) return hasNonLatin ? target : native;
  if (nativeNonLatin && !targetNonLatin) return hasNonLatin ? native : target;

  if (hasDistinctMarkers(clauseText, target)) return target;
  if (hasDistinctMarkers(clauseText, native) && !hasDistinctMarkers(clauseText, target)) return native;
  if (LEAD_IN_REGEX.test(clauseText.trim())) return native;

  const words = clauseText.split(/\s+/);
  const nativeScore = scoreLanguageMatch(words, native);
  const targetScore = scoreLanguageMatch(words, target);

  if (nativeScore >= 2 && targetScore === 0) return native;
  if (nativeScore >= 3 && nativeScore >= targetScore * 3) return native;
  if (targetScore >= 1) return target;

  return def;
}

function segmentSingleUntaggedSentence(
  sentence: string,
  native: string,
  target: string,
  defaultLang: string,
  speed: number,
): SpeechSegment[] {
  const text = sentence.trim();
  if (!text) return [];

  // 1. Double-quoted target phrase: e.g. Repeat after me: "Un elefante"
  const doubleQuotePattern = /(["“][^"”]{2,}?["”])/g;
  if (doubleQuotePattern.test(text) && native !== target) {
    const parts = text.split(/(["“][^"”]{2,}?["”])/).filter(Boolean);
    if (parts.length > 1) {
      const segments: SpeechSegment[] = [];
      for (const part of parts) {
        const trimmed = part.trim();
        if (!trimmed) continue;
        const isDoubleQuoted =
          (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
          (trimmed.startsWith("“") && trimmed.endsWith("”"));
        if (isDoubleQuoted) {
          const cleanText = trimmed.replace(/^[“"]|[”"]$/g, "").trim();
          if (cleanText) {
            segments.push({
              text: cleanText,
              language: detectClauseLanguage(cleanText, native, target, target),
              speed,
            });
          }
        } else {
          const cleanLeadIn = trimmed.replace(/:\s*$/, "").trim();
          if (cleanLeadIn) {
            segments.push({
              text: trimmed,
              language: detectClauseLanguage(cleanLeadIn, native, target, native),
              speed,
            });
          }
        }
      }
      if (segments.length > 0) return mergeAdjacentSegments(segments);
    }
  }

  // 2. Lead-in colons: e.g. "Repeat after me: Un elefante"
  const colonIdx = text.indexOf(":");
  if (colonIdx > 0 && colonIdx < text.length - 1 && native !== target) {
    const left = text.slice(0, colonIdx).trim();
    const right = text.slice(colonIdx + 1).trim();
    if (left && right && right.length >= 2 && (LEAD_IN_REGEX.test(left + ": ") || left.split(/\s+/).length <= 6)) {
      const leftWords = left.split(/\s+/);
      const rightWords = right.split(/\s+/);
      const leftNativeScore = scoreLanguageMatch(leftWords, native);
      const rightTargetScore = scoreLanguageMatch(rightWords, target);

      if (LEAD_IN_REGEX.test(left + ": ") || leftNativeScore > 0 || rightTargetScore > 0) {
        return [
          { text: left, language: native, speed },
          { text: right, language: target, speed },
        ];
      }
    }
  }

  // 3. Distinct non-Latin script blocks
  const isNonLatin = NON_LATIN_CHAR_RE.test(text);
  if (isNonLatin && NON_LATIN_LANGS.has(native) !== NON_LATIN_LANGS.has(target)) {
    const nonLatinRegex =
      /([\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]+(?:[\s\u3000-\u303F\uFF00-\uFFEF\p{P}]*[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]+)*)/gu;
    const pieces = text.split(nonLatinRegex).filter(Boolean);
    if (pieces.length > 1) {
      const nonLatinLang = NON_LATIN_LANGS.has(target) ? target : native;
      const latinLang = nonLatinLang === target ? native : target;
      const segments: SpeechSegment[] = [];
      for (const piece of pieces) {
        const clean = piece.trim();
        if (!clean || !/\p{L}|\p{N}/u.test(clean)) continue;
        const hasNonLatin = NON_LATIN_CHAR_RE.test(clean);
        segments.push({
          text: clean,
          language: hasNonLatin ? nonLatinLang : latinLang,
          speed,
        });
      }
      if (segments.length > 0) return mergeAdjacentSegments(segments);
    }
  }

  return [{ text, language: detectClauseLanguage(text, native, target, defaultLang), speed }];
}

/**
 * Splits text into SpeechSegments, each with the proper language code.
 */
export function segmentBilingualText(
  rawText: string,
  options: {
    nativeLanguage?: string;
    targetLanguage?: string;
    defaultLanguage?: string;
    speed?: number;
  } = {},
): SpeechSegment[] {
  const text = stripRomanization(rawText).trim();
  if (!text) return [];

  const native = options.nativeLanguage?.toLowerCase().split("-")[0] || "en";
  const target =
    options.targetLanguage?.toLowerCase().split("-")[0] ||
    options.defaultLanguage?.toLowerCase().split("-")[0] ||
    "es";
  const defaultLang = options.defaultLanguage?.toLowerCase().split("-")[0] ?? target;
  const speed = options.speed ?? 1.0;

  // 1. Explicit `<lang code="xx">`, `<lang="xx">`, or `[xx]` tags
  COMBINED_LANG_TAG_RE.lastIndex = 0;
  if (COMBINED_LANG_TAG_RE.test(text)) {
    COMBINED_LANG_TAG_RE.lastIndex = 0;
    const segments: SpeechSegment[] = [];
    let lastIdx = 0;
    let match: RegExpExecArray | null;

    while ((match = COMBINED_LANG_TAG_RE.exec(text)) !== null) {
      if (match.index > lastIdx) {
        const untagged = text.slice(lastIdx, match.index).replace(STRAY_TAG_TOKEN_RE, "").trim();
        if (untagged) {
          segments.push(
            ...segmentBilingualText(untagged, {
              nativeLanguage: native,
              targetLanguage: target,
              defaultLanguage: defaultLang,
              speed,
            }),
          );
        }
      }
      const lang = (match[1] || match[3]).toLowerCase().split("-")[0];
      const content = (match[2] || match[4]).replace(STRAY_TAG_TOKEN_RE, "").trim();
      if (content) {
        segments.push({ text: content, language: lang, speed });
      }
      lastIdx = COMBINED_LANG_TAG_RE.lastIndex;
    }
    if (lastIdx < text.length) {
      const rest = text.slice(lastIdx).replace(STRAY_TAG_TOKEN_RE, "").trim();
      if (rest) {
        segments.push(
          ...segmentBilingualText(rest, {
            nativeLanguage: native,
            targetLanguage: target,
            defaultLanguage: defaultLang,
            speed,
          }),
        );
      }
    }
    return mergeAdjacentSegments(segments);
  }

  const cleaned = text.replace(STRAY_TAG_TOKEN_RE, "").trim();
  if (!cleaned) return [];

  // 2. Multi-sentence split for untagged bilingual responses
  const sentences = cleaned
    .split(/(?<=[.?!。？！])\s+/)
    .map((s) => s.trim())
    .filter(Boolean);
  if (sentences.length > 1) {
    const all: SpeechSegment[] = [];
    for (const s of sentences) {
      all.push(...segmentSingleUntaggedSentence(s, native, target, defaultLang, speed));
    }
    return mergeAdjacentSegments(all);
  }

  return segmentSingleUntaggedSentence(cleaned, native, target, defaultLang, speed);
}

function mergeAdjacentSegments(segments: SpeechSegment[]): SpeechSegment[] {
  if (segments.length <= 1) return segments;
  const merged: SpeechSegment[] = [];
  for (const seg of segments) {
    const prev = merged[merged.length - 1];
    if (prev && prev.language === seg.language && prev.speed === seg.speed) {
      prev.text = `${prev.text} ${seg.text}`.trim();
    } else {
      merged.push({ ...seg });
    }
  }
  return merged;
}

function createWavHeader(dataLength: number, sampleRate: number): Uint8Array {
  const header = new Uint8Array(44);
  const view = new DataView(header.buffer);

  header[0] = 0x52; header[1] = 0x49; header[2] = 0x46; header[3] = 0x46;
  view.setUint32(4, 36 + dataLength, true);
  header[8] = 0x57; header[9] = 0x41; header[10] = 0x56; header[11] = 0x45;
  header[12] = 0x66; header[13] = 0x6d; header[14] = 0x74; header[15] = 0x20;
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 1 * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  header[36] = 0x64; header[37] = 0x61; header[38] = 0x74; header[39] = 0x61;
  view.setUint32(40, dataLength, true);

  return header;
}

export function buildWavFromPcmSegments(
  pcmBuffers: Uint8Array[],
  sampleRate = 24000,
  pauseMs = 180,
): Uint8Array {
  const nonNullBuffers = pcmBuffers.filter((b) => b.byteLength > 0);
  if (nonNullBuffers.length === 0) {
    return createWavHeader(0, sampleRate);
  }

  const silenceSamples = Math.round((sampleRate * pauseMs) / 1000);
  const silenceBytes = silenceSamples * 2;
  const silenceBuffer = new Uint8Array(silenceBytes);

  let totalDataLength = 0;
  nonNullBuffers.forEach((buf, idx) => {
    totalDataLength += buf.byteLength;
    if (idx < nonNullBuffers.length - 1) {
      totalDataLength += silenceBytes;
    }
  });

  const header = createWavHeader(totalDataLength, sampleRate);
  const result = new Uint8Array(44 + totalDataLength);
  result.set(header, 0);

  let offset = 44;
  nonNullBuffers.forEach((buf, idx) => {
    result.set(buf, offset);
    offset += buf.byteLength;
    if (idx < nonNullBuffers.length - 1) {
      result.set(silenceBuffer, offset);
      offset += silenceBytes;
    }
  });

  return result;
}
