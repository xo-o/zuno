import asyncio
import os
from typing import AsyncIterator, Optional
import livekit.plugins.cartesia as cartesia
from livekit import rtc
import numpy as np

from bilingual import SpeechSegment

# Curated Cartesia Sonic-3 multilingual voices.
# Every voice here natively supports 23+ locales so the tutor maintains the exact same
# vocal identity and tone when switching between `nativeLanguage` and `targetLanguage`.
CARTESIA_MULTILINGUAL_VOICES: dict[str, str] = {
    "warm": "ec1e269e-9ca0-402f-8a18-58e0e022355a",       # Ariana - Kind Friend
    "calm": "ae823354-f9be-4aef-8543-f569644136b4",       # Mariana - Nurturing Guide
    "energetic": "4df027cb-2920-4a1f-8c34-f21529d5c3fe",  # Carson - Friendly Support
}

DEFAULT_CARTESIA_VOICE = CARTESIA_MULTILINGUAL_VOICES["warm"]

# Explicit language codes accepted by Cartesia `sonic-3` (`2025-04-16`).
CARTESIA_SONIC3_LANGS = {
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
    """Returns a Cartesia `sonic-3` language code, or `None` so scripts like `fa`/`ur` auto-detect."""
    if not code:
        return None
    normalized = code.strip().lower().split("-")[0]
    return normalized if normalized in CARTESIA_SONIC3_LANGS else None


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

    multi-segment bilingual speech (e.g. English lead-in -> Spanish target phrase)
    synthesizes cleanly with the authentic accent for each language segment.
    """

    def __init__(
        self,
        voice_id: str | None = None,
        tutor_voice: str | None = None,
    ) -> None:
        self.voice_id = resolve_cartesia_voice(voice_id, tutor_voice)
        self.tutor_voice = tutor_voice
        self._engines: dict[str, cartesia.TTS] = {}

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

    async def aclose(self) -> None:
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
    """Synthesizes a list of bilingual SpeechSegments in sequence, inserting a natural

    prosodic silence frame between segments.
    """
    sample_rate = 24000
    for idx, seg in enumerate(segments):
        trimmed = seg.text.strip()
        if not trimmed:
            continue
        engine = synthesizer.get_tts(seg.language, seg.speed)
        stream = engine.synthesize(trimmed)
        async for ev in stream:
            yield ev.frame

        pause_ms = seg.pause_after_ms or (default_pause_ms if idx < len(segments) - 1 else 0)
        if pause_ms > 0:
            samples_per_channel = int(sample_rate * pause_ms / 1000)
            silence_data = np.zeros(samples_per_channel, dtype=np.int16)
            yield rtc.AudioFrame(
                data=silence_data.tobytes(),
                sample_rate=sample_rate,
                num_channels=1,
                samples_per_channel=samples_per_channel,
            )

