# ZunoAI — Interactive Voice Lesson Flow Specification (`03g`)

**Figma Reference:** [`03g · Flow — Topics → Topic → Lesson (Teach · Build · Recall · Roleplay) → Evaluation`](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2421-8385) (`2421:8385`)  
**Target Stack:** Android (`Kotlin / Jetpack Compose` + `VoiceSessionViewModel`), Backend (`Node.js / Hono / Drizzle`), Voice Worker (`Python LiveKit Agents` in `voice-agent/agent.py`)

---

## 1. System Overview & Lesson State Machine

Every Topic Lesson (~5 minutes) progresses through a deterministic **5-stage pedagogical state machine** orchestrated by the Python `voice-agent` (`agent.py`) and rendered reactively on Android via **LiveKit Reliable Data Channel** events:

```text
[01 Topics List] ──► [02 Topic Detail]
                           │ (Tap "Continue" / Lesson Row)
                           ▼
              ┌─────────────────────────┐
              │ 03a · TEACH & MODEL     │  Stage 1a: Bilingual framing + model target phrase
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │ 03b · SHADOW & DRILL    │  Stage 1b: Pronunciation scoring + back-chaining chunks
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │ 03c · BUILDING BLOCKS   │  Stage 2: Pattern substitution (stem + new vocab block)
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │ 03d · ACTIVE RECALL     │  Stage 3: Native situational prompt (Spanish hidden)
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │ 03e · ROLEPLAY (TUTOR)  │◄─┐ Stage 4a: In-character prompt + comprehension curveball
              └────────────┬────────────┘  │
                           ▼               │ (Multi-turn loop until goals met)
              ┌─────────────────────────┐  │
              │ 03f · ROLEPLAY (USER)   │──┘ Stage 4b: User turn + implicit recast + rescue chips
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │ 04 · EVALUATION         │  Stage 5: Metrics, recasts, practiced phrases & mastery
              └────────────┬────────────┘
                           ▼
                   [05 Topic Updated]
```

---

## 2. Database & API Contract (`backend/`)

### 2.1 Drizzle Schema Additions (`backend/src/db/app-schema.ts`)

Extend `topicLesson` with structured curriculum metadata (generated once when the Topic is created) and add `lessonEvaluation` to persist post-lesson results:

```typescript
export type LessonCurriculum = {
  targetPhrases: Array<{
    id: string;
    phrase: string;          // e.g. "Disculpe, ¿este autobús va al centro?"
    translation: string;     // e.g. "Excuse me, does this bus go downtown?"
    focusWord: string;       // e.g. "autobús"
    syllables: string;       // e.g. "au · to · BÚS"
    pronunciationCue: string;// e.g. "Stress final syllable"
    chunks: string[];        // e.g. ["va al centro", "este autobús"]
  }>;
  buildingBlocks: Array<{
    promptNative: string;    // e.g. "Now ask if this bus goes to the train station."
    newWord: string;         // e.g. "la estación"
    meaning: string;         // e.g. "train station"
    stemHint: string;        // e.g. "¿Este autobús…?"
    expectedTarget: string;  // e.g. "¿Este autobús va a la estación?"
  }>;
  recallPrompts: Array<{
    situationNative: string; // e.g. "The doors open. Ask the driver which bus goes downtown."
    firstWordHint: string;   // e.g. "Disculpe…"
    expectedTargets: string[];
  }>;
  roleplay: {
    aiRole: string;          // e.g. "Bus driver"
    setting: string;         // e.g. "Central bus terminal"
    goals: Array<{
      id: string;
      label: string;         // e.g. "Ask route", "Ask stops"
      description: string;
    }>;
  };
};

// Add column to topic_lesson:
// curriculum: jsonb("curriculum").$type<LessonCurriculum>(),

export const lessonEvaluation = pgTable("lesson_evaluation", {
  id: text("id").primaryKey(),
  lessonId: text("lesson_id").notNull().references(() => topicLesson.id, { onDelete: "cascade" }),
  conversationId: text("conversation_id").notNull().references(() => conversation.id, { onDelete: "cascade" }),
  userId: text("user_id").notNull().references(() => user.id, { onDelete: "cascade" }),
  durationSec: integer("duration_sec").notNull(),
  pronunciationScore: integer("pronunciation_score").notNull(), // 0-100
  goalsCompleted: integer("goals_completed").notNull(),
  goalsTotal: integer("goals_total").notNull(),
  fluencyLabel: text("fluency_label", { enum: ["Developing", "Steady", "Natural", "Fluent"] }).notNull(),
  coachNotes: jsonb("coach_notes").$type<Array<{
    category: string;        // e.g. "Roleplay recast · Natural phrasing"
    correction: string;      // e.g. "“Voy en el cinco” → “Tomo la línea cinco”"
    ttsSampleText: string;   // e.g. "Tomo la línea cinco"
    kind: "grammar" | "pronunciation";
  }>>().notNull(),
  phraseResults: jsonb("phrase_results").$type<Array<{
    phrase: string;
    status: "mastered" | "practice";
  }>>().notNull(),
  createdAt: timestamp("created_at").defaultNow().notNull(),
});
```

### 2.2 HTTP Endpoints (`backend/src/index.ts`)
* `GET /api/topics` → Returns user's topics with `{ completedLessons, totalLessons }` (`01 · Topics — List`).
* `GET /api/topics/:topicId` → Returns topic metadata + ordered `topicLesson[]` + `estimatedMinutesLeft` (`02` & `05`).
* `POST /api/lessons/:lessonId/session` → Creates/resumes a `conversation` (`kind = "lesson"`), mints a LiveKit token embedding `lessonId`, `curriculum`, `nativeLanguage`, `targetLanguage`, `level`, and `teachInTarget` inside `participant.metadata`.
* `POST /api/lessons/:lessonId/complete` → Persists `lessonEvaluation`, sets `topicLesson.completedAt = now()`, and returns the evaluation summary + next `lessonId` (`04`).

---

## 3. LiveKit Data Channel Protocol (`voice-agent` ↔ `android`)

Extend `broadcast_event()` and `_on_client_data()` in `voice-agent/agent.py` and `VoiceSessionViewModel` on Android with the following JSON packet types (`reliable = True`):

### 3.1 Agent → Client Events

```jsonc
// 1. Stage transition (drives which UI layout 03a–03f renders)
{
  "type": "lesson_stage",
  "stage": "teach_model" | "shadow_drill" | "building_blocks" | "active_recall" | "roleplay",
  "pill": {
    "icon": "book-open" | "waveform" | "sparkles" | "rotate-ccw" | "users",
    "role": "Learn",
    "goal": "Phrase 1 of 3"
  },
  "card": {
    // Populated for teach_model / shadow_drill ("teach_card") or building_blocks ("scaffold_card"); null for recall/roleplay
    "kind": "teach_card",
    "setupPrompt": "To politely ask if this bus goes downtown, say:",
    "phrase": "Disculpe, ¿este autobús va al centro?",
    "translation": "Excuse me, does this bus go downtown?",
    "focusWord": "autobús",
    "syllables": "au · to · BÚS",
    "cue": "Stress final syllable",
    "chunks": ["va al centro", "este autobús"]
  },
  "recallPrompt": null, // Populated in active_recall: { "eyebrow": "Say in Spanish", "cue": "The doors open..." }
  "rescueChips": ["hint", "reveal", "slow"] // Controls bottom rescue row
}

// 2. Real-time pronunciation / drill evaluation (updates Feedback Pill on 03b)
{
  "type": "pronunciation_eval",
  "score": 88,
  "passed": false,
  "focusWord": "autobús",
  "feedbackText": "88% match · Clear rhythm, stress “-bús” once more",
  "showChunks": true
}

// 3. Rescue response (when user taps Hint / First word / Reveal / Translate)
{
  "type": "rescue_payload",
  "action": "first_word" | "hint" | "reveal" | "translate",
  "text": "Disculpe, ¿qué…"
}

// 4. Lesson completed (triggers navigation from Live Call -> 04 · Evaluation)
{
  "type": "lesson_complete",
  "lessonId": "les_123",
  "evaluation": {
    "durationSec": 305,
    "pronunciationScore": 91,
    "goalsCompleted": 5,
    "goalsTotal": 5,
    "fluencyLabel": "Natural",
    "coachNotes": [
      {
        "category": "Roleplay recast · Natural phrasing",
        "correction": "“Voy en el cinco” → “Tomo la línea cinco”",
        "ttsSampleText": "Tomo la línea cinco",
        "kind": "grammar"
      },
      {
        "category": "Pronunciation · Stress",
        "correction": "au·to·BÚS — stress the final syllable",
        "ttsSampleText": "autobús",
        "kind": "pronunciation"
      }
    ],
    "phraseResults": [
      { "phrase": "¿Este autobús va al centro?", "status": "mastered" },
      { "phrase": "¿Cuántas paradas son?", "status": "mastered" },
      { "phrase": "¿Pasa por la estación?", "status": "practice" }
    ]
  }
}
```

### 3.2 Client → Agent Commands

```jsonc
// User tapped a bottom Rescue Chip or Teach Card audio action
{
  "type": "lesson_action",
  "action": "listen_normal" | "listen_slow" | "play_chunk" | "hint" | "first_word" | "reveal" | "translate",
  "payload": "va al centro" // Optional: specific chunk or phrase text
}
```

---

## 4. Screen-by-Screen & Case-by-Case Implementation Specs

---

### Case `01` · Topics — List (`2421:8386`)
* **Purpose:** Entry point showing all user-created and curated learning topics.
* **UI Components:** `Top Bar` (`Back + Title`, search action), `Collection Card` list (`2px` vertical spacing inside `radius/2xl` clipped `List Group`), `+ New topic` `FAB`.
* **Behavior:**
  * Fetches `GET /api/topics`.
  * Subtitle displays `{completed} of {total} lessons` when in progress, or `Completed` when all lessons have `completedAt != null`.
  * Tapping a card navigates to `02 · Topic — In progress`.
  * Tapping `+ New topic` opens the Topic creation flow (prompt + optional material upload).

---

### Case `02` · Topic — In Progress (`2421:8472`)
* **Purpose:** Displays the 5-lesson learning path for a Topic and launches the next incomplete lesson.
* **UI Components:** Topic Hero (`Title`, `Translation`, `{done} of {total} lessons · {min} min left`, primary `Continue` button), `Section Header ("Lessons")`, 5× `Lesson Row` (`Status = Done | Current | Upcoming`).
* **Behavior:**
  * **Primary CTA (`Continue`):** Starts the first lesson where `completedAt == null` (here, Lesson 3: *Elige el autobús adecuado*) by calling `POST /api/lessons/:id/session`, connecting to the LiveKit room, and entering `03a`.
  * **Tapping a `Current` lesson row:** Same as `Continue`.
  * **Tapping a `Done` lesson row:** Opens the saved `04 · Evaluation` summary for that lesson (with a secondary option to *Practice again*).
  * **Tapping an `Upcoming` lesson row:** Either locked until sequential completion or shows a confirm dialog (*"Jump ahead to Lesson 4?"*).

---

### Case `03a` · Voice — Teach & Model (`2440:8984`)
* **Pedagogical Technique:** **Bilingual Framing + Modeling** — Explain meaning/politeness in `nativeLanguage`, then model the exact `targetLanguage` sentence so cognitive load is zero.
* **Figma Components:** `Top Bar` (call timer `00:18`), `Roleplay Pill` (`Learn · Phrase 1 of 3`), compact `Voice Orb · Speaking`, setup text, `Teach Card` (`Phrase`, `Translation`, `Syllables`, `Cue`, `Listen`, `0.75×`), `Call controls`.
* **Agent & Audio Pipeline Rules:**
  1. On entering `teach_model`, `agent.py` emits `lesson_stage` (`stage = "teach_model"`) with the `Teach Card` payload.
  2. **Bilingual TTS Handling:** Unless `userProfile.teachInTarget == true` (or CEFR level is `C1/C2`), the agent speaks the short setup in `nativeLanguage` (*"To politely ask if this bus goes downtown, say:"*), pauses `300ms`, and speaks the target phrase in the native `targetLanguage` TTS voice (*"Disculpe, ¿este autobús va al centro?"*).
  3. **Card Actions:**
     * Tapping **`Listen`** sends `{"type": "lesson_action", "action": "listen_normal"}` → agent speaks only the target phrase at `speed = 1.0` without advancing LLM context.
     * Tapping **`0.75×`** sends `{"type": "lesson_action", "action": "listen_slow"}` → agent synthesizes the target phrase at `speed = 0.75`.
* **Transition → `03b`:** Automatically transitions to `shadow_drill` (`03b`) as soon as TTS playback finishes and user microphone capture opens.

---

### Case `03b` · Voice — Shadow & Pronunciation (`2440:9473`)
* **Pedagogical Technique:** **Shadowing + Word-Stress Evaluation + Back-Chaining Chunks**.
* **Figma Components:** `Roleplay Pill` (`Shadow · Pronunciation drill`), `Voice Orb · Listening`, `Teach Card` (with `focusWord` **autobús** highlighted in `text/primary` against `text/secondary` sentence text), full-width `Feedback Pill`, chunk `Chip`s (`va al centro`, `este autobús`).
* **Pronunciation Scoring Logic (`agent.py`):**
  1. Deepgram Nova-3 STT returns word-level transcripts and confidence scores (`ev.alternatives[0].words`) in `targetLanguage`.
  2. Compute `similarity_score`: normalized Levenshtein token match (`60%` weight) + Deepgram per-word confidence on `focusWord` (`40%` weight).
  3. **Pass (`score >= 85`):**
     * Emit `pronunciation_eval` (`passed: true`, e.g. `"94% match · Natural pronunciation"`).
     * Give brief spoken praise (*"¡Muy bien!"*) and advance to the next phrase or Stage 2 (`03c`).
  4. **Retry / Back-chaining (`score < 85`, max 2 retries):**
     * Emit `pronunciation_eval` (`passed: false`, `focusWord`, `feedbackText`, `showChunks: true`).
     * Reveal the back-chaining `chunks` chips below `Feedback Pill`. Tapping a chunk chip sends `play_chunk` so the learner can isolate and shadow just `"va al centro"` or `"este autobús"`.
     * After 2 retries, log the phrase as `"practice"` for `04 · Evaluation` and move forward constructively so the learner never gets stuck in a loop.

---

### Case `03c` · Voice — Building Blocks (`2440:9913`)
* **Pedagogical Technique:** **Pattern Substitution (Michel Thomas Method)** — Combine a learned sentence stem with a newly introduced vocabulary block without showing the full answer.
* **Figma Components:** `Roleplay Pill` (`Build · Combine pattern`), `Voice Orb · Listening`, `Scaffold Card` (`Prompt`, `New word` + `Meaning` vocab pill, `Stem hint`), live streaming user transcript (`Web/Title`), `Listening status`, rescue chips (`Hint`, `Reveal`, `Slow`).
* **Implementation Rules:**
  1. Agent emits `lesson_stage` (`stage = "building_blocks"`) with `Scaffold Card` data—**never** sending the full `expectedTarget` in `card` so the user constructs it mentally rather than reading aloud.
  2. Agent speaks the prompt: *"The word for train station is **la estación**. Now ask if this bus goes to the train station."*
  3. **Extended Endpointing Delay:** Set `min_endpointing_delay = 1.4` seconds (up from `0.5s`) during `building_blocks` and `active_recall` because learners pause mid-sentence while conjugating or recalling nouns.
  4. **Live Transcript Streaming:** As `user_input_transcribed` fires (`is_final = false` → `true`), stream the learner's words below `Scaffold Card`.
  5. **Rescue Chips:**
     * `Hint` → Agent speaks/displays the verb connector (*"Use: va a la…"*).
     * `Reveal` → Reveals `expectedTarget` inside the card and converts the turn into a quick shadow repeat before advancing.
     * `Slow` → Replays the tutor's prompt at `0.75×` speed.

---

### Case `03d` · Voice — Active Recall (`2440:10346`)
* **Pedagogical Technique:** **Spaced Anticipation (Pimsleur Method)** — Situational prompt in `nativeLanguage`, target Spanish **completely hidden**, forcing active retrieval from memory.
* **Figma Components:** `Roleplay Pill` (`Recall · Speak from memory`), full-size `Voice Orb · Listening`, `Say in Spanish` eyebrow, `recallCue` in `nativeLanguage`, `Target hidden · Speak when ready` status, rescue chips (`First word`, `Reveal`, `Slow`).
* **Implementation Rules:**
  1. Agent emits `lesson_stage` (`stage = "active_recall"`, `card = null`, `recallPrompt = {...}`).
  2. Even when `Captions` toggle in `Call controls` is **ON**, the UI **must suppress Spanish target captions** until the user finishes speaking (or taps `Reveal`).
  3. **Silence Rescue Timer:** If no VAD speech start is detected within `6.0 seconds`, subtly pulse the `First word` chip.
  4. **Rescue Chips:**
     * `First word` → Whispers/displays only `firstWordHint` (*"Start with: Disculpe, ¿qué…"*), preserving 80% of the active recall effort.
     * `Reveal` → Displays the full target phrase and marks this item `"practice"` for spaced review in `04 · Evaluation`.
  5. **Evaluation:** LLM checks semantic equivalence (accepting valid synonyms, e.g. *¿Qué autobús va al centro?* or *¿Cuál autobús pasa por el centro?*), confirms with the native model, and transitions to `03e`.

---

### Case `03e` & `03f` · Voice — Goal-Driven Roleplay (`2440:10765` & `2440:11202`)
* **Pedagogical Technique:** **Task-Based Roleplay + Listening Curveball + Implicit Conversational Recasting**.
* **Figma Components:**
  * **`03e` (Tutor Turn):** `Roleplay Pill` (`Bus driver · Goal 1 of 2 · Ask route`), full-size `Voice Orb · Speaking`, 2-line rolling captions (`Previous` dimmed `text/tertiary` → `Current` bold `text/primary`).
  * **`03f` (Learner Turn):** `Roleplay Pill` (`Bus driver · Goal 2 of 2 · Ask stops`), full-size `Voice Orb · Listening`, dimmed last tutor line → bold live user speech, `Listening · Take your time` indicator, rescue chips (`Hint`, `Translate`, `Slow`).
* **Implementation Rules:**
  1. **Switch STT to Target Language:** Ensure Deepgram STT is locked to `targetLanguage` + `accent` (e.g., `es-419` or `es-ES`).
  2. **Goal Tracking via LLM Tool Call:** Give Gemini 2.5 Flash a lightweight function tool `mark_roleplay_goal_complete(goal_id: str, recast_note?: { user_said: str, natural_form: str })`:
     * Whenever the learner satisfies Goal 1, the tool updates `Roleplay Pill` to `Goal 2 of 2 · Ask stops`.
  3. **Strict Non-Interruption (Implicit Recasting):**
     * System prompt rule for Stage 4: *"Stay 100% in character as `aiRole`. NEVER break character to explain grammar rules if the learner's meaning is understandable. Naturally echo the more idiomatic phrasing inside your reply, and record any grammar/phrasing improvement silently via `recast_note` for post-lesson evaluation."*
  4. **Comprehension Curveball:** Include one realistic detail in the AI character's response (e.g., *"No, este va hacia el norte. Toma la línea cinco enfrente."*) so the learner must process real spoken input before completing Goal 2.
  5. **Captions Toggle Behavior (`Call controls` bottom-right):**
     * **ON (Default for A1–B1):** Shows rolling 2-line Spanish captions (`03e` / `03f`). English translation stays hidden unless the user taps **`Translate`** rescue chip (preserving target-language immersion).
     * **OFF:** Hides `Captions` text stack while keeping `Roleplay Pill` visible (`Flow 3a` classic minimal mode).
  6. **Stage 5 ("One Last Fix") Wrap-Up:** Once all roleplay goals are marked complete, the tutor steps out of character for one brief turn—if `recast_note` was logged, asks the learner to repeat the natural phrase once (*"Tomo la línea cinco"*), then emits `lesson_complete` and navigates to `04`.

---

### Case `04` · Lesson Complete — Evaluation (`2421:8995`)
* **Purpose:** Consolidate speaking feedback, highlight natural recasts, and classify phrases into `Mastered` vs. `Practice` for spaced repetition in future lessons.
* **Figma Components:**
  * `Top Bar` (`Lesson 3 of 5`)
  * Header (`Lesson complete · 5 min`, lesson title)
  * 3× `Eval Stat` cards:
    1. **Pronunciation:** Average `pronunciation_eval` score across Stage 1–3 (`91%`).
    2. **Goals:** Completed lesson & roleplay objectives (`5 of 5`).
    3. **Fluency:** Mapped from turn latency, hesitations, and `Reveal` usage (`Natural` | `Steady` | `Developing`).
  * `Section Header ("Coach feedback")` + 2× `Coach Note` cards:
    * Populated from logged `recast_note`s and lowest-scoring `focusWord` stress cue.
    * Tapping a `Coach Note` (or its trailing speaker icon) synthesizes/plays `ttsSampleText` locally or via `/api/tts/preview`.
  * `Section Header ("Practiced phrases")` + `List Group` of `Phrase Row` (`Status = Mastered | Review`):
    * Phrases completed without `Reveal` and `score >= 85` → `Mastered` (`✓` inverse badge).
    * Phrases where `Reveal` was used or `score < 85` after retries → `Practice` (`↻` badge, automatically injected into the next lesson's `active_recall` pool!).
  * **Bottom CTAs:**
    * **`Next lesson` (Primary):** Calls `POST /api/lessons/:nextLessonId/session` (or navigates to `05` if final lesson in topic).
    * **`Back to topic` (Ghost):** Navigates to `05 · Topic — Updated`.

---

### Case `05` · Topic — Updated (`2421:9440`)
* **Purpose:** Confirm progression along the Topic path.
* **Behavior:**
  * Re-fetches `GET /api/topics/:topicId` (or optimistically updates local state in `ZunoViewModel`).
  * Lesson 3 transitions to `Status = Done` (`✓ Done`), Lesson 4 becomes `Status = Current` (`Up next`), and Topic subtitle updates (`3 of 5 lessons · 10 min left`).

---

## 5. Edge Cases & Production Safeguards

1. **Mid-Lesson Exit (`End call` or System Back):**
   * Show a lightweight confirmation sheet: *"End lesson early? Your progress in Lesson 3 won't be marked complete."* (`Continue lesson` vs. `End call`).
   * Disconnect LiveKit room cleanly without setting `topicLesson.completedAt`.
2. **Barge-In / Tap Orb to Interrupt:**
   * Silero VAD (`min_speech_duration = 0.05`) is already configured in `agent.py` for natural barge-in; tapping the `Voice Orb` while the agent is speaking should also send `{"type": "interrupt"}` to cancel active TTS output immediately.
3. **Noisy Environment / Empty STT:**
   * If Deepgram returns empty or `< 0.35` confidence audio during a drill, do **not** penalize `pronunciationScore`; display a transient toast/caption (*"Didn't catch that — try speaking a bit closer"*).
4. **CEFR Level Scaling (`userProfile.level`):**
   * **A1–A2:** Include all stages (`03a → 03f`), `Captions` ON by default, `speed = 0.92` on initial models.
   * **B1–B2:** Streamline `03a` setup to 1 crisp clause, `speed = 1.0`, include comprehension curveballs in `03e`.
   * **C1–C2 (or `teachInTarget = true`):** Conduct `03a`, `03c`, and `03d` prompts 100% in `targetLanguage` (paraphrasing situations in target language rather than translating from English).
