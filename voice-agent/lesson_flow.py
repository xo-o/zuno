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

from voices import update_cartesia_tts

logger = logging.getLogger("voice-agent.lesson")


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
        self.last_prompt_lang: str = self.target_lang
        self.last_prompt_text: str = ""

        self._speech_lock = asyncio.Lock()
        self._genai_client: genai.Client | None = None

    @property
    def cue_lang(self) -> str:
        return self.target_lang if self.teach_in_target else self.native_lang

    @property
    def model_speed(self) -> float:
        return 0.92 if self.level in ("A1", "A2") else 1.0

    def _genai(self) -> genai.Client:
        if self._genai_client is None:
            api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
            self._genai_client = genai.Client(api_key=api_key)
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

    async def _speak(
        self,
        text: str,
        *,
        language: str,
        speed: float = 1.0,
        allow_interruptions: bool = True,
        record_as_tutor_line: bool = False,
    ) -> None:
        if not self.session or not text.strip():
            return
        async with self._speech_lock:
            update_cartesia_tts(self.tts, language=language, speed=speed)
            if record_as_tutor_line:
                self.last_tutor_line = text
                await self.broadcast({
                    "type": "transcript",
                    "speaker": "agent",
                    "text": text,
                    "is_final": True,
                })
            self.last_prompt_lang = language
            self.last_prompt_text = text
            handle = self.session.say(
                text,
                allow_interruptions=allow_interruptions,
                add_to_chat_ctx=False,
            )
            try:
                await handle.wait_for_playout()
            except Exception as e:
                logger.debug(f"Speech interrupted or cancelled: {e}")
            finally:
                # Always keep TTS pointed at targetLanguage & 1.0x speed after auxiliary speech
                update_cartesia_tts(self.tts, language=self.target_lang, speed=1.0)

    def _set_endpointing_delay(self, delay_sec: float) -> None:
        if not self.session:
            return
        try:
            if hasattr(self.session, "_opts") and hasattr(self.session._opts, "min_endpointing_delay"):
                self.session._opts.min_endpointing_delay = delay_sec
        except Exception:
            pass

    def _teach_card_payload(self, item: dict[str, Any], include_setup: bool = True) -> dict[str, Any]:
        return {
            "kind": "teach_card",
            "setupPrompt": item.get("setupNative", "") if include_setup else "",
            "phrase": item.get("phrase", ""),
            "translation": item.get("translation", ""),
            "focusWord": item.get("focusWord", ""),
            "syllables": item.get("syllables", ""),
            "cue": item.get("pronunciationCue", ""),
            "chunks": item.get("chunks") or [],
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

        await self.broadcast({
            "type": "lesson_stage",
            "stage": "teach_model",
            "pill": {
                "icon": "book-open",
                "role": "Learn",
                "goal": f"Phrase {idx + 1} of {total}",
            },
            "card": self._teach_card_payload(item, include_setup=True),
            "recallPrompt": None,
            "rescueChips": [],
        })

        if not self.teach_in_target and setup:
            await self._speak(setup, language=self.native_lang, speed=1.0)
            await asyncio.sleep(0.3)

        await self._speak(phrase, language=self.target_lang, speed=self.model_speed)

        # Transition immediately into 03b Shadow & Pronunciation if still in teach_model for this phrase
        if self.stage == "teach_model" and self.phrase_idx == idx and not self.completed:
            await self._enter_shadow_drill(idx)

    async def _enter_shadow_drill(self, idx: int) -> None:
        self.stage = "shadow_drill"
        self.phrase_idx = idx
        self._set_endpointing_delay(0.8)
        item = self.target_phrases[idx]

        await self.broadcast({
            "type": "lesson_stage",
            "stage": "shadow_drill",
            "pill": {
                "icon": "waveform",
                "role": "Shadow",
                "goal": "Pronunciation drill",
            },
            "card": self._teach_card_payload(item, include_setup=False),
            "recallPrompt": None,
            "rescueChips": [],
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
        stem_hint = item.get("stemHint", "")

        # Never include expectedTarget in Scaffold Card so learner assembles it actively
        await self.broadcast({
            "type": "lesson_stage",
            "stage": "building_blocks",
            "pill": {
                "icon": "sparkles",
                "role": "Build",
                "goal": "Combine pattern",
            },
            "card": {
                "kind": "scaffold_card",
                "prompt": prompt_native,
                "newWord": new_word,
                "meaning": meaning,
                "stemHint": stem_hint,
            },
            "recallPrompt": None,
            "rescueChips": ["hint", "reveal", "slow"],
        })

        if not self.teach_in_target and meaning and new_word:
            await self._speak(f"The word for {meaning} is", language=self.native_lang, speed=1.0)
            await self._speak(new_word, language=self.target_lang, speed=self.model_speed)
            await asyncio.sleep(0.25)
            await self._speak(prompt_native, language=self.native_lang, speed=1.0)
        else:
            await self._speak(prompt_native, language=self.cue_lang, speed=1.0)

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
        cue = item.get("situationNative", "")

        await self.broadcast({
            "type": "lesson_stage",
            "stage": "active_recall",
            "pill": {
                "icon": "rotate-ccw",
                "role": "Recall",
                "goal": "Speak from memory",
            },
            "card": None,
            "recallPrompt": {
                "eyebrow": f"Say in {self.target_lang_name}",
                "cue": cue,
            },
            "rescueChips": ["first_word", "reveal", "slow"],
        })

        await self._speak(cue, language=self.cue_lang, speed=1.0)

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

    async def handle_structured_user_turn(self, transcript: str, confidence: float | None) -> None:
        if self.completed or self.wrapping_up:
            return
        if self.stage == "teach_model":
            # Learner jumped in early and shadowed right away — score it as shadow_drill!
            self.stage = "shadow_drill"

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

        self.pronunciation_scores.append(score)
        if self.lowest_focus is None or score < self.lowest_focus[0]:
            self.lowest_focus = (score, item)

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
            await self._speak(praise, language=self.target_lang, speed=1.0)
            await asyncio.sleep(0.35)
            await self._advance_after_phrase()
            return

        self.phrase_retries += 1
        if self.phrase_retries <= 2:
            await self.broadcast({
                "type": "pronunciation_eval",
                "score": score,
                "passed": False,
                "focusWord": focus_word,
                "feedbackText": f'{score}% match · Clear rhythm, {cue[0].lower() + cue[1:] if cue else "try once more"}',
                "showChunks": True,
            })
            if not self.teach_in_target:
                await self._speak(f"Almost! {cue}. Listen:", language=self.native_lang, speed=1.0)
            await self._speak(focus_word or phrase, language=self.target_lang, speed=0.88)
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
            await self._speak("Good effort, let's keep moving.", language=self.native_lang, speed=1.0)
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
            praise = PRAISE_BY_LANG.get(self.target_lang, PRAISE_BY_LANG["en"])
            await self._speak(f"{praise} {expected}", language=self.target_lang, speed=1.0)
            await asyncio.sleep(0.3)
            await self._advance_after_block()
            return

        self.block_retries += 1
        if self.block_retries <= 1:
            tip = hint or f"Try combining {item.get('stemHint', '')} with {new_word}."
            await self._speak(tip, language=self.cue_lang, speed=1.0)
            return

        self.phrase_results[expected] = "practice"
        await self._speak(expected, language=self.target_lang, speed=0.92)
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
            if primary_target:
                status = "practice" if primary_target in self.revealed_phrases else "mastered"
                self.phrase_results.setdefault(primary_target, status)
            praise = PRAISE_BY_LANG.get(self.target_lang, PRAISE_BY_LANG["en"])
            confirm = f"{praise} {primary_target}".strip()
            await self._speak(confirm, language=self.target_lang, speed=1.0)
            await asyncio.sleep(0.35)
            await self._advance_after_recall()
            return

        self.recall_retries += 1
        if self.recall_retries <= 1:
            first_word = item.get("firstWordHint") or (primary_target.split(" ")[0] + "…" if primary_target else "")
            await self.broadcast({
                "type": "rescue_payload",
                "action": "first_word",
                "text": f"Start with: {first_word}",
            })
            if not self.teach_in_target:
                await self._speak("Start with:", language=self.native_lang, speed=1.0)
            await self._speak(first_word.rstrip("…."), language=self.target_lang, speed=0.92)
            return

        if primary_target:
            self.phrase_results[primary_target] = "practice"
            await self._speak(primary_target, language=self.target_lang, speed=0.92)
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
                "rescueChips": ["hint", "translate", "slow"],
            })
            return (
                f"Goal marked complete. Stay in character as {ai_role} in {self.target_lang_name} "
                f"(1–2 short sentences, include a realistic detail if natural) to prompt '{next_goal.get('description', next_goal.get('label', ''))}'."
            )

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
            await self._speak(
                "Great job finishing the scene! One quick native tip:",
                language=self.native_lang,
                speed=1.0,
            )
            await self._speak(
                top_recast["natural_form"],
                language=self.target_lang,
                speed=0.95,
            )
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
                await self._speak(phrase, language=self.target_lang, speed=1.0)
            return

        if action in ("listen_slow", "slow"):
            if self.stage in ("teach_model", "shadow_drill"):
                phrase = payload or self._current_target_phrase()
                if phrase:
                    await self._speak(phrase, language=self.target_lang, speed=0.75)
            elif self.stage == "roleplay" and self.last_tutor_line:
                await self._speak(self.last_tutor_line, language=self.target_lang, speed=0.75)
            elif self.last_prompt_text:
                await self._speak(self.last_prompt_text, language=self.last_prompt_lang, speed=0.78)
            return

        if action == "play_chunk" and payload:
            await self._speak(payload, language=self.target_lang, speed=0.90)
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
                await self._speak(hint.rstrip("…."), language=self.target_lang, speed=0.90)
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
                await self._speak(connector.rstrip("…"), language=self.target_lang, speed=0.90)
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
                await self._speak(target, language=self.target_lang, speed=0.90)
            return

        if action == "translate" and self.last_tutor_line:
            res = await self._quick_llm_json(
                f"Translate this {self.target_lang_name} line naturally into {self.native_lang_name}: "
                f"'{self.last_tutor_line}'. "
                'Return JSON: {"translation": string}'
            )
            translation = str(res.get("translation") or "")
            if translation:
                await self.broadcast({
                    "type": "rescue_payload",
                    "action": "translate",
                    "text": translation,
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
            f"(learner CEFR level: {orch.level}).\n"
            f"Scene & setting: {setting}\n"
            f"Learner goals to complete:\n{goals_desc}\n\n"
            "STRICT RULES:\n"
            f"1. Speak 100% in {orch.target_lang_name} in 1–2 short, natural conversational sentences (under 140 chars).\n"
            "2. Stay 100% in character. NEVER break character to explain grammar rules if the learner's meaning is understandable.\n"
            "3. Implicit recasting: if the learner says something slightly unnatural or ungrammatical, naturally echo the idiomatic phrasing in your reply and record it via `mark_roleplay_goal_complete`.\n"
            "4. Include one realistic detail or mild curveball (e.g., bus number, platform, price, or stop count) so the learner listens actively.\n"
            "5. Whenever the learner satisfies a goal, IMMEDIATELY call `mark_roleplay_goal_complete` with that `goal_id`.\n"
            "6. NEVER output markdown, asterisks, bullets, emojis, or stage directions."
        )

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
