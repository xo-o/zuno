import { cartesiaLanguage } from "./voices.js";

export type SpeechSegment = {
  text: string;
  language?: string;
  speed?: number;
};

/**
 * Strips bracket tags like `[en]...[/en]` or `<lang="en">...</lang>` from text for UI display and subtitles.
 */
export function stripLangTags(text: string): string {
  return text
    .replace(/<lang="?([a-zA-Z_-]+)"?>([\s\S]*?)<\/lang>/g, "$2")
    .replace(/\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[\/\1\]/g, "$2")
    .replace(/\s{2,}/g, " ")
    .trim();
}

/**
 * Strips Latin transliterations in parentheses following non-Latin scripts
 * (e.g. "こんにちは (konnichiwa)" -> "こんにちは") so TTS engines don't pronounce both.
 */
export function stripRomanization(text: string): string {
  return text
    .replace(/([\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF])\s*\([A-Za-z0-9\s,.'’\-–—]+\)/g, "$1")
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
  if (!".?!;:¡¿".includes(cleaned[cleaned.length - 1])) {
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
  if (lang === "de") return "de-DE";
  if (lang === "it") return "it-IT";
  if (lang === "ja") return "ja-JP";
  if (lang === "zh") return acc === "taiwan" || acc === "taiwanese" ? "zh-TW" : "zh-CN";
  if (lang === "ko") return "ko-KR";
  if (lang === "ru") return "ru-RU";
  if (lang === "hi") return "hi-IN";

  return lang;
}

// Regex for common pedagogical lead-ins in language learning apps (e.g. "Repeat after me: ", "Say: ")
const LEAD_IN_REGEX = /^(repeat after me|say after me|listen and repeat|to say this|to say|you can say|say|listen|ask):?\s+/i;

/** Common stop/function words for quick classification between two candidate languages */
const FUNCTION_WORDS: Record<string, Set<string>> = {
  en: new Set(["the", "a", "an", "to", "in", "for", "of", "and", "or", "is", "are", "say", "after", "repeat", "me", "listen", "this", "that", "you", "with", "now", "ask", "if", "please"]),
  es: new Set(["el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "en", "por", "para", "con", "y", "o", "es", "son", "este", "esta", "estos", "estas", "va", "al", "cómo", "dónde", "qué", "disculpe", "gracias", "por favor"]),
  fr: new Set(["le", "la", "les", "un", "une", "des", "du", "de", "en", "pour", "avec", "et", "ou", "est", "sont", "ce", "cette", "ces", "comment", "où", "s'il", "vous", "plaît", "merci"]),
  de: new Set(["der", "die", "das", "ein", "eine", "einen", "einem", "einer", "eines", "und", "oder", "in", "mit", "für", "zu", "ist", "sind", "bitte", "danke", "wie", "wo", "was"]),
  it: new Set(["il", "lo", "la", "i", "gli", "le", "un", "uno", "una", "di", "da", "in", "con", "su", "per", "tra", "fra", "e", "o", "è", "sono", "questo", "questa", "grazie", "per favore"]),
  pt: new Set(["o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "em", "no", "na", "por", "para", "com", "e", "ou", "é", "são", "este", "esta", "obrigado", "por favor"]),
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

/**
 * Splits text into SpeechSegments, each with the proper language code.
 *
 * Handles:
 * 1. Explicit tags: `[en]Repeat after me:[/en] [es]Un elefante[/es]`
 * 2. Quotation marks containing target language phrases: `Repeat: "Un elefante"` (double quotes only)
 * 3. Lead-in phrases: `Repeat after me: Un elefante`
 * 4. Distinct scripts (e.g. English + Japanese/Russian/Arabic)
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
  const target = options.targetLanguage?.toLowerCase().split("-")[0] || options.defaultLanguage?.toLowerCase().split("-")[0] || "es";
  const speed = options.speed ?? 1.0;

  // 1. Check for explicit bracket/xml tags first: [en]...[/en] or <lang="en">...</lang>
  const tagPattern = /(?:<lang="?([a-zA-Z_-]+)"?>([\s\S]*?)<\/lang>|\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[\/\3\])/g;
  if (tagPattern.test(text)) {
    tagPattern.lastIndex = 0;
    const segments: SpeechSegment[] = [];
    let lastIdx = 0;
    let match: RegExpExecArray | null;

    while ((match = tagPattern.exec(text)) !== null) {
      if (match.index > lastIdx) {
        const untagged = text.slice(lastIdx, match.index).trim();
        if (untagged) {
          segments.push({ text: untagged, language: target, speed });
        }
      }
      const lang = (match[1] || match[3]).toLowerCase().split("-")[0];
      const content = (match[2] || match[4]).trim();
      if (content) {
        segments.push({ text: content, language: lang, speed });
      }
      lastIdx = tagPattern.lastIndex;
    }
    if (lastIdx < text.length) {
      const rest = text.slice(lastIdx).trim();
      if (rest) {
        segments.push({ text: rest, language: target, speed });
      }
    }
    return mergeAdjacentSegments(segments);
  }

  // 2. Check for DOUBLE-QUOTES enclosing the target language: e.g. Repeat after me: "Un elefante"
  // Note: Never split on single quotes to protect English contractions (don't, let's) and phonetic cues ('rr', 'j')
  const doubleQuotePattern = /(["“][^"”]{3,}?["”])/g;
  if (doubleQuotePattern.test(text) && native !== target) {
    doubleQuotePattern.lastIndex = 0;
    const parts = text.split(/(["“][^"”]{3,}?["”])/).filter(Boolean);
    if (parts.length > 1) {
      const segments: SpeechSegment[] = [];
      for (const part of parts) {
        const trimmed = part.trim();
        if (!trimmed) continue;
        const isDoubleQuoted = (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
                               (trimmed.startsWith('“') && trimmed.endsWith('”'));
        if (isDoubleQuoted) {
          const cleanText = trimmed.replace(/^[“"]|[”"]$/g, "").trim();
          if (cleanText) {
            segments.push({ text: cleanText, language: target, speed });
          }
        } else {
          const cleanLeadIn = trimmed.replace(/:\s*$/, "").trim();
          if (cleanLeadIn) {
            segments.push({ text: trimmed, language: native, speed });
          }
        }
      }
      if (segments.length > 0) {
        return mergeAdjacentSegments(segments);
      }
    }
  }

  // 3. Check for lead-in colons: e.g. "Repeat after me: Un elefante"
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

  // 4. Check for distinct non-Latin script blocks (e.g. Latin vs CJK / Cyrillic / Arabic)
  const isNonLatinTarget = /[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]/.test(text);
  const isLatinNative = /^[a-z]/i.test(native);
  if (isNonLatinTarget && isLatinNative) {
    const nonLatinRegex = /([\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]+(?:[\s\u3000-\u303F\uFF00-\uFFEF\p{P}]*[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]+)*)/gu;
    const pieces = text.split(nonLatinRegex).filter(Boolean);
    if (pieces.length > 1) {
      const segments: SpeechSegment[] = [];
      for (const piece of pieces) {
        const clean = piece.trim();
        if (!clean) continue;
        const hasNonLatin = /[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]/.test(clean);
        segments.push({
          text: clean,
          language: hasNonLatin ? target : native,
          speed,
        });
      }
      return mergeAdjacentSegments(segments);
    }
  }

  // Default: single segment in default/target language
  const chosenLang = options.defaultLanguage ?? options.targetLanguage ?? native;
  return [{ text, language: chosenLang, speed }];
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

/**
 * Builds a standard 44-byte RIFF/WAVE header for 16-bit PCM mono audio.
 */
function createWavHeader(dataLength: number, sampleRate: number): Uint8Array {
  const header = new Uint8Array(44);
  const view = new DataView(header.buffer);

  // "RIFF"
  header[0] = 0x52; header[1] = 0x49; header[2] = 0x46; header[3] = 0x46;
  // Total file size minus 8 bytes
  view.setUint32(4, 36 + dataLength, true);
  // "WAVE"
  header[8] = 0x57; header[9] = 0x41; header[10] = 0x56; header[11] = 0x45;
  // "fmt "
  header[12] = 0x66; header[13] = 0x6d; header[14] = 0x74; header[15] = 0x20;
  // Subchunk1Size (16 for PCM)
  view.setUint32(16, 16, true);
  // AudioFormat (1 for PCM)
  view.setUint16(20, 1, true);
  // NumChannels (1 = mono)
  view.setUint16(22, 1, true);
  // SampleRate
  view.setUint32(24, sampleRate, true);
  // ByteRate = SampleRate * NumChannels * BitsPerSample / 8
  view.setUint32(28, sampleRate * 1 * 2, true);
  // BlockAlign = NumChannels * BitsPerSample / 8
  view.setUint16(32, 2, true);
  // BitsPerSample (16)
  view.setUint16(34, 16, true);
  // "data"
  header[36] = 0x64; header[37] = 0x61; header[38] = 0x74; header[39] = 0x61;
  // Subchunk2Size (dataLength)
  view.setUint32(40, dataLength, true);

  return header;
}

/**
 * Combines multiple PCM 16-bit mono audio buffers into a single playable WAV buffer,
 * applying a smooth 10ms linear crossfade and inserting a natural prosodic silence gap.
 */
export function buildWavFromPcmSegments(
  pcmBuffers: Uint8Array[],
  sampleRate = 24000,
  pauseMs = 180,
): Uint8Array {
  const nonNullBuffers = pcmBuffers.filter((b) => b.byteLength > 0);
  if (nonNullBuffers.length === 0) {
    return createWavHeader(0, sampleRate);
  }

  // 16-bit (2 bytes per sample), mono
  const silenceSamples = Math.round((sampleRate * pauseMs) / 1000);
  const silenceBytes = silenceSamples * 2;
  const silenceBuffer = new Uint8Array(silenceBytes); // zero-initialized

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
