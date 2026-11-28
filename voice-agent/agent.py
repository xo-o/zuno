import asyncio
import json
import logging
import os
import time
from collections.abc import Awaitable, Callable

import certifi
from dotenv import load_dotenv

# Python builds on macOS often ship without a CA bundle, which breaks TLS to LiveKit Cloud / ElevenLabs.
os.environ.setdefault("SSL_CERT_FILE", certifi.where())

from google import genai
from google.genai import types
from livekit import rtc
from livekit.agents import (
    Agent,
    AgentServer,
    AgentSession,
    JobContext,
    cli,
    function_tool,
    room_io,
    stt as lk_stt,
    vad as lk_vad,
)
from livekit.agents.types import NOT_GIVEN
from livekit.agents.voice import io as voice_io
import livekit.plugins.deepgram as dg
import livekit.plugins.elevenlabs as elevenlabs
import livekit.plugins.google as gg
import livekit.plugins.silero as silero

from avatar_session import MuseTalkAvatarSession
from bilingual import (
    detect_clause_language,
    strip_lang_tags,
    strip_lang_tags_stream,
)
from lesson_flow import LessonAgent, LessonOrchestrator
from onboarding_flow import OnboardingAgent, OnboardingOrchestrator
from roleplay_flow import RoleplayAgent, RoleplayOrchestrator
from voices import (
    ElevenLabsSynthesizer,
    create_elevenlabs_tts,
    resolve_elevenlabs_voice,
    resolve_tutor_name,
    stream_llm_tts,
    synthesize_speech,
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


def _elevenlabs_api_key() -> str | None:
    return os.environ.get("ELEVENLABS_API_KEY") or os.environ.get("ELEVEN_API_KEY") or None


def can_use_multi_stt(language: str = "en", native_language: str | None = None) -> bool:
    base_target = (language or "en").lower().split("-")[0]
    base_native = (native_language or "").lower().split("-")[0]
    if not base_native or base_native == base_target:
        return False
    if _elevenlabs_api_key():
        # ElevenLabs Scribe v2 Realtime supports 90+ languages with language_code + secondary_languages.
        return True
    return base_target in NOVA3_MULTI_LANGS and base_native in NOVA3_MULTI_LANGS


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
    elevenlabs_key = _elevenlabs_api_key()
    target_base = (language or "en").lower().split("-")[0]
    native_base = (native_language or "").lower().split("-")[0]
    if elevenlabs_key:
        secondary = (
            [native_base]
            if (multilingual and native_base and native_base != target_base)
            else NOT_GIVEN
        )
        return elevenlabs.STT(
            model="scribe_v2_realtime",
            language_code=target_base,
            secondary_languages=secondary,
            include_language_detection=True,
            api_key=elevenlabs_key,
        )

    if multilingual and can_use_multi_stt(language, native_language):
        return dg.STT(
            model="nova-3",
            language="multi",
            endpointing_ms=300,
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
        endpointing_ms=300,
        interim_results=True,
        api_key=os.environ.get("DEEPGRAM_API_KEY"),
    )


CEFR_VOICE_GUIDANCE: dict[str, str] = {
    "A1": "CEFR A1 (Beginner): use simple high-frequency everyday words, short present-tense sentences (4–8 words), and avoid idioms or complex clauses.",
    "A2": "CEFR A2 (Elementary): use common everyday vocabulary, simple present/near-future/basic past forms, and short clear sentences without obscure slang.",
    "B1": "CEFR B1 (Intermediate): use natural everyday plus travel/work vocabulary, past/future/polite conditional forms, and friendly follow-up questions.",
    "B2": "CEFR B2 (Upper-Intermediate): use varied idiomatic vocabulary, opinions, hypotheticals, and natural conversational nuance.",
    "C1": "CEFR C1 (Advanced): use authentic native register, subtle idioms, abstract ideas, and persuasive/nuanced phrasing without simplifying unnecessarily.",
    "C2": "CEFR C2 (Mastery): use rich native-level phrasing, domain vocabulary, and subtle humor or register shifts.",
}


def cefr_voice_guidance(level: str) -> str:
    return CEFR_VOICE_GUIDANCE.get((level or "B1").strip().upper(), CEFR_VOICE_GUIDANCE["B1"])


def _build_voice_Guardrails(
    *,
    is_free_talk: bool,
    native_code: str,
    native_name: str,
    target_code: str,
    target_name: str,
    variety_name: str,
    level: str = "B1",
    multi_stt_active: bool,
) -> str:
    level_guide = cefr_voice_guidance(level)
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
            f"- LEARNER LEVEL CALIBRATION: {level_guide}\n"
            f"- USER-LANGUAGE DEFAULT: Speak in {native_name} by default. "
            "Chat warmly and naturally about ANY topic the learner brings up (daily life, travel, culture, work, hobbies, grammar, or vocabulary).\n"
            f"- SEAMLESS BILINGUAL SWITCHING: Include {target_name} ({variety_name}) words, phrases, or examples directly and naturally in your speech, "
            f"strictly calibrated to CEFR {level}. "
            f"If the learner speaks in {target_name} or asks to practice in {target_name}, mirror and converse in {target_name} at CEFR {level}, "
            f"switching back smoothly to {native_name} whenever they speak {native_name} or ask for help.\n"
            f"- PRONUNCIATION & 'HOW DO I SAY' COACHING: When the learner asks how to say or pronounce something in {target_name}, "
            f"give a brief 1-sentence tip in {native_name}, model the {target_name} phrase naturally, and invite them to try it. "
            "NEVER write fake English phonetic spellings (like 'reh-free-heh-rah-dor').\n"
            f"{stt_rule}"
            "- Keep responses concise and conversational: 1 to 3 short sentences.\n"
            "- NEVER output XML tags (<lang>), markdown headers (#), bold (**), italics (*), bullets (-), or numbered lists.\n"
            "- NEVER output stage directions like (laughs), (sighs), or romanization in parentheses."
        )

    return (
        "CRITICAL VOICE FORMATTING RULES:\n"
        f"- LEARNER LEVEL CALIBRATION: {level_guide}\n"
        f"- Speak primarily in {target_name} ({variety_name}) calibrated to CEFR {level} unless clarification in {native_name} is needed.\n"
        "- Keep responses short, concise, and natural: 1 to 2 sentences typically (under 140 characters).\n"
        "- NEVER output XML tags (<lang>), markdown headers (#), bold (**), italics (*), bullets (-), or numbered lists.\n"
        "- NEVER output stage directions like (laughs), (sighs), or romanization in parentheses.\n"
        "- Respond directly, warmly, and immediately."
    )


class ConversationalAgent(Agent):
    def __init__(
        self,
        tts=None,
        stt=None,
        synthesizer: ElevenLabsSynthesizer | None = None,
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
        tutor_id: str = "sofia",
        tutor_name: str = "Sofía",
        speaking_pace: str = "normal",
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
        self.level = (level or "B1").strip().upper()
        self.conversation_kind = conversation_kind
        self.teach_in_target = teach_in_target
        self.multi_stt_active = multi_stt_active
        self._stt_engine = stt
        self.synthesizer = synthesizer or ElevenLabsSynthesizer()
        self.broadcast = broadcast
        self.tutor_id = (tutor_id or "sofia").strip().lower()
        self.tutor_name = tutor_name or "Sofía"
        self.speaking_pace = (speaking_pace or "normal").strip().lower()
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
            level=self.level,
            multi_stt_active=multi_stt_active,
        )
        base_intro = (
            f"You are {self.tutor_name} (Zuno's AI language coach), a warm, knowledgeable personal {target_language_name} coach and conversation partner "
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
        is_slow_persona = self.speaking_pace == "slow" or self.tutor_id == "valeria"
        if self.level == "A1":
            speed = 0.90
        elif self.level == "A2":
            speed = 0.94
        else:
            speed = 1.0
        if is_slow_persona:
            if self.level in ("A1", "A2", "B1"):
                speed = min(speed, 0.92 if self.level != "A1" else 0.90)
            else:
                speed = min(speed, 0.95)
        elif self.is_free_talk and self.level not in ("A1", "A2"):
            speed = 1.0
        return speed

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
            if isinstance(self._stt_engine, elevenlabs.STT):
                self._stt_engine.update_options(secondary_languages=[chosen])
                logger.info("Updated ElevenLabs STT secondary language to %s", chosen)
            elif hasattr(self._stt_engine, "update_options"):
                self._stt_engine.update_options(language=stt_code)
                logger.info("Switched Free Talk STT language dynamically to %s", stt_code)
        except Exception as e:
            logger.warning("Failed to switch STT language to %s: %s", stt_code, e)
        return f"Now listening for {stt_code}."

    async def transcription_node(self, text, model_settings):
        async for chunk in strip_lang_tags_stream(text):
            yield chunk

    async def tts_node(self, text, model_settings):
        async for frame in stream_llm_tts(
            self.synthesizer,
            text,
            speed=self.base_speed,
        ):
            yield frame

    async def on_enter(self) -> None:
        if self.silent_enter:
            return
        if self.opening:
            clean_opening = strip_lang_tags(self.opening)
            self.last_tutor_line = clean_opening
            audio = synthesize_speech(self.synthesizer, clean_opening, speed=self.base_speed)
            self.session.say(clean_opening, audio=audio, add_to_chat_ctx=False)
            return
        if self.resume:
            if self.is_free_talk:
                self.session.generate_reply(
                    instructions=(
                        "The learner just switched to a live voice call in the middle of this Free Talk conversation. "
                        "If their last message has not been answered yet, answer it naturally. Otherwise, continue "
                        f"warmly from where you left off in {self.native_language_name} "
                        f"(or {self.target_language_name} if they were actively speaking {self.target_language_name}) "
                        "in 1 short sentence."
                    )
                )
            else:
                self.session.generate_reply(
                    instructions=(
                        "The learner just switched to a live voice call in the middle of this conversation. "
                        "If their last message has not been answered yet, answer it. Otherwise, continue "
                        f"naturally from where the conversation left off in {self.target_language_name} "
                        "(one short sentence, in your role)."
                    )
                )
            return
        if self.is_free_talk:
            self.session.generate_reply(
                instructions=(
                    f"Say a warm, friendly 1-sentence hello in {self.native_language_name} "
                    f"to a CEFR {self.level} {self.target_language_name} learner and invite them to talk about any topic, "
                    f"practice {self.target_language_name} at their level, or ask how to say or pronounce anything."
                )
            )
        elif self.tutor_instructions:
            self.session.generate_reply(
                instructions=(
                    f"Greet the learner in one short sentence in {self.target_language_name} suited to CEFR {self.level} "
                    "and invite them to start, following your role."
                )
            )
        else:
            self.session.generate_reply(
                instructions=(
                    f"Say a warm, brief one-sentence hello to the learner in {self.target_language_name} suited to CEFR {self.level} "
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
                audio = synthesize_speech(self.synthesizer, phrase, speed=0.75)
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


class LiveCaptionTextOutput(voice_io.TextOutput):
    """Receives audio-synchronized agent text chunks from LiveKit TranscriptSynchronizer
    and forwards real-time interim/final captions to the room."""

    def __init__(
        self,
        on_caption: Callable[[str, str, bool], Awaitable[None]],
    ) -> None:
        super().__init__(label="zuno-live-captions", next_in_chain=None)
        self._on_caption = on_caption
        self._buffer = ""
        self._last_emitted = ""
        self._segment_seq = 0
        self._segment_id = self._next_segment_id()
        self._streamed_texts: list[str] = []
        self._tasks: set[asyncio.Task] = set()

    def _next_segment_id(self) -> str:
        self._segment_seq += 1
        return f"agent-{self._segment_seq}-{int(time.time() * 1000)}"

    def was_streamed(self, text: str) -> bool:
        norm = strip_lang_tags(text).strip()
        if not norm:
            return False
        for item in self._streamed_texts[-6:]:
            if item == norm or norm.startswith(item) or item.startswith(norm):
                return True
        return False

    async def capture_text(self, text: str) -> None:
        self._buffer += text
        clean = strip_lang_tags(self._buffer).strip()
        if clean and clean != self._last_emitted:
            self._last_emitted = clean
            await self._on_caption(self._segment_id, clean, False)

    def flush(self) -> None:
        seg_id = self._segment_id
        final_text = strip_lang_tags(self._buffer).strip()
        self._buffer = ""
        self._last_emitted = ""
        self._segment_id = self._next_segment_id()
        if final_text:
            self._streamed_texts.append(final_text)
            if len(self._streamed_texts) > 20:
                self._streamed_texts = self._streamed_texts[-10:]
            task = asyncio.create_task(self._on_caption(seg_id, final_text, True))
            self._tasks.add(task)
            task.add_done_callback(self._tasks.discard)


async def run_multi_participant_transcriber(ctx: JobContext, meta: dict, mode: str) -> None:
    """Real-time multi-speaker STT captioning for 1:1 human calls and group Live Rooms."""
    target_lang = str(meta.get("targetLanguage") or meta.get("language") or "en")
    native_lang = str(meta.get("nativeLanguage") or "en")
    multilingual = bool(
        native_lang and native_lang.lower().split("-")[0] != target_lang.lower().split("-")[0]
    )

    vad = silero.VAD.load(min_speech_duration=0.10, min_silence_duration=0.45)
    stt_engine = create_stt(
        target_lang,
        native_language=native_lang,
        multilingual=multilingual,
    )

    active_tasks: dict[str, asyncio.Task] = {}
    room_closed = asyncio.Event()

    async def broadcast_transcript(
        *,
        speaker: str,
        speaker_id: str,
        speaker_name: str,
        segment_id: str,
        text: str,
        is_final: bool,
    ) -> None:
        try:
            payload = json.dumps({
                "type": "transcript",
                "speaker": speaker,
                "speakerId": speaker_id,
                "speakerName": speaker_name,
                "segmentId": segment_id,
                "text": text,
                "is_final": is_final,
            }).encode("utf-8")
            await ctx.room.local_participant.publish_data(payload, reliable=True)
        except Exception as e:
            logger.debug("Multi-party caption broadcast error: %s", e)

    async def transcribe_track(participant: rtc.RemoteParticipant, track: rtc.Track) -> None:
        speaker_id = participant.identity
        speaker_name = participant.name or participant.identity
        audio_stream = rtc.AudioStream.from_track(
            track=track,
            sample_rate=16000,
            num_channels=1,
        )
        vad_stream = vad.stream()
        stt_stream = stt_engine.stream()

        seg_seq = 0

        def next_seg_id() -> str:
            nonlocal seg_seq
            seg_seq += 1
            return f"{speaker_id}-{seg_seq}-{int(time.time() * 1000)}"

        current_seg_id = next_seg_id()

        async def pump_audio() -> None:
            async for ev in audio_stream:
                vad_stream.push_frame(ev.frame)
                stt_stream.push_frame(ev.frame)
            vad_stream.end_input()
            stt_stream.end_input()

        async def pump_vad() -> None:
            async for ev in vad_stream:
                if ev.type == lk_vad.VADEventType.END_OF_SPEECH:
                    stt_stream.flush()

        async def pump_stt() -> None:
            nonlocal current_seg_id
            async for ev in stt_stream:
                if ev.type not in (
                    lk_stt.SpeechEventType.INTERIM_TRANSCRIPT,
                    lk_stt.SpeechEventType.FINAL_TRANSCRIPT,
                ):
                    continue
                if not ev.alternatives:
                    continue
                text = (ev.alternatives[0].text or "").strip()
                if not text:
                    continue
                is_final = ev.type == lk_stt.SpeechEventType.FINAL_TRANSCRIPT
                await broadcast_transcript(
                    speaker="user",
                    speaker_id=speaker_id,
                    speaker_name=speaker_name,
                    segment_id=current_seg_id,
                    text=text,
                    is_final=is_final,
                )
                if is_final:
                    current_seg_id = next_seg_id()

        try:
            await asyncio.gather(pump_audio(), pump_vad(), pump_stt())
        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.debug("Track transcriber stopped for %s: %s", speaker_id, e)
        finally:
            for closable in (audio_stream, vad_stream, stt_stream):
                try:
                    await closable.aclose()
                except Exception:
                    pass

    def maybe_start_track(participant: rtc.RemoteParticipant, track: rtc.Track | None) -> None:
        if track is None or track.kind != rtc.TrackKind.KIND_AUDIO:
            return
        ident = participant.identity or ""
        if ident.startswith("agent-") or ident.startswith("avatar-"):
            return
        key = f"{ident}:{track.sid}"
        if key in active_tasks and not active_tasks[key].done():
            return
        logger.info("Starting live caption transcriber for %s (%s)", participant.name or ident, mode)
        active_tasks[key] = asyncio.create_task(transcribe_track(participant, track))

    def stop_participant_tracks(ident: str) -> None:
        prefix = f"{ident}:"
        for key in list(active_tasks.keys()):
            if key.startswith(prefix):
                task = active_tasks.pop(key)
                task.cancel()

    @ctx.room.on("track_subscribed")
    def _on_track_subscribed(
        track: rtc.Track,
        publication: rtc.RemoteTrackPublication,
        remote_participant: rtc.RemoteParticipant,
    ) -> None:
        maybe_start_track(remote_participant, track)

    @ctx.room.on("track_unpublished")
    def _on_track_unpublished(
        publication: rtc.RemoteTrackPublication,
        remote_participant: rtc.RemoteParticipant,
    ) -> None:
        key = f"{remote_participant.identity}:{publication.sid}"
        task = active_tasks.pop(key, None)
        if task:
            task.cancel()

    @ctx.room.on("participant_disconnected")
    def _on_participant_disconnected(remote_participant: rtc.RemoteParticipant) -> None:
        stop_participant_tracks(remote_participant.identity)
        humans = [
            p for p in ctx.room.remote_participants.values()
            if not (p.identity or "").startswith(("agent-", "avatar-"))
        ]
        if not humans:
            room_closed.set()

    @ctx.room.on("disconnected")
    def _on_room_disconnected(*_args) -> None:
        room_closed.set()

    for remote in ctx.room.remote_participants.values():
        for pub in remote.track_publications.values():
            if pub.track is not None:
                maybe_start_track(remote, pub.track)

    if mode == "live_room" and meta.get("sourceKind") == "system":
        prompts = meta.get("discussionPrompts") or []
        round_idx = max(0, int(meta.get("currentRound") or 1) - 1)
        if isinstance(prompts, list) and round_idx < len(prompts):
            prompt_text = str(prompts[round_idx] or "").strip()
            if prompt_text:
                await broadcast_transcript(
                    speaker="agent",
                    speaker_id="zuno-host",
                    speaker_name="Zuno",
                    segment_id="sys-q",
                    text=f"“{prompt_text}”",
                    is_final=True,
                )

    await room_closed.wait()
    for task in list(active_tasks.values()):
        task.cancel()
    if active_tasks:
        await asyncio.gather(*active_tasks.values(), return_exceptions=True)


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

    if mode in ("peer_human_only", "live_room"):
        logger.info(f"Starting multi-participant live captioner for {mode} room {ctx.room.name}")
        await run_multi_participant_transcriber(ctx, meta, mode)
        return

    tutor_id = str(meta.get("tutorId") or "") or None
    tutor_voice = meta.get("tutorVoice")
    if mode == "onboarding":
        tutor_id = "sofia"
        tutor_voice = "warm"
    current_voice = resolve_elevenlabs_voice(meta.get("voice"), tutor_voice, tutor_id)
    tutor_name = str(meta.get("tutorName") or resolve_tutor_name(tutor_id, tutor_voice))
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
    conversation_kind = str(meta.get("conversationKind") or "freetalk")

    is_bilingual_free_talk = (
        mode == "conversation"
        and conversation_kind == "freetalk"
        and not teach_in_target
        and native_language.lower().split("-")[0] != current_language.lower().split("-")[0]
    )

    logger.info(
        f"Initialized session: mode={mode}, kind={conversation_kind}, provider=elevenlabs, "
        f"voice={current_voice}, target_lang={current_language}, native_lang={native_language}, accent={current_accent}"
    )

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

    vad = silero.VAD.load(min_speech_duration=0.10, min_silence_duration=0.45)
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
    session_synthesizer = ElevenLabsSynthesizer(
        voice_id=current_voice,
        tutor_voice=tutor_voice,
        tutor_id=tutor_id,
    )
    tts = create_elevenlabs_tts(synthesizer=session_synthesizer)

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
            synthesizer=session_synthesizer,
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
            synthesizer=session_synthesizer,
            broadcast=broadcast_event,
        )
        agent = OnboardingAgent(
            orchestrator=onboarding_orchestrator,
            tts=tts,
            stt=stt,
        )
        initial_endpointing = 0.65
    else:
        speaking_pace = str(meta.get("speakingPace") or ("slow" if (tutor_id or "").lower() == "valeria" else "normal"))
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
            tutor_id=tutor_id or "sofia",
            tutor_name=tutor_name,
            speaking_pace=speaking_pace,
            tutor_instructions=tutor_instructions,
            resume=resume,
            opening=opening,
        )
        agent = conversational_agent
        initial_endpointing = 0.55 if hands_free else 1.6

    avatar_id_meta = meta.get("avatarId") or ((tutor_id or "sofia") if meta.get("videoAvatar") else None)
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
        lesson_orchestrator.caption_streaming_active = True
    if roleplay_orchestrator is not None:
        roleplay_orchestrator.session = session
        roleplay_orchestrator.caption_streaming_active = True
    if onboarding_orchestrator is not None:
        onboarding_orchestrator.session = session

    agent_display_name = tutor_name
    user_identity = participant.identity
    user_display_name = participant.name or "You"
    agent_identity = ctx.room.local_participant.identity

    async def _on_agent_caption(seg_id: str, text: str, is_final: bool) -> None:
        if lesson_orchestrator is not None:
            await lesson_orchestrator.on_agent_caption(seg_id, text, is_final)
        elif roleplay_orchestrator is not None:
            await roleplay_orchestrator.on_agent_caption(seg_id, text, is_final)
        elif conversational_agent is not None:
            conversational_agent.last_tutor_line = text

        await broadcast_event({
            "type": "transcript",
            "speaker": "agent",
            "speakerId": agent_identity,
            "speakerName": agent_display_name,
            "segmentId": seg_id,
            "text": text,
            "is_final": is_final,
        })

    caption_output = LiveCaptionTextOutput(on_caption=_on_agent_caption)

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
    user_seg_seq = 1
    current_user_seg_id = f"user-{user_seg_seq}-{int(time.time() * 1000)}"

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
                    "provider": "elevenlabs",
                })
            )

    @session.on("user_input_transcribed")
    def _on_user_input(ev) -> None:
        nonlocal last_user_speech_end, user_seg_seq, current_user_seg_id
        logger.info(f"User transcript (final={ev.is_final}): {ev.transcript}")
        seg_id = current_user_seg_id
        if ev.is_final:
            last_user_speech_end = time.time()
            user_seg_seq += 1
            current_user_seg_id = f"user-{user_seg_seq}-{int(time.time() * 1000)}"

        asyncio.create_task(
            broadcast_event({
                "type": "transcript",
                "speaker": "user",
                "speakerId": user_identity,
                "speakerName": user_display_name,
                "segmentId": seg_id,
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

            if caption_output.was_streamed(clean_text):
                return

            if lesson_orchestrator is not None and lesson_orchestrator.stage == "roleplay":
                lesson_orchestrator.last_tutor_line = clean_text
                tname = lesson_orchestrator.tutor_name
                ai_role = lesson_orchestrator.roleplay_cfg.get("aiRole") or tname
                role_label = f"{ai_role} · {tname}" if tname.lower() not in ai_role.lower() else ai_role
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
                        "isFinal": True,
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
                    "speakerId": agent_identity,
                    "speakerName": agent_display_name,
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
            text_output=room_io.TextOutputOptions(
                next_in_chain=caption_output,
                sync_transcription=True,
            ),
        ),
    )


if __name__ == "__main__":
    cli.run_app(server)
