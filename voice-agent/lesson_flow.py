import asyncio
import json
import logging
import os
import re
import time
import unicodedata
from typing import Any, Callable, Coroutine

from google import genai
from google.genai import types
from livekit.agents import Agent, AgentSession, StopResponse, function_tool, llm
import livekit.plugins.cartesia as cartesia

from bilingual import (
    SpeechClause,
    SpeechSegment,
    ensure_terminal_punct,
    segment_bilingual_text,
    strip_lang_tags,
    strip_lang_tags_stream,
    strip_parenthetical_notes,
)
from voices import (
    BilingualCartesiaSynthesizer,
    stream_bilingual_tts,
    synthesize_bilingual_segments,
    update_cartesia_tts,
)

logger = logging.getLogger("voice-agent.lesson")

NATIVE_QUESTION_CUES = (
    "what does", "what is", "what's", "what means", "mean in", "meaning of",
    "how do i", "how do you", "how to say", "why is", "why do", "can you explain",
    "explain", "translate", "help me", "i don't understand", "didn't catch",
    "que significa", "qué significa", "como se dice", "cómo se dice", "por qué",
)
REPEAT_CUES = ("repeat", "say that again", "one more time", "say it again", "repite", "otra vez")
SLOW_CUES = ("slower", "slow down", "slowly", "more slowly", "más lento", "despacio")
HINT_CUES = ("give me a hint", "hint", "first word", "a clue", "pista")


def _compute_blank_lengths(phrase: str) -> list[int]:
    words = [re.sub(r"[^\w]", "", w) for w in (phrase or "").split()]
    lengths = [max(2, min(8, len(w))) for w in words if w]
    return lengths[:5] if lengths else [5, 4, 6]


def _normalize_tokens(text: str) -> list[str]:
    folded = unicodedata.normalize("NFD", text.lower())
    without_marks = "".join(ch for ch in folded if unicodedata.category(ch) != "Mn")
    cleaned = re.sub(r"[^\w\s]", " ", without_marks, flags=re.UNICODE)
    return [tok for tok in cleaned.split() if tok]


def _levenshtein(seq_a: list[str] | str, seq_b: list[str] | str) -> int:
    m, n = len(seq_a), len(seq_b)
    if m == 0:
        return n
    if n == 0:
        return m
    prev = list(range(n + 1))
    for i in range(1, m + 1):
        curr = [i] + [0] * n
        for j in range(1, n + 1):
            cost = 0 if seq_a[i - 1] == seq_b[j - 1] else 1
            curr[j] = min(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        prev = curr
    return prev[n]


def score_pronunciation(
    spoken: str,
    expected_phrase: str,
    focus_word: str,
    stt_confidence: float | None = None,
) -> tuple[int, bool]:
    """
    Computes 0–100 pronunciation score:
      - 60% normalized token/character Levenshtein match against expected_phrase
      - 40% focusWord accuracy + Deepgram STT confidence
    Returns (score, is_noise_or_empty).
    """
    spoken_tokens = _normalize_tokens(spoken)
    if not spoken_tokens:
        return 0, True
    if stt_confidence is not None and stt_confidence < 0.35 and len(spoken_tokens) <= 1:
        return 0, True

    expected_tokens = _normalize_tokens(expected_phrase)
    focus_tokens = _normalize_tokens(focus_word)

    if not expected_tokens:
        return 90, False

    tok_dist = _levenshtein(spoken_tokens, expected_tokens)
    tok_sim = max(0.0, 1.0 - tok_dist / max(len(spoken_tokens), len(expected_tokens)))

    spoken_str = " ".join(spoken_tokens)
    expected_str = " ".join(expected_tokens)
    char_dist = _levenshtein(spoken_str, expected_str)
    char_sim = max(0.0, 1.0 - char_dist / max(len(spoken_str), len(expected_str), 1))

    phrase_match = 0.55 * char_sim + 0.45 * tok_sim

    # Focus word presence / character similarity
    focus_str = " ".join(focus_tokens)
    if focus_str and focus_str in spoken_str:
        focus_sim = 1.0
    elif focus_tokens:
        best = 0.0
        window = len(focus_tokens)
        for idx in range(max(1, len(spoken_tokens) - window + 1)):
            candidate = " ".join(spoken_tokens[idx : idx + window])
            d = _levenshtein(candidate, focus_str)
            best = max(best, max(0.0, 1.0 - d / max(len(candidate), len(focus_str), 1)))
        focus_sim = best
    else:
        focus_sim = phrase_match

    conf = stt_confidence if (stt_confidence is not None and stt_confidence > 0) else 0.90
    focus_component = 0.65 * focus_sim + 0.35 * min(1.0, conf)

    raw_score = (0.60 * phrase_match + 0.40 * focus_component) * 100.0
    score = int(round(max(12.0, min(99.0, raw_score))))
    return score, False


PRAISE_BY_LANG: dict[str, str] = {
    "es": "¡Muy bien!",
    "fr": "Très bien !",
    "de": "Sehr gut!",
    "it": "Molto bene!",
    "pt": "Muito bem!",
    "ja": "いいですね！",
    "ko": "아주 좋아요!",
    "zh": "非常好！",
    "nl": "Heel goed!",
    "pl": "Bardzo dobrze!",
    "ru": "Отлично!",
    "tr": "Çok iyi!",
    "sv": "Mycket bra!",
    "ar": "ممتاز!",
    "hi": "बहुत बढ़िया!",
    "en": "Nice work!",
}


class LessonOrchestrator:
    """Drives the 5-stage 03g pedagogical state machine over LiveKit Data Channels + Cartesia TTS."""

    def __init__(
        self,
        *,
        meta: dict[str, Any],
        tts: cartesia.TTS,
        broadcast: Callable[[dict[str, Any]], Coroutine[Any, Any, None]],
    ) -> None:
        self.meta = meta
        self.tts = tts
        self.broadcast = broadcast
        self.session: AgentSession | None = None
        self.agent: "LessonAgent | None" = None

        self.lesson_id: str = meta.get("lessonId", "")
        self.lesson_title: str = meta.get("lessonTitle", "")
        self.native_lang: str = meta.get("nativeLanguage") or "en"
        self.target_lang: str = meta.get("targetLanguage") or meta.get("language") or "es"
        self.target_lang_name: str = meta.get("targetLanguageName") or self.target_lang.upper()
        self.native_lang_name: str = meta.get("nativeLanguageName") or "English"
        self.level: str = meta.get("level") or "B1"
        self.teach_in_target: bool = bool(meta.get("teachInTarget", False)) or self.level in ("C1", "C2")
        self.curriculum: dict[str, Any] = meta.get("curriculum") or {}

        self.target_phrases: list[dict[str, Any]] = list(self.curriculum.get("targetPhrases") or [])
        self.building_blocks: list[dict[str, Any]] = list(self.curriculum.get("buildingBlocks") or [])
        self.recall_prompts: list[dict[str, Any]] = list(self.curriculum.get("recallPrompts") or [])
        self.roleplay_cfg: dict[str, Any] = dict(self.curriculum.get("roleplay") or {})
        self.roleplay_goals: list[dict[str, Any]] = list(self.roleplay_cfg.get("goals") or [])

        self.stage: str = "teach_model"
        self.phrase_idx: int = 0
        self.phrase_retries: int = 0
        self.block_idx: int = 0
        self.block_retries: int = 0
        self.recall_idx: int = 0
        self.recall_retries: int = 0

        self.completed_goal_ids: set[str] = set()
        self.roleplay_turns: int = 0
        self.wrapping_up: bool = False
        self.completed: bool = False

        self.started_at: float = time.time()
        self.pronunciation_scores: list[int] = []
        self.lowest_focus: tuple[int, dict[str, Any]] | None = None
        self.revealed_phrases: set[str] = set()
        self.rescue_count: int = 0
        self.phrase_results: dict[str, str] = {}
        self.recast_notes: list[dict[str, str]] = []
        self.last_tutor_line: str = ""
        self.last_tutor_turn_id: str = ""
        self._turn_seq: int = 0
        self.last_prompt_lang: str = self.target_lang
        self.last_prompt_text: str = ""
        self.last_prompt_clauses: list[SpeechClause] = []

        self.synthesizer = BilingualCartesiaSynthesizer(
            voice_id=meta.get("voice"),
            tutor_voice=meta.get("tutorVoice"),
            accent=meta.get("accent"),
        )
        self._speech_lock = asyncio.Lock()
        self._genai_client: genai.Client | None = None

    def _next_turn_id(self, prefix: str = "les") -> str:
        self._turn_seq += 1
        return f"{prefix}-{self._turn_seq}-{int(time.time() * 1000)}"

    async def _emit_lesson_turn(
        self,
        *,
        speaker: str,
        sender_label: str,
        text: str,
        translation: str | None = None,
        actions: list[str] | None = None,
        turn_id: str | None = None,
    ) -> str:
        tid = turn_id or self._next_turn_id("agent" if speaker == "agent" else "user")
        if speaker == "agent":
            self.last_tutor_turn_id = tid
        await self.broadcast({
            "type": "lesson_turn",
            "id": tid,
            "speaker": speaker,
            "senderLabel": sender_label,
            "text": text,
            "translation": translation,
            "actions": actions or [],
        })
        return tid

    def _roleplay_goals_payload(self) -> list[dict[str, Any]]:
        goals = self.roleplay_goals or [{"id": "goal_1", "label": "Complete task", "description": "Complete the conversation"}]
        first_incomplete_found = False
        result: list[dict[str, Any]] = []
        for idx, g in enumerate(goals):
            gid = str(g.get("id") or f"goal_{idx + 1}")
            done = gid in self.completed_goal_ids
            is_current = False
            if not done and not first_incomplete_found:
                is_current = True
                first_incomplete_found = True
            sample = ""
            if idx < len(self.target_phrases):
                sample = str(self.target_phrases[idx].get("phrase") or "")
            elif self.target_phrases:
                sample = str(self.target_phrases[-1].get("phrase") or "")
            result.append({
                "id": gid,
                "index": idx + 1,
                "label": str(g.get("label") or f"Goal {idx + 1}"),
                "description": str(g.get("description") or g.get("label") or ""),
                "samplePhrase": sample,
                "completed": done,
                "current": is_current,
            })
        return result

    def _current_goal_progress(self) -> tuple[int, int]:
        total = max(1, len(self.roleplay_goals))
        done = len(self.completed_goal_ids)
        return min(total, done + 1), total

    @property
    def cue_lang(self) -> str:
        return self.target_lang if self.teach_in_target else self.native_lang

    @property
    def model_speed(self) -> float:
        return 0.92 if self.level in ("A1", "A2") else 1.0

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
                    temperature=0.1,
                    response_mime_type="application/json",
                ),
            )
            return json.loads(resp.text or "{}")

        try:
            return await asyncio.to_thread(_call)
        except Exception as e:
            logger.warning(f"Quick LLM check fallback: {e}")
            return {}

    async def _speak_clauses(
        self,
        clauses: list[SpeechClause],
        *,
        display_text: str | None = None,
        allow_interruptions: bool = True,
        record_as_tutor_line: bool = False,
    ) -> None:
        """Speaks a sequence of distinct language clauses without heuristic parsing.

        Synthesizes all clauses in parallel and streams them out as a single continuous speech turn.
        """
        if not self.session or not clauses:
            return
        async with self._speech_lock:
            clean_text = strip_lang_tags(display_text or " ".join(c.text for c in clauses))
            self.last_prompt_clauses = list(clauses)
            self.last_prompt_text = clean_text
            self.last_prompt_lang = clauses[-1].language if clauses else self.target_lang

            if record_as_tutor_line:
                self.last_tutor_line = clean_text
                await self.broadcast({
                    "type": "transcript",
                    "speaker": "agent",
                    "text": clean_text,
                    "is_final": True,
                })

            audio_stream = synthesize_bilingual_segments(self.synthesizer, clauses)
            handle = self.session.say(
                clean_text,
                audio=audio_stream,
                allow_interruptions=allow_interruptions,
                add_to_chat_ctx=False,
            )
            try:
                await handle.wait_for_playout()
            except Exception as e:
                logger.debug(f"Speech interrupted or cancelled: {e}")

    async def _speak_mono(
        self,
        text: str,
        *,
        language: str,
        speed: float = 1.0,
        allow_interruptions: bool = True,
        record_as_tutor_line: bool = False,
    ) -> None:
        """Synthesizes an utterance 100% in a single language with zero heuristic splitting."""
        if not text.strip():
            return
        clause = SpeechClause(
            text=text,
            language=language,
            speed=speed,
            pause_after_ms=0,
        )
        await self._speak_clauses(
            [clause],
            display_text=text,
            allow_interruptions=allow_interruptions,
            record_as_tutor_line=record_as_tutor_line,
        )

    async def _speak(
        self,
        text: str,
        *,
        language: str | None = None,
        speed: float = 1.0,
        allow_interruptions: bool = True,
        record_as_tutor_line: bool = False,
    ) -> None:
        """Fallback for untagged dynamic lines: segments if explicit tags or colon patterns exist."""
        if not self.session or not text.strip():
            return
        segments = segment_bilingual_text(
            text,
            native_lang=self.native_lang,
            target_lang=self.target_lang,
            default_lang=language or self.target_lang,
            speed=speed,
        )
        await self._speak_clauses(
            segments,
            display_text=text,
            allow_interruptions=allow_interruptions,
            record_as_tutor_line=record_as_tutor_line,
        )

    def _set_endpointing_delay(self, delay_sec: float) -> None:
        if not self.session:
            return
        try:
            if hasattr(self.session, "_opts") and hasattr(self.session._opts, "min_endpointing_delay"):
                self.session._opts.min_endpointing_delay = delay_sec
        except Exception:
            pass

    def _teach_card_payload(self, item: dict[str, Any], include_setup: bool = True, goal_label: str = "") -> dict[str, Any]:
        return {
            "kind": "teach_card",
            "setupPrompt": item.get("setupNative", "") if include_setup else "",
            "phrase": item.get("phrase", ""),
            "translation": item.get("translation", ""),
            "focusWord": item.get("focusWord", ""),
            "syllables": item.get("syllables", ""),
            "cue": item.get("pronunciationCue", ""),
            "chunks": item.get("chunks") or [],
            "goalLabel": goal_label,
        }

    # ── Stage Transitions ─────────────────────────────────────────────────────

    async def start(self) -> None:
        if self.target_phrases:
            await self._enter_teach_model(0)
        elif self.building_blocks:
            await self._enter_building_blocks(0)
        elif self.recall_prompts:
            await self._enter_active_recall(0)
        else:
            await self._enter_roleplay()

    async def _enter_teach_model(self, idx: int) -> None:
        self.stage = "teach_model"
        self.phrase_idx = idx
        self.phrase_retries = 0
        self._set_endpointing_delay(0.6)

        item = self.target_phrases[idx]
        total = len(self.target_phrases)
        setup = item.get("setupNative") or f"Listen to phrase {idx + 1}:"
        phrase = item.get("phrase", "")
        translation = item.get("translation", "")
        goal_label = ""
        if self.roleplay_goals:
            goal_label = str(self.roleplay_goals[min(idx, len(self.roleplay_goals) - 1)].get("label") or "")

        await self.broadcast({
            "type": "lesson_stage",
            "stage": "teach_model",
            "pill": {
                "icon": "book-open",
                "role": "Learn",
                "goal": f"Phrase {idx + 1} of {total}",
                "contextNote": goal_label,
            },
            "card": self._teach_card_payload(item, include_setup=True, goal_label=goal_label),
            "recallPrompt": None,
            "rescueChips": [],
        })
        await self._emit_lesson_turn(
            speaker="agent",
            sender_label="Sofía · Speaking",
            text=phrase,
            translation=None if self.teach_in_target else translation,
            actions=["listen_normal", "listen_slow"],
        )

        clauses: list[SpeechClause] = []
        if not self.teach_in_target and setup:
            clauses.append(
                SpeechClause(text=setup, language=self.native_lang, speed=1.0, pause_after_ms=220)
            )
        clauses.append(
            SpeechClause(text=phrase, language=self.target_lang, speed=self.model_speed)
        )
        display_text = f"{setup} {phrase}".strip() if (not self.teach_in_target and setup) else phrase
        await self._speak_clauses(clauses, display_text=display_text)

        # Transition immediately into 03b Shadow & Pronunciation if still in teach_model for this phrase
        if self.stage == "teach_model" and self.phrase_idx == idx and not self.completed:
            await self._enter_shadow_drill(idx)

    async def _enter_shadow_drill(self, idx: int) -> None:
        self.stage = "shadow_drill"
        self.phrase_idx = idx
        self._set_endpointing_delay(0.8)
        item = self.target_phrases[idx]
        focus_word = item.get("focusWord", "")
        stress_note = f"Stress “{focus_word}”" if focus_word else (item.get("pronunciationCue") or "Pronunciation drill")

        await self.broadcast({
            "type": "lesson_stage",
            "stage": "shadow_drill",
            "pill": {
                "icon": "waveform",
                "role": "Shadow",
                "goal": "Pronunciation drill",
                "contextNote": stress_note,
            },
            "card": self._teach_card_payload(item, include_setup=False, goal_label=stress_note),
            "recallPrompt": None,
            "rescueChips": [],
        })
        if self.last_tutor_turn_id:
            await self.broadcast({
                "type": "lesson_turn_update",
                "id": self.last_tutor_turn_id,
                "senderLabel": "Sofía",
            })

    async def _advance_after_phrase(self) -> None:
        next_idx = self.phrase_idx + 1
        if next_idx < len(self.target_phrases):
            await self._enter_teach_model(next_idx)
        elif self.building_blocks:
            await self._enter_building_blocks(0)
        elif self.recall_prompts:
            await self._enter_active_recall(0)
        else:
            await self._enter_roleplay()

    async def _enter_building_blocks(self, idx: int) -> None:
        self.stage = "building_blocks"
        self.block_idx = idx
        self.block_retries = 0
        self._set_endpointing_delay(1.4)

        item = self.building_blocks[idx]
        prompt_native = item.get("promptNative", "")
        new_word = item.get("newWord", "")
        meaning = item.get("meaning", "")
        clean_meaning = strip_parenthetical_notes(meaning) or meaning
        stem_hint = item.get("stemHint", "")

        # Never include expectedTarget in Scaffold Card so learner assembles it actively
        await self.broadcast({
            "type": "lesson_stage",
            "stage": "building_blocks",
            "pill": {
                "icon": "sparkles",
                "role": "Build",
                "goal": "Combine pattern",
                "contextNote": "Pattern drill",
            },
            "card": {
                "kind": "scaffold_card",
                "prompt": prompt_native,
                "newWord": new_word,
                "meaning": clean_meaning,
                "stemHint": stem_hint,
            },
            "recallPrompt": None,
            "rescueChips": ["hint", "reveal", "slow"],
        })
        await self._emit_lesson_turn(
            speaker="agent",
            sender_label="Sofía",
            text=prompt_native,
            actions=["hint", "reveal"],
        )

        if not self.teach_in_target and clean_meaning and new_word:
            clean_prompt = ensure_terminal_punct(prompt_native)
            clauses = [
                SpeechClause(text=f"{clean_prompt} Here is the word for {clean_meaning}:", language=self.native_lang, speed=1.0, pause_after_ms=200),
                SpeechClause(text=f"{new_word}.", language=self.target_lang, speed=self.model_speed),
            ]
            await self._speak_clauses(clauses, display_text=f"{clean_prompt} The word for {clean_meaning} is {new_word}.")
        else:
            await self._speak_mono(prompt_native, language=self.cue_lang, speed=1.0)

    async def _advance_after_block(self) -> None:
        next_idx = self.block_idx + 1
        if next_idx < len(self.building_blocks):
            await self._enter_building_blocks(next_idx)
        elif self.recall_prompts:
            await self._enter_active_recall(0)
        else:
            await self._enter_roleplay()

    async def _enter_active_recall(self, idx: int) -> None:
        self.stage = "active_recall"
        self.recall_idx = idx
        self.recall_retries = 0
        self._set_endpointing_delay(1.4)

        item = self.recall_prompts[idx]
        cue = ensure_terminal_punct(item.get("situationNative", ""))
        targets = item.get("expectedTargets") or []
        blank_lengths = _compute_blank_lengths(targets[0] if targets else "")

        await self.broadcast({
            "type": "lesson_stage",
            "stage": "active_recall",
            "pill": {
                "icon": "rotate-ccw",
                "role": "Recall",
                "goal": "Speak from memory",
                "contextNote": "Target hidden",
            },
            "card": None,
            "recallPrompt": {
                "eyebrow": f"Say in {self.target_lang_name}",
                "cue": cue,
                "blankLengths": blank_lengths,
            },
            "rescueChips": ["first_word", "reveal", "slow"],
        })
        await self._emit_lesson_turn(
            speaker="agent",
            sender_label="Sofía",
            text=cue,
            actions=["first_word", "reveal"],
        )

        await self._speak_mono(cue, language=self.cue_lang, speed=1.0)

    async def _advance_after_recall(self) -> None:
        next_idx = self.recall_idx + 1
        if next_idx < len(self.recall_prompts):
            await self._enter_active_recall(next_idx)
        else:
            await self._enter_roleplay()

    async def _enter_roleplay(self) -> None:
        self.stage = "roleplay"
        self._set_endpointing_delay(1.0)
        update_cartesia_tts(self.tts, language=self.target_lang, speed=1.0)

        ai_role = self.roleplay_cfg.get("aiRole") or "Conversation partner"
        first_goal = self.roleplay_goals[0] if self.roleplay_goals else {"id": "goal_1", "label": "Complete task"}
        total_goals = max(1, len(self.roleplay_goals))

        await self.broadcast({
            "type": "lesson_stage",
            "stage": "roleplay",
            "pill": {
                "icon": "users",
                "role": ai_role,
                "goal": f"Goal 1 of {total_goals} · {first_goal.get('label', '')}".strip(" ·"),
            },
            "card": None,
            "recallPrompt": None,
            "roleplayGoals": self._roleplay_goals_payload(),
            "rescueChips": ["hint", "translate", "slow"],
        })

        if self.session and self.agent:
            await self.agent.activate_roleplay_instructions()
            self.session.generate_reply(
                instructions=(
                    f"Start the roleplay scene in {self.target_lang_name} as '{ai_role}'. "
                    "Speak 1–2 short natural sentences in character to invite the learner toward Goal 1."
                )
            )

    # ── Turn Handling for Stages 03b / 03c / 03d ─────────────────────────────

    async def _classify_learner_intent(self, transcript: str) -> str:
        low = transcript.strip().lower()
        if not low:
            return "attempt"
        if any(cue in low for cue in REPEAT_CUES):
            return "repeat"
        if any(cue in low for cue in SLOW_CUES):
            return "slow"
        if any(cue in low for cue in HINT_CUES):
            return "hint"
        if self.native_lang != self.target_lang:
            looks_like_question = (
                any(cue in low for cue in NATIVE_QUESTION_CUES)
                or (
                    "?" in low
                    and any(
                        w in low.split()
                        for w in ("what", "why", "how", "when", "does", "do", "is", "can", "mean", "means")
                    )
                )
            )
            if looks_like_question:
                res = await self._quick_llm_json(
                    f"The learner is in a {self.target_lang_name} lesson (native language: {self.native_lang_name}). "
                    f"Current target phrase: '{self._current_target_phrase()}'. "
                    f"Learner said: '{transcript}'. "
                    'Is this a question/request to the tutor in their native language, or an attempt at the target phrase? '
                    'Return JSON: {"intent": "question" | "attempt"}'
                )
                if str(res.get("intent") or "").lower() == "question":
                    return "question"
        return "attempt"

    async def _answer_bilingual_question(self, question_text: str) -> None:
        await self._emit_lesson_turn(
            speaker="user",
            sender_label="You",
            text=question_text,
        )
        current_target = self._current_target_phrase()
        res = await self._quick_llm_json(
            f"You are Sofía, a warm bilingual {self.target_lang_name} tutor speaking with a {self.native_lang_name} learner "
            f"(level {self.level}). Current lesson prompt/phrase: '{current_target}'. "
            f"Learner asked: '{question_text}'. "
            f"Answer concisely in 1–2 short spoken sentences: explain in {self.native_lang_name} and pronounce any "
            f"{self.target_lang_name} words/examples in separate target clauses. "
            'Return JSON: {"clauses": [{"text": string, "lang": "native" | "target"}]}'
        )
        raw_clauses = res.get("clauses")
        clauses: list[SpeechClause] = []
        if isinstance(raw_clauses, list):
            for item in raw_clauses[:4]:
                if not isinstance(item, dict):
                    continue
                t = str(item.get("text") or "").strip()
                if not t:
                    continue
                lang_flag = str(item.get("lang") or "native").lower()
                lang_code = self.target_lang if lang_flag == "target" else self.native_lang
                clauses.append(SpeechClause(text=t, language=lang_code, speed=1.0, pause_after_ms=140))

        if not clauses:
            clauses = [
                SpeechClause(text="Here is how that sounds:", language=self.native_lang, speed=1.0, pause_after_ms=180),
                SpeechClause(text=current_target or "Let's try together.", language=self.target_lang if current_target else self.native_lang, speed=self.model_speed),
            ]
        display = " ".join(c.text for c in clauses)
        await self._emit_lesson_turn(
            speaker="agent",
            sender_label="Sofía",
            text=display,
            actions=["listen_normal", "listen_slow"] if current_target else [],
        )
        await self._speak_clauses(clauses, display_text=display)

    async def handle_structured_user_turn(self, transcript: str, confidence: float | None) -> None:
        if self.completed or self.wrapping_up:
            return
        if self.stage == "teach_model":
            # Learner jumped in early and shadowed right away — score it as shadow_drill!
            self.stage = "shadow_drill"

        intent = await self._classify_learner_intent(transcript)
        if intent == "repeat":
            await self.handle_client_action("listen_normal")
            return
        if intent == "slow":
            await self.handle_client_action("listen_slow")
            return
        if intent == "hint":
            action = "first_word" if self.stage == "active_recall" else "hint"
            await self.handle_client_action(action)
            return
        if intent == "question":
            await self._answer_bilingual_question(transcript)
            return

        if self.stage == "shadow_drill":
            await self._handle_shadow_turn(transcript, confidence)
        elif self.stage == "building_blocks":
            await self._handle_building_blocks_turn(transcript, confidence)
        elif self.stage == "active_recall":
            await self._handle_recall_turn(transcript, confidence)

    async def _handle_shadow_turn(self, transcript: str, confidence: float | None) -> None:
        item = self.target_phrases[self.phrase_idx]
        phrase = item.get("phrase", "")
        focus_word = item.get("focusWord", "")
        cue = item.get("pronunciationCue") or "Check word stress"

        score, is_noise = score_pronunciation(transcript, phrase, focus_word, confidence)
        if is_noise:
            await self.broadcast({
                "type": "pronunciation_eval",
                "score": 0,
                "passed": False,
                "focusWord": focus_word,
                "feedbackText": "Didn't catch that — try speaking a bit closer",
                "showChunks": False,
            })
            return

        spoken_tokens = _normalize_tokens(transcript)
        focus_tokens = _normalize_tokens(focus_word)
        focus_sim = 1.0 if (focus_tokens and " ".join(focus_tokens) in " ".join(spoken_tokens)) else 0.0

        self.pronunciation_scores.append(score)
        if self.lowest_focus is None or score < self.lowest_focus[0]:
            self.lowest_focus = (score, item)

        await self._emit_lesson_turn(
            speaker="user",
            sender_label=f"You · {score}% match",
            text=f"“{transcript}”",
        )

        if score >= 85:
            status = "practice" if phrase in self.revealed_phrases else "mastered"
            self.phrase_results[phrase] = status
            await self.broadcast({
                "type": "pronunciation_eval",
                "score": score,
                "passed": True,
                "focusWord": focus_word,
                "feedbackText": f"{score}% match · Natural pronunciation",
                "showChunks": False,
            })
            praise = PRAISE_BY_LANG.get(self.target_lang, PRAISE_BY_LANG["en"])
            await self._speak_mono(praise, language=self.target_lang, speed=1.0)
            await asyncio.sleep(0.35)
            await self._advance_after_phrase()
            return

        # Smart recognition: user repeated ONLY the key focus word (e.g. said "Desarrollador" alone)
        if focus_tokens and len(spoken_tokens) <= len(focus_tokens) + 1 and (focus_sim >= 0.85 or score >= 35):
            feedback_msg = f"Great pronunciation on '{focus_word}'! Now say the full sentence."
            await self.broadcast({
                "type": "pronunciation_eval",
                "score": max(score, 78),
                "passed": False,
                "focusWord": focus_word,
                "feedbackText": feedback_msg,
                "showChunks": True,
            })
            if not self.teach_in_target:
                clauses = [
                    SpeechClause(text="Nice pronunciation on that word! Now say the full sentence.", language=self.native_lang, speed=1.0, pause_after_ms=200),
                    SpeechClause(text=phrase, language=self.target_lang, speed=self.model_speed),
                ]
                await self._speak_clauses(clauses, display_text=f"Nice pronunciation on that word! Now say the full sentence: {phrase}")
            else:
                await self._speak_mono(phrase, language=self.target_lang, speed=self.model_speed)
            return

        self.phrase_retries += 1
        if self.phrase_retries == 1:
            await self.broadcast({
                "type": "pronunciation_eval",
                "score": score,
                "passed": False,
                "focusWord": focus_word,
                "feedbackText": f"{score}% match · Clear rhythm, try once more",
                "showChunks": True,
            })
            if not self.teach_in_target:
                clauses = [
                    SpeechClause(text="Almost! Listen closely to the key word, then try the whole sentence.", language=self.native_lang, speed=1.0, pause_after_ms=200),
                    SpeechClause(text=f"{focus_word}.", language=self.target_lang, speed=0.85, pause_after_ms=250),
                    SpeechClause(text=phrase, language=self.target_lang, speed=self.model_speed),
                ]
                await self._speak_clauses(clauses, display_text=f"Almost! Listen to '{focus_word}', then try: {phrase}")
            else:
                clauses = [
                    SpeechClause(text=f"{focus_word}.", language=self.target_lang, speed=0.85, pause_after_ms=250),
                    SpeechClause(text=phrase, language=self.target_lang, speed=self.model_speed),
                ]
                await self._speak_clauses(clauses, display_text=f"{focus_word}. {phrase}")
            return

        if self.phrase_retries == 2:
            await self.broadcast({
                "type": "pronunciation_eval",
                "score": score,
                "passed": False,
                "focusWord": focus_word,
                "feedbackText": f"{score}% match · Listen to the full sentence",
                "showChunks": True,
            })
            if not self.teach_in_target:
                clauses = [
                    SpeechClause(text="Listen once more to the full phrase:", language=self.native_lang, speed=1.0, pause_after_ms=200),
                    SpeechClause(text=phrase, language=self.target_lang, speed=self.model_speed),
                ]
                await self._speak_clauses(clauses, display_text=f"Listen once more: {phrase}")
            else:
                await self._speak_mono(phrase, language=self.target_lang, speed=self.model_speed)
            return

        # Max retries reached: record as "practice" and move forward constructively
        self.phrase_results[phrase] = "practice"
        await self.broadcast({
            "type": "pronunciation_eval",
            "score": score,
            "passed": True,
            "focusWord": focus_word,
            "feedbackText": f"{score}% match · Saved for spaced practice",
            "showChunks": False,
        })
        if not self.teach_in_target:
            await self._speak_mono("Good effort, let's keep moving.", language=self.native_lang, speed=1.0)
        await asyncio.sleep(0.3)
        await self._advance_after_phrase()

    async def _handle_building_blocks_turn(self, transcript: str, confidence: float | None) -> None:
        item = self.building_blocks[self.block_idx]
        expected = item.get("expectedTarget", "")
        new_word = item.get("newWord", "")

        score, is_noise = score_pronunciation(transcript, expected, new_word, confidence)
        if is_noise:
            return

        passed = score >= 76
        if not passed:
            check = await self._quick_llm_json(
                f"Target language: {self.target_lang_name}. Task: '{item.get('promptNative', '')}'. "
                f"Expected sentence: '{expected}' (must include '{new_word}' or natural equivalent). "
                f"Learner said: '{transcript}'. "
                'Return JSON: {"accepted": boolean, "shortHint": string}'
            )
            passed = bool(check.get("accepted", False))
            hint = str(check.get("shortHint") or "")
        else:
            hint = ""

        if passed:
            self.pronunciation_scores.append(max(score, 85))
            status = "practice" if expected in self.revealed_phrases else "mastered"
            self.phrase_results[expected] = status
            await self._emit_lesson_turn(
                speaker="user",
                sender_label="You · Speaking",
                text=f"“{transcript}”",
            )
            praise = PRAISE_BY_LANG.get(self.target_lang, PRAISE_BY_LANG["en"])
            await self._speak_mono(f"{praise} {expected}", language=self.target_lang, speed=1.0)
            await asyncio.sleep(0.3)
            await self._advance_after_block()
            return

        await self._emit_lesson_turn(
            speaker="user",
            sender_label="You · Speaking",
            text=f"“{transcript}”",
        )
        self.block_retries += 1
        if self.block_retries <= 1:
            if not self.teach_in_target and item.get("stemHint") and new_word:
                stem_clean = item.get("stemHint", "").rstrip("….").strip()
                clauses = [
                    SpeechClause(text="Almost! Start with this pattern and add the new word:", language=self.native_lang, speed=1.0, pause_after_ms=220),
                    SpeechClause(text=f"{stem_clean}… {new_word}.", language=self.target_lang, speed=0.90),
                ]
                await self._speak_clauses(clauses, display_text=f"Try combining: {stem_clean}… {new_word}")
            else:
                tip = hint or f"Try combining {item.get('stemHint', '')} with {new_word}."
                await self._speak_mono(tip, language=self.cue_lang, speed=1.0)
            return

        self.phrase_results[expected] = "practice"
        await self._speak_mono(expected, language=self.target_lang, speed=0.92)
        await asyncio.sleep(0.3)
        await self._advance_after_block()

    async def _handle_recall_turn(self, transcript: str, confidence: float | None) -> None:
        item = self.recall_prompts[self.recall_idx]
        targets = item.get("expectedTargets") or []
        primary_target = targets[0] if targets else ""

        best_score = 0
        is_noise = True
        for t in targets or [""]:
            sc, noise = score_pronunciation(transcript, t, "", confidence)
            if not noise:
                is_noise = False
                best_score = max(best_score, sc)

        if is_noise:
            return

        passed = best_score >= 78
        if not passed:
            check = await self._quick_llm_json(
                f"Target language: {self.target_lang_name}. Situation: '{item.get('situationNative', '')}'. "
                f"Reference answers: {json.dumps(targets, ensure_ascii=False)}. "
                f"Learner said: '{transcript}'. Accept any natural, grammatically understandable synonym in {self.target_lang_name}. "
                'Return JSON: {"accepted": boolean}'
            )
            passed = bool(check.get("accepted", False))

        if passed:
            self.pronunciation_scores.append(max(best_score, 86))
            await self._emit_lesson_turn(
                speaker="user",
                sender_label="You · Spoken recall",
                text=f"“{transcript}”",
            )
            if primary_target:
                status = "practice" if primary_target in self.revealed_phrases else "mastered"
                self.phrase_results.setdefault(primary_target, status)
            praise = PRAISE_BY_LANG.get(self.target_lang, PRAISE_BY_LANG["en"])
            confirm = f"{praise} {primary_target}".strip()
            await self._speak_mono(confirm, language=self.target_lang, speed=1.0)
            await asyncio.sleep(0.35)
            await self._advance_after_recall()
            return

        await self._emit_lesson_turn(
            speaker="user",
            sender_label="You · Spoken recall",
            text=f"“{transcript}”",
        )
        self.recall_retries += 1
        if self.recall_retries <= 1:
            first_word = item.get("firstWordHint") or (primary_target.split(" ")[0] + "…" if primary_target else "")
            await self.broadcast({
                "type": "rescue_payload",
                "action": "first_word",
                "text": f"Start with: {first_word}",
            })
            first_word_clean = first_word.rstrip("….").strip()
            if not self.teach_in_target and first_word_clean:
                clauses = [
                    SpeechClause(text="Start your sentence like this:", language=self.native_lang, speed=1.0, pause_after_ms=180),
                    SpeechClause(text=f"{first_word_clean}.", language=self.target_lang, speed=0.90),
                ]
                await self._speak_clauses(clauses, display_text=f"Start with: {first_word_clean}…")
            else:
                await self._speak_mono(f"{first_word_clean}.", language=self.target_lang, speed=0.90)
            return

        if primary_target:
            self.phrase_results[primary_target] = "practice"
            await self._speak_mono(primary_target, language=self.target_lang, speed=0.92)
        await asyncio.sleep(0.35)
        await self._advance_after_recall()

    # ── Roleplay Goal Tracking & Wrap-Up (Stages 03e / 03f -> 04) ────────────

    async def on_roleplay_goal_marked(
        self,
        goal_id: str,
        user_said: str = "",
        natural_recast: str = "",
    ) -> str:
        valid_ids = {g.get("id") for g in self.roleplay_goals if g.get("id")}
        if goal_id in valid_ids:
            self.completed_goal_ids.add(goal_id)
        elif self.roleplay_goals:
            # Match sequentially if model passed an approximate goal id
            for g in self.roleplay_goals:
                gid = g.get("id")
                if gid and gid not in self.completed_goal_ids:
                    self.completed_goal_ids.add(gid)
                    break

        if user_said.strip() and natural_recast.strip() and user_said.strip().lower() != natural_recast.strip().lower():
            self.recast_notes.append({
                "user_said": user_said.strip(),
                "natural_form": natural_recast.strip(),
            })

        remaining = [g for g in self.roleplay_goals if g.get("id") not in self.completed_goal_ids]
        total_goals = max(1, len(self.roleplay_goals))
        ai_role = self.roleplay_cfg.get("aiRole") or "Conversation partner"

        if remaining:
            next_goal = remaining[0]
            done_count = total_goals - len(remaining)
            goal_num = min(total_goals, done_count + 1)
            await self.broadcast({
                "type": "lesson_stage",
                "stage": "roleplay",
                "pill": {
                    "icon": "users",
                    "role": ai_role,
                    "goal": f"Goal {goal_num} of {total_goals} · {next_goal.get('label', '')}".strip(" ·"),
                },
                "card": None,
                "recallPrompt": None,
                "roleplayGoals": self._roleplay_goals_payload(),
                "rescueChips": ["hint", "translate", "slow"],
            })
            return (
                f"Goal marked complete. Stay in character as {ai_role} in {self.target_lang_name} "
                f"(1–2 short sentences, include a realistic detail if natural) to prompt '{next_goal.get('description', next_goal.get('label', ''))}'."
            )

        # All roleplay goals completed -> broadcast final completed pill/goals and wrap up
        await self.broadcast({
            "type": "lesson_stage",
            "stage": "roleplay",
            "pill": {
                "icon": "users",
                "role": ai_role,
                "goal": f"Goal {total_goals} of {total_goals} · Complete",
            },
            "card": None,
            "recallPrompt": None,
            "roleplayGoals": self._roleplay_goals_payload(),
            "rescueChips": ["hint", "translate", "slow"],
        })

        # All roleplay goals completed -> schedule wrap-up after final reply finishes playing
        if not self.wrapping_up:
            self.wrapping_up = True
            asyncio.create_task(self._finish_lesson_after_turn())
        return (
            f"All roleplay goals are now complete! Give one warm, natural closing sentence in character in {self.target_lang_name}."
        )

    async def _finish_lesson_after_turn(self) -> None:
        # Let the final in-character turn finish speaking
        await asyncio.sleep(2.2)
        while self.session and "speaking" in str(getattr(self.session, "agent_state", "")).lower():
            await asyncio.sleep(0.35)

        if self.completed:
            return

        # Optional brief "One Last Fix" recap if a recast was captured
        if self.recast_notes and not self.teach_in_target:
            top_recast = self.recast_notes[0]
            clauses = [
                SpeechClause(text="Great job finishing the scene! Here is a natural way to phrase that:", language=self.native_lang, speed=1.0, pause_after_ms=200),
                SpeechClause(text=top_recast["natural_form"], language=self.target_lang, speed=0.95),
            ]
            await self._speak_clauses(clauses, display_text=f"Great job finishing the scene! One quick native tip: {top_recast['natural_form']}")
            await asyncio.sleep(0.4)

        await self.emit_lesson_complete()

    async def emit_lesson_complete(self) -> None:
        if self.completed:
            return
        self.completed = True

        duration_sec = max(30, int(round(time.time() - self.started_at)))
        avg_pronunciation = (
            int(round(sum(self.pronunciation_scores) / len(self.pronunciation_scores)))
            if self.pronunciation_scores
            else 90
        )

        phrase_goals = len(self.target_phrases)
        rp_goals = len(self.roleplay_goals)
        goals_total = max(1, phrase_goals + rp_goals)
        goals_completed = min(goals_total, len(self.target_phrases) + len(self.completed_goal_ids))
        if goals_completed == 0:
            goals_completed = goals_total

        if self.rescue_count == 0 and avg_pronunciation >= 90:
            fluency_label = "Natural"
        elif self.rescue_count <= 2 and avg_pronunciation >= 80:
            fluency_label = "Steady"
        else:
            fluency_label = "Developing"

        coach_notes: list[dict[str, str]] = []
        if self.recast_notes:
            rec = self.recast_notes[0]
            coach_notes.append({
                "category": "Roleplay recast · Natural phrasing",
                "correction": f"“{rec['user_said']}” → “{rec['natural_form']}”",
                "ttsSampleText": rec["natural_form"],
                "kind": "grammar",
            })

        focus_item = self.lowest_focus[1] if self.lowest_focus else (self.target_phrases[0] if self.target_phrases else None)
        if focus_item:
            syllables = focus_item.get("syllables", "")
            cue = focus_item.get("pronunciationCue", "")
            focus_word = focus_item.get("focusWord") or focus_item.get("phrase", "")
            cue_formatted = cue[0].lower() + cue[1:] if cue else "check stress"
            coach_notes.append({
                "category": "Pronunciation · Stress",
                "correction": f"{syllables} — {cue_formatted}" if syllables else cue,
                "ttsSampleText": focus_word,
                "kind": "pronunciation",
            })

        phrase_results_list: list[dict[str, str]] = []
        seen: set[str] = set()
        for tp in self.target_phrases:
            p = tp.get("phrase", "")
            if p and p not in seen:
                seen.add(p)
                phrase_results_list.append({
                    "phrase": p,
                    "status": self.phrase_results.get(p, "mastered"),
                })
        for phrase_key, status in self.phrase_results.items():
            if phrase_key and phrase_key not in seen and len(phrase_results_list) < 5:
                seen.add(phrase_key)
                phrase_results_list.append({
                    "phrase": phrase_key,
                    "status": status,
                })

        await self.broadcast({
            "type": "lesson_complete",
            "lessonId": self.lesson_id,
            "evaluation": {
                "durationSec": duration_sec,
                "pronunciationScore": avg_pronunciation,
                "goalsCompleted": goals_completed,
                "goalsTotal": goals_total,
                "fluencyLabel": fluency_label,
                "coachNotes": coach_notes[:2],
                "phraseResults": phrase_results_list,
            },
        })

    # ── Client Rescue & Card Actions ─────────────────────────────────────────

    async def handle_client_action(self, action: str, payload: str | None = None) -> None:
        if self.completed:
            return
        if self.session:
            try:
                self.session.interrupt()
            except Exception:
                pass

        if action == "listen_normal":
            phrase = payload or self._current_target_phrase()
            if phrase:
                await self._speak_mono(phrase, language=self.target_lang, speed=1.0)
            return

        if action in ("listen_slow", "slow"):
            if self.stage in ("teach_model", "shadow_drill"):
                phrase = payload or self._current_target_phrase()
                if phrase:
                    await self._speak_mono(phrase, language=self.target_lang, speed=0.75)
            elif self.stage == "roleplay" and self.last_tutor_line:
                await self._speak_mono(self.last_tutor_line, language=self.target_lang, speed=0.75)
            elif self.last_prompt_clauses:
                slow_clauses = [
                    SpeechClause(
                        text=c.text,
                        language=c.language,
                        speed=round(c.speed * 0.75, 2),
                        pause_after_ms=c.pause_after_ms,
                    )
                    for c in self.last_prompt_clauses
                ]
                await self._speak_clauses(slow_clauses, display_text=self.last_prompt_text)
            elif self.last_prompt_text:
                await self._speak_mono(self.last_prompt_text, language=self.last_prompt_lang, speed=0.78)
            return

        if action == "play_chunk" and payload:
            await self._speak_mono(payload, language=self.target_lang, speed=0.88)
            return

        self.rescue_count += 1

        if action == "first_word":
            if self.stage == "active_recall" and self.recall_idx < len(self.recall_prompts):
                item = self.recall_prompts[self.recall_idx]
                hint = item.get("firstWordHint") or ""
                await self.broadcast({
                    "type": "rescue_payload",
                    "action": "first_word",
                    "text": f"Start with: {hint}",
                })
                hint_clean = hint.rstrip("….").strip()
                if hint_clean:
                    await self._speak_mono(f"{hint_clean}.", language=self.target_lang, speed=0.88)
            return

        if action == "hint":
            if self.stage == "building_blocks" and self.block_idx < len(self.building_blocks):
                item = self.building_blocks[self.block_idx]
                expected = item.get("expectedTarget", "")
                words = expected.split()
                connector = " ".join(words[: min(4, len(words))]) + "…"
                await self.broadcast({
                    "type": "rescue_payload",
                    "action": "hint",
                    "text": f"Use: {connector}",
                })
                conn_clean = connector.rstrip("….").strip()
                if conn_clean:
                    await self._speak_mono(f"{conn_clean}.", language=self.target_lang, speed=0.88)
            elif self.stage == "roleplay":
                remaining = [g for g in self.roleplay_goals if g.get("id") not in self.completed_goal_ids]
                goal_desc = remaining[0].get("description", remaining[0].get("label", "")) if remaining else "Wrap up politely"
                res = await self._quick_llm_json(
                    f"Give a 1-sentence {self.target_lang_name} reply suggestion for a {self.level} learner "
                    f"whose goal is '{goal_desc}' in response to '{self.last_tutor_line}'. "
                    'Return JSON: {"suggestion": string}'
                )
                suggestion = str(res.get("suggestion") or (self.target_phrases[-1]["phrase"] if self.target_phrases else ""))
                if suggestion:
                    await self.broadcast({
                        "type": "rescue_payload",
                        "action": "hint",
                        "text": f"Try: {suggestion}",
                    })
            return

        if action == "reveal":
            target = self._current_target_phrase()
            if target:
                self.revealed_phrases.add(target)
                self.phrase_results[target] = "practice"
                await self.broadcast({
                    "type": "rescue_payload",
                    "action": "reveal",
                    "text": target,
                })
                await self._speak_mono(target, language=self.target_lang, speed=0.88)
            return

        if action == "translate":
            line_to_translate = strip_lang_tags(payload or self.last_tutor_line).strip()
            if not line_to_translate:
                return
            res = await self._quick_llm_json(
                f"Translate this {self.target_lang_name} line naturally into {self.native_lang_name}: "
                f"'{line_to_translate}'. "
                'Return JSON: {"translation": string}'
            )
            translation = str(res.get("translation") or "")
            if translation:
                await self.broadcast({
                    "type": "rescue_payload",
                    "action": "translate",
                    "text": translation,
                })
                if self.last_tutor_turn_id:
                    await self.broadcast({
                        "type": "lesson_turn_update",
                        "id": self.last_tutor_turn_id,
                        "translation": translation,
                    })

    def _current_target_phrase(self) -> str:
        if self.stage in ("teach_model", "shadow_drill") and self.phrase_idx < len(self.target_phrases):
            return self.target_phrases[self.phrase_idx].get("phrase", "")
        if self.stage == "building_blocks" and self.block_idx < len(self.building_blocks):
            return self.building_blocks[self.block_idx].get("expectedTarget", "")
        if self.stage == "active_recall" and self.recall_idx < len(self.recall_prompts):
            targets = self.recall_prompts[self.recall_idx].get("expectedTargets") or []
            return targets[0] if targets else ""
        return ""


class LessonAgent(Agent):
    """LiveKit Agent driving structured stages (03a–03d) and LLM tool-assisted Roleplay (03e–03f)."""

    def __init__(
        self,
        *,
        orchestrator: LessonOrchestrator,
        tts: cartesia.TTS,
        stt: Any,
    ) -> None:
        self.orchestrator = orchestrator
        orchestrator.agent = self
        super().__init__(
            instructions=self._build_roleplay_instructions(),
            tts=tts,
            stt=stt,
        )

    def _build_roleplay_instructions(self) -> str:
        orch = self.orchestrator
        ai_role = orch.roleplay_cfg.get("aiRole") or "Local speaker"
        setting = orch.roleplay_cfg.get("setting") or orch.lesson_title
        goals_desc = "\n".join(
            f"- {g.get('id')}: {g.get('label')} — {g.get('description', '')}"
            for g in orch.roleplay_goals
        )
        return (
            f"You are playing the role of '{ai_role}' in a live spoken {orch.target_lang_name} roleplay "
            f"(learner CEFR level: {orch.level}, native language: {orch.native_lang_name}).\n"
            f"Scene & setting: {setting}\n"
            f"Learner goals to complete:\n{goals_desc}\n\n"
            "STRICT RULES:\n"
            f'1. Speak 100% in {orch.target_lang_name} wrapped in <lang code="{orch.target_lang}">...</lang> in 1–2 short, natural conversational sentences (under 140 chars).\n'
            f"2. If the learner explicitly asks a question or asks for clarification in {orch.native_lang_name} (e.g. 'what does X mean?' or 'how do I say…?'), "
            f'briefly answer in <lang code="{orch.native_lang}">...</lang> and model the <lang code="{orch.target_lang}">...</lang> phrase, then stay in character.\n'
            "3. Otherwise stay 100% in character. NEVER break character to lecture about grammar.\n"
            "4. Implicit recasting: if the learner says something slightly unnatural or ungrammatical, naturally echo the idiomatic phrasing in your reply and record it via `mark_roleplay_goal_complete`.\n"
            "5. Include one realistic detail or mild curveball (e.g., bus number, platform, price, or stop count) so the learner listens actively.\n"
            "6. Whenever the learner satisfies a goal, IMMEDIATELY call `mark_roleplay_goal_complete` with that `goal_id`.\n"
            "7. NEVER output markdown, asterisks, bullets, emojis, or stage directions."
        )

    async def transcription_node(self, text, model_settings):
        async for chunk in strip_lang_tags_stream(text):
            yield chunk

    async def tts_node(self, text, model_settings):
        orch = self.orchestrator
        async for frame in stream_bilingual_tts(
            orch.synthesizer,
            text,
            native_lang=orch.native_lang,
            target_lang=orch.target_lang,
            default_lang=orch.target_lang,
            speed=orch.model_speed,
        ):
            yield frame

    async def activate_roleplay_instructions(self) -> None:
        await self.update_instructions(self._build_roleplay_instructions())

    async def on_enter(self) -> None:
        self.orchestrator.session = self.session
        asyncio.create_task(self.orchestrator.start())

    async def on_user_turn_completed(
        self, turn_ctx: llm.ChatContext, new_message: llm.ChatMessage
    ) -> None:
        orch = self.orchestrator
        orch.session = self.session
        text = (new_message.text_content or "").strip()
        confidence = getattr(new_message, "transcript_confidence", None)

        if orch.completed or orch.wrapping_up:
            raise StopResponse()

        if orch.stage != "roleplay":
            asyncio.create_task(orch.handle_structured_user_turn(text, confidence))
            raise StopResponse()

        if text:
            goal_num, total_goals = orch._current_goal_progress()
            asyncio.create_task(
                orch._emit_lesson_turn(
                    speaker="user",
                    sender_label=f"You · Speaking (Goal {goal_num} of {total_goals})",
                    text=f"“{text}”",
                )
            )

        # In roleplay (03e/03f): cap runaway roleplays after 6 learner turns so the 5-min lesson always concludes cleanly.
        orch.roleplay_turns += 1
        if orch.roleplay_turns >= 6 and not orch.wrapping_up:
            for g in orch.roleplay_goals:
                if g.get("id"):
                    orch.completed_goal_ids.add(g["id"])
            orch.wrapping_up = True
            asyncio.create_task(orch._finish_lesson_after_turn())

    @function_tool
    async def mark_roleplay_goal_complete(
        self,
        goal_id: str,
        user_said: str = "",
        natural_recast: str = "",
    ) -> str:
        """Call as soon as the learner achieves one of the roleplay goals.

        Args:
            goal_id: Exact ID of the completed goal (e.g. 'goal_1' or 'goal_2').
            user_said: Optional exact learner phrase if it could be phrased more naturally.
            natural_recast: Optional more idiomatic target-language phrasing of what the learner meant.
        """
        return await self.orchestrator.on_roleplay_goal_marked(
            goal_id=goal_id,
            user_said=user_said,
            natural_recast=natural_recast,
        )
