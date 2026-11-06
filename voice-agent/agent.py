import asyncio
import json
import logging
import os
import time
from dotenv import load_dotenv

from google.cloud import texttospeech
from livekit import rtc
from livekit.agents import (
    Agent,
    AgentServer,
    AgentSession,
    JobContext,
    cli,
    room_io,
)
import livekit.plugins.cartesia as cartesia
import livekit.plugins.deepgram as dg
import livekit.plugins.google as gg
import livekit.plugins.silero as silero
from breeze_tts import BreezeTTS, create_breeze_stream_adapter

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("voice-agent")

server = AgentServer()

INSTRUCTIONS = (
    "You are Nova, an ultra-responsive, friendly, and natural conversational voice AI. "
    "You are speaking live with the user over a real-time voice call. "
    "CRITICAL FORMATTING RULES: "
    "- Generate ONLY plain conversational speech in the language the user speaks. "
    "- Keep responses short, concise, and natural: 1 to 2 sentences typically (under 120 characters). "
    "- NEVER use markdown headers (#), bold (**), italics (*), bullets (-), or numbered lists. "
    "- NEVER use brackets, parentheticals, or stage directions like [pause], [laughs], or (sighs). "
    "- Respond directly, warmly, and immediately."
)

GCP_LANGUAGE_MAP = {
    "en": "en-US",
    "es": "es-ES",
    "fr": "fr-FR",
    "de": "de-DE",
    "it": "it-IT",
    "pt": "pt-BR",
    "ja": "ja-JP",
    "ko": "ko-KR",
    "zh": "cmn-CN",
    "hi": "hi-IN",
    "ar": "ar-XA",
    "ru": "ru-RU",
    "nl": "nl-NL",
    "sv": "sv-SE",
    "tr": "tr-TR",
}

LANGUAGE_CONFIRMATIONS = {
    "es": "¡Listo! Voz cambiada a español.",
    "fr": "Bonjour! Voix changée en français.",
    "de": "Hallo! Stimme auf Deutsch umgestellt.",
    "it": "Ciao! Voce cambiata in italiano.",
    "pt": "Olá! Voz alterada para português.",
    "ja": "こんにちは！音声を日本語に切り替えました。",
    "ko": "안녕하세요! 음성을 한국어로 변경했습니다.",
    "zh": "你好！已切换为中文语音。",
    "hi": "नमस्ते! आवाज हिंदी में बदल दी गई है।",
    "ar": "مرحباً! تم تغيير الصوت.",
    "ru": "Здравствуйте! Голос успешно переключен.",
    "nl": "Hallo! Stem gewijzigd in het Nederlands.",
    "sv": "Hej! Rösten ändrad till svenska.",
    "tr": "Merhaba! Ses Türkçe olarak değiştirildi.",
}


def create_tts(provider: str, voice_id: str, language: str = "en"):
    provider_clean = provider.lower()
    if provider_clean in ("breeze", "breezeblue"):
        # Breeze TTS 2 (Real-time with Voice Direction & natural pacing)
        return create_breeze_stream_adapter(
            api_key=os.environ.get("BREEZE_API_KEY"),
            voice_id=voice_id,
            model="breeze-tts-2",
            instructions="Natural, warm, engaging, and friendly conversational delivery with natural pauses.",
            language_code=language,
            guidance_scale=3.0,
            speed=1.0,
        )
    elif provider_clean == "cartesia":
        word_timestamps = language in ("en", "de", "es", "fr")
        return cartesia.TTS(
            api_key=os.environ.get("CARTESIA_API_KEY"),
            model="sonic-3",
            voice=voice_id,
            language=language,
            word_timestamps=word_timestamps,
        )
    elif provider_clean in ("gcp", "google"):
        lang_code = GCP_LANGUAGE_MAP.get(language, "en-US")
        creds_file = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
        tts_kwargs = {
            "voice_name": voice_id,
            "model_name": "gemini-2.5-flash-tts",
            "language": lang_code,
            "audio_encoding": texttospeech.AudioEncoding.LINEAR16,
            "sample_rate": 24000,
        }
        if creds_file and os.path.exists(creds_file):
            tts_kwargs["credentials_file"] = creds_file
        return gg.TTS(**tts_kwargs)
    else:
        # Deepgram: Use Aura-2 for aura models, Flux v2 otherwise
        if str(voice_id).startswith("aura"):
            return dg.TTS(
                model=voice_id,
                api_key=os.environ.get("DEEPGRAM_API_KEY"),
            )
        return dg.TTSv2(
            model=voice_id,
            api_key=os.environ.get("DEEPGRAM_API_KEY"),
        )


def create_stt(language: str = "en"):
    if language == "en":
        return dg.STTv2(
            model="flux-general-en",
            eager_eot_threshold=0.7,
            eot_threshold=0.7,
            eot_timeout_ms=3000,
            api_key=os.environ.get("DEEPGRAM_API_KEY"),
        )
    else:
        # Native language recognition with fast 25ms endpointing
        return dg.STT(
            model="nova-3",
            language=language,
            endpointing_ms=25,
            interim_results=True,
            api_key=os.environ.get("DEEPGRAM_API_KEY"),
        )


class ConversationalAgent(Agent):
    def __init__(self, tts=None, stt=None, language: str = "en") -> None:
        self.language = language
        super().__init__(instructions=INSTRUCTIONS, tts=tts, stt=stt)

    async def on_enter(self) -> None:
        if self.language in LANGUAGE_CONFIRMATIONS:
            greeting = LANGUAGE_CONFIRMATIONS[self.language]
            self.session.generate_reply(
                instructions=f"Say: '{greeting}' and ask in 1 short sentence how you can assist."
            )
        else:
            self.session.generate_reply(
                instructions="Say a warm, brief one-sentence hello to the user and ask what they would like to talk about today."
            )


@server.rtc_session()
async def entrypoint(ctx: JobContext) -> None:
    logger.info(f"Connecting agent to room: {ctx.room.name}")
    await ctx.connect()

    logger.info("Waiting for participant...")
    participant = await ctx.wait_for_participant()
    logger.info(f"Participant joined: {participant.identity}, metadata: {participant.metadata}")

    current_provider = "breeze"
    current_voice = "voc_9f5gtax4x5ac"
    current_language = "en"

    # Inspect participant metadata if preset at connection time
    if participant.metadata:
        try:
            meta = json.loads(participant.metadata)
            if "provider" in meta:
                current_provider = meta["provider"]
            if "voice" in meta:
                current_voice = meta["voice"]
            if "language" in meta:
                current_language = meta["language"]
            logger.info(
                f"Initialized from participant metadata: provider={current_provider}, voice={current_voice}, lang={current_language}"
            )
        except Exception as e:
            logger.warning(f"Error parsing metadata: {e}")

    # Fast VAD for instant barge-in cut-off
    vad = silero.VAD.load(min_speech_duration=0.05, min_silence_duration=0.25)
    stt = create_stt(current_language)
    llm = gg.LLM(model="gemini-2.5-flash", temperature=0.4)
    tts = create_tts(current_provider, current_voice, current_language)

    agent = ConversationalAgent(tts=tts, stt=stt, language=current_language)

    session = AgentSession(
        stt=stt,
        vad=vad,
        llm=llm,
        tts=tts,
    )

    last_user_speech_end = 0.0

    async def broadcast_event(data: dict) -> None:
        try:
            payload = json.dumps(data).encode("utf-8")
            await ctx.room.local_participant.publish_data(payload, reliable=True)
        except Exception as e:
            logger.debug(f"Failed to publish data event: {e}")

    @session.on("agent_state_changed")
    def _on_state_changed(ev) -> None:
        nonlocal last_user_speech_end
        new_state = str(ev.new_state).lower()
        logger.info(f"Agent state changed: {new_state}")

        asyncio.create_task(
            broadcast_event({
                "type": "agent_state",
                "state": new_state,
            })
        )

        # Broadcast round-trip latency when agent starts speaking after user input
        if "speaking" in new_state and last_user_speech_end > 0:
            total_latency_ms = (time.time() - last_user_speech_end) * 1000
            last_user_speech_end = 0.0
            asyncio.create_task(
                broadcast_event({
                    "type": "metrics",
                    "total_ms": round(total_latency_ms),
                    "voice": current_voice,
                    "provider": current_provider,
                })
            )

    @session.on("user_input_transcribed")
    def _on_user_input(ev) -> None:
        nonlocal last_user_speech_end
        logger.info(f"User transcript (final={ev.is_final}): {ev.transcript}")
        if ev.is_final:
            last_user_speech_end = time.time()

        asyncio.create_task(
            broadcast_event({
                "type": "transcript",
                "speaker": "user",
                "text": ev.transcript,
                "is_final": ev.is_final,
            })
        )

    @session.on("conversation_item_added")
    def _on_item_added(ev) -> None:
        item = ev.item
        if hasattr(item, "role") and item.role == "assistant":
            content = getattr(item, "content", "")
            if isinstance(content, list):
                content = " ".join(str(part) for part in content)
            logger.info(f"Agent reply: {content}")
            asyncio.create_task(
                broadcast_event({
                    "type": "transcript",
                    "speaker": "agent",
                    "text": str(content),
                    "is_final": True,
                })
            )

    # Listen for voice changes from client over WebRTC Data Channel
    @ctx.room.on("data_received")
    def _on_client_data(packet: rtc.DataPacket) -> None:
        nonlocal current_provider, current_voice, current_language
        try:
            text = packet.data.decode("utf-8")
            data = json.loads(text)
            if data.get("type") == "change_voice":
                provider = data.get("provider", "deepgram")
                voice_id = data.get("voice", "flux-alexis-en")
                lang = data.get("language", "en")
                logger.info(f"Dynamic voice switch requested: provider={provider}, voice={voice_id}, lang={lang}")

                current_provider = provider
                current_voice = voice_id
                current_language = lang

                new_tts = create_tts(current_provider, current_voice, current_language)
                new_stt = create_stt(current_language)

                # 1. Update session default references
                session._tts = new_tts
                session._stt = new_stt

                # 2. Update active activity models if activity is alive
                if getattr(session, "_activity", None) is not None:
                    session._activity._update_models(new_tts=new_tts, new_stt=new_stt)

                # 3. Update active agent
                session.update_agent(ConversationalAgent(tts=new_tts, stt=new_stt, language=current_language))

                asyncio.create_task(
                    broadcast_event({
                        "type": "voice_changed",
                        "provider": current_provider,
                        "voice": current_voice,
                        "language": current_language,
                    })
                )

                # Instant spoken confirmation in the newly switched voice & language
                if current_language in LANGUAGE_CONFIRMATIONS:
                    confirm_text = LANGUAGE_CONFIRMATIONS[current_language]
                else:
                    prov_label = (
                        "Breeze Blue" if current_provider in ("breeze", "breezeblue")
                        else "Google Cloud" if current_provider in ("gcp", "google")
                        else "Cartesia" if current_provider == "cartesia"
                        else "Deepgram"
                    )
                    confirm_text = f"Switched to {prov_label}."

                asyncio.create_task(session.say(confirm_text))
        except Exception as e:
            logger.warning(f"Error handling client data packet: {e}", exc_info=True)

    await session.start(
        agent=agent,
        room=ctx.room,
        room_options=room_io.RoomOptions(
            audio_input=room_io.AudioInputOptions(),
        ),
    )


if __name__ == "__main__":
    cli.run_app(server)
