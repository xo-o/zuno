import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { getProfile } from "../lib/profile.js";
import {
  DEFAULT_ELEVENLABS_MODEL,
  FALLBACK_PREMADE_VOICE_ID,
  resolveTutor,
} from "../lib/voices.js";
import { buildWavFromPcm, cleanTextForTts } from "../lib/bilingual-tts.js";

const previewSchema = z.object({
  text: z.string().trim().min(1).max(600),
  language: z.string().trim().max(16).optional(),
  nativeLanguage: z.string().trim().max(16).optional(),
  defaultLanguage: z.string().trim().max(16).optional(),
  tutorId: z.string().trim().max(32).optional(),
  tutorVoice: z.string().trim().max(32).optional(),
  slow: z.boolean().optional(),
});

const ttsLimit = rateLimit({ windowMs: 60_000, max: 60 });

function clampSpeed(speed: number): number {
  return Math.round(Math.max(0.7, Math.min(1.2, speed)) * 100) / 100;
}

async function fetchElevenLabsPcm(options: {
  apiKey: string;
  voiceId: string;
  modelId: string;
  text: string;
  speed: number;
}): Promise<Uint8Array> {
  const cleanedText = cleanTextForTts(options.text);
  if (!cleanedText) return new Uint8Array(0);

  const clampedSpeed = clampSpeed(options.speed);
  const payload: Record<string, unknown> = {
    text: cleanedText,
    model_id: options.modelId,
    apply_text_normalization: "auto",
  };
  if (clampedSpeed !== 1.0) {
    payload.voice_settings = { speed: clampedSpeed };
  }

  let activeVoiceId = options.voiceId;
  for (let attempt = 0; attempt < 2; attempt++) {
    const res = await fetch(
      `https://api.elevenlabs.io/v1/text-to-speech/${encodeURIComponent(activeVoiceId)}?output_format=pcm_24000`,
      {
        method: "POST",
        headers: {
          "xi-api-key": options.apiKey,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      },
    );
    if (res.ok) {
      return new Uint8Array(await res.arrayBuffer());
    }
    if (res.status === 402 && activeVoiceId !== FALLBACK_PREMADE_VOICE_ID && attempt === 0) {
      activeVoiceId = FALLBACK_PREMADE_VOICE_ID;
      continue;
    }
    if (res.status === 429 && attempt === 0) {
      await new Promise((resolve) => setTimeout(resolve, 300));
      continue;
    }

    const errText = await res.text().catch(() => "");
    console.error(`ElevenLabs TTS preview failed (${res.status}):`, errText);
    throw new HTTPException(502, { message: "Voice preview unavailable right now" });
  }
  throw new HTTPException(502, { message: "Voice preview unavailable right now" });
}

export const ttsRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .post("/preview", ttsLimit, async (c) => {
    const apiKey = (process.env.ELEVENLABS_API_KEY ?? process.env.ELEVEN_API_KEY ?? "").trim();
    if (!apiKey) {
      throw new HTTPException(500, { message: "ElevenLabs TTS is not configured" });
    }
    const modelId = (process.env.ELEVENLABS_MODEL || DEFAULT_ELEVENLABS_MODEL).trim();
    const userId = c.get("user").id;
    const body = await parseBody(c, previewSchema);
    const profile = await getProfile(userId);
    const chosenTutor = resolveTutor(
      body.tutorId ?? profile.tutorId,
      body.tutorVoice ?? profile.tutorVoice,
    );
    const speed = body.slow ? 0.75 : 1.0;

    // Synthesize the entire utterance (including mixed languages) in one continuous ElevenLabs pass
    const pcmBuffer = await fetchElevenLabsPcm({
      apiKey,
      voiceId: chosenTutor.voiceId,
      modelId,
      text: body.text,
      speed,
    });

    const wav = buildWavFromPcm(pcmBuffer, 24000);

    return new Response(wav, {
      status: 200,
      headers: {
        "Content-Type": "audio/wav",
        "Content-Length": String(wav.byteLength),
        "Cache-Control": "private, max-age=86400",
      },
    });
  });
