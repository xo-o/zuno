import asyncio
import logging
import os
from typing import Any, AsyncIterable, AsyncIterator, Optional

import aiohttp
import livekit.plugins.cartesia as cartesia
from livekit import rtc
import numpy as np

from bilingual import (
    SpeechClause,
    SpeechSegment,
    ensure_terminal_punct,
    iter_streaming_clauses,
)

logger = logging.getLogger("voice-agent.voices")

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
    """Resolves language and accent into an explicit Cartesia regional locale code for sonic-3.6."""
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
        return "de-CH" if acc in ("switzerland", "swiss") else "de-DE"
    if lang == "nl":
        return "nl-BE" if acc in ("belgium", "flemish") else "nl-NL"
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
    if lang == "pl":
        return "pl-PL"
    if lang == "tr":
        return "tr-TR"
    if lang == "sv":
        return "sv-SE"
    if lang == "ar":
        return "ar-SA"
    if lang == "id":
        return "id-ID"
    if lang == "vi":
        return "vi-VN"

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
    """Synthesizes speech with Cartesia `sonic-3.6` regional locale conditioning for a unified voice identity.

    Supports both pre-segmented parallel batching (for structured lesson prompts) and incremental
    lookahead clause streaming (for live Roleplay & Free Talk LLM streams).
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
        """Fetches raw PCM s16le 24kHz audio from Cartesia `sonic-3.6` with explicit regional `locale`."""
        punct_text = ensure_terminal_punct(text)
        if not punct_text or not self.api_key:
            return b""

        speed_clamped = round(max(0.6, min(2.0, float(speed))), 2)
        locale = resolve_cartesia_locale(language, self.accent, self.tutor_voice)
        payload: dict = {
            "model_id": "sonic-3.6",
            "transcript": punct_text,
            "voice": {"mode": "id", "id": self.voice_id},
            "locale": locale,
            "output_format": {
                "container": "raw",
                "encoding": "pcm_s16le",
                "sample_rate": 24000,
            },
            "generation_config": {"speed": speed_clamped},
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
                    # Fallback to sonic-3 with base language if 3.6 locale is unsupported for a rare language
                    fallback_lang = cartesia_language(language)
                    fallback_payload = dict(payload)
                    fallback_payload["model_id"] = "sonic-3"
                    fallback_payload.pop("locale", None)
                    if fallback_lang:
                        fallback_payload["language"] = fallback_lang
                    async with session.post(
                        "https://api.cartesia.ai/tts/bytes",
                        headers={
                            "X-API-Key": self.api_key,
                            "Cartesia-Version": "2025-04-16",
                            "Content-Type": "application/json",
                        },
                        json=fallback_payload,
                    ) as resp2:
                        if resp2.status == 200:
                            return await resp2.read()
                        return b""
            except asyncio.CancelledError:
                raise
            except Exception as e:
                if attempt == 0:
                    await asyncio.sleep(0.2)
                else:
                    logger.warning("Cartesia TTS fetch failed for locale=%s: %s", locale, e)
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


def _pcm_to_audio_frames(pcm_bytes: bytes, pause_after_ms: int = 0) -> list[rtc.AudioFrame]:
    """Converts 24kHz s16le PCM bytes into 20ms LiveKit AudioFrames with 10ms anti-pop linear ramps."""
    if not pcm_bytes:
        return []
    sample_rate = 24000
    chunk_samples = 480  # 20ms at 24kHz
    frame_bytes = chunk_samples * 2

    even_len = (len(pcm_bytes) // 2) * 2
    if even_len == 0:
        return []
    int16_samples = np.frombuffer(pcm_bytes[:even_len], dtype=np.int16).copy()
    total_samples = len(int16_samples)

    ramp_len = min(240, total_samples // 4)  # 10ms ramp
    if ramp_len > 0:
        fade_in = np.linspace(0.0, 1.0, ramp_len, dtype=np.float32)
        fade_out = np.linspace(1.0, 0.0, ramp_len, dtype=np.float32)
        int16_samples[:ramp_len] = (int16_samples[:ramp_len].astype(np.float32) * fade_in).astype(np.int16)
        int16_samples[-ramp_len:] = (int16_samples[-ramp_len:].astype(np.float32) * fade_out).astype(np.int16)

    frames: list[rtc.AudioFrame] = []
    ramped_bytes = int16_samples.tobytes()
    for off in range(0, len(ramped_bytes), frame_bytes):
        chunk = ramped_bytes[off : off + frame_bytes]
        n_samples = len(chunk) // 2
        if n_samples > 0:
            frames.append(
                rtc.AudioFrame(
                    data=chunk,
                    sample_rate=sample_rate,
                    num_channels=1,
                    samples_per_channel=n_samples,
                )
            )

    if pause_after_ms > 0:
        pause_samples = int(sample_rate * pause_after_ms / 1000)
        silence_bytes = np.zeros(pause_samples, dtype=np.int16).tobytes()
        for off in range(0, len(silence_bytes), frame_bytes):
            chunk = silence_bytes[off : off + frame_bytes]
            n_samples = len(chunk) // 2
            if n_samples > 0:
                frames.append(
                    rtc.AudioFrame(
                        data=chunk,
                        sample_rate=sample_rate,
                        num_channels=1,
                        samples_per_channel=n_samples,
                    )
                )
    return frames


async def synthesize_bilingual_segments(
    synthesizer: BilingualCartesiaSynthesizer,
    segments: list[SpeechSegment],
    default_pause_ms: int = 180,
) -> AsyncIterator[rtc.AudioFrame]:
    """Synthesizes all SpeechSegments in parallel and streams 20ms AudioFrames with smooth pauses."""
    valid_segments = [s for s in segments if s.text.strip()]
    if not valid_segments:
        return

    tasks = [
        synthesizer.fetch_pcm_bytes(seg.text, seg.language, seg.speed)
        for seg in valid_segments
    ]
    raw_pcm_list = await asyncio.gather(*tasks, return_exceptions=True)

    for idx, (seg, pcm_res) in enumerate(zip(valid_segments, raw_pcm_list)):
        if isinstance(pcm_res, Exception) or not pcm_res:
            continue
        is_last = idx >= len(valid_segments) - 1
        pause_ms = 0 if is_last else (seg.pause_after_ms or default_pause_ms)
        for frame in _pcm_to_audio_frames(pcm_res, pause_after_ms=pause_ms):
            yield frame


_QUEUE_UNSET = object()


async def stream_bilingual_tts(
    synthesizer: BilingualCartesiaSynthesizer,
    text_stream: AsyncIterable[str],
    *,
    native_lang: str = "en",
    target_lang: str = "es",
    default_lang: Optional[str] = None,
    speed: float = 1.0,
) -> AsyncIterator[rtc.AudioFrame]:
    """Streams live LLM tokens clause-by-clause through Cartesia `sonic-3.6` with regional `locale`.

    Starts playing the first sentence/clause immediately while prefetching subsequent clauses in the
    background so multi-sentence or bilingual turns play with zero inter-clause network stalls.
    """
    queue: asyncio.Queue[tuple[SpeechSegment, asyncio.Task[bytes]] | None] = asyncio.Queue()
    inflight_tasks: list[asyncio.Task[bytes]] = []

    async def _producer() -> None:
        try:
            async for seg in iter_streaming_clauses(
                text_stream,
                native_lang=native_lang,
                target_lang=target_lang,
                default_lang=default_lang or target_lang,
                speed=speed,
            ):
                if not seg.text.strip():
                    continue
                task = asyncio.create_task(
                    synthesizer.fetch_pcm_bytes(seg.text, seg.language, seg.speed)
                )
                inflight_tasks.append(task)
                await queue.put((seg, task))
        finally:
            await queue.put(None)

    producer_task = asyncio.create_task(_producer())
    try:
        current = await queue.get()
        while current is not None:
            seg, pcm_task = current
            next_item: Any = _QUEUE_UNSET
            if not queue.empty():
                next_item = queue.get_nowait()

            pcm_bytes = await pcm_task
            if next_item is _QUEUE_UNSET and not queue.empty():
                next_item = queue.get_nowait()

            if pcm_bytes:
                if next_item is not _QUEUE_UNSET and next_item is not None:
                    next_seg = next_item[0]
                    pause_ms = 180 if next_seg.language != seg.language else 80
                else:
                    pause_ms = 0
                for frame in _pcm_to_audio_frames(pcm_bytes, pause_after_ms=pause_ms):
                    yield frame

            if next_item is _QUEUE_UNSET:
                current = await queue.get()
            else:
                current = next_item
    finally:
        if not producer_task.done():
            producer_task.cancel()
        for t in inflight_tasks:
            if not t.done():
                t.cancel()
