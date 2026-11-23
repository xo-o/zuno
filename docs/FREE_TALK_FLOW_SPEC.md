# ZunoAI — Free Talk Bilingual Coach & Pronunciation Flow Specification

**Related Specs:**
- [`docs/ROLEPLAY_VOICE_FLOW_SPEC.md`](./ROLEPLAY_VOICE_FLOW_SPEC.md) — 90/10 target-first Roleplay voice practice
- [`docs/VOICE_LESSON_FLOW_SPEC.md`](./VOICE_LESSON_FLOW_SPEC.md) — 5-stage Topic Lesson voice curriculum (`03a`–`03f`)
- [`docs/TOPICS_AND_ROLEPLAYS_FLOW_SPEC.md`](./TOPICS_AND_ROLEPLAYS_FLOW_SPEC.md) — Topics & Roleplays library and community sharing

**Target Stack:** Android (`Kotlin / Jetpack Compose` + `VoiceSessionViewModel` + `ZunoViewModel`), Backend (`Node.js / Hono / Drizzle / Gemini`), Voice Worker (`Python LiveKit Agents` in `voice-agent/agent.py`, `bilingual.py`, `voices.py`)

---

## 1. Product Positioning Across Zuno Learning Modes

| Mode | Primary Language | Secondary Language | Pedagogical Contract |
| :--- | :--- | :--- | :--- |
| **Free Talk** (`Home Orb` / `Zuno` Nav Tab / `Free Talk` Chat) | **User language (`nativeLanguage`)** *(unless `"Teach in target"` is enabled)* | **Target language (`targetLanguage`)** dynamically on demand or when spoken by the learner | Relaxed, open-topic personal AI language coach: discuss any topic, ask *"how do I say / pronounce X?"*, explore grammar/culture or uploaded photos/PDFs, and switch fluidly into target-language practice whenever desired. |
| **Topics** (`03 · Topics`) | Bilingual framing → Target phrase drills | User language prompts | Structured 5-stage 5-minute curriculum (`Teach → Shadow → Build → Recall → Mini-Roleplay`). |
| **Roleplays** (`04 · Roleplays`) | **90%+ Target language** in character | User language coach aside only when requested | Open-ended in-character scene simulation (`aiRole` ↔ `userRole`) with 3 mission goals. |

---

## 2. Live Voice Free Talk (`voice-agent/agent.py` & `POST /api/livekit/token`)

### 2.1 Mode & `conversationKind` Routing
- `POST /api/livekit/token` (`backend/src/index.ts`) emits `mode: "conversation"` together with `conversationKind: context.kind` (`"freetalk"` | `"levelcheck"` | `"lesson"`).
- `ConversationalAgent` activates **bilingual user-language-first Free Talk** (`is_free_talk = True`) whenever:
  - `conversationKind == "freetalk"`,
  - `teachInTarget == False`, and
  - `nativeLanguage != targetLanguage`.
- `levelcheck` and `peer_partner` remain target-language-first (`is_free_talk = False`).

### 2.2 Spoken Behavior & Pronunciation Coaching
1. **Warm User-Language Greeting (`on_enter`):**
   - Fresh Home / Free Talk calls greet the learner in **1 concise sentence in `nativeLanguage`** (`<lang code="{native}">...</lang>`), inviting them to talk about any topic, practice `targetLanguageName`, or ask how to say or pronounce anything.
   - Calls resumed from an existing Free Talk chat continue seamlessly from the recent history in whichever language the learner was using.
2. **Open Topics & Fluid Code-Switching:**
   - Default speech uses `<lang code="{native}">...</lang>` (`sonic-3.6` user locale, e.g. `en-US`, `en-GB`).
   - Every `targetLanguage` word, phrase, or example is wrapped in `<lang code="{target}">...</lang>` (`sonic-3.6` regional target locale, e.g. `es-MX`, `es-ES`, `fr-FR`, `ja-JP`, `ko-KR`) using the **exact same Cartesia voice identity** (`Ariana` / `Mariana` / `Carson`).
   - Whenever the learner speaks in `targetLanguage` or asks to practice in `targetLanguage`, Sofía mirrors and converses in `<lang code="{target}">...</lang>`, switching back smoothly to `<lang code="{native}">...</lang>` whenever the learner switches or asks a question.
3. **Pronunciation & *"How Do I Pronounce…"* Flow:**
   - When the learner asks how to pronounce a `targetLanguage` word or phrase:
     1. **Sound/Stress Tip (`nativeLanguage`):** 1 concise sentence explaining key stress or mouth placement in `<lang code="{native}">...</lang>`.
     2. **Natural Speed Model (`targetLanguage`):** `<lang code="{target}">...</lang>` at normal `1.0×` speed.
     3. **Slow Clarity Model (`targetLanguage`, `0.76×`):** `<lang code="{target}" speed="slow">...</lang>` separated cleanly by a `200ms` prosodic breath, followed by an invitation to try saying it.
     4. **Zero Faux-Phonetic Spellings:** Prompts explicitly forbid English phonetic respellings (such as *"reh-free-heh-rah-dor"*), ensuring Cartesia `sonic-3.6` synthesizes authentic target-language phonemes.
4. **Bidirectional In-Call `Translate` & `listen_slow`:**
   - Tapping **`Translate`** on a Free Talk voice bubble detects the clause's language and translates `targetLanguage → nativeLanguage` or `nativeLanguage → targetLanguage` automatically.
   - Tapping **`0.75×` (`listen_slow`)** replays the turn through `BilingualCartesiaSynthesizer` at `0.76×` speed with full avatar lip-sync.

### 2.3 Multilingual STT & Non-`multi` Language Pair Fallback
- **Nova-3 Multilingual (`language="multi"`):** Used automatically whenever both `nativeLanguage` and `targetLanguage` belong to `NOVA3_MULTI_LANGS` (`en, es, fr, de, hi, ru, pt, ja, it, nl`), recognizing both languages and mid-sentence code-switching simultaneously.
- **Non-`multi` Language Pairs (e.g., `en` + `ko`, `zh`, `ar`, `tr`, `pl`, `sv`, `id`, `vi`):**
  - Initializes Deepgram `nova-3` in **`nativeLanguage`** (instead of `targetLanguage`) so user-language Free Talk works immediately without transcription garbling.
  - Exposes `@function_tool set_listening_language(language_code)` on `ConversationalAgent` so when the learner switches to practicing or pronouncing `targetLanguage`, Sofía updates `stt.update_options(language=...)` dynamically in-place.

---

## 3. Bilingual Tag & Clause Segmentation (`voice-agent/bilingual.py` & `backend/src/lib/bilingual-tts.ts`)

1. **`<lang>` Speed Attribute Support:**
   - Supports `<lang code="es">...</lang>`, `<lang code="es" speed="slow">...</lang>` (`0.76×`), `<lang code="es" speed="0.8">...</lang>`, `<lang="es">...</lang>`, and `[es]...[/es]`.
   - `strip_lang_tags()`, `strip_lang_tags_stream()`, and Android `VoiceSessionViewModel.stripVoiceTags()` strip all `<lang ...>` attributes across arbitrary streaming token splits so UI captions and stored transcripts contain clean plain text.
2. **Symmetric `detect_clause_language()`:**
   - When `default_lang == native_lang` (Free Talk), untagged sentences stay in `native_lang` unless unmistakable `target_lang` script, diacritics (`¿¡ñßç…`), or function-word dominance (`target_score >= 2 && native_score == 0`) is present — preventing shared loanwords/articles (`"a"`, `"no"`, `"me"`, `"de"`) from flipping user-language sentences to `target_lang`.
   - Double-quoted target words/phrases inside untagged user-language sentences (e.g., `In Spain people say "vale" for okay.`) automatically split and voice `"vale"` in `target_lang` while keeping surrounding English glosses (`literally means "little coffee"`) in `native_lang`.

---

## 4. Text Free Talk & TTS Preview (`backend/src/lib/tutor.ts`, `conversations.ts`, `routes/tts.ts`)

1. **User-Language-First Text Replies (`tutorSystem` & `generateReply`):**
   - Replies in `nativeLanguage` by default (putting `targetLanguage` words/examples in double quotes), and switches to `targetLanguage` whenever the learner writes in `targetLanguage` or asks to practice.
   - For pronunciation questions in text chat, formats stressed syllables in `ALL CAPS` separated by ` · ` (e.g. `fe · rro · ca · RRIL`), adds a short mouth/sound cue in `nativeLanguage`, and invites tapping **Listen**.
2. **Conditional `feedback`, `translation`, and `title`:**
   - `feedback`: Populated **only** when the learner wrote or attempted `targetLanguage` in their message; returns `""` (`null` in `message.feedback`) when the learner wrote purely in `nativeLanguage`, avoiding artificial corrections on user-language questions.
   - `translation`: Returns `""` (`null` in DB) when the assistant reply is already primarily in `nativeLanguage`, and provides a natural `nativeLanguage` translation when the assistant replies in `targetLanguage`.
   - `title`: Auto-titles new Free Talk threads in `nativeLanguage` (2–4 words, e.g., `"Pronouncing rolling R"`, `"Weekend trip to Madrid"`).
3. **`POST /api/tts/preview` (`defaultLanguage`):**
   - Accepts optional `defaultLanguage` (set to `appLanguage.code` by Android `ChatScreen` when listening to Free Talk messages) so mixed user-language + quoted target-language bubbles voice each span in its exact regional `sonic-3.6` locale.

---

## 5. Android UX Details (`ZunoApp.kt`, `ChatScreen.kt`, `VoiceModeScreen.kt`)

- **Bilingual Composer Invitation:** `ChatScreen` displays localized `free_talk_composer_placeholder` (*"Ask anything, or practice in {Language}…"*) for Free Talk threads (while keeping target-language placeholders on Roleplays, Lessons, and Level Checks).
- **Free Talk Live Coach Prompts (`POST /api/chats/coach-prompts` with `mode = "freetalk"`):** Provides tappable pronunciation/practice cards (`Answers`, `Follow-ups`, `Questions`) tailored to open Free Talk sessions, while `mode = "peer"` preserves 100% target-language partner prompts for 1:1 Peer Calls.
