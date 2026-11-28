# 12 · Vocabulary & Voice Sprint

**Figma Section:** [`12 · Vocabulary`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2462-9514) (`2462:9514`)

---

## 1. Overview

Vocabulary builds a personal deck directly from real practice—inline suggestions during AI chats, post-call phrase reviews, completed lessons, and optional manual entry—rather than forcing manual flashcards. Learners review due phrases in a **2-minute Voice Sprint** with Zuno using spaced repetition.

---

## 2. User Flow

```text
Capture Entry Points:
  ├── Chat Glossary Save (11.8) ───────► 1-tap bookmark under AI message
  ├── Post-Call / Meeting Review (11.9)► Sheet of 3–5 transcript phrases when a call ends
  ├── Lesson Evaluation Save (11.10) ──► 1-tap bookmark per row or "Save all"
  └── Search Miss / "+" (11.12 ─► 11.13) ► Manual add sheet (Zuno auto-fills meaning & cue)
         │
         ▼
Vocabulary Library
  ├── 0 saved ─► Empty Library (11.11) — Ways to build your deck
  └── ≥ 1 saved ─► SRS Library (11.1) — Due now | Learning | Mastered + Detail Sheet (11.14)
                     ├── Due == 0 ─► All Caught Up (11.7) + "Run bonus sprint"
                     └── Tap "Start 2-min Voice Sprint" ─► Active Recall (11.2) (or 11.15 No Captions)
                                                             ├── Strong match ─► Match & Interval (11.3)
                                                             └── Miss / Stress ─► Coach & Shadow (11.4)
                                                                                     │
                                                                                     ▼ (All 5 complete)
                                                                           Sprint Complete Evaluation (11.5)
                                                                                     │
                                                                                     ▼
                                                                           Library Updated (11.6 / 11.7)
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **11.1 · SRS Library** | `2462:10079` | Saved count, due count, **Start 2-min Voice Sprint** CTA, filter chips (**Due now**, **Learning**, **Mastered**), phrase rows with interval badge & audio | Filter, play audio, open phrase detail, start Voice Sprint, search, add `+` |
| **11.2 · Sprint — Active Recall** | `2733:12889` | Voice call UI with English/native recall cue and hidden target phrase (`_ _ _ _ _`), rescue chips (**First word**, **Reveal**) | Speak target phrase from memory or ask for hint |
| **11.3 · Sprint — Match & Interval** | `2733:12957` | Match accuracy badge (*94% match · +7d*), **Listen** / **0.75×** replay chips, green confirmation, **Next phrase →** | Advance to next phrase |
| **11.4 · Sprint — Coach & Shadow** | `2733:13086` | Retry feedback (*Stress “-CA-” · Reset to due tomorrow*), syllable stress pills (`es` · `CA` · `la`), and practice chunks | Shadow slowly and retry |
| **11.5 · Sprint Complete — Evaluation** | `2733:13196` | Accuracy %, mastered count, +XP, coach recasts, and reviewed phrases with updated intervals | Practice remaining due phrases or return to Vocabulary |
| **11.6 · Vocabulary — Updated** | `2733:13349` | Library reflecting decreased due count and newly advanced intervals (`7d`, `14d`) | Continue reviewing or browse |
| **11.7 · Vocabulary — All Caught Up** | `2746:13274` | *"All caught up for today"* hero with next due date and **Run bonus sprint** secondary CTA | Run bonus sprint or browse Learning/Mastered |
| **11.8 · Capture — Chat Glossary** | `2983:12340` | Inline **SUGGESTED FROM CHAT · TAP TO SAVE** chips (`para llevar`, `recién horneado`) + toast with **Undo** | Play audio, 1-tap **Save** |
| **11.9 · Capture — Post-Call Phrases** | `2990:12861` | Bottom sheet after a voice/video call ends listing 3–5 curated transcript phrases with tags (*New phrase*, *Native tip*, *Used by Sofía*, *Your reply*) | Toggle checkboxes, preview audio, **Save N phrases** or **Skip for now** |
| **11.10 · Capture — Lesson Evaluation** | `2983:12494` | Lesson Evaluation rows with 1-tap bookmark toggle and **Save all** header action + **Undo** toast | Bookmark phrases from finished lesson |
| **11.11 · Empty Library** | `2985:12530` | *"No saved phrases yet"* + **Ways to build your deck** (*Chat with Zuno*, *Complete a lesson*, *Add a phrase manually*) | Jump to chat, lesson, or manual add |
| **11.12 · Search & Quick Add** | `2985:12634` | Live filter; when unmatched (*"sobremesa"*), shows **+ Add “sobremesa”** CTA | Search or tap quick-add |
| **11.13 · Add Phrase Sheet** | `2985:12721` | Manual entry with target phrase, auto-filled meaning (with stress & recall cue), and optional context note | Save to Vocabulary |
| **11.14 · Phrase Detail Sheet** | `2985:12783` | Phrase, meaning, status/streak, source chip (*Café roleplay*), **Listen** / **0.75×**, **EXAMPLE IN CONTEXT**, **Practice with Sofía**, **Edit**, **Remove** | Hear slow/normal audio, practice 1-on-1, edit, or delete |
| **11.15 · Sprint — No Captions** | `7352:15637` | Full-screen **animated AI Tutor avatar** mode during a Voice Sprint (with optional learner camera preview if video is enabled) | Practice purely by ear; toggle captions or camera back on |

---

## 4. How It Works

- **Avatars & Video during Voice Sprints:**
  - **Voice-Only (Camera Off):** Shows the active **AI Tutor's avatar** in the call top bar when Captions are On (`11.2`–`11.4`), and the **full-screen animated AI Tutor avatar** when Captions are Off (`11.15`).
  - **Video Enabled (Camera On):** Keeps the **AI Tutor's animated avatar/video** visible alongside the learner's live camera preview (falling back to the learner's avatar if camera is off).
- **Capture fits session boundaries:**
  - Ongoing text chats use non-intrusive inline chips (`11.8`)—never annoying popups when backing out of a chat.
  - Bounded voice/video calls and 1:1 meetings trigger a curated post-call review sheet (`11.9`) once the call ends.
  - Completed lessons offer 1-tap bookmarking and **Save all** (`11.10`).
- **Spaced repetition scheduling:**
  - Recalling a phrase cleanly without revealing it pushes the next review out (`3d` → `7d` → `14d` → longer).
  - Using **Reveal** or missing pronunciation/stress triggers a quick shadow drill (`11.4`) and resets the phrase to **Due tomorrow** (`1d`).
- **Language-scoped & duplicate-safe:** Each learning language maintains its own Vocabulary deck, and saving the same phrase twice updates context rather than creating duplicates.
- **Survives chat deletion:** Deleting a conversation never deletes words saved from it to Vocabulary.

---

## 5. Edge Cases & Empty / Error States

- **All caught up (`11.7`):** Shows `Zero phrases due` under the **Due now** filter while still letting the learner run an optional bonus sprint or browse **Learning** / **Mastered**.
- **Search miss (`11.12`):** Converts any missing search query directly into a prefilled **+ Add “<query>”** action.
- **Undo accidental save (`11.8`, `11.10`):** Saving shows a brief toast with **Undo** to revert in one tap.
