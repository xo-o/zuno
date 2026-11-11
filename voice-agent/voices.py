import asyncio
import os
import aiohttp
from typing import AsyncIterator, Optional
import livekit.plugins.cartesia as cartesia
from livekit import rtc
import numpy as np

from bilingual import SpeechClause, SpeechSegment, ensure_terminal_punct

# Curated Cartesia Sonic-3.6 multilingual voices.
# Ariana (en-US native, speaks es-MX/es-ES natively), Mariana (es-CO native, speaks en natively), Carson (en-US native).
CARTESIA_MULTILINGUAL_VOICES: dict[str, str] = {
    "warm": "ec1e269e-9ca0-402f-8a18-58e0e022355a",       # Ariana - Kind Friend
    "calm": "ae823354-f9be-4aef-8543-f569644136b4",       # Mariana - Nurturing Guide
    "energetic": "4df027cb-2920-4a1f-8c34-f21529d5c3fe",  # Carson - Friendly Support
}

DEFAULT_CARTESIA_VOICE = CARTESIA_MULTILINGUAL_VOICES["warm"]

# Explicit language codes accepted by Cartesia
CARTESIA_LANGS = {
    "ar", "bg", "bn", "cs", "da", "de", "el", "en", "es", "fi", "fr",
    "gu", "he", "hi", "hr", "hu", "id", "it", "ja", "ka", "kn", "ko",
    "ml", "mr", "ms", "nl", "no", "or", "pa", "pl", "pt", "ro", "ru",
    "sk", "sv", "ta", "te", "th", "tl", "tr", "uk", "vi", "zh",
}

ALLOWED_MULTILINGUAL_VOICE_IDS = set(CARTESIA_MULTILINGUAL_VOICES.values())


def resolve_cartesia_voice(voice_id: str | None = None, tutor_voice: str | None = None) -> str:
    """Always resolves to one of Zuno's verified Cartesia multilingual voices."""
    if tutor_voice and tutor_voice in CARTESIA_MULTILINGUAL_VOICES:
        return CARTESIA_MULTILINGUAL_VOICES[tutor_voice]
    if voice_id and voice_id in ALLOWED_MULTILINGUAL_VOICE_IDS:
        return voice_id
    return DEFAULT_CARTESIA_VOICE


def cartesia_language(code: str | None) -> str | None:
    """Returns a Cartesia base language code, or `None` so scripts like `fa`/`ur` auto-detect."""
    if not code:
        return None
    normalized = code.strip().lower().split("-")[0]
    return normalized if normalized in CARTESIA_LANGS else None


def resolve_cartesia_locale(
    language: str | None,
    accent: str | None = None,
    tutor_voice: str | None = None,
) -> str:
    """Resolves language and accent into an explicit Cartesia regional locale code."""
    lang = cartesia_language(language) or "es"
    acc = (accent or "").lower().strip()

    if lang == "es":
        if acc in ("spain", "castilian"):
            return "es-ES"
        if tutor_voice == "calm":
            return "es-CO"
        return "es-MX"

    if lang == "en":
        if acc in ("uk", "british"):
            return "en-GB"
        if acc in ("au", "australia", "australian"):
            return "en-AU"
        return "en-US"

    if lang == "fr":
        return "fr-CA" if acc in ("canada", "canadian") else "fr-FR"

    if lang == "pt":
        return "pt-PT" if acc in ("portugal", "european") else "pt-BR"

    if lang == "de":
        return "de-DE"
    if lang == "it":
        return "it-IT"
    if lang == "ja":
        return "ja-JP"
    if lang == "zh":
        return "zh-TW" if acc in ("taiwan", "taiwanese") else "zh-CN"
    if lang == "ko":
        return "ko-KR"
    if lang == "ru":
        return "ru-RU"
    if lang == "hi":
        return "hi-IN"

    return lang


def create_cartesia_tts(
    voice_id: str | None = None,
    tutor_voice: str | None = None,
    language: str | None = "en",
    speed: float = 1.0,
) -> cartesia.TTS:
    resolved_voice = resolve_cartesia_voice(voice_id, tutor_voice)
    resolved_lang = cartesia_language(language)
    word_timestamps = resolved_lang in ("en", "de", "es", "fr")
    return cartesia.TTS(
        api_key=os.environ.get("CARTESIA_API_KEY"),
        model="sonic-3",
        voice=resolved_voice,
        language=resolved_lang,
        speed=speed,
        word_timestamps=word_timestamps,
    )


def update_cartesia_tts(
    tts_engine: cartesia.TTS,
    *,
    language: str | None,
    speed: float = 1.0,
) -> None:
    """Switches synthesis language/speed in-place while keeping the exact same Cartesia voice."""
    resolved_lang = cartesia_language(language)
    tts_engine.update_options(
        language=resolved_lang,
        speed=speed,
    )


class BilingualCartesiaSynthesizer:
    """Caches language-specific Cartesia TTS engines for the same voice identity so

    multi-clause bilingual speech (e.g. English lead-in -> Spanish target phrase)
    synthesizes cleanly with authentic accents and zero race conditions.
    """

    def __init__(
        self,
        voice_id: str | None = None,
        tutor_voice: str | None = None,
        accent: str | None = None,
    ) -> None:
        self.voice_id = resolve_cartesia_voice(voice_id, tutor_voice)
        self.tutor_voice = tutor_voice
        self.accent = accent
        self.api_key = os.environ.get("CARTESIA_API_KEY")
        self._engines: dict[str, cartesia.TTS] = {}
        self._session: Optional[aiohttp.ClientSession] = None

    def get_tts(self, language: str | None, speed: float = 1.0) -> cartesia.TTS:
        resolved_lang = cartesia_language(language) or "en"
        speed_clamped = round(max(0.6, min(2.0, float(speed))), 2)
        key = f"{resolved_lang}:{speed_clamped}"
        if key not in self._engines:
            self._engines[key] = create_cartesia_tts(
                voice_id=self.voice_id,
                tutor_voice=self.tutor_voice,
                language=resolved_lang,
                speed=speed_clamped,
            )
        return self._engines[key]

    async def _get_session(self) -> aiohttp.ClientSession:
        if self._session is None or self._session.closed:
            self._session = aiohttp.ClientSession(
                timeout=aiohttp.ClientTimeout(total=20, sock_connect=5)
            )
        return self._session

    async def fetch_pcm_bytes(
        self,
        text: str,
        language: str,
        speed: float = 1.0,
    ) -> bytes:
        """Fetches raw PCM s16le 24kHz audio from Cartesia's HTTP endpoint with clean terminal punctuation."""
        punct_text = ensure_terminal_punct(text)
        if not punct_text or not self.api_key:
            return b""

        locale = resolve_cartesia_locale(language, self.accent, self.tutor_voice)
        payload = {
            "model_id": "sonic-3.6",
            "transcript": punct_text,
            "voice": {"mode": "id", "id": self.voice_id},
            "locale": locale,
            "output_format": {
                "container": "raw",
                "encoding": "pcm_s16le",
                "sample_rate": 24000,
            },
            "generation_config": {"speed": speed},
        }

        session = await self._get_session()
        for attempt in range(2):
            try:
                async with session.post(
                    "https://api.cartesia.ai/tts/bytes",
                    headers={
                        "X-API-Key": self.api_key,
                        "Cartesia-Version": "2026-03-01",
                        "Content-Type": "application/json",
                    },
                    json=payload,
                ) as resp:
                    if resp.status == 200:
                        return await resp.read()
                    if resp.status == 429 and attempt == 0:
                        await asyncio.sleep(0.3)
                        continue
                    # Fallback to sonic-3 with base language if 3.6 locale rejected
                    fallback_lang = cartesia_language(language) or "es"
                    payload["model_id"] = "sonic-3"
                    payload.pop("locale", None)
                    payload["language"] = fallback_lang
                    async with session.post(
                        "https://api.cartesia.ai/tts/bytes",
                        headers={
                            "X-API-Key": self.api_key,
                            "Cartesia-Version": "2025-04-16",
                            "Content-Type": "application/json",
                        },
                        json=payload,
                    ) as resp2:
                        if resp2.status == 200:
                            return await resp2.read()
                        return b""
            except Exception:
                if attempt == 0:
                    await asyncio.sleep(0.2)
                else:
                    return b""
        return b""

    async def aclose(self) -> None:
        if self._session and not self._session.closed:
            await self._session.close()
            self._session = None
        for engine in self._engines.values():
            try:
                await engine.aclose()
            except Exception:
                pass
        self._engines.clear()


async def synthesize_bilingual_segments(
    synthesizer: BilingualCartesiaSynthesizer,
    segments: list[SpeechSegment],
    default_pause_ms: int = 180,
) -> AsyncIterator[rtc.AudioFrame]:
    """Synthesizes all bilingual SpeechSegments in parallel, then streams out audio frames

    in correct sequence with smooth prosodic pauses. Zero network gap between language clauses!
    """
    valid_segments = [s for s in segments if s.text.strip()]
    if not valid_segments:
        return

    # 1. Pre-fetch all clauses in parallel!
    tasks = [
        synthesizer.fetch_pcm_bytes(seg.text, seg.language, seg.speed)
        for seg in valid_segments
    ]
    raw_pcm_list = await asyncio.gather(*tasks, return_exceptions=True)

    sample_rate = 24000
    chunk_samples = 480  # 20ms frames at 24kHz

    for idx, (seg, pcm_res) in enumerate(zip(valid_segments, raw_pcm_list)):
        if isinstance(pcm_res, Exception) or not pcm_res:
            continue

        pcm_bytes: bytes = pcm_res
        total_samples = len(pcm_bytes) // 2
        int16_samples = np.frombuffer(pcm_bytes, dtype=np.int16).copy()

        # Smooth linear ramp at clause start and end to avoid audio pops
        ramp_len = min(240, total_samples // 4)  # 10ms ramp
        if ramp_len > 0:
            fade_in = np.linspace(0.0, 1.0, ramp_len, dtype=np.float32)
            fade_out = np.linspace(1.0, 0.0, ramp_len, dtype=np.float32)
            int16_samples[:ramp_len] = (int16_samples[:ramp_len].astype(np.float32) * fade_in).astype(np.int16)
            int16_samples[-ramp_len:] = (int16_samples[-ramp_len:].astype(np.float32) * fade_out).astype(np.int16)

        # Yield in 20ms AudioFrames
        ramped_bytes = int16_samples.tobytes()
        frame_bytes = chunk_samples * 2
        for off in range(0, len(ramped_bytes), frame_bytes):
            chunk = ramped_bytes[off : off + frame_bytes]
            n_samples = len(chunk) // 2
            if n_samples > 0:
                yield rtc.AudioFrame(
                    data=chunk,
                    sample_rate=sample_rate,
                    num_channels=1,
                    samples_per_channel=n_samples,
                )

        # Natural silence pause between language clauses
        pause_ms = seg.pause_after_ms or (default_pause_ms if idx < len(valid_segments) - 1 else 0)
        if pause_ms > 0 and idx < len(valid_segments) - 1:
            pause_samples = int(sample_rate * pause_ms / 1000)
            silence_data = np.zeros(pause_samples, dtype=np.int16)
            silence_bytes = silence_data.tobytes()
            for off in range(0, len(silence_bytes), frame_bytes):
                chunk = silence_bytes[off : off + frame_bytes]
                n_samples = len(chunk) // 2
                if n_samples > 0:
                    yield rtc.AudioFrame(
                        data=chunk,
                        sample_rate=sample_rate,
                        num_channels=1,
                        samples_per_channel=n_samples,
                    )
