import asyncio
import json
import logging
import os
import time
from typing import Any, Callable, Coroutine

from google import genai
from google.genai import types
from livekit.agents import Agent, AgentSession, StopResponse, function_tool, llm

from bilingual import (
    strip_lang_tags,
    strip_lang_tags_stream,
)
from voices import (
    ElevenLabsSynthesizer,
    ElevenLabsTTS,
    resolve_tutor_name,
    stream_llm_tts,
    synthesize_speech,
)

logger = logging.getLogger("voice-agent.roleplay")

REPEAT_EXACT_CUES = {
    "repeat",
    "repeat please",
    "can you repeat",
    "can you repeat that",
    "say that again",
    "one more time",
    "repite",
    "repite por favor",
}
SLOW_EXACT_CUES = {
    "slower",
    "slower please",
    "speak slower",
    "more slowly",
    "más despacio",
    "mas despacio",
    "más lento",
    "mas lento",
}


class RoleplayOrchestrator:
    """Drives dynamic practice-oriented Live Roleplay sessions over LiveKit Data Channels + ElevenLabs TTS.

    Key pedagogical guarantees:
    1. 90%+ target-language in-character conversation voiced with natural `eleven_v4_turbo` multilingual delivery.
    2. Seamless bilingual clarification aside: whenever the learner asks for clarification/meaning/how-to-say,
       the tutor briefly explains in `native_lang`, models the target phrase in `target_lang`, and resumes character
       in a single continuous voice pass.
    3. Mission goal tracking + implicit recasting without any artificial turn limit (open-ended practice).
    """

    def __init__(
        self,
        *,
        meta: dict[str, Any],
        tts: ElevenLabsTTS,
        synthesizer: ElevenLabsSynthesizer,
        broadcast: Callable[[dict[str, Any]], Coroutine[Any, Any, None]],
    ) -> None:
        self.meta = meta
        self.tts = tts
        self.synthesizer = synthesizer
        self.broadcast = broadcast
        self.session: AgentSession | None = None
        self.agent: "RoleplayAgent | None" = None

        rp_meta: dict[str, Any] = dict(meta.get("roleplay") or {})
        self.roleplay_id: str = str(meta.get("roleplayId") or rp_meta.get("id") or "")
        self.conversation_id: str = str(meta.get("conversationId") or "")
        self.title: str = str(rp_meta.get("title") or meta.get("title") or "Roleplay")
        self.scenario: str = str(rp_meta.get("scenario") or self.title)
        self.user_role: str = str(rp_meta.get("userRole") or "Learner")
        self.ai_role: str = str(rp_meta.get("aiRole") or "Conversation partner")
        self.tutor_name: str = str(meta.get("tutorName") or resolve_tutor_name(meta.get("tutorId"), meta.get("tutorVoice")))
        self.setting: str = str(rp_meta.get("setting") or self.scenario)
        self.opening_line: str = str(meta.get("opening") or rp_meta.get("openingLine") or "").strip()

        self.native_lang: str = str(meta.get("nativeLanguage") or "en").lower().split("-")[0]
        self.target_lang: str = str(meta.get("targetLanguage") or meta.get("language") or "es").lower().split("-")[0]
        self.target_lang_name: str = str(meta.get("targetLanguageName") or self.target_lang.upper())
        self.native_lang_name: str = str(meta.get("nativeLanguageName") or "English")
        self.variety_name: str = str(meta.get("varietyName") or self.target_lang_name)
        self.level: str = str(meta.get("level") or "B1")
        self.teach_in_target: bool = bool(meta.get("teachInTarget", False)) or self.level in ("C1", "C2")
        self.teaching_style: str = str(meta.get("teachingStyle") or "balanced")
        self.strict_mode: bool = bool(meta.get("strictMode", False))
        self.resume: bool = bool(meta.get("resume", False))
        self.history_transcript: str = str(meta.get("historyTranscript") or "").strip()

        raw_goals = rp_meta.get("goals")
        if isinstance(raw_goals, list) and raw_goals:
            self.roleplay_goals: list[dict[str, Any]] = list(raw_goals)
        else:
            self.roleplay_goals = [
                {
                    "id": "goal_1",
                    "label": "Open scene",
                    "description": f"Start the conversation as {self.user_role}",
                    "samplePhrase": "",
                },
                {
                    "id": "goal_2",
                    "label": "Handle details",
                    "description": f"Navigate the situation: {self.scenario}",
                    "samplePhrase": "",
                },
                {
                    "id": "goal_3",
                    "label": "Wrap up",
                    "description": "Confirm details and close politely",
                    "samplePhrase": "",
                },
            ]

        self.completed_goal_ids: set[str] = set()
        self.recast_notes: list[dict[str, str]] = []
        self.turn_count: int = 0
        self.rescue_count: int = 0

        self.last_tutor_line: str = ""
        self.last_tutor_turn_id: str = ""
        self._turn_history_by_text: dict[str, str] = {}
        self._turn_seq: int = 0
        self._speech_lock = asyncio.Lock()
        self._genai_client: genai.Client | None = None
        self.caption_streaming_active: bool = False
        self._streamed_turn_ids: set[str] = set()

    @property
    def base_speed(self) -> float:
        return 0.94 if self.level in ("A1", "A2") else 1.0

    @property
    def sender_role_label(self) -> str:
        return f"{self.ai_role} · {self.tutor_name}" if self.tutor_name.lower() not in self.ai_role.lower() else self.ai_role

    def _next_turn_id(self, prefix: str = "rp") -> str:
        self._turn_seq += 1
        return f"{prefix}-{self._turn_seq}-{int(time.time() * 1000)}"

    def _genai(self) -> genai.Client:
        if self._genai_client is None:
            api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
            self._genai_client = genai.Client(
                api_key=api_key,
                http_options=types.HttpOptions(api_version="v1beta"),
            )
        return self._genai_client

    async def _quick_llm_json(self, prompt: str) -> dict[str, Any]:
        def _call() -> dict[str, Any]:
            resp = self._genai().models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt,
                config=types.GenerateContentConfig(
                    temperature=0.2,
                    response_mime_type="application/json",
                ),
            )
            return json.loads(resp.text or "{}")

        try:
            return await asyncio.to_thread(_call)
        except Exception as e:
            logger.warning("Roleplay quick LLM JSON fallback: %s", e)
            return {}

    def _roleplay_goals_payload(self) -> list[dict[str, Any]]:
        first_incomplete_found = False
        result: list[dict[str, Any]] = []
        for idx, g in enumerate(self.roleplay_goals):
            gid = str(g.get("id") or f"goal_{idx + 1}")
            done = gid in self.completed_goal_ids
            is_current = False
            if not done and not first_incomplete_found:
                is_current = True
                first_incomplete_found = True
            result.append({
                "id": gid,
                "index": idx + 1,
                "label": str(g.get("label") or f"Goal {idx + 1}"),
                "description": str(g.get("description") or g.get("label") or ""),
                "samplePhrase": str(g.get("samplePhrase") or ""),
                "completed": done,
                "current": is_current,
            })
        return result

    def _current_goal_progress(self) -> tuple[int, int, str]:
        total = max(1, len(self.roleplay_goals))
        remaining = [g for g in self.roleplay_goals if g.get("id") not in self.completed_goal_ids]
        if not remaining:
            return total, total, "All goals complete · Open practice"
        next_goal = remaining[0]
        done_count = total - len(remaining)
        goal_num = min(total, done_count + 1)
        label = str(next_goal.get("label") or "").strip()
        return goal_num, total, f"Goal {goal_num} of {total} · {label}".strip(" ·")

    async def broadcast_stage(self) -> None:
        _, _, goal_text = self._current_goal_progress()
        await self.broadcast({
            "type": "lesson_stage",
            "stage": "roleplay",
            "pill": {
                "icon": "users",
                "role": self.ai_role,
                "goal": goal_text,
                "contextNote": self.title,
            },
            "card": None,
            "recallPrompt": None,
            "roleplayGoals": self._roleplay_goals_payload(),
            "rescueChips": ["hint", "translate", "slow"],
        })

    async def emit_turn(
        self,
        *,
        speaker: str,
        sender_label: str,
        text: str,
        translation: str | None = None,
        actions: list[str] | None = None,
        is_final: bool = True,
    ) -> str:
        tid = self._next_turn_id("rp-agent" if speaker == "agent" else "rp-user")
        if speaker == "agent":
            self.last_tutor_line = text
            self.last_tutor_turn_id = tid
            self._turn_history_by_text[text.strip()] = tid
        await self.broadcast({
            "type": "lesson_turn",
            "id": tid,
            "speaker": speaker,
            "senderLabel": sender_label,
            "text": text,
            "translation": translation,
            "actions": actions or [],
            "isFinal": is_final,
        })
        return tid

    async def on_agent_caption(self, segment_id: str, text: str, is_final: bool) -> None:
        clean = strip_lang_tags(text).strip()
        if not clean:
            return
        sender_label = self.sender_role_label if is_final else f"{self.sender_role_label} · Speaking"
        self.last_tutor_line = clean
        self.last_tutor_turn_id = segment_id
        if is_final:
            self._turn_history_by_text[clean] = segment_id

        if segment_id not in self._streamed_turn_ids:
            self._streamed_turn_ids.add(segment_id)
            await self.broadcast({
                "type": "lesson_turn",
                "id": segment_id,
                "speaker": "agent",
                "senderLabel": sender_label,
                "text": clean,
                "translation": None,
                "actions": ["translate", "listen_slow"],
                "isFinal": is_final,
            })
        else:
            await self.broadcast({
                "type": "lesson_turn_update",
                "id": segment_id,
                "senderLabel": sender_label,
                "text": clean,
                "isFinal": is_final,
            })

    async def _speak(
        self,
        text: str,
        *,
        speed: float | None = None,
        allow_interruptions: bool = True,
        record_in_transcript: bool = False,
    ) -> None:
        clean_text = strip_lang_tags(text).strip()
        if not self.session or not clean_text:
            return
        async with self._speech_lock:
            if record_in_transcript and not self.caption_streaming_active:
                await self.emit_turn(
                    speaker="agent",
                    sender_label=self.sender_role_label,
                    text=clean_text,
                    actions=["translate", "listen_slow"],
                )
                await self.broadcast({
                    "type": "transcript",
                    "speaker": "agent",
                    "text": clean_text,
                    "is_final": True,
                })

            audio_stream = synthesize_speech(
                self.synthesizer,
                clean_text,
                speed=speed if speed is not None else self.base_speed,
            )
            handle = self.session.say(
                clean_text,
                audio=audio_stream,
                allow_interruptions=allow_interruptions,
                add_to_chat_ctx=record_in_transcript,
            )
            try:
                await handle.wait_for_playout()
            except Exception as e:
                logger.debug("Roleplay speech interrupted: %s", e)

    async def start(self) -> None:
        await self.broadcast_stage()
        if not self.session:
            return

        if self.opening_line:
            clean_opening = strip_lang_tags(self.opening_line)
            await self._speak(
                clean_opening,
                speed=self.base_speed,
                record_in_transcript=not self.resume,
            )
            return

        if self.resume and self.history_transcript:
            self.session.generate_reply(
                instructions=(
                    f"The learner just joined live voice for this ongoing {self.target_lang_name} roleplay as '{self.user_role}'. "
                    f"Stay in character as '{self.ai_role}' and speak 1 short, natural sentence in "
                    f"{self.variety_name} continuing from where the conversation left off."
                )
            )
            return

        first_goal = self.roleplay_goals[0] if self.roleplay_goals else {}
        goal_hint = first_goal.get("description") or first_goal.get("label") or self.scenario
        self.session.generate_reply(
            instructions=(
                f"Open the roleplay scene in character as '{self.ai_role}' speaking to '{self.user_role}'. "
                f"Output ONLY 1–2 short, natural spoken sentences in {self.variety_name} ({self.target_lang_name}, CEFR {self.level}) "
                f"that set the moment and naturally invite the learner toward: {goal_hint}."
            )
        )

    async def on_goal_completed(
        self,
        goal_id: str,
        user_said: str = "",
        natural_recast: str = "",
    ) -> str:
        valid_ids = {g.get("id") for g in self.roleplay_goals if g.get("id")}
        if goal_id in valid_ids:
            self.completed_goal_ids.add(goal_id)
        elif self.roleplay_goals:
            for g in self.roleplay_goals:
                gid = g.get("id")
                if gid and gid not in self.completed_goal_ids:
                    self.completed_goal_ids.add(gid)
                    break

        await self.record_recast(user_said=user_said, natural_form=natural_recast)
        await self.broadcast_stage()

        remaining = [g for g in self.roleplay_goals if g.get("id") not in self.completed_goal_ids]
        if remaining:
            next_goal = remaining[0]
            return (
                f"Goal marked complete. Stay in character as '{self.ai_role}' in "
                f"{self.variety_name} (1–2 short sentences) and naturally steer toward "
                f"'{next_goal.get('description', next_goal.get('label', ''))}'."
            )

        return (
            f"All structured goals are now complete! Stay warmly in character as '{self.ai_role}' in "
            f"{self.variety_name} so the learner can keep practicing dynamically or wrap up naturally."
        )

    async def record_recast(self, user_said: str, natural_form: str) -> None:
        u = (user_said or "").strip()
        n = strip_lang_tags(natural_form or "").strip()
        if u and n and u.lower() != n.lower():
            self.recast_notes.append({"user_said": u, "natural_form": n})
            await self.broadcast({
                "type": "rescue_payload",
                "action": "recast",
                "text": f"Native tip: “{n}”",
            })

    async def handle_client_action(self, action: str, payload: str | None = None) -> None:
        if self.session:
            try:
                self.session.interrupt()
            except Exception:
                pass

        if action == "listen_normal":
            phrase = strip_lang_tags(payload or self.last_tutor_line).strip()
            if phrase:
                await self._speak(phrase, speed=self.base_speed)
            return

        if action in ("listen_slow", "slow"):
            phrase = strip_lang_tags(payload or self.last_tutor_line).strip()
            if phrase:
                await self._speak(phrase, speed=0.75)
            return

        if action == "play_chunk" and payload:
            clean_chunk = strip_lang_tags(payload).strip()
            if clean_chunk:
                await self._speak(clean_chunk, speed=0.90)
            return

        self.rescue_count += 1

        if action == "hint":
            remaining = [g for g in self.roleplay_goals if g.get("id") not in self.completed_goal_ids]
            goal_desc = (
                remaining[0].get("description", remaining[0].get("label", ""))
                if remaining
                else f"Continue or wrap up politely as {self.user_role}"
            )
            fallback_sample = str(remaining[0].get("samplePhrase") or "") if remaining else ""
            res = await self._quick_llm_json(
                f"Give ONE short, natural {self.variety_name} ({self.target_lang_name}) sentence a {self.level} learner "
                f"playing '{self.user_role}' can say next to '{self.ai_role}'.\n"
                f"Current goal: '{goal_desc}'.\n"
                f"Partner just said: '{self.last_tutor_line}'.\n"
                'Return JSON: {"suggestion": string, "translation": string}'
            )
            suggestion = strip_lang_tags(str(res.get("suggestion") or fallback_sample)).strip()
            translation = str(res.get("translation") or "").strip()
            if suggestion:
                hint_display = f"Try: “{suggestion}”" + (f" ({translation})" if translation and not self.teach_in_target else "")
                await self.broadcast({
                    "type": "rescue_payload",
                    "action": "hint",
                    "text": hint_display,
                })
            return

        if action == "translate":
            line_to_translate = strip_lang_tags(payload or self.last_tutor_line).strip()
            if not line_to_translate:
                return
            res = await self._quick_llm_json(
                f"Translate this spoken line naturally into {self.native_lang_name}: '{line_to_translate}'. "
                'Return JSON: {"translation": string}'
            )
            translation = str(res.get("translation") or "").strip()
            if translation:
                await self.broadcast({
                    "type": "rescue_payload",
                    "action": "translate",
                    "text": translation,
                })
                target_turn_id = self._turn_history_by_text.get(line_to_translate) or self.last_tutor_turn_id
                if target_turn_id:
                    await self.broadcast({
                        "type": "lesson_turn_update",
                        "id": target_turn_id,
                        "translation": translation,
                    })


class RoleplayAgent(Agent):
    """LiveKit Agent for dynamic practice-oriented Roleplays with ElevenLabs multilingual synthesis."""

    def __init__(
        self,
        *,
        orchestrator: RoleplayOrchestrator,
        tts: ElevenLabsTTS,
        stt: Any,
    ) -> None:
        self.orchestrator = orchestrator
        orchestrator.agent = self
        super().__init__(
            instructions=self._build_instructions(),
            tts=tts,
            stt=stt,
        )

    def _build_instructions(self) -> str:
        orch = self.orchestrator
        goals_lines = "\n".join(
            f"- {g.get('id')}: {g.get('label')} — {g.get('description', '')}"
            for g in orch.roleplay_goals
        )

        if orch.teach_in_target or orch.native_lang == orch.target_lang:
            clarification_rule = (
                f"2. CLARIFICATION REQUESTS: If the learner asks what a word means, asks 'how do I say...', or seems stuck, "
                f"explain simply in 1 short sentence in {orch.target_lang_name} using easy {orch.level}-level words, model a "
                f"natural phrase they can say, and immediately continue in character."
            )
        else:
            clarification_rule = (
                f"2. DYNAMIC CLARIFICATION ASIDE (USER LANGUAGE ALLOWED ONLY WHEN NEEDED): "
                f"90%+ of the conversation MUST be in {orch.target_lang_name}. However, if the learner explicitly asks for "
                f"clarification, translation, meaning, or 'how do I say...' (in {orch.native_lang_name} or {orch.target_lang_name}), "
                f"briefly explain in 1 short {orch.native_lang_name} sentence, model the {orch.target_lang_name} phrase naturally, "
                f"and immediately return to character (e.g. 'Sure, that means oat milk. Try asking me: ¿Tienen leche de avena?')."
            )

        strict_rule = (
            "4. FEEDBACK & RECASTING: Strict mode is ON — if the learner makes a grammar or vocabulary mistake, briefly "
            f"model the exact correction in {orch.target_lang_name}, call `log_roleplay_recast`, and continue in character."
            if orch.strict_mode
            else "4. IMPLICIT RECASTING: If the learner's meaning is clear but slightly unnatural or ungrammatical, do NOT "
            "interrupt the scene with a grammar lecture — naturally echo the idiomatic phrasing in your in-character reply "
            "and silently call `log_roleplay_recast`."
        )

        history_block = (
            f"\nCONVERSATION SO FAR (continue naturally without repeating yourself):\n{orch.history_transcript}\n"
            if orch.history_transcript
            else ""
        )

        return (
            f"You are playing '{orch.ai_role}' in a live spoken {orch.variety_name} ({orch.target_lang_name}) roleplay "
            f"with a {orch.native_lang_name} speaker at CEFR level {orch.level}.\n"
            f"Learner's role: '{orch.user_role}'\n"
            f"Scenario: {orch.scenario}\n"
            f"Setting & context: {orch.setting}\n"
            f"Practice goals for the learner:\n{goals_lines}\n"
            f"{history_block}\n"
            "CRITICAL VOICE & LANGUAGE RULES:\n"
            f"1. TARGET-LANGUAGE DEFAULT: Stay fully in character as '{orch.ai_role}' and speak in {orch.variety_name}. "
            f"Keep every turn concise and conversational: 1 to 2 short sentences (typically under 150 characters) suited "
            f"to CEFR {orch.level}, ending with a natural prompt or question.\n"
            f"{clarification_rule}\n"
            "3. REALISTIC PRACTICE: Include natural real-life details (prices, availability, times, follow-up questions) "
            "so the learner practices genuine listening and spontaneous speaking.\n"
            f"{strict_rule}\n"
            "5. GOAL TRACKING: As soon as the learner satisfies one of the practice goals, call `mark_roleplay_goal_complete` "
            "with that `goal_id` AND include your next spoken in-character reply.\n"
            "6. PLAIN SPEECH ONLY: NEVER output XML tags (<lang>), markdown (**bold**, *italics*, # headers, bullets), "
            "emojis, romanization in parentheses, or stage directions like (laughs) or [pause]."
        )

    async def transcription_node(self, text, model_settings):
        async for chunk in strip_lang_tags_stream(text):
            yield chunk

    async def tts_node(self, text, model_settings):
        orch = self.orchestrator
        async for frame in stream_llm_tts(
            orch.synthesizer,
            text,
            speed=orch.base_speed,
        ):
            yield frame

    async def on_enter(self) -> None:
        self.orchestrator.session = self.session
        asyncio.create_task(self.orchestrator.start())

    async def on_user_turn_completed(
        self, turn_ctx: llm.ChatContext, new_message: llm.ChatMessage
    ) -> None:
        orch = self.orchestrator
        orch.session = self.session
        text = (new_message.text_content or "").strip()
        if not text:
            raise StopResponse()

        norm = text.lower().strip(" .?!¡¿")
        if norm in REPEAT_EXACT_CUES and orch.last_tutor_line:
            asyncio.create_task(orch.handle_client_action("listen_normal"))
            raise StopResponse()
        if norm in SLOW_EXACT_CUES and orch.last_tutor_line:
            asyncio.create_task(orch.handle_client_action("listen_slow"))
            raise StopResponse()

        orch.turn_count += 1
        goal_num, total_goals, _ = orch._current_goal_progress()
        all_done = len(orch.completed_goal_ids) >= len(orch.roleplay_goals) and len(orch.roleplay_goals) > 0
        sender_label = (
            "You · Open practice"
            if all_done
            else f"You · Speaking (Goal {goal_num} of {total_goals})"
        )
        asyncio.create_task(
            orch.emit_turn(
                speaker="user",
                sender_label=sender_label,
                text=f"“{text}”",
            )
        )

    @function_tool
    async def mark_roleplay_goal_complete(
        self,
        goal_id: str,
        user_said: str = "",
        natural_recast: str = "",
    ) -> str:
        """Call as soon as the learner achieves one of the roleplay practice goals.

        Args:
            goal_id: Exact ID of the completed goal (e.g. 'goal_1', 'goal_2', 'goal_3').
            user_said: Optional exact learner phrase if it could be phrased more naturally.
            natural_recast: Optional more idiomatic target-language phrasing of what the learner meant.
        """
        return await self.orchestrator.on_goal_completed(
            goal_id=goal_id,
            user_said=user_said,
            natural_recast=natural_recast,
        )

    @function_tool
    async def log_roleplay_recast(
        self,
        user_said: str,
        natural_recast: str,
    ) -> str:
        """Silently logs a more natural or grammatically idiomatic phrasing for what the learner just said.

        Args:
            user_said: What the learner actually said.
            natural_recast: Natural idiomatic phrasing in the target language.
        """
        await self.orchestrator.record_recast(user_said=user_said, natural_form=natural_recast)
        return "Recast logged. Continue naturally in character."
