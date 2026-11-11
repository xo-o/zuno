import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { getProfile } from "../lib/profile.js";
import { cartesiaLanguage, cartesiaVoiceFor } from "../lib/voices.js";
import {
  buildWavFromPcmSegments,
  ensureTerminalPunct,
  resolveCartesiaLocale,
  segmentBilingualText,
} from "../lib/bilingual-tts.js";

const previewSchema = z.object({
  text: z.string().trim().min(1).max(600),
  language: z.string().trim().max(16).optional(),
  nativeLanguage: z.string().trim().max(16).optional(),
  slow: z.boolean().optional(),
});

const ttsLimit = rateLimit({ windowMs: 60_000, max: 60 });

async function fetchCartesiaPcm(options: {
  apiKey: string;
  voiceId: string;
  text: string;
  locale: string;
  fallbackLanguage?: string;
  speed: number;
}): Promise<Uint8Array> {
  const punctText = ensureTerminalPunct(options.text);
  if (!punctText) return new Uint8Array(0);

  const payload: Record<string, unknown> = {
    model_id: "sonic-3.6",
    transcript: punctText,
    voice: { mode: "id", id: options.voiceId },
    locale: options.locale,
    output_format: {
      container: "raw",
      encoding: "pcm_s16le",
      sample_rate: 24000,
    },
    generation_config: { speed: options.speed },
  };

  for (let attempt = 0; attempt < 2; attempt++) {
    const res = await fetch("https://api.cartesia.ai/tts/bytes", {
      method: "POST",
      headers: {
        "X-API-Key": options.apiKey,
        "Cartesia-Version": "2026-03-01",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    if (res.ok) {
      return new Uint8Array(await res.arrayBuffer());
    }
    if (res.status === 429 && attempt === 0) {
      await new Promise((resolve) => setTimeout(resolve, 300));
      continue;
    }

    // Fallback to sonic-3 with base language if sonic-3.6/locale fails
    payload.model_id = "sonic-3";
    delete payload.locale;
    if (options.fallbackLanguage) {
      payload.language = options.fallbackLanguage;
    }
    const resFallback = await fetch("https://api.cartesia.ai/tts/bytes", {
      method: "POST",
      headers: {
        "X-API-Key": options.apiKey,
        "Cartesia-Version": "2025-04-16",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    if (resFallback.ok) {
      return new Uint8Array(await resFallback.arrayBuffer());
    }
    const errText = await res.text().catch(() => "");
    console.error(`Cartesia TTS preview failed (${res.status}):`, errText);
    throw new HTTPException(502, { message: "Voice preview unavailable right now" });
  }
  throw new HTTPException(502, { message: "Voice preview unavailable right now" });
}

export const ttsRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .post("/preview", ttsLimit, async (c) => {
    const apiKey = process.env.CARTESIA_API_KEY;
    if (!apiKey) {
      throw new HTTPException(500, { message: "Cartesia TTS is not configured" });
    }
    const userId = c.get("user").id;
    const body = await parseBody(c, previewSchema);
    const profile = await getProfile(userId);
    const voiceId = cartesiaVoiceFor(profile.tutorVoice);
    const targetLanguage = body.language ?? profile.targetLanguage;
    const nativeLanguage = body.nativeLanguage ?? profile.nativeLanguage;
    const speed = body.slow ? 0.75 : 1.0;

    // Segment text into language-specific portions (e.g. English intro -> Spanish target phrase)
    const segments = segmentBilingualText(body.text, {
      nativeLanguage,
      targetLanguage,
      defaultLanguage: targetLanguage,
      speed,
    });

    // Synthesize all segments in parallel using the exact same Cartesia voice
    const pcmBuffers = await Promise.all(
      segments.map((seg) =>
        fetchCartesiaPcm({
          apiKey,
          voiceId,
          text: seg.text,
          locale: resolveCartesiaLocale(seg.language, profile.accent, profile.tutorVoice),
          fallbackLanguage: cartesiaLanguage(seg.language),
          speed: seg.speed ?? speed,
        }),
      ),
    );

    // Stitch segments together with natural prosodic pause into a single WAV response
    const wav = buildWavFromPcmSegments(pcmBuffers, 24000, 180);

    return new Response(wav, {
      status: 200,
      headers: {
        "Content-Type": "audio/wav",
        "Content-Length": String(wav.byteLength),
        "Cache-Control": "private, max-age=86400",
      },
    });
  });
