# ZunoAI — Vocabulary & Voice SRS Flow Specification (`12 · Vocabulary`)

**Figma Reference:** [`12 · Vocabulary`](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2462-9514) (`2462:9514`)  
**Target Stack:** Android (`Kotlin / Jetpack Compose` + `ZunoViewModel` + `VoiceSessionViewModel`), Backend (`Node.js / Hono / Drizzle / Gemini`), Voice Worker (`Python LiveKit Agents` in `voice-agent/agent.py`)

---

## 1. End-to-End Flow Overview

Vocabulary in ZunoAI is **conversation- and context-first**: instead of extracting words from images or forcing learners to maintain manual flashcard decks, phrases come directly from **conversation history** (live inline suggestions as the chat unfolds, plus an end-of-chat/call review sheet), **completed lessons**, and an optional **manual `+` / search fallback**. Review happens through a **2-minute Voice SRS Sprint** with Sofía.

```text
  ┌────────────────────────────────────────── CAPTURE & CREATION ──────────────────────────────────────────┐
  │                                                                                                        │
  │  [11.8 Inline Chat]       [11.9 Post-Call / Meeting]  [11.10 End-of-Lesson Save]  [11.12 / 11.13 +]    │
  │   1-tap bookmark on        Curated 3–5 phrase list     1-tap bookmark per row      Search miss / +     │
  │   suggested phrases        when voice/video call or    or "Save all" on Lesson     sheet (AI fills       │
  │   during ongoing chat      1:1 meeting ends            Evaluation (`2677:27395`)   meaning + cue)      │
  │         │                            │                            │                         │          │
  └─────────┼────────────────────────────┼────────────────────────────┼─────────────────────────┼──────────┘
            └────────────────────────────┴────────────────────────────┴─────────────────────────┘
                                                      │
                                                      ▼
                            POST /api/vocabulary or /batch (deduped upsert)
                                                      │
            ┌─────────────────────────────────────────┴─────────────────────────────────────────┐
            ▼ (0 saved)                                                                         ▼ (≥ 1 saved)
  [11.11 · Empty Library]                                                        [11.1 · Vocabulary — SRS Library]
   • 0 saved · Ways to build deck                                                 • Filters: Due now | Learning | Mastered
   • Chat / Lesson / Manual add                                                   • Instant TTS playback per row
                                                                                  • Search [11.12] · Add [11.13] · Detail [11.14]
                                                                                                │
                                                                           Tap "Start 2-min Voice Sprint" (or "Practice with Sofía")
                                                                                                ▼
                                                                                 [11.2 · Sprint — Active Recall]
                                                                                  • Sofía gives native cue; target hidden (_ _ _)
                                                                                  • Rescue chips: "First word" | "Reveal"
                                                                                                │
                                                                         ┌──────────────────────┴──────────────────────┐
                                                                         ▼ (Match ≥ 80%)                               ▼ (Miss / Low stress)
                                                           [11.3 · Match & Interval]                     [11.4 · Coach & Shadow]
                                                            • Live score + new SRS interval               • Syllable stress pills (es·CA·la)
                                                            • "Next phrase →"                             • Slow 0.75× + Retry → Tomorrow
                                                                         └──────────────────────┬──────────────────────┘
                                                                                                ▼ (All 5 phrases complete)
                                                                                 [11.5 · Sprint complete — Evaluation]
                                                                                  • Accuracy %, Mastered, +XP
                                                                                  • Coach recasts + updated intervals
                                                                                                │
                                                                         ┌──────────────────────┴──────────────────────┐
                                                                         ▼ (Remaining due > 0)                         ▼ (Due == 0)
                                                           [11.6 · Vocabulary — Updated]                 [11.7 · Vocabulary — All Caught Up]
                                                            • Updated counts & intervals                  • "Zero phrases due" + "Run bonus sprint"
```

---

## 2. Screen Inventory (`12 · Vocabulary`, `2462:9514`)

### Row 1 — Core SRS Library & Voice Sprint (`11.1`–`11.7`)

| Screen | Figma Node | Purpose | Key Elements |
| :--- | :--- | :--- | :--- |
| **`11.1 · Vocabulary — SRS Library`** | `2462:10079` | Primary Vocabulary tab/destination | Top bar (`Search` + `+`), SRS Hero (`148 saved`, `5 phrases ready`, `Start 2-min Voice Sprint`), filter chips (`Due now · 5`, `Learning · 34`, `Mastered · 109`), `Vocab Row` list (`Due now` / `7d` / `14d` / `30d` + audio trigger). |
| **`11.2 · Sprint — Active Recall`** | `2733:12889` | Live voice recall drill | `Voice call · 00:42`, Sofía portrait (`296dp` max), top-aligned conversation (`Sofía · Recall cue` in native language), `First word` + `Reveal` rescue chips, `You · Spoken recall` dashed prompt, `Sprint Coach Card` (`Recall · Phrase 2 of 5`, `Café context`, word-length blank tiles `_ _ _ _ _`). |
| **`11.3 · Sprint — Match & Interval`** | `2733:12957` | Successful recall & SRS advance | `You · 94% match · +7d`, `Listen` + `0.75×` chips, green check feedback pill (`Mastered · Next SRS review in 7 days`), `Next phrase →` button in `Sprint Coach Card`. |
| **`11.4 · Sprint — Coach & Shadow`** | `2733:13086` | Pronunciation / recall recovery | `You · 74% · Retry`, feedback pill (`Stress “-CA-” · Reset to due tomorrow`), `Shadow · Phrase 3 of 5` card with syllable pills (`es` / `CA` / `la`) and chunk audio chips (`hacer escala`, `en Madrid`). |
| **`11.5 · Sprint complete — Evaluation`** | `2733:13196` | Post-sprint summary | `Sprint · 5 of 5`, `92% Accuracy`, `4 of 5 Mastered`, `+25 XP`, `Coach feedback` cards (recasts + syllable stress), `Reviewed phrases` list with updated intervals (`Next: 7d`, `Tomorrow`), `Practice 1 due phrase` / `Back to Vocabulary`. |
| **`11.6 · Vocabulary — Updated`** | `2733:13349` | Library state after sprint | Hero updates (`1 phrase left due today`, `4 phrases advanced`), chip counts update (`Due now · 1`, `Learning · 36`, `Mastered · 111`), rows reflect new intervals (`pedir la cuenta` → `7d`). |
| **`11.7 · Vocabulary — All Caught Up`** | `2746:13274` | Zero-due state (deck > 0) | Hero shows `All caught up for today` + secondary `Run bonus sprint` CTA; `Due now · 0` filter displays `Zero phrases due` empty card prompting user to browse `Learning` or `Mastered`. |

### Row 2 — Creation, Discovery & Phrase Management (`11.8`–`11.14`)

| Screen | Figma Node | Purpose | Key Elements |
| :--- | :--- | :--- | :--- |
| **`11.8 · Capture — Chat Glossary Save`** | `2983:12340` | Inline suggestions during chat | `Sofía` assistant bubble with `SUGGESTED FROM CHAT · TAP TO SAVE` card (`para llevar` → `✓ Saved`, `recién horneado` → `Save` + audio) + top `Toast` (`Saved to Vocabulary · para llevar`, `Undo`). |
| **`11.9 · Capture — Post-Call / Meeting Phrases`** | `2990:12861` | Post-call / 1:1 meeting review | Triggered when a voice/video call or 1:1 meeting ends (`Video call · 03:42 · Ended` visible behind scrim). `Save from this call` sheet lists 4 curated phrases from the transcript with checkboxes, audio preview, `Save 3 phrases to Vocabulary` CTA, and `Skip for now`. |
| **`11.10 · Capture — Lesson Evaluation Save`** | `2983:12494` | End-of-lesson phrase saving (mirrors `04 · Lesson complete`, `2677:27395`) | `Practiced phrases` header adds a bookmark `"Save all"` action; every `Phrase Row` (`Show save = true`) displays a 1-tap `Bookmark` / green `Check` toggle beside audio (`Listen`), plus `Toast` (`Saved to Vocabulary · 2 phrases`, `Undo`). |
| **`11.11 · Vocabulary — Empty Library`** | `2985:12530` | First-run state (`0 saved`) | Search hidden (`+` visible), `Empty State` (`No saved phrases yet`), and `Ways to build your deck` card (`Chat with Sofía`, `Complete a lesson`, `Add a phrase manually`). |
| **`11.12 · Vocabulary — Search & Quick Add`** | `2985:12634` | Live search + missing-word CTA | Active `Search Field` (`sobremesa`), filter chips, `No matches for “sobremesa”` empty state, and primary `+ Add “sobremesa”` CTA that opens `11.13` prefilled. |
| **`11.13 · Vocabulary — Add Phrase Sheet`** | `2985:12721` | Manual add with AI enrichment | Modal bottom sheet (`Add phrase`) with `Phrase in Spanish`, `Meaning (auto-filled by Sofía)` + helper `"Generated with syllable stress & recall cue"`, `Context or note (optional)`, and `Save to Vocabulary` CTA. |
| **`11.14 · Vocabulary — Phrase Detail Sheet`** | `2985:12783` | Inspect, practice, edit, or delete | Modal bottom sheet (`Phrase details`) showing phrase, meaning, `Due now · 0 streak` + source chip (`Café roleplay`), `Listen` / `0.75×` chips, `EXAMPLE IN CONTEXT` card, `Practice with Sofía` CTA, `Edit`, and `Remove`. |

---

## 3. Creation Channels & Best-Practice UX Rules

### 3.1 Why Capture Follows Session Boundaries

- **Ongoing Text Chats & Free Talks (`11.8` — Inline only, NO end-of-chat popup):** Text chats and Free Talk threads never really "end". Showing a modal sheet when closing or leaving a text chat would feel disruptive and repetitive. Instead, Sofía surfaces `0–2` non-intrusive inline suggestions (`SUGGESTED FROM CHAT · TAP TO SAVE`) right below her messages so the learner can 1-tap bookmark any word as the conversation goes (`ZunoToast` + `Undo`).
- **Bounded Calls & 1:1 Meetings (`11.9` — Post-call / post-meeting review sheet):** Unlike ongoing text chats, live voice/video calls with Sofía and 1:1 peer meetings have a clear ending (`Video call · 03:42 · Ended`). Right after a call/meeting ends, a **`Save from this call`** bottom sheet presents 3–5 curated phrases from the call transcript (new phrases, coaching tips, corrections) with pre-checked defaults, audio preview, and `Skip for now`.
- **Lessons (`11.10` / `2677:27395` — End-of-lesson bookmarking):** On `04 · Lesson complete — Evaluation`, every row under `Practiced phrases` (`Phrase Row` with `Show save = true`) exposes a 1-tap bookmark toggle beside `Listen`, alongside a section-header `"Save all"` bookmark action (`ZunoToast` + `Undo`). Missed `"practice"` phrases can be pre-marked or prioritized as `Due now` (`1d`), while learner-bookmarked `"mastered"` phrases enter at `7d` so the deck stays intentional rather than bloated.

### 3.2 How Vocabulary Items Are Created (4 Entry Points)

1. **Inline Chat Suggestions (`11.8` — Real-time, 1-tap during ongoing chat)**
   - Each assistant message can include `0–2` `glossary` items (`"para llevar — to go"`).
   - Each item renders a speaker icon (`zuno_ic_volume`) and a `Save` / `✓ Saved` pill (`zuno_ic_bookmark` / `zuno_ic_check`).
   - Tapping `Save` optimistically marks it saved, calls `POST /api/vocabulary`, and shows `ZunoToast("Saved to Vocabulary · <phrase>", actionLabel = "Undo")`.
   - Assistant bubbles and 1:1 peer chat messages (after translation) also support a `"Save phrase"` action in message long-press / overflow.

2. **Post-Call / Post-Meeting Phrase Review (`11.9` — Bounded calls & 1:1 meetings only)**
   - Triggered automatically when a live AI voice/video call or 1:1 peer meeting ends (with `>= 3` spoken turns), or reopened from the `"Notes"` / `"Save phrases"` action on the `Returned Call Summary` card (`Video call · 03:42 · Ended`). Never triggered on regular text-only chats.
   - Backend (`POST /api/conversations/:id/vocabulary-suggestions?callId=...`) inspects the call transcript & coaching tips, filters out phrases already in the user's deck, and returns up to `5` ranked candidates with tags (`New phrase`, `Native tip`, `Used by Sofía`, `Your reply`).
   - User can preview TTS (`Icon/volume`), toggle checkboxes, and tap **`Save N phrases to Vocabulary`** (`POST /api/vocabulary/batch`) or **`Skip for now`**.

3. **End-of-Lesson Phrase Saving (`11.10` & `2677:27395` — Explicit on Lesson Evaluation)**
   - On `04 · Lesson complete — Evaluation`, the `Practiced phrases` header displays a `"Save all"` bookmark action and each `Phrase Row` (`Show save = true`) shows a 1-tap bookmark toggle beside `Listen`.
   - Tapping a row's bookmark (or `"Save all"`) calls `POST /api/vocabulary` / `POST /api/vocabulary/batch` carrying over the lesson curriculum's `translation`, `syllables`, `pronunciationCue`, and `chunks`, and shows `ZunoToast("Saved to Vocabulary", actionLabel = "Undo")`.
   - `"practice"` phrases enter with `state = "due"` (`intervalDays = 1`), and `"mastered"` phrases enter with `state = "learning"` (`intervalDays = 7`).

4. **Manual Quick-Add (`11.12` → `11.13` — Explicit fallback)**
   - Accessed via `+` in the Vocabulary top bar, `"Add a phrase manually"` in `11.11`, or `+ Add “<query>”` when search finds no matches (`11.12`).
   - If `meaning` is omitted, the backend calls Gemini (`enrichVocabularyItem`) to populate `translation`, `exampleTarget`, `exampleNative`, `syllables`, `stressCue`, `chunks`, and `recallPromptNative`.

### 3.3 Core UX & Lifecycle Rules

- **Language-scoped decks:** Every item is scoped to `(userId, language)`. Switching learning languages shows only that language's deck while preserving others.
- **Idempotent deduplication:** Normalize `phrase` (`trim().lowercase()`, collapsed whitespace, stripped outer punctuation) into `normalizedPhrase`. Re-saving an existing item updates `sourceLabel`/`example` if empty and bumps `updatedAt` rather than duplicating rows.
- **Detached from chat deletion:** Deleting a conversation (`DELETE /api/conversations/:id`) sets `sourceConversationId = NULL` (`ON DELETE SET NULL`) and preserves `sourceLabel` (e.g. `"Pedir un café"`, `"Café roleplay"`), honoring `"Words saved to Vocabulary stay"`.
- **Instant audio everywhere:** Every row in `11.1`, `11.8`, `11.9`, and `11.14` plays Cartesia/TTS audio on tap without leaving the screen.

---

## 4. Spaced Repetition (SRS) Scheduling Model

Zuno uses an adapted **SM-2 voice-recall algorithm** tuned for short 5-phrase speaking sprints:

| Outcome during Voice Sprint | Accuracy / Score | State Transition | Next Interval |
| :--- | :--- | :--- | :--- |
| **Clean Recall (`11.3`)** | Match ≥ 80%, no `Reveal` used | `due` → `learning` (reps < 2) or `mastered` (reps ≥ 2) | Rep 1: `3d` → Rep 2: `7d` → Rep 3: `14d` → `round(interval * ease)` (max `180d`) |
| **Assisted Recall** | Match ≥ 80%, used `First word` hint | `learning` | `min(currentInterval, 3d)` (ease − 0.1) |
| **Missed / Shadow Drill (`11.4`)** | Match < 80% or used `Reveal` | `due` / `learning` (`repetitions = 0`) | Reset to **`1d` (`Tomorrow`)** (ease − 0.2, min `1.3`) |
| **Lesson Auto-Save (Mastered)** | From `lessonEvaluation` | `learning` (`repetitions = 1`) | `7d` |
| **Lesson Auto-Save (Practice)** | From `lessonEvaluation` | `due` (`repetitions = 0`) | `1d` (`Tomorrow`) |

* **Filter chip definitions (`11.1`):**
  - `Due now`: `dueAt <= now`
  - `Learning`: `dueAt > now` and `repetitions < 2` (or `intervalDays < 14`)
  - `Mastered`: `dueAt > now` and `repetitions >= 2` (`intervalDays >= 14`)

---

## 5. Backend Schema & API Contract (`backend/`)

### 5.1 Drizzle Schema (`backend/src/db/app-schema.ts`)

```typescript
export const vocabularyItem = pgTable(
  "vocabulary_item",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    language: text("language").notNull(),
    phrase: text("phrase").notNull(),
    normalizedPhrase: text("normalized_phrase").notNull(),
    translation: text("translation").notNull(),
    contextCue: text("context_cue"),                  // e.g. "Café context"
    recallPromptNative: text("recall_prompt_native"), // e.g. "Could you bring me the bill, please?"
    exampleTarget: text("example_target"),
    exampleNative: text("example_native"),
    syllables: text("syllables"),                     // e.g. "es · CA · la"
    stressCue: text("stress_cue"),                    // e.g. "Stress “-CA-”"
    chunks: jsonb("chunks").$type<string[]>().default([]).notNull(),
    sourceKind: text("source_kind", {
      enum: ["chat", "roleplay", "lesson", "peer", "manual"],
    }).notNull(),
    sourceLabel: text("source_label").notNull(),      // e.g. "Café roleplay", "Pedir un café", "Lesson 3"
    sourceConversationId: text("source_conversation_id").references(() => conversation.id, {
      onDelete: "set null",
    }),
    sourceMessageId: text("source_message_id"),
    // SM-2 SRS fields
    repetitions: integer("repetitions").default(0).notNull(),
    intervalDays: integer("interval_days").default(0).notNull(),
    easeFactor: real("ease_factor").default(2.5).notNull(),
    dueAt: timestamp("due_at").defaultNow().notNull(),
    lastReviewedAt: timestamp("last_reviewed_at"),
    lastAccuracy: integer("last_accuracy"),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at")
      .defaultNow()
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [
    uniqueIndex("vocab_user_lang_norm_idx").on(table.userId, table.language, table.normalizedPhrase),
    index("vocab_user_lang_due_idx").on(table.userId, table.language, table.dueAt),
  ],
);

export const vocabSprint = pgTable(
  "vocab_sprint",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    language: text("language").notNull(),
    isBonus: boolean("is_bonus").default(false).notNull(),
    durationSec: integer("duration_sec").notNull(),
    accuracyScore: integer("accuracy_score").notNull(),
    masteredCount: integer("mastered_count").notNull(),
    totalCount: integer("total_count").notNull(),
    xpEarned: integer("xp_earned").notNull(),
    coachNotes: jsonb("coach_notes").$type<CoachNoteItem[]>().default([]).notNull(),
    results: jsonb("results")
      .$type<Array<{ vocabId: string; phrase: string; passed: boolean; nextIntervalDays: number }>>()
      .default([])
      .notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [index("vocab_sprint_user_created_idx").on(table.userId, table.createdAt)],
);
```

### 5.2 HTTP Routes (`backend/src/routes/vocabulary.ts` & `conversations.ts`)

- **`GET /api/vocabulary?filter=due|learning|mastered&q=...`**
  - Scoped to `profile.targetLanguage`.
  - Returns `{ counts: { total, due, learning, mastered }, nextDueInDays: number | null, items: VocabItemDto[] }`.
- **`POST /api/vocabulary`**
  - Body: `{ phrase, translation?, contextCue?, sourceKind, sourceLabel, sourceConversationId?, sourceMessageId? }`.
  - Enriches missing fields via Gemini (`lib/tutor.ts`), upserts by `(userId, language, normalizedPhrase)`, returns `{ item, created: boolean }`.
- **`POST /api/vocabulary/batch`**
  - Body: `{ items: Array<{ phrase, translation, contextCue?, exampleTarget?, exampleNative?, sourceKind, sourceLabel, sourceConversationId? }> }`.
  - Used by `11.9 · Save from this call` to insert all checked phrases in one transaction; returns `{ savedCount, items }`.
- **`POST /api/conversations/:id/vocabulary-suggestions`** (scoped to `callId` / `peerCallId`)
  - Extracts `3–5` high-value phrases from an ended voice/video call or 1:1 peer meeting transcript & coaching tips (excluding already-saved `normalizedPhrase` entries), tagged with `reason` (`New phrase`, `Native tip`, `Used by Sofía`, `Your reply`) and `recommended: boolean` for `11.9`.
- **`POST /api/vocabulary/enrich`**
  - Preview helper for `11.13 · Add Phrase Sheet`: takes `{ phrase }`, returns `{ translation, exampleTarget, exampleNative, contextCue }` before final save.
- **`PATCH /api/vocabulary/:id`**
  - Updates user-edited fields (`phrase`, `translation`, `contextCue`, `exampleTarget`, `exampleNative`) from `11.14`.
- **`DELETE /api/vocabulary/:id`**
  - Removes item from deck (`11.14` Remove). Supports client `Undo` via re-POST.
- **`POST /api/vocabulary/sprint/session`**
  - Optional body: `{ vocabIds?: string[], bonus?: boolean }`. Mints a LiveKit token with `mode: "vocab_sprint"` and embedded `sprintItems[]`.
- **`POST /api/vocabulary/sprint/:sprintId/complete`**
  - Applies SM-2 updates, records `vocab_sprint`, awards XP, and returns evaluation (`11.5`) + updated counts (`11.6` / `11.7`).

---

## 6. LiveKit Voice Sprint Protocol (`voice-agent` ↔ `android`)

Reuses the existing `voice-agent/agent.py` LiveKit Data Channel architecture (`topic = "zuno.vocab_sprint"`), parallel to the `03g` lesson state machine:

| Event (`agent` → `android`) | Payload | Drives UI Screen |
| :--- | :--- | :--- |
| `sprint_prompt` | `{ index, total, vocabId, contextCue, recallPromptNative, wordLengths: number[], firstWordHint }` | `11.2 · Active Recall` (blank word-length tiles `_ _ _ _ _`) |
| `sprint_match` | `{ vocabId, accuracy, passed: true, nextIntervalDays, label }` | `11.3 · Match & Interval` (`94% match · +7d`, `Next phrase →`) |
| `sprint_shadow` | `{ vocabId, phrase, translation, accuracy, syllables, stressCue, chunks }` | `11.4 · Coach & Shadow` (syllable pills `es · CA · la` + chunk chips) |
| `sprint_complete` | `{ sprintId, durationSec, accuracyScore, masteredCount, totalCount, xpEarned, coachNotes, results }` | `11.5 · Sprint complete — Evaluation` |

Client actions (`android` → `agent`): `{"action": "first_word"}`, `{"action": "reveal"}`, `{"action": "next_phrase"}`, `{"action": "retry_shadow"}`.

---

## 7. Android Architecture & Localization Notes

- **Navigation (`ZunoApp.kt`):**
  - Replace `onVocabularyClick = { onComingSoon(...) }` in `HomeScreen.kt` (Explore Zuno grid) and `AppDrawer.kt` with `ZunoRoute.Vocabulary`.
  - Add `ZunoRoute.VocabularySprint(sprintId?, vocabIds?)` and `ZunoRoute.VocabularyEvaluation(sprintId)` (reusing `PortraitStageCard`, top-aligned `LazyColumn`, `ZunoToast`, and `LessonEvaluationScreen` stat/note/row primitives).
- **Optimistic UI & State (`ZunoViewModel.kt`):**
  - Maintain `StateFlow<Loadable<VocabularyLibrary>>` and a fast `savedPhrasesSet: StateFlow<Set<String>>` (normalized phrases for active language) so inline chat suggestions (`11.8`), end-of-chat review sheets (`11.9`), and lesson evaluation rows (`11.10`) reflect saved state instantaneously with `ZunoToast` + `Undo`.
- **Localization:** All labels, empty states, SRS interval labels (`Due now`, `%1$dd`, `Tomorrow`), and toast copy must be added to `strings_features.xml` across all 8 locales (`en`, `es`, `fr`, `de`, `it`, `pt-rBR`, `nl`, `pl`).
