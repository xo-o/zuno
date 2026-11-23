import asyncio
import json
import logging
import os
import time

import certifi
from dotenv import load_dotenv
from google import genai
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
    function_tool,
    room_io,
)
import livekit.plugins.deepgram as dg
import livekit.plugins.google as gg
import livekit.plugins.silero as silero

from avatar_session import MuseTalkAvatarSession
from bilingual import (
    detect_clause_language,
    segment_bilingual_text,
    strip_lang_tags,
    strip_lang_tags_stream,
)
from lesson_flow import LessonAgent, LessonOrchestrator
from onboarding_flow import OnboardingAgent, OnboardingOrchestrator
from roleplay_flow import RoleplayAgent, RoleplayOrchestrator
from voices import (
    BilingualCartesiaSynthesizer,
    create_cartesia_tts,
    resolve_cartesia_voice,
    stream_bilingual_tts,
    synthesize_bilingual_segments,
    update_cartesia_tts,
)

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("voice-agent")

server = AgentServer()

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

NOVA3_MULTI_LANGS = {"en", "es", "fr", "de", "hi", "ru", "pt", "ja", "it", "nl"}


def can_use_multi_stt(language: str = "en", native_language: str | None = None) -> bool:
    base_target = (language or "en").lower().split("-")[0]
    base_native = (native_language or "").lower().split("-")[0]
    return bool(
        base_native
        and base_native != base_target
        and base_target in NOVA3_MULTI_LANGS
        and base_native in NOVA3_MULTI_LANGS
    )


def resolve_stt_code(language: str, accent: str | None = None) -> str:
    base = (language or "en").lower().split("-")[0]
    return STT_ACCENT_CODES.get((base, accent or ""), base)


def create_stt(
    language: str = "en",
    accent: str | None = None,
    native_language: str | None = None,
    multilingual: bool = False,
    allow_dynamic_switch: bool = False,
):
    if multilingual and can_use_multi_stt(language, native_language):
        return dg.STT(
            model="nova-3",
            language="multi",
            endpointing_ms=25,
            interim_results=True,
            api_key=os.environ.get("DEEPGRAM_API_KEY"),
        )
    if language == "en" and accent in (None, "us") and not allow_dynamic_switch:
        return dg.STTv2(
            model="flux-general-en",
            eager_eot_threshold=0.7,
            eot_threshold=0.7,
            eot_timeout_ms=3000,
            api_key=os.environ.get("DEEPGRAM_API_KEY"),
        )
    return dg.STT(
        model="nova-3",
        language=resolve_stt_code(language, accent),
        endpointing_ms=25,
        interim_results=True,
        api_key=os.environ.get("DEEPGRAM_API_KEY"),
    )


def _build_voice_Guardrails(
    *,
    is_free_talk: bool,
    native_code: str,
    native_name: str,
    target_code: str,
    target_name: str,
    variety_name: str,
    multi_stt_active: bool,
) -> str:
    if is_free_talk:
        stt_rule = (
            ""
            if multi_stt_active
            else (
                f'- LISTENING LANGUAGE TOOL: If the learner asks to practice speaking in {target_name} or is about to repeat '
                f'a {target_name} word for pronunciation feedback, call `set_listening_language("{target_code}")`; if they switch '
                f'back to {native_name}, call `set_listening_language("{native_code}")`.\n'
            )
        )
        return (
            "CRITICAL FREE TALK VOICE & BILINGUAL RULES:\n"
            f"- USER-LANGUAGE DEFAULT: Speak in {native_name} wrapped in <lang code=\"{native_code}\">...</lang> by default. "
            "Chat warmly and naturally about ANY topic the learner brings up (daily life, travel, culture, work, hobbies, grammar, or vocabulary).\n"
            f"- DYNAMIC TARGET-LANGUAGE SWITCHING: Whenever you say any {target_name} ({variety_name}) word, phrase, or sentence, "
            f'wrap it in <lang code="{target_code}">...</lang> so it is voiced with authentic native phonetics. '
            f"If the learner speaks in {target_name} or asks to practice in {target_name}, mirror and converse in "
            f'<lang code="{target_code}">...</lang>, switching back smoothly to <lang code="{native_code}">...</lang> whenever they speak {native_name} or ask for help.\n'
            f"- PRONUNCIATION & 'HOW DO I SAY' COACHING: When the learner asks how to say or pronounce something in {target_name}, "
            f'give a brief 1-sentence tip in <lang code="{native_code}">...</lang>, model it naturally in <lang code="{target_code}">...</lang>, '
            f'model it slowly in <lang code="{target_code}" speed="slow">...</lang> when pronunciation is asked, and invite them to try it. '
            "NEVER write fake English phonetic spellings (like 'reh-free-heh-rah-dor').\n"
            f"{stt_rule}"
            "- Keep responses concise and conversational: 1 to 3 short sentences.\n"
            "- NEVER use markdown headers (#), bold (**), italics (*), bullets (-), or numbered lists.\n"
            "- NEVER output stage directions like (laughs), (sighs), or romanization in parentheses."
        )

    return (
        "CRITICAL VOICE FORMATTING RULES:\n"
        f'- Speak primarily in {target_name} ({variety_name}) wrapped in <lang code="{target_code}">...</lang> unless clarification is needed.\n'
        "- When switching between the learner's native language and target language in the same reply, "
        'wrap each language span in <lang code="xx">...</lang> so each clause is voiced with authentic native phonetics.\n'
        "- Keep responses short, concise, and natural: 1 to 2 sentences typically (under 140 characters).\n"
        "- NEVER use markdown headers (#), bold (**), italics (*), bullets (-), or numbered lists.\n"
        "- NEVER output stage directions like (laughs), (sighs), or romanization in parentheses.\n"
        "- Respond directly, warmly, and immediately."
    )


class ConversationalAgent(Agent):
    def __init__(
        self,
        tts=None,
        stt=None,
        synthesizer: BilingualCartesiaSynthesizer | None = None,
        broadcast=None,
        language: str = "en",
        target_language_name: str = "Spanish",
        variety_name: str | None = None,
        accent: str | None = None,
        native_language: str = "en",
        native_language_name: str = "English",
        level: str = "B1",
        conversation_kind: str = "freetalk",
        teach_in_target: bool = False,
        multi_stt_active: bool = True,
        tutor_instructions: str | None = None,
        resume: bool = False,
        opening: str | None = None,
        silent_enter: bool = False,
    ) -> None:
        self.language = language
        self.target_language_name = target_language_name
        self.variety_name = variety_name or target_language_name
        self.accent = accent
        self.native_language = native_language
        self.native_language_name = native_language_name
        self.level = level
        self.conversation_kind = conversation_kind
        self.teach_in_target = teach_in_target
        self.multi_stt_active = multi_stt_active
        self._stt_engine = stt
        self.synthesizer = synthesizer or BilingualCartesiaSynthesizer()
        self.broadcast = broadcast
        self.tutor_instructions = tutor_instructions
        self.resume = resume
        self.opening = opening
        self.silent_enter = silent_enter
        self.last_tutor_line: str = ""
        self._genai_client: genai.Client | None = None

        native_base = (native_language or "en").lower().split("-")[0]
        target_base = (language or "es").lower().split("-")[0]
        self.is_free_talk = (
            conversation_kind == "freetalk"
            and not teach_in_target
            and native_base != target_base
        )

        guardrails = _build_voice_Guardrails(
            is_free_talk=self.is_free_talk,
            native_code=native_base,
            native_name=native_language_name,
            target_code=target_base,
            target_name=target_language_name,
            variety_name=self.variety_name,
            multi_stt_active=multi_stt_active,
        )
        base_intro = (
            f"You are Zuno (Sofía), a warm, knowledgeable personal {target_language_name} coach and conversation partner "
            "speaking live with the learner over a real-time voice call."
        )
        instructions = (
            f"{tutor_instructions}\n\n{guardrails}"
            if tutor_instructions
            else f"{base_intro}\n\n{guardrails}"
        )
        super().__init__(instructions=instructions, tts=tts, stt=stt)

    @property
    def default_spoken_lang(self) -> str:
        return self.native_language if self.is_free_talk else self.language

    @property
    def base_speed(self) -> float:
        if self.is_free_talk:
            return 1.0
        return 0.94 if self.level in ("A1", "A2") else 1.0

    @function_tool
    async def set_listening_language(self, language_code: str) -> str:
        """Switches speech recognition between the learner's user language and target language when needed.

        Args:
            language_code: Two-letter ISO language code to listen for next (e.g. 'en', 'ko', 'zh', 'es').
        """
        if self.multi_stt_active or self._stt_engine is None:
            return "Multilingual speech recognition is already active for both languages."
        norm = (language_code or "").strip().lower().split("-")[0]
        target_base = self.language.lower().split("-")[0]
        native_base = self.native_language.lower().split("-")[0]
        chosen = target_base if norm == target_base else native_base
        stt_code = resolve_stt_code(chosen, self.accent if chosen == target_base else None)
        try:
            if hasattr(self._stt_engine, "update_options"):
                self._stt_engine.update_options(language=stt_code)
                logger.info("Switched Free Talk STT language dynamically to %s", stt_code)
        except Exception as e:
            logger.warning("Failed to switch STT language to %s: %s", stt_code, e)
        return f"Now listening for {stt_code}."

    async def transcription_node(self, text, model_settings):
        async for chunk in strip_lang_tags_stream(text):
            yield chunk

    async def tts_node(self, text, model_settings):
        async for frame in stream_bilingual_tts(
            self.synthesizer,
            text,
            native_lang=self.native_language,
            target_lang=self.language,
            default_lang=self.default_spoken_lang,
            speed=self.base_speed,
        ):
            yield frame

    async def on_enter(self) -> None:
        if self.silent_enter:
            return
        if self.opening:
            clean_opening = strip_lang_tags(self.opening)
            self.last_tutor_line = clean_opening
            clauses = segment_bilingual_text(
                self.opening,
                native_lang=self.native_language,
                target_lang=self.language,
                default_lang=self.default_spoken_lang,
                speed=self.base_speed,
            )
            audio = synthesize_bilingual_segments(self.synthesizer, clauses)
            self.session.say(clean_opening, audio=audio, add_to_chat_ctx=False)
            return
        if self.resume:
            if self.is_free_talk:
                self.session.generate_reply(
                    instructions=(
                        "The learner just switched to a live voice call in the middle of this Free Talk conversation. "
                        "If their last message has not been answered yet, answer it naturally. Otherwise, continue "
                        f'warmly from where you left off in <lang code="{self.native_language}">...</lang> '
                        f'(or <lang code="{self.language}">...</lang> if they were actively speaking {self.target_language_name}) '
                        "in 1 short sentence."
                    )
                )
            else:
                self.session.generate_reply(
                    instructions=(
                        "The learner just switched to a live voice call in the middle of this conversation. "
                        "If their last message has not been answered yet, answer it. Otherwise, continue "
                        f'naturally from where the conversation left off in <lang code="{self.language}">...</lang> '
                        "(one short sentence, in your role)."
                    )
                )
            return
        if self.is_free_talk:
            self.session.generate_reply(
                instructions=(
                    f'Say a warm, friendly 1-sentence hello in <lang code="{self.native_language}">...</lang> '
                    f"and invite the learner to talk about any topic, practice {self.target_language_name}, "
                    "or ask how to say or pronounce anything."
                )
            )
        elif self.tutor_instructions:
            self.session.generate_reply(
                instructions=(
                    f'Greet the learner in one short sentence wrapped in <lang code="{self.language}">...</lang> '
                    "and invite them to start, following your role."
                )
            )
        else:
            self.session.generate_reply(
                instructions=(
                    f'Say a warm, brief one-sentence hello to the learner in <lang code="{self.language}">...</lang> '
                    "and ask what they would like to talk about today."
                )
            )

    async def handle_client_action(self, action: str, payload: str | None = None) -> None:
        if action in ("listen_slow", "slow"):
            phrase = strip_lang_tags(payload or self.last_tutor_line).strip()
            if phrase and self.session:
                try:
                    self.session.interrupt()
                except Exception:
                    pass
                clauses = segment_bilingual_text(
                    phrase,
                    native_lang=self.native_language,
                    target_lang=self.language,
                    default_lang=self.default_spoken_lang,
                    speed=0.76,
                )
                audio = synthesize_bilingual_segments(self.synthesizer, clauses)
                self.session.say(phrase, audio=audio, add_to_chat_ctx=False)
            return

        if action == "translate" and self.broadcast:
            line = strip_lang_tags(payload or self.last_tutor_line).strip()
            if not line:
                return
            try:
                if self._genai_client is None:
                    api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
                    self._genai_client = genai.Client(
                        api_key=api_key,
                        http_options=types.HttpOptions(api_version="v1beta"),
                    )
                client = self._genai_client
                detected_lang = detect_clause_language(
                    line,
                    native_lang=self.native_language,
                    target_lang=self.language,
                    default_lang=self.default_spoken_lang,
                )
                native_base = self.native_language.lower().split("-")[0]
                dest_lang_name = (
                    self.target_language_name
                    if detected_lang == native_base and native_base != self.language.lower().split("-")[0]
                    else self.native_language_name
                )

                def _translate() -> str:
                    resp = client.models.generate_content(
                        model="gemini-2.5-flash",
                        contents=f"Translate naturally into {dest_lang_name}: '{line}'. Return JSON: {{\"translation\": string}}",
                        config=types.GenerateContentConfig(temperature=0.1, response_mime_type="application/json"),
                    )
                    return str(json.loads(resp.text or "{}").get("translation") or "").strip()

                translation = await asyncio.to_thread(_translate)
                if translation:
                    await self.broadcast({
                        "type": "rescue_payload",
                        "action": "translate",
                        "text": translation,
                    })
            except Exception as e:
                logger.warning("Conversational translate fallback error: %s", e)


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

    raw_mode = str(meta.get("mode") or "")
    if raw_mode:
        mode = raw_mode
    elif meta.get("curriculum"):
        mode = "lesson"
    elif meta.get("roleplay"):
        mode = "roleplay"
    else:
        mode = "conversation"

    if mode == "peer_human_only":
        logger.info(f"Peer human-only room {ctx.room.name}; agent stepping out.")
        return

    tutor_voice = meta.get("tutorVoice")
    current_voice = resolve_cartesia_voice(meta.get("voice"), tutor_voice)
    current_language = str(meta.get("targetLanguage") or meta.get("language") or "en")
    target_language_name = str(meta.get("targetLanguageName") or current_language.upper())
    variety_name = str(meta.get("varietyName") or target_language_name)
    current_accent = meta.get("accent") or None
    hands_free = bool(meta.get("handsFree", True))
    tutor_instructions = meta.get("instructions") or None
    resume = bool(meta.get("resume", False))
    opening = meta.get("opening") or None
    learner_level = str(meta.get("level") or "B1")
    native_language = str(meta.get("nativeLanguage") or "en")
    native_language_name = str(meta.get("nativeLanguageName") or "English")
    teach_in_target = bool(meta.get("teachInTarget", False))
    conversation_kind = str(
        meta.get("conversationKind") or ("peer_partner" if mode == "peer_partner" else "freetalk")
    )

    is_bilingual_free_talk = (
        mode == "conversation"
        and conversation_kind == "freetalk"
        and not teach_in_target
        and native_language.lower().split("-")[0] != current_language.lower().split("-")[0]
    )

    logger.info(
        f"Initialized session: mode={mode}, kind={conversation_kind}, provider=cartesia-sonic-3.6, "
        f"voice={current_voice}, target_lang={current_language}, native_lang={native_language}, accent={current_accent}"
    )

    if mode == "peer_partner":
        partner_name = str(meta.get("partnerName") or "Lucía M.")
        partner_level = str(meta.get("partnerLevel") or "B1")
        partner_interests = ", ".join(meta.get("partnerInterests") or ["Travel", "Daily life"])
        tutor_instructions = (
            f"You are {partner_name}, a friendly fellow language learner practicing {current_language} "
            f"at CEFR level {partner_level}. Your interests include {partner_interests}. "
            f"You just joined a 1:1 peer practice voice call with another learner. "
            f"Talk naturally like a friendly practice partner (NOT as a teacher or AI assistant). "
            f'Greet them warmly in <lang code="{current_language}">...</lang> and ask how their day is going or what they want to practice.'
        )
        ping_event = asyncio.Event()

        @ctx.room.on("data_received")
        def _on_wait_data(packet: rtc.DataPacket) -> None:
            try:
                msg = json.loads(packet.data.decode("utf-8"))
                if msg.get("type") == "ping_partner":
                    ping_event.set()
            except Exception:
                pass

        try:
            await asyncio.wait_for(ping_event.wait(), timeout=2.5)
        except asyncio.TimeoutError:
            pass

        try:
            await ctx.room.local_participant.publish_data(
                json.dumps({"type": "partner_joined", "partnerName": partner_name}).encode("utf-8"),
                reliable=True,
            )
        except Exception as e:
            logger.debug(f"Failed to broadcast partner_joined: {e}")

    multi_stt_supported = can_use_multi_stt(current_language, native_language)
    if mode == "onboarding":
        stt_lang = native_language
        stt_accent = None
    elif is_bilingual_free_talk and not multi_stt_supported:
        # Non-multi language pair (e.g. English + Korean/Mandarin/Arabic): start in user's native language
        # so Free Talk works naturally out of the box, with `set_listening_language` tool for dynamic switches.
        stt_lang = native_language
        stt_accent = None
    else:
        stt_lang = current_language
        stt_accent = current_accent

    vad = silero.VAD.load(min_speech_duration=0.05, min_silence_duration=0.25)
    use_multilingual_stt = mode in ("lesson", "roleplay", "conversation")
    stt = create_stt(
        stt_lang,
        stt_accent,
        native_language=native_language,
        multilingual=use_multilingual_stt,
        allow_dynamic_switch=(is_bilingual_free_talk and not multi_stt_supported),
    )
    llm = gg.LLM(
        model="gemini-2.5-flash",
        temperature=0.45 if is_bilingual_free_talk else 0.4,
        http_options=types.HttpOptions(api_version="v1beta"),
    )
    tts_default_lang = (
        native_language
        if (mode == "onboarding" or is_bilingual_free_talk)
        else current_language
    )
    tts = create_cartesia_tts(
        voice_id=current_voice,
        tutor_voice=tutor_voice,
        language=tts_default_lang,
    )
    session_synthesizer = BilingualCartesiaSynthesizer(
        voice_id=current_voice,
        tutor_voice=tutor_voice,
        accent=current_accent,
    )

    async def broadcast_event(data: dict) -> None:
        try:
            payload = json.dumps(data).encode("utf-8")
            await ctx.room.local_participant.publish_data(payload, reliable=True)
        except Exception as e:
            logger.debug(f"Failed to publish data event: {e}")

    lesson_orchestrator: LessonOrchestrator | None = None
    roleplay_orchestrator: RoleplayOrchestrator | None = None
    onboarding_orchestrator: OnboardingOrchestrator | None = None
    conversational_agent: ConversationalAgent | None = None

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
    elif mode == "roleplay":
        roleplay_orchestrator = RoleplayOrchestrator(
            meta=meta,
            tts=tts,
            synthesizer=session_synthesizer,
            broadcast=broadcast_event,
        )
        agent = RoleplayAgent(
            orchestrator=roleplay_orchestrator,
            tts=tts,
            stt=stt,
        )
        initial_endpointing = 0.75 if hands_free else 1.6
    elif mode == "onboarding":
        onboarding_orchestrator = OnboardingOrchestrator(
            meta=meta,
            tts=tts,
            broadcast=broadcast_event,
        )
        agent = OnboardingAgent(
            orchestrator=onboarding_orchestrator,
            tts=tts,
            stt=stt,
        )
        initial_endpointing = 0.65
    else:
        conversational_agent = ConversationalAgent(
            tts=tts,
            stt=stt,
            synthesizer=session_synthesizer,
            broadcast=broadcast_event,
            language=current_language,
            target_language_name=target_language_name,
            variety_name=variety_name,
            accent=current_accent,
            native_language=native_language,
            native_language_name=native_language_name,
            level=learner_level,
            conversation_kind=conversation_kind,
            teach_in_target=teach_in_target,
            multi_stt_active=(use_multilingual_stt and multi_stt_supported),
            tutor_instructions=tutor_instructions,
            resume=resume,
            opening=opening,
        )
        agent = conversational_agent
        initial_endpointing = 0.55 if hands_free else 1.6

    avatar_id_meta = meta.get("avatarId") or ("elena" if meta.get("videoAvatar") else None)
    render_mode = str(meta.get("renderMode") or "server").lower()
    use_video_avatar = bool(avatar_id_meta) and render_mode != "client"

    session = AgentSession(
        stt=stt,
        vad=vad,
        llm=llm,
        tts=tts,
        min_endpointing_delay=initial_endpointing,
        resume_false_interruption=not use_video_avatar,
    )

    avatar_session: MuseTalkAvatarSession | None = None
    if use_video_avatar:
        try:
            avatar_session = MuseTalkAvatarSession(avatar_id=str(avatar_id_meta))
            await avatar_session.start(session, room=ctx.room)
        except Exception as e:
            logger.error(f"Failed to start MuseTalk avatar '{avatar_id_meta}', falling back to audio-only: {e}")
            avatar_session = None
            use_video_avatar = False

    if lesson_orchestrator is not None:
        lesson_orchestrator.session = session
    if roleplay_orchestrator is not None:
        roleplay_orchestrator.session = session
    if onboarding_orchestrator is not None:
        onboarding_orchestrator.session = session

    async def _cleanup_resources() -> None:
        await session_synthesizer.aclose()
        if lesson_orchestrator is not None:
            await lesson_orchestrator.synthesizer.aclose()
        if onboarding_orchestrator is not None and hasattr(onboarding_orchestrator, "synthesizer"):
            await onboarding_orchestrator.synthesizer.aclose()
        if avatar_session is not None:
            await avatar_session.aclose()

    ctx.add_shutdown_callback(_cleanup_resources)

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
            if not clean_text:
                return
            logger.info(f"Agent reply: {clean_text}")

            if lesson_orchestrator is not None and lesson_orchestrator.stage == "roleplay":
                lesson_orchestrator.last_tutor_line = clean_text
                ai_role = lesson_orchestrator.roleplay_cfg.get("aiRole") or "Sofía"
                role_label = f"{ai_role} · Sofía" if "sofía" not in ai_role.lower() else ai_role
                turn_id = f"rp-agent-{int(time.time() * 1000)}"
                lesson_orchestrator.last_tutor_turn_id = turn_id
                asyncio.create_task(
                    broadcast_event({
                        "type": "lesson_turn",
                        "id": turn_id,
                        "speaker": "agent",
                        "senderLabel": role_label,
                        "text": clean_text,
                        "actions": ["translate", "listen_slow"],
                    })
                )
            elif roleplay_orchestrator is not None and clean_text != roleplay_orchestrator.last_tutor_line:
                asyncio.create_task(
                    roleplay_orchestrator.emit_turn(
                        speaker="agent",
                        sender_label=roleplay_orchestrator.sender_role_label,
                        text=clean_text,
                        actions=["translate", "listen_slow"],
                    )
                )
            elif conversational_agent is not None:
                conversational_agent.last_tutor_line = clean_text

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

            if msg_type in ("lesson_action", "roleplay_action"):
                action = str(data.get("action") or "")
                payload = data.get("payload")
                payload_str = str(payload) if payload is not None else None
                if lesson_orchestrator is not None:
                    asyncio.create_task(
                        lesson_orchestrator.handle_client_action(action=action, payload=payload_str)
                    )
                elif roleplay_orchestrator is not None:
                    asyncio.create_task(
                        roleplay_orchestrator.handle_client_action(action=action, payload=payload_str)
                    )
                elif conversational_agent is not None:
                    asyncio.create_task(
                        conversational_agent.handle_client_action(action=action, payload=payload_str)
                    )
                return

            if msg_type == "onboarding_action" and onboarding_orchestrator is not None:
                action = str(data.get("action") or "")
                asyncio.create_task(
                    onboarding_orchestrator.handle_client_action(
                        action=action,
                        data=data,
                    )
                )
                return

            if msg_type == "change_language":
                lang = str(data.get("language") or current_language)
                current_language = lang
                update_cartesia_tts(tts, language=current_language, speed=1.0)
                if conversational_agent is not None:
                    conversational_agent.language = current_language
        except Exception as e:
            logger.warning(f"Error handling client data packet: {e}", exc_info=True)

    await session.start(
        agent=agent,
        room=ctx.room,
        room_options=room_io.RoomOptions(
            audio_input=room_io.AudioInputOptions(),
            audio_output=not use_video_avatar,
        ),
    )


if __name__ == "__main__":
    cli.run_app(server)
