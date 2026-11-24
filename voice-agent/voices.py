import asyncio
import logging
import os
import ssl
from typing import Any, AsyncIterable, AsyncIterator, Optional

import aiohttp
import certifi
from livekit import rtc
from livekit.agents import APIConnectOptions, APIConnectionError, APIStatusError, APITimeoutError, tts, utils
from livekit.agents.types import DEFAULT_API_CONNECT_OPTIONS
import numpy as np

from bilingual import clean_text_for_tts, ensure_terminal_punct, iter_streaming_sentences

logger = logging.getLogger("voice-agent.voices")

DEFAULT_ELEVENLABS_MODEL = "eleven_v4_turbo"
ELEVENLABS_API_BASE = "https://api.elevenlabs.io/v1"
FALLBACK_PREMADE_VOICE_ID = "hpp4J3VqNfWAUOO0d1Us"  # Bella - fallback if a library voice is unavailable

# Curated AI Tutors with strict 1:1 avatar-to-voice mapping (ElevenLabs Eleven v4 Turbo multilingual).
TUTOR_CATALOG: dict[str, dict[str, str]] = {
    "sofia": {
        "name": "Sofía",
        "voice_preset": "warm",
        "voice_id": "DODLEQrClDo8wCz460ld",  # Lauren - Friendly, Comforting and Soft
    },
    "valeria": {
        "name": "Valeria",
        "voice_preset": "calm",
        "voice_id": "EXAVITQu4vr4xnSDxMaL",  # Sarah - Mature, Reassuring, Confident
    },
    "claire": {
        "name": "Claire",
        "voice_preset": "crisp",
        "voice_id": "Xb7hH8MSUJpSbSDYk0k2",  # Alice - Clear, Engaging Educator
    },
    "mateo": {
        "name": "Mateo",
        "voice_preset": "energetic",
        "voice_id": "TX3LPaxmHKxFdv7VOQHJ",  # Liam - Energetic
    },
    "lucas": {
        "name": "Lucas",
        "voice_preset": "steady",
        "voice_id": "cjVigY5qzO86Huf0OWal",  # Eric - Smooth, Trustworthy
    },
    "ethan": {
        "name": "Ethan",
        "voice_preset": "casual",
        "voice_id": "bIHbv24MWmeRgasZH58o",  # Will - Relaxed Optimist
    },
}

ELEVENLABS_VOICES: dict[str, str] = {
    spec["voice_preset"]: spec["voice_id"] for spec in TUTOR_CATALOG.values()
}

DEFAULT_ELEVENLABS_VOICE = ELEVENLABS_VOICES["warm"]
ALLOWED_VOICE_IDS = set(ELEVENLABS_VOICES.values()) | {FALLBACK_PREMADE_VOICE_ID}


def resolve_tutor_name(tutor_id: str | None = None, tutor_voice: str | None = None) -> str:
    if tutor_id:
        norm = tutor_id.strip().lower()
        if norm == "elena":
            return "Sofía"
        if norm in TUTOR_CATALOG:
            return TUTOR_CATALOG[norm]["name"]
    if tutor_voice:
        norm_v = tutor_voice.strip().lower()
        for spec in TUTOR_CATALOG.values():
            if spec["voice_preset"] == norm_v:
                return spec["name"]
    return "Sofía"


def resolve_elevenlabs_voice(
    voice_id: str | None = None,
    tutor_voice: str | None = None,
    tutor_id: str | None = None,
) -> str:
    """Always resolves to one of Zuno's verified ElevenLabs multilingual voices."""
    if tutor_id:
        norm_id = tutor_id.strip().lower()
        if norm_id == "elena":
            return TUTOR_CATALOG["sofia"]["voice_id"]
        if norm_id in TUTOR_CATALOG:
            return TUTOR_CATALOG[norm_id]["voice_id"]
    if tutor_voice and tutor_voice in ELEVENLABS_VOICES:
        return ELEVENLABS_VOICES[tutor_voice]
    if voice_id and voice_id in ALLOWED_VOICE_IDS:
        return voice_id
    return DEFAULT_ELEVENLABS_VOICE


def clamp_elevenlabs_speed(speed: float = 1.0) -> float:
    """Clamps speed to ElevenLabs' supported [0.70, 1.20] range."""
    return round(max(0.70, min(1.20, float(speed))), 2)


def _apply_fade_ramp(pcm_bytes: bytes, *, fade_in: bool = False, fade_out: bool = False) -> bytes:
    if not pcm_bytes or (not fade_in and not fade_out):
        return pcm_bytes
    even_len = (len(pcm_bytes) // 2) * 2
    if even_len == 0:
        return b""
    samples = np.frombuffer(pcm_bytes[:even_len], dtype=np.int16).copy()
    ramp_len = min(120, len(samples))  # 5ms ramp at 24kHz
    if ramp_len <= 0:
        return samples.tobytes()
    if fade_in:
        ramp = np.linspace(0.0, 1.0, ramp_len, dtype=np.float32)
        samples[:ramp_len] = (samples[:ramp_len].astype(np.float32) * ramp).astype(np.int16)
    if fade_out:
        ramp = np.linspace(1.0, 0.0, ramp_len, dtype=np.float32)
        samples[-ramp_len:] = (samples[-ramp_len:].astype(np.float32) * ramp).astype(np.int16)
    return samples.tobytes()


def _pcm_to_audio_frames(pcm_bytes: bytes, pause_after_ms: int = 0) -> list[rtc.AudioFrame]:
    """Converts 24kHz s16le PCM bytes into 20ms LiveKit AudioFrames with clean boundary ramps."""
    if not pcm_bytes:
        return []
    sample_rate = 24000
    chunk_samples = 480  # 20ms at 24kHz
    frame_bytes = chunk_samples * 2

    even_len = (len(pcm_bytes) // 2) * 2
    if even_len == 0:
        return []

    ramped_bytes = _apply_fade_ramp(pcm_bytes[:even_len], fade_in=True, fade_out=True)
    frames: list[rtc.AudioFrame] = []
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


class ElevenLabsSynthesizer:
    """Unified ElevenLabs `eleven_v4_turbo` synthesizer with automatic multilingual code-switching.

    Omit `language_code` so ElevenLabs runs in `Auto` language mode—voicing mixed-language utterances
    (e.g. 'Hola, how are you? Estoy bien.') in a single continuous pass with authentic native accents.
    """

    def __init__(
        self,
        voice_id: str | None = None,
        tutor_voice: str | None = None,
        tutor_id: str | None = None,
        model: str | None = None,
        api_key: str | None = None,
    ) -> None:
        self.voice_id = resolve_elevenlabs_voice(voice_id, tutor_voice, tutor_id)
        self.tutor_voice = tutor_voice
        self.tutor_id = tutor_id
        self.model = (model or os.environ.get("ELEVENLABS_MODEL") or DEFAULT_ELEVENLABS_MODEL).strip()
        self.api_key = (api_key or os.environ.get("ELEVENLABS_API_KEY") or os.environ.get("ELEVEN_API_KEY") or "").strip()
        self._session: Optional[aiohttp.ClientSession] = None

    async def _get_session(self) -> aiohttp.ClientSession:
        if self._session is None or self._session.closed:
            ssl_ctx = ssl.create_default_context(cafile=certifi.where())
            self._session = aiohttp.ClientSession(
                connector=aiohttp.TCPConnector(ssl=ssl_ctx),
                timeout=aiohttp.ClientTimeout(total=25, sock_connect=6),
            )
        return self._session

    def _build_payload(self, text: str, speed: float = 1.0) -> dict[str, Any]:
        speed_clamped = clamp_elevenlabs_speed(speed)
        payload: dict[str, Any] = {
            "text": text,
            "model_id": self.model,
            "apply_text_normalization": "auto",
        }
        if speed_clamped != 1.0:
            payload["voice_settings"] = {"speed": speed_clamped}
        return payload

    async def stream_pcm_chunks(self, text: str, speed: float = 1.0) -> AsyncIterator[bytes]:
        """Streams raw 24kHz s16le PCM chunks over HTTP chunked transfer encoding for ~100ms TTFB."""
        cleaned = ensure_terminal_punct(clean_text_for_tts(text))
        if not cleaned or not self.api_key:
            return

        payload = self._build_payload(cleaned, speed)
        session = await self._get_session()
        active_voice = self.voice_id

        for attempt in range(2):
            url = f"{ELEVENLABS_API_BASE}/text-to-speech/{active_voice}/stream?output_format=pcm_24000"
            try:
                async with session.post(
                    url,
                    headers={
                        "xi-api-key": self.api_key,
                        "Content-Type": "application/json",
                    },
                    json=payload,
                ) as resp:
                    if resp.status == 200:
                        carry = b""
                        async for chunk in resp.content.iter_chunked(4096):
                            if not chunk:
                                continue
                            buf = carry + chunk
                            even_len = (len(buf) // 2) * 2
                            if even_len > 0:
                                yield buf[:even_len]
                            carry = buf[even_len:]
                        return

                    if resp.status == 402 and active_voice != FALLBACK_PREMADE_VOICE_ID and attempt == 0:
                        logger.warning(
                            "Library voice %s returned 402; falling back to premade voice %s",
                            active_voice,
                            FALLBACK_PREMADE_VOICE_ID,
                        )
                        active_voice = FALLBACK_PREMADE_VOICE_ID
                        continue

                    if resp.status == 429 and attempt == 0:
                        await asyncio.sleep(0.3)
                        continue

                    err_body = await resp.text()
                    logger.warning("ElevenLabs TTS stream failed (%s): %s", resp.status, err_body[:240])
                    return
            except asyncio.CancelledError:
                raise
            except Exception as e:
                if attempt == 0:
                    await asyncio.sleep(0.2)
                else:
                    logger.warning("ElevenLabs TTS request error: %s", e)
                    return

    async def fetch_pcm_bytes(self, text: str, speed: float = 1.0) -> bytes:
        """Fetches the complete 24kHz s16le PCM buffer for a sentence (used for background prefetching)."""
        chunks: list[bytes] = []
        async for chunk in self.stream_pcm_chunks(text, speed=speed):
            chunks.append(chunk)
        return b"".join(chunks)

    async def aclose(self) -> None:
        if self._session and not self._session.closed:
            await self._session.close()
            self._session = None


class _ElevenLabsChunkedStream(tts.ChunkedStream):
    def __init__(
        self,
        *,
        tts_engine: "ElevenLabsTTS",
        input_text: str,
        conn_options: APIConnectOptions,
    ) -> None:
        super().__init__(tts=tts_engine, input_text=input_text, conn_options=conn_options)
        self._tts_engine = tts_engine

    async def _run(self, output_emitter: tts.AudioEmitter) -> None:
        cleaned = ensure_terminal_punct(clean_text_for_tts(self._input_text))
        output_emitter.initialize(
            request_id=utils.shortuuid(),
            sample_rate=24000,
            num_channels=1,
            mime_type="audio/pcm",
        )
        if not cleaned:
            output_emitter.flush()
            return

        try:
            async for pcm_chunk in self._tts_engine.synthesizer.stream_pcm_chunks(
                cleaned, speed=self._tts_engine.speed
            ):
                output_emitter.push(pcm_chunk)
            output_emitter.flush()
        except asyncio.TimeoutError as e:
            raise APITimeoutError() from e
        except APIStatusError:
            raise
        except Exception as e:
            raise APIConnectionError() from e


class ElevenLabsTTS(tts.TTS):
    """LiveKit Agents `tts.TTS` implementation backed by ElevenLabs `eleven_v4_turbo` (`pcm_24000`)."""

    def __init__(
        self,
        *,
        synthesizer: ElevenLabsSynthesizer,
        speed: float = 1.0,
    ) -> None:
        super().__init__(
            capabilities=tts.TTSCapabilities(streaming=False),
            sample_rate=24000,
            num_channels=1,
        )
        self.synthesizer = synthesizer
        self.speed = clamp_elevenlabs_speed(speed)

    @property
    def model(self) -> str:
        return self.synthesizer.model

    @property
    def provider(self) -> str:
        return "elevenlabs"

    def update_options(self, *, speed: float | None = None, voice_id: str | None = None) -> None:
        if speed is not None:
            self.speed = clamp_elevenlabs_speed(speed)
        if voice_id:
            self.synthesizer.voice_id = resolve_elevenlabs_voice(voice_id=voice_id)

    def synthesize(
        self,
        text: str,
        *,
        conn_options: APIConnectOptions = DEFAULT_API_CONNECT_OPTIONS,
    ) -> tts.ChunkedStream:
        return _ElevenLabsChunkedStream(
            tts_engine=self,
            input_text=text,
            conn_options=conn_options,
        )

    async def aclose(self) -> None:
        await self.synthesizer.aclose()


def create_elevenlabs_tts(
    voice_id: str | None = None,
    tutor_voice: str | None = None,
    tutor_id: str | None = None,
    speed: float = 1.0,
    synthesizer: ElevenLabsSynthesizer | None = None,
) -> ElevenLabsTTS:
    synth = synthesizer or ElevenLabsSynthesizer(
        voice_id=voice_id,
        tutor_voice=tutor_voice,
        tutor_id=tutor_id,
    )
    return ElevenLabsTTS(synthesizer=synth, speed=speed)


async def synthesize_speech(
    synthesizer: ElevenLabsSynthesizer,
    text: str,
    *,
    speed: float = 1.0,
) -> AsyncIterator[rtc.AudioFrame]:
    """Streams a complete utterance (monolingual or mixed-language) in a single continuous ElevenLabs call."""
    cleaned = clean_text_for_tts(text)
    if not cleaned:
        return

    sample_rate = 24000
    frame_bytes = 480 * 2  # 20ms at 24kHz mono 16-bit
    buf = bytearray()
    first_frame = True

    async for pcm_chunk in synthesizer.stream_pcm_chunks(cleaned, speed=speed):
        buf.extend(pcm_chunk)
        # Keep at least 1 frame in reserve so we can apply a clean fade-out ramp to the very last frame
        while len(buf) >= frame_bytes * 2:
            raw_frame = bytes(buf[:frame_bytes])
            del buf[:frame_bytes]
            if first_frame:
                raw_frame = _apply_fade_ramp(raw_frame, fade_in=True)
                first_frame = False
            yield rtc.AudioFrame(
                data=raw_frame,
                sample_rate=sample_rate,
                num_channels=1,
                samples_per_channel=len(raw_frame) // 2,
            )

    even_rem = (len(buf) // 2) * 2
    if even_rem > 0:
        tail = _apply_fade_ramp(
            bytes(buf[:even_rem]),
            fade_in=first_frame,
            fade_out=True,
        )
        for off in range(0, len(tail), frame_bytes):
            chunk = tail[off : off + frame_bytes]
            n_samples = len(chunk) // 2
            if n_samples > 0:
                yield rtc.AudioFrame(
                    data=chunk,
                    sample_rate=sample_rate,
                    num_channels=1,
                    samples_per_channel=n_samples,
                )


_QUEUE_UNSET = object()


async def stream_llm_tts(
    synthesizer: ElevenLabsSynthesizer,
    text_stream: AsyncIterable[str],
    *,
    speed: float = 1.0,
) -> AsyncIterator[rtc.AudioFrame]:
    """Streams live LLM tokens sentence-by-sentence through ElevenLabs `eleven_v4_turbo` (`Auto` language).

    Mixed-language sentences stay 100% intact (zero dual-speak splitting). Sentence 1 streams immediately
    while subsequent sentences prefetch in the background so multi-sentence replies play seamlessly.
    """
    queue: asyncio.Queue[tuple[int, str, asyncio.Task[bytes] | None] | None] = asyncio.Queue()
    inflight_tasks: list[asyncio.Task[bytes]] = []

    async def _producer() -> None:
        idx = 0
        try:
            async for sentence in iter_streaming_sentences(text_stream):
                if not sentence.strip():
                    continue
                if idx == 0:
                    # First sentence streams directly via `synthesize_speech` for fastest TTFB
                    await queue.put((idx, sentence, None))
                else:
                    task = asyncio.create_task(
                        synthesizer.fetch_pcm_bytes(sentence, speed=speed)
                    )
                    inflight_tasks.append(task)
                    await queue.put((idx, sentence, task))
                idx += 1
        finally:
            await queue.put(None)

    producer_task = asyncio.create_task(_producer())
    try:
        current = await queue.get()
        while current is not None:
            idx, sentence, pcm_task = current
            next_item: Any = _QUEUE_UNSET
            if not queue.empty():
                next_item = queue.get_nowait()

            if pcm_task is None:
                # Stream first sentence directly chunk-by-chunk as bytes arrive from ElevenLabs
                async for frame in synthesize_speech(synthesizer, sentence, speed=speed):
                    yield frame
                if next_item is _QUEUE_UNSET and not queue.empty():
                    next_item = queue.get_nowait()
                if next_item is not _QUEUE_UNSET and next_item is not None:
                    # Brief 60ms natural breath before prefetched sentence 2
                    for frame in _pcm_to_audio_frames(np.zeros(24, dtype=np.int16).tobytes(), pause_after_ms=55):
                        yield frame
            else:
                pcm_bytes = await pcm_task
                if next_item is _QUEUE_UNSET and not queue.empty():
                    next_item = queue.get_nowait()
                if pcm_bytes:
                    has_more = next_item is not _QUEUE_UNSET and next_item is not None
                    for frame in _pcm_to_audio_frames(pcm_bytes, pause_after_ms=60 if has_more else 0):
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
