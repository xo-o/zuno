import asyncio
import json
import logging
import os
import re
from typing import Any, Callable, Coroutine

from google import genai
from google.genai import types
from livekit.agents import Agent, AgentSession, StopResponse, llm
import livekit.plugins.cartesia as cartesia

from bilingual import SpeechClause, ensure_terminal_punct
from voices import BilingualCartesiaSynthesizer, synthesize_bilingual_segments, update_cartesia_tts

logger = logging.getLogger("voice-agent.onboarding")

LANG_NAMES_EN: dict[str, str] = {
    "ar": "Arabic",
    "bn": "Bengali",
    "zh": "Mandarin",
    "cs": "Czech",
    "da": "Danish",
    "nl": "Dutch",
    "en": "English",
    "fi": "Finnish",
    "fr": "French",
    "de": "German",
    "el": "Greek",
    "he": "Hebrew",
    "hi": "Hindi",
    "hu": "Hungarian",
    "id": "Indonesian",
    "it": "Italian",
    "ja": "Japanese",
    "ko": "Korean",
    "ms": "Malay",
    "no": "Norwegian",
    "fa": "Persian",
    "pl": "Polish",
    "pt": "Portuguese",
    "ro": "Romanian",
    "ru": "Russian",
    "es": "Spanish",
    "sv": "Swedish",
    "tl": "Tagalog",
    "th": "Thai",
    "tr": "Turkish",
    "uk": "Ukrainian",
    "ur": "Urdu",
    "vi": "Vietnamese",
}

PROMPTS_BY_APP_LANG: dict[str, dict[str, str]] = {
    "en": {
        "name": "Hi, I’m Zuno. What should I call you?",
        "level": "Nice to meet you, {name}. How much {target} do you know?",
        "goals": "Why are you learning {target}, {name}?",
    },
    "es": {
        "name": "Hola, soy Zuno. ¿Cómo te gustaría que te llame?",
        "level": "Mucho gusto, {name}. ¿Cuánto {target} sabes?",
        "goals": "¿Por qué estás aprendiendo {target}, {name}?",
    },
    "fr": {
        "name": "Salut, je suis Zuno. Comment dois-je t’appeler ?",
        "level": "Enchanté, {name}. Quel est ton niveau en {target} ?",
        "goals": "Pourquoi apprends-tu le {target}, {name} ?",
    },
    "de": {
        "name": "Hallo, ich bin Zuno. Wie soll ich dich nennen?",
        "level": "Freut mich, {name}. Wie viel {target} kannst du schon?",
        "goals": "Warum lernst du {target}, {name}?",
    },
    "it": {
        "name": "Ciao, sono Zuno. Come posso chiamarti?",
        "level": "Piacere, {name}. Quanto {target} conosci?",
        "goals": "Perché stai imparando {target}, {name}?",
    },
    "pt": {
        "name": "Olá, sou o Zuno. Como posso chamar você?",
        "level": "Prazer, {name}. Quanto de {target} você já sabe?",
        "goals": "Por que você está aprendendo {target}, {name}?",
    },
    "nl": {
        "name": "Hoi, ik ben Zuno. Hoe zal ik je noemen?",
        "level": "Leuk je te ontmoeten, {name}. Hoeveel {target} spreek je al?",
        "goals": "Waarom leer je {target}, {name}?",
    },
    "pl": {
        "name": "Cześć, jestem Zuno. Jak mam się do ciebie zwracać?",
        "level": "Miło cię poznać, {name}. Ile {target} już znasz?",
        "goals": "Dlaczego uczysz się języka {target}, {name}?",
    },
}


class OnboardingOrchestrator:
    """Drives the 3-step spoken onboarding conversation (`5b` Name -> `5c` Level -> `5d` Goals)."""

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
        self.agent: "OnboardingAgent | None" = None

        self.native_lang: str = (meta.get("nativeLanguage") or "en").split("-")[0].lower()
        self.target_lang: str = meta.get("targetLanguage") or "es"
        self.target_lang_name: str = LANG_NAMES_EN.get(self.target_lang, self.target_lang.upper())

        ob = meta.get("onboarding") or {}
        self.step: str = str(ob.get("step") or "name")
        self.learner_name: str = str(ob.get("name") or "")
        self.level: str = str(ob.get("level") or "")
        self.paused: bool = False
        self.completed: bool = False
        self._speech_lock = asyncio.Lock()
        self._processing = False

        voice_id = getattr(tts, "_opts", None) and tts._opts.voice
        self.synthesizer = BilingualCartesiaSynthesizer(voice_id=voice_id)
        self._genai = genai.Client(api_key=os.environ.get("GOOGLE_API_KEY"))

    def _prompt_for_step(self, step: str) -> str:
        table = PROMPTS_BY_APP_LANG.get(self.native_lang, PROMPTS_BY_APP_LANG["en"])
        template = table.get(step, PROMPTS_BY_APP_LANG["en"].get(step, ""))
        first_name = (self.learner_name.strip().split(" ")[0] if self.learner_name.strip() else "friend")
        return template.format(name=first_name, target=self.target_lang_name)

    async def _quick_llm_json(self, prompt: str) -> dict[str, Any]:
        def _call() -> dict[str, Any]:
            resp = self._genai.models.generate_content(
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
            logger.warning(f"Onboarding LLM parse fallback: {e}")
            return {}

    async def _speak(self, text: str) -> None:
        if not self.session or self.paused or not text.strip():
            return
        async with self._speech_lock:
            clean = ensure_terminal_punct(text.strip())
            update_cartesia_tts(self.tts, language=self.native_lang, speed=1.0)
            audio_stream = synthesize_bilingual_segments(
                self.synthesizer,
                [SpeechClause(text=clean, language=self.native_lang, speed=1.0)],
            )
            handle = self.session.say(
                clean,
                audio=audio_stream,
                allow_interruptions=True,
                add_to_chat_ctx=False,
            )
            try:
                await handle.wait_for_playout()
            except Exception as e:
                logger.debug(f"Onboarding speech interrupted: {e}")

    async def start(self) -> None:
        await asyncio.sleep(0.25)
        if not self.paused and not self.completed:
            await self._speak(self._prompt_for_step(self.step))

    async def handle_user_turn(self, transcript: str, confidence: float | None) -> None:
        if self.paused or self.completed or self._processing:
            return
        raw = transcript.strip()
        if not raw or (confidence is not None and confidence < 0.30 and len(raw.split()) <= 1):
            return

        self._processing = True
        try:
            await self.broadcast({"type": "agent_state", "state": "thinking"})
            current_step = self.step

            if current_step == "name":
                stripped = re.sub(
                    r"^(?:hi|hey|hello|call me|my name is|i am|i'm|im|it's|its)[,!\s]+",
                    "",
                    raw,
                    flags=re.IGNORECASE,
                ).strip(" .!?")
                if re.match(r"^[\w'\- ]{1,30}$", stripped) and 1 <= len(stripped.split()) <= 3:
                    name = " ".join(w.capitalize() for w in stripped.split())
                    valid = True
                    retry = ""
                else:
                    res = await self._quick_llm_json(
                        f"Extract the user's preferred name from '{raw}' in response to 'What should I call you?'. "
                        f"Return JSON: {{\"valid\": boolean, \"name\": string, \"retryPrompt\": string}} (retryPrompt in '{self.native_lang}' if invalid)."
                    )
                    valid = bool(res.get("valid")) and bool(str(res.get("name") or "").strip())
                    name = str(res.get("name") or "").strip()
                    retry = str(res.get("retryPrompt") or "Sorry, what should I call you?")

                if not valid:
                    await self._speak(retry)
                    return

                self.learner_name = name
                self.step = "level"
                next_prompt = self._prompt_for_step("level")
                await self.broadcast({
                    "type": "onboarding_turn",
                    "step": "name",
                    "transcript": raw,
                    "name": name,
                    "nextStep": "level",
                    "nextPrompt": next_prompt,
                })
                await self._speak(next_prompt)
                return

            if current_step == "level":
                res = await self._quick_llm_json(
                    f"Map the learner's self-reported {self.target_lang_name} level '{raw}' to CEFR: "
                    "A1 (complete beginner / few words), A2 (tourist basics), B1 (intermediate / everyday basics), "
                    "B2 (upper intermediate), C1/C2 (advanced / fluent). "
                    f"Return JSON: {{\"valid\": boolean, \"level\": \"A1\"|\"A2\"|\"B1\"|\"B2\"|\"C1\"|\"C2\", \"retryPrompt\": string}} "
                    f"(valid=false only if pure noise, retryPrompt in '{self.native_lang}')."
                )
                level = str(res.get("level") or "B1")
                if level not in ("A1", "A2", "B1", "B2", "C1", "C2"):
                    level = "B1"
                if res and res.get("valid") is False:
                    retry = str(res.get("retryPrompt") or self._prompt_for_step("level"))
                    await self._speak(retry)
                    return

                self.level = level
                self.step = "goals"
                next_prompt = self._prompt_for_step("goals")
                await self.broadcast({
                    "type": "onboarding_turn",
                    "step": "level",
                    "transcript": raw,
                    "level": level,
                    "nextStep": "goals",
                    "nextPrompt": next_prompt,
                })
                await self._speak(next_prompt)
                return

            if current_step == "goals":
                res = await self._quick_llm_json(
                    f"Extract motivation and interests for learning {self.target_lang_name} from: '{raw}'. "
                    "Return JSON: {"
                    "\"valid\": boolean, "
                    "\"focus\": \"conversation\"|\"travel\"|\"work\"|\"exams\"|\"culture\", "
                    "\"interests\": string[], "
                    "\"goalNote\": string, "
                    "\"retryPrompt\": string"
                    "} "
                    "(valid=true for any genuine reason even 'for fun'; valid=false only if gibberish noise)."
                )
                if res and res.get("valid") is False:
                    retry = str(res.get("retryPrompt") or self._prompt_for_step("goals"))
                    await self._speak(retry)
                    return

                focus = str(res.get("focus") or "conversation")
                if focus not in ("conversation", "travel", "work", "exams", "culture"):
                    focus = "conversation"
                interests = [str(x).strip()[:60] for x in (res.get("interests") or []) if str(x).strip()][:5]
                goal_note = str(res.get("goalNote") or raw).strip()[:160]

                self.completed = True
                await self.broadcast({
                    "type": "onboarding_turn",
                    "step": "goals",
                    "transcript": raw,
                    "focus": focus,
                    "interests": interests,
                    "goalNote": goal_note,
                    "nextStep": "preparing",
                })
        finally:
            self._processing = False

    async def handle_client_action(self, action: str, data: dict[str, Any]) -> None:
        if self.session:
            try:
                self.session.interrupt()
            except Exception:
                pass

        if action == "pause":
            self.paused = True
            return

        if action == "sync_step":
            self.paused = bool(data.get("paused", False))
            new_step = str(data.get("step") or self.step)
            if "name" in data and data["name"] is not None:
                self.learner_name = str(data["name"])
            if "level" in data and data["level"] is not None:
                self.level = str(data["level"])
            if "targetLanguage" in data and data["targetLanguage"]:
                self.target_lang = str(data["targetLanguage"])
                self.target_lang_name = LANG_NAMES_EN.get(self.target_lang, self.target_lang.upper())
            self.step = new_step
            self.completed = False
            if not self.paused and bool(data.get("speakPrompt", True)):
                await self._speak(self._prompt_for_step(self.step))


class OnboardingAgent(Agent):
    """LiveKit Agent wrapper for structured voice onboarding (`5b`–`5d`)."""

    def __init__(
        self,
        *,
        orchestrator: OnboardingOrchestrator,
        tts: cartesia.TTS,
        stt: Any,
    ) -> None:
        self.orchestrator = orchestrator
        orchestrator.agent = self
        super().__init__(
            instructions="You are Zuno conducting a warm onboarding check-in.",
            tts=tts,
            stt=stt,
        )

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
        if not orch.paused and not orch.completed:
            asyncio.create_task(orch.handle_user_turn(text, confidence))
        raise StopResponse()
