# ZunoAI — Live Roleplay Voice Practice Specification (`04 · Roleplays` Live Session)

**Related Specs:**
- [`docs/TOPICS_AND_ROLEPLAYS_FLOW_SPEC.md`](./TOPICS_AND_ROLEPLAYS_FLOW_SPEC.md) — Roleplay library, creation builder, community explore & sharing
- [`docs/VOICE_LESSON_FLOW_SPEC.md`](./VOICE_LESSON_FLOW_SPEC.md) — 5-stage structured Topic Lesson (`03a`–`03f`) & shared Data Channel protocol

**Target Stack:** Android (`Kotlin / Jetpack Compose` + `VoiceSessionViewModel`), Backend (`Node.js / Hono / Drizzle / Gemini`), Voice Worker (`Python LiveKit Agents` in `voice-agent/roleplay_flow.py`, `bilingual.py`, `voices.py`)

---

## 1. Pedagogical Philosophy & System Overview

Unlike **Topics** (`03a`–`03f`), which guide learners through deterministic 5-minute drill stages (`Teach & Model` → `Shadow` → `Building Blocks` → `Active Recall` → `Mini-Roleplay`), **Standalone Roleplays** are **dynamic, open-ended real-world speaking simulations** designed for fluency and spontaneous practice.

### Core Language & Voice Contract ("90/10 Immersive Practice + Dynamic Coach Aside")
1. **Target-Language Default (90%+ of Conversation):**
   - Sofía embodies `aiRole` (e.g., *Barista*, *Hiring Manager*, *Landlord*) while the learner plays `userRole`.
   - All in-character turns are spoken **100% in `targetLanguage`** using the learner's selected regional `accent` (`es-MX`, `es-ES`, `es-CO`, `fr-FR`, `fr-CA`, `pt-BR`, `pt-PT`, etc.) and CEFR `level` (`A1`–`C2`).
2. **Dynamic Clarification Aside (User Language When Needed):**
   - Learners in live practice can naturally step out and ask for help in either their **user/native language** or **target language** (e.g., *"Wait, what does 'propina' mean?"*, *"How do I ask if you have oat milk?"*, *"Sorry, I didn't catch that"*).
   - When clarification is requested (and `profile.teachInTarget` is `false`):
     1. **Coach Aside (`nativeLanguage` locale):** 1 concise sentence explaining the meaning or answering the question.
     2. **Target Model (`targetLanguage` locale):** Models the exact natural phrase in `targetLanguage`.
     3. **Return to Scene (`targetLanguage` locale):** Prompts the learner back in character so immersion resumes immediately.
   - When `profile.teachInTarget` is `true` (or `level` is `C1`/`C2`), clarifications use simple `targetLanguage` paraphrases instead of switching languages.
3. **Implicit Recasting (Zero Grammar Lectures):**
   - Minor grammar or phrasing slips never interrupt the scene. The avatar echoes the idiomatic phrasing naturally inside its in-character reply and logs `{ user_said, natural_form }` via `log_roleplay_recast` / `mark_roleplay_goal_complete`, surfacing a subtle `Native tip` pill in the UI.
4. **3-Goal Mission + Unlimited Open Practice:**
   - Every Roleplay includes **3 progressive practice goals** (`goal_1`, `goal_2`, `goal_3`) with a playable `samplePhrase` in `targetLanguage`.
   - Completing all 3 goals transitions the status pill to **`All goals complete · Open practice`** without terminating the call, allowing the learner to continue practicing dynamically as long as they want.

```text
[Roleplays List / Preview / Settings / Scheduled / Recent]
        │  (Tap Roleplay or "Start live roleplay" → POST /api/roleplays/:id/session)
        ▼
┌───────────────────────────────────────────────────────────────────┐
│ Stage 1 · Scene Opening (100% Target Language · `sonic-3.6`)      │
│ • Avatar enters as `aiRole · Sofía` and opens the scene naturally │
│ • Bottom Coach Card displays Goal 1, Goal 2, Goal 3 & Rescue tab  │
└───────────────────────────────┬───────────────────────────────────┘
                                ▼
┌───────────────────────────────────────────────────────────────────┐
│ Stage 2 · Dynamic Practice Loop (`nova-3` Multilingual STT)       │
│  ├─► Target-Language Turn ──► In-character reply (`target` locale)│
│  │                            + implicit recast + goal checkoff   │
│  ├─► Clarification Question ► 1-sentence aside (`native` locale)  │
│  │                            + modeled phrase (`target` locale)  │
│  │                            + in-character prompt (`target`)    │
│  └─► Rescue Chips (`Hint` | `Translate` | `Slow` | Goal Sample)   │
│                               ► Instant Data Channel coaching     │
└───────────────────────────────┬───────────────────────────────────┘
                                ▼  (Learner taps End Call / Back)
┌───────────────────────────────────────────────────────────────────┐
│ Stage 3 · Transcript & Call Summary Persistence                   │
│ • Saves spoken turns + call duration summary to `conversation`    │
│ • Transitions to `ChatScreen` (or pops back if 0 turns spoken)    │
└───────────────────────────────────────────────────────────────────┘
```

---

## 2. Audio, STT & Bilingual TTS Pipeline (`voice-agent/`)

### 2.1 Why Regional `sonic-3.6` Locale Conditioning Is Mandatory
Zuno's curated vocal identities (`warm` = Ariana, `energetic` = Carson, `calm` = Mariana) retain consistent timbre across languages, but English-native voice embeddings (`Ariana`, `Carson`) sound noticeably accented if synthesized with legacy `sonic-3` (`2025-04-16`) without regional `locale`.

Every utterance in `RoleplayAgent`, `LessonAgent`, and `ConversationalAgent` therefore streams through **`stream_bilingual_tts()` (`voice-agent/voices.py`)**:
1. **Incremental Clause Streaming (`iter_streaming_clauses` in `voice-agent/bilingual.py`):**
   - Consumes LLM tokens as they stream from Gemini 2.5 Flash.
   - Buffers only up to each closed `<lang code="xx">...</lang>` tag or sentence boundary (`.?!¡¿。？！`), keeping Time-To-First-Audio low while prefetching the next clause concurrently.
   - Strips `<lang>` / `[xx]` tags across arbitrary token splits in `strip_lang_tags_stream()` so UI captions and saved DB transcripts never contain raw markup.
2. **Per-Clause `sonic-3.6` + Regional `locale` Synthesis:**
   - Target clauses synthesize via Cartesia `model_id = "sonic-3.6"` (`Cartesia-Version: 2026-03-01`) with `resolve_cartesia_locale(target_lang, accent, tutor_voice)` (e.g., `es-MX`, `es-ES`, `es-CO`, `fr-FR`, `fr-CA`, `pt-BR`, `pt-PT`, `de-DE`, `de-CH`, `ja-JP`, `ko-KR`, `zh-CN`, `zh-TW`), with automatic fallback to `sonic-3` base `language` if a locale is unavailable.
   - Native clarification clauses synthesize with `resolve_cartesia_locale(native_lang, ...)` (e.g., `en-US`, `en-GB`) using the **exact same `voice_id`**.
   - Clauses are joined with 10ms linear anti-pop fade ramps and a `180ms` prosodic pause on language switches (`80ms` between same-language sentences).
3. **Untagged Fallback Guard (`detect_clause_language`):**
   - If the LLM ever omits `<lang>` tags on a multi-sentence clarification response, sentence-level script, diacritic, and function-word scoring routes clear native-language explanations to `native_lang` while defaulting all roleplay dialogue strictly to `target_lang`.

### 2.2 Multilingual Speech-to-Text (`create_stt` in `voice-agent/agent.py`)
- When `targetLanguage != nativeLanguage` and both belong to Deepgram Nova-3's multilingual set (`en, es, fr, de, hi, ru, pt, ja, it, nl`), `create_stt()` configures **`dg.STT(model="nova-3", language="multi", endpointing_ms=25)`** for `lesson`, `roleplay`, and `conversation` modes so learners can switch fluidly between practicing the target language and asking a quick question in their user language.
- Voice shortcuts (`"repeat"`, `"say that again"`, `"slower"`, `"más despacio"`) are intercepted in `RoleplayAgent.on_user_turn_completed` to replay the last line immediately without burning an extra LLM turn.

---

## 3. Backend Schema & API Contract (`backend/`)

### 3.1 Drizzle Schema (`backend/src/db/app-schema.ts`)

```typescript
export type RoleplayGoalItem = {
  id: string;            // "goal_1" | "goal_2" | "goal_3"
  label: string;         // 2–3 word chip label in explanation language, e.g. "Order coffee"
  description: string;   // 1-sentence objective, e.g. "Greet the barista and order your drink"
  samplePhrase?: string; // Authentic target-language model phrase at learner's CEFR level
};

// Column on `roleplay`:
goals: jsonb("goals").$type<RoleplayGoalItem[]>(),
```

*Note:* `ensureRuntimeSchema()` (`backend/src/db/index.ts`) runs `ALTER TABLE "roleplay" ADD COLUMN IF NOT EXISTS "goals" jsonb;` idempotently on startup, and `ensureRoleplayGoals()` (`backend/src/routes/roleplays.ts`) lazily generates and caches 3 goals via Gemini for any legacy, seed, or community roleplay whose `goals` column is `null`.

### 3.2 HTTP Endpoints

- **`POST /api/roleplays/:id/session`** (Body: `{ restart?: boolean }`)
  1. Resolves the user's owned `roleplay` row, or auto-saves/forks a Zuno Official seed or public community roleplay on first launch.
  2. Lazily populates and caches `roleplay.goals` if missing.
  3. Finds the latest `conversation` (`kind = "roleplay"`) or creates a fresh one (when `restart == true` or none exists) without blocking on text-chat opening generation.
  4. Mints a LiveKit JWT (`room = "zuno-rp-..."`) with `participant.metadata`:
     - `mode: "roleplay"`, `voice`, `tutorVoice`, `roleplayId`, `conversationId`
     - `language`, `targetLanguage`, `targetLanguageName`, `varietyName`
     - `nativeLanguage`, `nativeLanguageName`, `accent`, `level`
     - `teachInTarget`, `teachingStyle`, `strictMode`, `handsFree`
     - `resume`, `historyTranscript`
     - `roleplay: { id, title, scenario, userRole, aiRole, setting, goals }`
  5. Returns `{ token, url, room, identity, conversationId, roleplayId, roleplay, goals }`.
- **`POST /api/livekit/token` (`backend/src/index.ts`)**
  - When `conversationId` belongs to an existing `roleplay` conversation (e.g., user tapped the Call button inside `ChatScreen`), automatically resolves `targetLanguage = thread.language || body.language || profile.targetLanguage`, loads `roleplay` + `goals`, and emits `mode: "roleplay"` metadata so chat-initiated calls get the exact same `RoleplayAgent` experience.

---

## 4. LiveKit Data Channel Protocol (`voice-agent/roleplay_flow.py` ↔ `android`)

Roleplay reuses the reliable Data Channel packet types handled by `VoiceSessionViewModel` and `VoiceModeScreen`:

### 4.1 Agent → Client Events
- **`lesson_stage` (`stage = "roleplay"`):**
  - `pill`: `{ icon: "users", role: aiRole, goal: "Goal 1 of 3 · Order drink" | "All goals complete · Open practice", contextNote: title }`
  - `roleplayGoals`: Array of `{ id, index, label, description, samplePhrase, completed, current }`
  - `rescueChips`: `["hint", "translate", "slow"]`
- **`lesson_turn`:** Emitted for both `"agent"` (`senderLabel = "{aiRole} · Sofía"`, `actions = ["translate", "listen_slow"]`) and `"user"` (`senderLabel = "You · Speaking (Goal X of Y)"` or `"You · Open practice"`).
- **`lesson_turn_update`:** Attaches inline `translation` to the corresponding assistant bubble when `translate` is triggered.
- **`rescue_payload`:** Surfaces contextual coaching pills for `hint` (`Try: “...”`), `translate`, and `recast` (`Native tip: “...”`).

### 4.2 Client → Agent Commands (`lesson_action` / `roleplay_action`)
- `play_chunk` (`payload`: sample phrase) → Speaks the goal's `samplePhrase` clearly at `0.90×` in `targetLanguage`.
- `hint` → Generates a 1-sentence `targetLanguage` reply suggestion (+ translation) tailored to the active goal and partner's last line.
- `translate` (`payload`: bubble text or null for last turn) → Translates the line into `nativeLanguage` and updates the bubble inline.
- `listen_slow` / `slow` → Replays the line at `0.76×` speed using `sonic-3.6` locale conditioning.
- `listen_normal` → Replays the line at normal speed.

---

## 5. Android UX & Navigation Rules (`ZunoApp.kt` & `VoiceModeScreen.kt`)

1. **Direct Live Voice Entry:**
   - Tapping a Roleplay row (`RoleplaysScreen`), **`Start live roleplay`** (`RoleplaySettingsScreen`), **`Start live`** (`CommunityRoleplayPreviewScreen`), **`Start over`** (`roleplayMenu`), a scheduled Roleplay, or a Recent Roleplay row calls `startRoleplayVoice(roleplayId)` → `POST /api/roleplays/:id/session` → `VoiceModeScreen`.
   - If microphone permission is declined, `startRoleplayVoice` falls back gracefully to `openRoleplay` (`ZunoRoute.Chat`) so text-only practice remains available.
   - The Roleplay overflow menu (`⋮`) also exposes **`Chat`** (`openRoleplay` → `ZunoRoute.Chat`) for reviewing past transcripts without starting a call.
2. **In-Call Stage UI (`VoiceModeScreen`):**
   - Renders `LessonAvatarVoiceBody` whenever `isLessonMode || lessonStage != null`, displaying the animated Sofía stage avatar (or side-by-side video tiles when camera is enabled), live transcript bubbles with `Translate` / `0.75×` actions, and `ZunoLessonCoachCard` with Goal tabs (`1`, `2`, `3`) + `Answers / Rescue` tab.
   - Uses `Live · MM:SS` in the top bar (not `Lesson · MM:SS`) and exits cleanly **without** the *"End lesson early?"* confirmation dialog.
3. **Clean Call Exit:**
   - If ≥ 1 turn was spoken (or prior messages exist), `endVoice()` persists the spoken voice turns and call summary to `conversationId` and navigates to `ZunoRoute.Chat(conversationId)`.
   - If 0 turns were spoken on a brand-new session, `leaveVoice()` pops cleanly back to the previous screen without stranding the user on an empty chat.
