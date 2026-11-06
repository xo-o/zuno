import asyncio
import logging
import os
import ssl
from typing import Optional

import aiohttp
import certifi
from livekit.agents import APIConnectionError, APIStatusError, APITimeoutError, tts, utils
from livekit.agents.types import APIConnectOptions, DEFAULT_API_CONNECT_OPTIONS

logger = logging.getLogger("breeze-tts")


class BreezeChunkedStream(tts.ChunkedStream):
    def __init__(
        self,
        *,
        tts_instance: "BreezeTTS",
        input_text: str,
        conn_options: APIConnectOptions,
    ) -> None:
        super().__init__(tts=tts_instance, input_text=input_text, conn_options=conn_options)
        self._breeze = tts_instance

    async def _run(self, output_emitter: tts.AudioEmitter) -> None:
        url = f"https://api.breeze.blue/v1/text-to-speech/{self._breeze._voice_id}/stream?output_format=pcm"
        headers = {
            "xi-api-key": self._breeze._api_key,
            "Content-Type": "application/json",
        }
        payload = {
            "text": self._input_text,
            "model_id": self._breeze._model,
            "instructions": self._breeze._instructions,
            "voice_settings": {
                "guidance_scale": self._breeze._guidance_scale,
                "speed": self._breeze._speed,
            },
        }
        if self._breeze._language_code:
            payload["language_code"] = self._breeze._language_code

        session = self._breeze._get_session()
        try:
            async with session.post(
                url,
                headers=headers,
                json=payload,
                timeout=aiohttp.ClientTimeout(total=25, sock_connect=self._conn_options.timeout),
            ) as resp:
                if resp.status != 200:
                    err_text = await resp.text()
                    logger.error(f"Breeze TTS HTTP {resp.status}: {err_text}")
                    resp.raise_for_status()

                output_emitter.initialize(
                    request_id=utils.shortuuid(),
                    sample_rate=self._breeze.sample_rate,
                    num_channels=self._breeze.num_channels,
                    mime_type="audio/pcm",
                )

                async for chunk, _ in resp.content.iter_chunks():
                    if chunk:
                        output_emitter.push(chunk)

                output_emitter.flush()
        except asyncio.TimeoutError:
            raise APITimeoutError("Breeze TTS request timed out") from None
        except aiohttp.ClientResponseError as e:
            raise APIStatusError(
                message=e.message, status_code=e.status, request_id=None, body=None
            ) from None
        except Exception as e:
            raise APIConnectionError(f"Breeze TTS connection error: {e}") from e


class BreezeTTS(tts.TTS):
    def __init__(
        self,
        *,
        api_key: Optional[str] = None,
        voice_id: str = "voc_9f5gtax4x5ac",  # Miles (Conversational)
        model: str = "breeze-tts-2",
        instructions: str = "Natural, warm, engaging, and friendly conversational delivery with natural pauses.",
        language_code: Optional[str] = "en",
        guidance_scale: float = 3.0,
        speed: float = 1.0,
    ) -> None:
        super().__init__(
            capabilities=tts.TTSCapabilities(streaming=False),
            sample_rate=24000,
            num_channels=1,
        )
        self._api_key = api_key or os.environ.get("BREEZE_API_KEY", "")
        if not self._api_key:
            raise ValueError("BREEZE_API_KEY is required for BreezeTTS")

        self._voice_id = voice_id
        # breeze-tts-2 handles en and zh; other languages use breeze-tts-2-multilingual
        if model == "breeze-tts-2" and language_code and language_code.lower() not in ("en", "zh"):
            self._model = "breeze-tts-2-multilingual"
        else:
            self._model = model
        self._instructions = instructions
        self._language_code = language_code
        self._guidance_scale = guidance_scale
        self._speed = speed
        self._session: Optional[aiohttp.ClientSession] = None

    def _get_session(self) -> aiohttp.ClientSession:
        try:
            return utils.http_context.http_session()
        except RuntimeError:
            if self._session is None or self._session.closed:
                ssl_ctx = ssl.create_default_context(cafile=certifi.where())
                connector = aiohttp.TCPConnector(ssl=ssl_ctx)
                self._session = aiohttp.ClientSession(connector=connector)
            return self._session

    @property
    def model(self) -> str:
        return self._model

    @property
    def provider(self) -> str:
        return "breeze"

    def update_options(
        self,
        *,
        voice_id: Optional[str] = None,
        instructions: Optional[str] = None,
        language_code: Optional[str] = None,
        guidance_scale: Optional[float] = None,
        speed: Optional[float] = None,
    ) -> None:
        if voice_id is not None:
            self._voice_id = voice_id
        if instructions is not None:
            self._instructions = instructions
        if language_code is not None:
            self._language_code = language_code
        if guidance_scale is not None:
            self._guidance_scale = guidance_scale
        if speed is not None:
            self._speed = speed

    def synthesize(
        self, text: str, *, conn_options: APIConnectOptions = DEFAULT_API_CONNECT_OPTIONS
    ) -> tts.ChunkedStream:
        return BreezeChunkedStream(
            tts_instance=self, input_text=text, conn_options=conn_options
        )

    async def aclose(self) -> None:
        if self._session and not self._session.closed:
            await self._session.close()


def create_breeze_stream_adapter(
    *,
    api_key: Optional[str] = None,
    voice_id: str = "voc_9f5gtax4x5ac",
    model: str = "breeze-tts-2",
    instructions: str = "Natural, warm, engaging, and friendly conversational delivery with natural pauses.",
    language_code: Optional[str] = "en",
    guidance_scale: float = 3.0,
    speed: float = 1.0,
) -> tts.StreamAdapter:
    base_tts = BreezeTTS(
        api_key=api_key,
        voice_id=voice_id,
        model=model,
        instructions=instructions,
        language_code=language_code,
        guidance_scale=guidance_scale,
        speed=speed,
    )
    return tts.StreamAdapter(tts=base_tts, text_pacing=True)
