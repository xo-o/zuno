import asyncio
import json
import logging
import os
import time

import certifi
from dotenv import load_dotenv
from google.genai import types

# Python builds on macOS often ship without a CA bundle, which breaks TLS to LiveKit Cloud.
os.environ.setdefault("SSL_CERT_FILE", certifi.where())

from livekit import rtc
from livekit.agents import (
    Agent,
    AgentServer,
    AgentSession,
    JobContext,
    cli,
    room_io,
)
import livekit.plugins.deepgram as dg
import livekit.plugins.google as gg
import livekit.plugins.silero as silero

from bilingual import segment_bilingual_text, strip_lang_tags
from lesson_flow import LessonAgent, LessonOrchestrator
from voices import (
    BilingualCartesiaSynthesizer,
    DEFAULT_CARTESIA_VOICE,
    create_cartesia_tts,
    resolve_cartesia_voice,
    synthesize_bilingual_segments,
    update_cartesia_tts,
)

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("voice-agent")

server = AgentServer()

INSTRUCTIONS = (
    "You are Zuno, an ultra-responsive, warm, and natural conversational language tutor. "
    "You are speaking live with the learner over a real-time voice call. "
    "CRITICAL FORMATTING RULES: "
    "- Generate ONLY plain conversational speech in the target language unless asked otherwise. "
    "- Keep responses short, concise, and natural: 1 to 2 sentences typically (under 120 characters). "
    "- NEVER use markdown headers (#), bold (**), italics (*), bullets (-), or numbered lists. "
    "- NEVER use brackets, parentheticals, or stage directions like [pause], [laughs], or (sighs). "
    "- Respond directly, warmly, and immediately."
)

# Deepgram Nova-3 language codes for accents that have their own model variant.
STT_ACCENT_CODES = {
    ("en", "uk"): "en-GB",
    ("en", "australia"): "en-AU",
    ("es", "latam"): "es-419",
    ("fr", "canada"): "fr-CA",
    ("de", "switzerland"): "de-CH",
    ("nl", "belgium"): "nl-BE",
    ("pt", "brazil"): "pt-BR",
    ("pt", "portugal"): "pt-PT",
    ("zh", "taiwan"): "zh-TW",
}


def create_stt(language: str = "en", accent: str | None = None):
    if language == "en" and accent in (None, "us"):
        return dg.STTv2(
            model="flux-general-en",
            eager_eot_threshold=0.7,
            eot_threshold=0.7,
            eot_timeout_ms=3000,
            api_key=os.environ.get("DEEPGRAM_API_KEY"),
        )
    return dg.STT(
        model="nova-3",
        language=STT_ACCENT_CODES.get((language, accent or ""), language),
        endpointing_ms=25,
        interim_results=True,
        api_key=os.environ.get("DEEPGRAM_API_KEY"),
    )


class ConversationalAgent(Agent):
    def __init__(
        self,
        tts=None,
        stt=None,
        language: str = "en",
        native_language: str = "en",
        tutor_instructions: str | None = None,
        resume: bool = False,
        opening: str | None = None,
        silent_enter: bool = False,
    ) -> None:
        self.language = language
        self.native_language = native_language
        self.tutor_instructions = tutor_instructions
        self.resume = resume
        self.opening = opening
        self.silent_enter = silent_enter
        instructions = f"{tutor_instructions}\n\n{INSTRUCTIONS}" if tutor_instructions else INSTRUCTIONS
        super().__init__(instructions=instructions, tts=tts, stt=stt)

    async def transcription_node(self, text, model_settings):
        async for delta in text:
            if isinstance(delta, str):
                yield strip_lang_tags(delta)
            else:
                yield delta

    async def tts_node(self, text, model_settings):
        chunks: list[str] = []
        async for delta in text:
            chunks.append(delta)
        full_text = "".join(chunks)

        if "[" in full_text and (f"[{self.native_language}]" in full_text or f"[{self.language}]" in full_text or "<lang=" in full_text):
            segments = segment_bilingual_text(
                full_text,
                native_lang=self.native_language,
                target_lang=self.language,
                default_lang=self.language,
            )
            voice_id = getattr(self.tts, "_opts", None) and self.tts._opts.voice
            synthesizer = BilingualCartesiaSynthesizer(voice_id=voice_id)
            async for frame in synthesize_bilingual_segments(synthesizer, segments):
                yield frame
        else:
            async def _forward():
                for c in chunks:
                    yield c
            async for frame in Agent.default.tts_node(self, _forward(), model_settings):
                yield frame

    async def on_enter(self) -> None:
        if self.silent_enter:
            return
        if self.opening:
            self.session.say(self.opening, add_to_chat_ctx=False)
            return
        if self.resume:
            self.session.generate_reply(
                instructions=(
                    "The learner just switched to a live voice call in the middle of this conversation. "
                    "If their last message has not been answered yet, answer it. Otherwise, continue "
                    "naturally from where the conversation left off. One short sentence, in your role."
                )
            )
            return
        if self.tutor_instructions:
            self.session.generate_reply(
                instructions="Greet the learner in one short sentence and invite them to start, following your role."
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
    logger.info(f"Participant joined: {participant.identity}")

    meta: dict = {}
    if participant.metadata:
        try:
            meta = json.loads(participant.metadata)
        except Exception as e:
            logger.warning(f"Error parsing metadata: {e}")

    mode = str(meta.get("mode") or ("lesson" if meta.get("curriculum") else "conversation"))
    tutor_voice = meta.get("tutorVoice")
    current_voice = resolve_cartesia_voice(meta.get("voice"), tutor_voice)
    current_language = str(meta.get("targetLanguage") or meta.get("language") or "en")
    current_accent = meta.get("accent") or None
    hands_free = bool(meta.get("handsFree", True))
    tutor_instructions = meta.get("instructions") or None
    resume = bool(meta.get("resume", False))
    opening = meta.get("opening") or None

    logger.info(
        f"Initialized session: mode={mode}, provider=cartesia, voice={current_voice}, lang={current_language}"
    )

    vad = silero.VAD.load(min_speech_duration=0.05, min_silence_duration=0.25)
    stt = create_stt(current_language, current_accent)
    llm = gg.LLM(
        model="gemini-2.5-flash",
        temperature=0.4,
        http_options=types.HttpOptions(api_version="v1beta"),
    )
    tts = create_cartesia_tts(
        voice_id=current_voice,
        tutor_voice=tutor_voice,
        language=current_language,
    )

    async def broadcast_event(data: dict) -> None:
        try:
            payload = json.dumps(data).encode("utf-8")
            await ctx.room.local_participant.publish_data(payload, reliable=True)
        except Exception as e:
            logger.debug(f"Failed to publish data event: {e}")

    lesson_orchestrator: LessonOrchestrator | None = None
    if mode == "lesson":
        lesson_orchestrator = LessonOrchestrator(
            meta=meta,
            tts=tts,
            broadcast=broadcast_event,
        )
        agent: Agent = LessonAgent(
            orchestrator=lesson_orchestrator,
            tts=tts,
            stt=stt,
        )
        initial_endpointing = 0.6
    else:
        agent = ConversationalAgent(
            tts=tts,
            stt=stt,
            language=current_language,
            native_language=str(meta.get("nativeLanguage") or "en"),
            tutor_instructions=tutor_instructions,
            resume=resume,
            opening=opening,
        )
        initial_endpointing = 0.5 if hands_free else 1.6

    session = AgentSession(
        stt=stt,
        vad=vad,
        llm=llm,
        tts=tts,
        min_endpointing_delay=initial_endpointing,
    )

    if lesson_orchestrator is not None:
        lesson_orchestrator.session = session

    last_user_speech_end = 0.0

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

        if "speaking" in new_state and last_user_speech_end > 0:
            total_latency_ms = (time.time() - last_user_speech_end) * 1000
            last_user_speech_end = 0.0
            asyncio.create_task(
                broadcast_event({
                    "type": "metrics",
                    "total_ms": round(total_latency_ms),
                    "voice": current_voice,
                    "provider": "cartesia",
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
            text = str(content).strip()
            if not text:
                return
            clean_text = strip_lang_tags(text)
            logger.info(f"Agent reply: {clean_text}")
            if lesson_orchestrator is not None and lesson_orchestrator.stage == "roleplay":
                lesson_orchestrator.last_tutor_line = clean_text
            asyncio.create_task(
                broadcast_event({
                    "type": "transcript",
                    "speaker": "agent",
                    "text": clean_text,
                    "is_final": True,
                })
            )

    @ctx.room.on("data_received")
    def _on_client_data(packet: rtc.DataPacket) -> None:
        nonlocal current_language
        try:
            text = packet.data.decode("utf-8")
            data = json.loads(text)
            msg_type = data.get("type")

            if msg_type == "interrupt":
                try:
                    session.interrupt()
                except Exception:
                    pass
                return

            if msg_type == "lesson_action" and lesson_orchestrator is not None:
                action = str(data.get("action") or "")
                payload = data.get("payload")
                asyncio.create_task(
                    lesson_orchestrator.handle_client_action(
                        action=action,
                        payload=str(payload) if payload is not None else None,
                    )
                )
                return

            if msg_type == "change_language":
                lang = str(data.get("language") or current_language)
                current_language = lang
                update_cartesia_tts(tts, language=current_language, speed=1.0)
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
