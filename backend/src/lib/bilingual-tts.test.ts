import test from "node:test";
import assert from "node:assert/strict";
import {
  buildWavFromPcm,
  cleanTextForTts,
  stripLangTags,
  stripRomanization,
} from "./bilingual-tts.js";
import { resolveTutor, TUTOR_IDS } from "./voices.js";

test("bilingual-tts: stripLangTags removes legacy bracket and xml tags", () => {
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

  const normal = "Hola (amigo)";
  assert.equal(stripRomanization(normal), "Hola (amigo)");
});

test("bilingual-tts: cleanTextForTts keeps mixed-language text intact for single-pass ElevenLabs synthesis", () => {
  const mixed = "Hola, how are you? Estoy bien";
  assert.equal(cleanTextForTts(mixed), "Hola, how are you? Estoy bien.");

  const withMarkdown = "**Sure!** In Spain people say “vale” (smiles)";
  assert.equal(cleanTextForTts(withMarkdown), "Sure! In Spain people say “vale”.");
});

test("bilingual-tts: buildWavFromPcm generates valid 44-byte RIFF/WAVE header and 24kHz PCM16 payload", () => {
  const pcm = new Uint8Array(4800); // 100ms at 24kHz 16-bit mono
  pcm.fill(0x2a);

  const wav = buildWavFromPcm(pcm, 24000);
  assert.equal(wav.byteLength, 44 + 4800);

  assert.equal(String.fromCharCode(wav[0], wav[1], wav[2], wav[3]), "RIFF");
  assert.equal(String.fromCharCode(wav[8], wav[9], wav[10], wav[11]), "WAVE");
  assert.equal(String.fromCharCode(wav[12], wav[13], wav[14], wav[15]), "fmt ");
  assert.equal(String.fromCharCode(wav[36], wav[37], wav[38], wav[39]), "data");

  const view = new DataView(wav.buffer);
  assert.equal(view.getUint16(20, true), 1); // PCM format
  assert.equal(view.getUint16(22, true), 1); // 1 channel
  assert.equal(view.getUint32(24, true), 24000); // sample rate
  assert.equal(view.getUint16(34, true), 16); // 16 bit
  assert.equal(view.getUint32(40, true), 4800); // data length
});

test("voices: strict 1:1 tutor-to-ElevenLabs-voice catalog covers 6 distinct tutors and presets", () => {
  assert.equal(TUTOR_IDS.length, 6);
  const voiceIds = new Set(TUTOR_IDS.map((id) => resolveTutor(id).voiceId));
  assert.equal(voiceIds.size, 6);
  assert.equal(resolveTutor("sofia").voiceId, "DODLEQrClDo8wCz460ld");
  assert.equal(resolveTutor(undefined, "warm").id, "sofia");
  assert.equal(resolveTutor(undefined, "calm").id, "valeria");
  assert.equal(resolveTutor(undefined, "energetic").id, "mateo");
  assert.equal(resolveTutor("lucas").voicePreset, "steady");
  assert.equal(resolveTutor("ethan").voicePreset, "casual");
  assert.equal(resolveTutor("claire").voicePreset, "crisp");
});
