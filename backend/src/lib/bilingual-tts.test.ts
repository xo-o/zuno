import test from "node:test";
import assert from "node:assert/strict";
import {
  buildWavFromPcmSegments,
  segmentBilingualText,
  stripLangTags,
  stripRomanization,
} from "./bilingual-tts.js";

test("bilingual-tts: stripLangTags removes bracket and xml tags", () => {
  const tagged1 = "[en]Repeat after me:[/en] [es]Un elefante[/es]";
  assert.equal(stripLangTags(tagged1), "Repeat after me: Un elefante");

  const tagged2 = '<lang="en">Listen carefully:</lang> <lang="es">Buenos días</lang>';
  assert.equal(stripLangTags(tagged2), "Listen carefully: Buenos días");

  const tagged3 = '<lang code="en">That means bill.</lang> <lang code="es">¿Me trae la cuenta?</lang>';
  assert.equal(stripLangTags(tagged3), "That means bill. ¿Me trae la cuenta?");
});

test("bilingual-tts: stripRomanization strips parenthesized latin after non-latin script", () => {
  const jp = "こんにちは (konnichiwa)";
  assert.equal(stripRomanization(jp), "こんにちは");

  const ru = "Привет (privet)";
  assert.equal(stripRomanization(ru), "Привет");

  const normal = "Hola (amigo)"; // Both latin: unchanged
  assert.equal(stripRomanization(normal), "Hola (amigo)");
});

test("bilingual-tts: segmentBilingualText handles explicit tags", () => {
  const input = "[en]Repeat after me:[/en] [es]Un elefante[/es]";
  const segments = segmentBilingualText(input, {
    nativeLanguage: "en",
    targetLanguage: "es",
  });
  assert.equal(segments.length, 2);
  assert.equal(segments[0].language, "en");
  assert.equal(segments[0].text, "Repeat after me:");
  assert.equal(segments[1].language, "es");
  assert.equal(segments[1].text, "Un elefante");

  const xmlCodeInput = '<lang code="en">Sure, that means oat milk. Try asking:</lang> <lang code="es">¿Tienen leche de avena?</lang>';
  const xmlSegs = segmentBilingualText(xmlCodeInput, {
    nativeLanguage: "en",
    targetLanguage: "es",
  });
  assert.equal(xmlSegs.length, 2);
  assert.equal(xmlSegs[0].language, "en");
  assert.equal(xmlSegs[1].language, "es");
});

test("bilingual-tts: segmentBilingualText detects untagged multi-sentence clarification + target follow-up", () => {
  const input = "Sure, that word means receipt in English. ¿Quieres el recibo ahora?";
  const segments = segmentBilingualText(input, {
    nativeLanguage: "en",
    targetLanguage: "es",
  });
  assert.equal(segments.length, 2);
  assert.equal(segments[0].language, "en");
  assert.equal(segments[1].language, "es");
});

test("bilingual-tts: segmentBilingualText splits lead-in colon pattern", () => {
  const input = "Repeat after me: Un elefante";
  const segments = segmentBilingualText(input, {
    nativeLanguage: "en",
    targetLanguage: "es",
  });
  assert.equal(segments.length, 2);
  assert.equal(segments[0].language, "en");
  assert.equal(segments[0].text, "Repeat after me");
  assert.equal(segments[1].language, "es");
  assert.equal(segments[1].text, "Un elefante");
});

test("bilingual-tts: segmentBilingualText splits quotes with target phrases", () => {
  const input = 'Try saying: "Disculpe, ¿este autobús va al centro?"';
  const segments = segmentBilingualText(input, {
    nativeLanguage: "en",
    targetLanguage: "es",
  });
  assert.equal(segments.length, 2);
  assert.equal(segments[0].language, "en");
  assert.equal(segments[0].text, "Try saying:");
  assert.equal(segments[1].language, "es");
  assert.equal(segments[1].text, "Disculpe, ¿este autobús va al centro?");
});

test("bilingual-tts: segmentBilingualText handles non-latin target script with latin native lead-in", () => {
  const input = "To greet someone in the morning, say: こんにちは";
  const segments = segmentBilingualText(input, {
    nativeLanguage: "en",
    targetLanguage: "ja",
  });
  assert.equal(segments.length, 2);
  assert.equal(segments[0].language, "en");
  assert.equal(segments[1].language, "ja");
  assert.equal(segments[1].text, "こんにちは");
});

test("bilingual-tts: segmentBilingualText preserves pure single-language text", () => {
  const input = "¿Dónde está la estación de tren?";
  const segments = segmentBilingualText(input, {
    nativeLanguage: "en",
    targetLanguage: "es",
  });
  assert.equal(segments.length, 1);
  assert.equal(segments[0].language, "es");
  assert.equal(segments[0].text, input);
});

test("bilingual-tts: buildWavFromPcmSegments generates valid 44-byte WAV header and PCM audio", () => {
  const pcm1 = new Uint8Array(4800); // 100ms at 24kHz 16-bit mono
  pcm1.fill(0x12);
  const pcm2 = new Uint8Array(4800);
  pcm2.fill(0x34);

  const wav = buildWavFromPcmSegments([pcm1, pcm2], 24000, 100);
  // 44 header + 4800 + (24000 * 0.1 * 2 = 4800 silence) + 4800 = 14444 bytes
  assert.equal(wav.byteLength, 44 + 4800 + 4800 + 4800);

  // Check RIFF header
  assert.equal(String.fromCharCode(wav[0], wav[1], wav[2], wav[3]), "RIFF");
  assert.equal(String.fromCharCode(wav[8], wav[9], wav[10], wav[11]), "WAVE");
  assert.equal(String.fromCharCode(wav[12], wav[13], wav[14], wav[15]), "fmt ");
  assert.equal(String.fromCharCode(wav[36], wav[37], wav[38], wav[39]), "data");

  const view = new DataView(wav.buffer);
  assert.equal(view.getUint16(20, true), 1); // PCM format
  assert.equal(view.getUint16(22, true), 1); // 1 channel
  assert.equal(view.getUint32(24, true), 24000); // sample rate
  assert.equal(view.getUint16(34, true), 16); // 16 bit
  assert.equal(view.getUint32(40, true), 4800 + 4800 + 4800); // data length
});
