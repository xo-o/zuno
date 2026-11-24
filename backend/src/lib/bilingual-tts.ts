const XML_LANG_TAG_RE = /<lang\b([^>]*)>([\s\S]*?)<\/lang>/gi;
const BRACKET_LANG_TAG_RE = /\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[\/\1\]/gi;
const STRAY_TAG_TOKEN_RE = /(?:<\/?lang\b[^>]*>|\[\/?[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?\])/gi;
const STAGE_DIRECTION_RE =
  /\[(?:pause|laughs?|sighs?|smiles?|chuckles?|clears throat|nods?)\]|\((?:pause|laughs?|sighs?|smiles?|chuckles?|clears throat|nods?)\)/gi;

/**
 * Strips legacy `<lang code="en">...</lang>`, `<lang="en">...</lang>`, and `[en]...[/en]` tags for UI display and TTS.
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
 * Removes markdown symbols and stage directions so TTS speaks natural prose only.
 */
export function stripSpeechArtifacts(text: string): string {
  return text
    .replace(STAGE_DIRECTION_RE, " ")
    .replace(/[*_`#~]+/g, "")
    .replace(/^\s*[-•]\s+/gm, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}

/**
 * Ensures text sent to ElevenLabs has clean terminal punctuation.
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
 * Cleans an utterance while preserving mixed-language text intact for single-pass ElevenLabs synthesis.
 */
export function cleanTextForTts(text: string): string {
  return ensureTerminalPunct(stripSpeechArtifacts(stripRomanization(stripLangTags(text))));
}

function createWavHeader(dataLength: number, sampleRate: number): Uint8Array {
  const header = new Uint8Array(44);
  const view = new DataView(header.buffer);

  header[0] = 0x52;
  header[1] = 0x49;
  header[2] = 0x46;
  header[3] = 0x46;
  view.setUint32(4, 36 + dataLength, true);
  header[8] = 0x57;
  header[9] = 0x41;
  header[10] = 0x56;
  header[11] = 0x45;
  header[12] = 0x66;
  header[13] = 0x6d;
  header[14] = 0x74;
  header[15] = 0x20;
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true); // PCM format
  view.setUint16(22, 1, true); // Mono
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 1 * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  header[36] = 0x64;
  header[37] = 0x61;
  header[38] = 0x74;
  header[39] = 0x61;
  view.setUint32(40, dataLength, true);

  return header;
}

/**
 * Wraps raw 16-bit signed LE mono PCM bytes into a standard 44-byte RIFF/WAVE container.
 */
export function buildWavFromPcm(pcmBuffer: Uint8Array, sampleRate = 24000): Uint8Array<ArrayBuffer> {
  const evenLength = Math.floor(pcmBuffer.byteLength / 2) * 2;
  const result = new Uint8Array(new ArrayBuffer(44 + evenLength));
  const header = createWavHeader(evenLength, sampleRate);
  result.set(header, 0);
  if (evenLength > 0) {
    result.set(pcmBuffer.subarray(0, evenLength), 44);
  }
  return result;
}
