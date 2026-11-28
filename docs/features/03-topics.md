# 03 · Topics & Guided Lessons

**Figma Section:** [`03 · Topics`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2677-27327) (`2677:27327`)

---

## 1. Overview

Topics organize structured speaking practice into **5-lesson mini-courses** (~5 minutes per lesson). Learners keep a clean personal library of their own or saved community topics, create custom topics through a natural chat with Zuno (optionally uploading PDFs or photos mid-chat), browse Zuno Official and community-shared topics, and complete interactive 5-stage voice lessons.

---

## 2. User Flow

```text
Topics List (01) or Empty (06)
  ├── Tap "+" ─► Add Choice Sheet (07)
  │                ├── Create a new topic ─► Chat Builder Greeting (10a) ─► Draft Ready (10b)
  │                │                            ├── Tap card ─► Draft Details Modal (19)
  │                │                            └── Create topic ─► Topic Settings (10c) ─► Start Lesson 1
  │                └── Find community topics ─► Browse Community (08) ─► Preview (09)
  │                                               ├── + Add to my Topics ─► Added (16)
  │                                               ├── ⋮ Overflow (15) ─► Report (17) ─► Report Sent (18)
  │                                               └── No search results (12) ─► + Create "<query>"
  └── Tap a Topic ─► Topic Detail (02)
                       ├── ⋮ Menu ─► Share & Visibility (11) / Overflow (13 Owner, 14 Saved)
                       └── Continue / Tap Lesson ─► 5-Stage Voice Lesson:
                             03a Teach & Model ─► 03b Shadow ─► 03c Build ─► 03d Active Recall ─► 03e/03f Mini-Roleplay
                             (or 03g No Captions Avatar Only)
                                  │
                                  ▼
                       04 Lesson Complete — Evaluation (save phrases to Vocabulary) ─► 05 Topic Updated
```

---

## 3. Screens & States

### Library, Discovery & Creation
| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **01 · Topics — List** | `2677:27365` | Personal list of created and saved topics only, with progress and `Public` indicator | Open topic, search, tap `+` |
| **06 · Topics — Empty** | `2998:12928` | Empty state (*"No topics yet"*) with centered **Explore** CTA and `+` in top bar | Tap **Explore** or `+` |
| **07 · Add Choice Sheet** | `3001:13792` | Bottom sheet with **Create a new topic** and **Find community topics** | Choose create or browse |
| **08 · Browse Community** | `3092:13920` | `Explore topics` search, category tabs (`All`, `Travel`, `Food`, `Work`, `Daily life`), **Zuno official** & **Community topics** | Search, filter by category, open preview |
| **12 · Browse Empty** | `3092:14013` | *"No topics for “astrophysics”"* empty state | Tap **Create “<query>”** |
| **09 / 15 / 16 · Community Preview** | `2998:13198` / `7210:11885` / `7210:11928` | Creator handle, learner count, 5-lesson syllabus; `⋮` overflow (Add, Share, Copy link, Report); card flips to **Start** + toast when added (`16`) | Add to my Topics, Start, Share, Report |
| **17 / 18 · Report Topic** | `7210:11967` / `7210:12048` | Reason picker + optional description; `18` closes sheet with confirmation toast | Submit report |
| **10a · Chat Builder (Greeting)** | `3007:13787` | Zuno greets first with starter topic chips and `+` attachment composer | Chat, tap chip, or attach PDF/photo |
| **10b · Chat Builder (Draft Ready)** | `3005:13754` | Inline **DRAFT TOPIC · 5 LESSONS** card (*Madrid Trip Essentials*) | **Refine**, inspect **Details**, or **Create topic** |
| **19 · Draft Details Modal** | `7226:12547` | Full title, summary description, and numbered 5-lesson syllabus before creating | Review lessons, **Create topic** |
| **10c · Topic Settings** | `3007:13865` | Post-creation visibility (`Only me` vs. `Public`), share link (`heyzuno.app/t/...`), and start CTA | Set visibility, share/copy link, **Start Lesson 1** |
| **02 / 05 · Topic Detail** | `2677:27375` / `2677:27421` | Progress header, current lesson CTA, full 5-lesson syllabus (`05` advances to next lesson after completion) | Continue lesson, tap any lesson, open `⋮` menu |
| **11 / 13 / 14 · Share & Overflow** | `2998:13327` / `3018:13926` / `3018:13969` | Visibility & share sheet (`11`); Owner overflow (`13`: Start next, Schedule, Pin, Share when public, Settings, Delete); Saved overflow (`14`: includes Report & Remove, no Settings) | Manage, schedule, share, report, or remove |

### Interactive Voice Lesson & Evaluation
| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **03a · Teach & Model** | `2677:27447` | Zuno introduces key phrase with syllable breakdown (`au · to · BÚS`) and meaning | Listen, tap slow/replay |
| **03b · Shadow & Pronunciation** | `2677:27468` | Pronunciation drill highlighting stressed syllable (*Stress “-BÚS”*) and practice chunks | Repeat aloud |
| **03c · Building Blocks** | `2677:27492` | Pattern substitution card (*la estación · train station* + stem hint *¿Este autobús…?*) | Speak combined phrase |
| **03d · Active Recall** | `2677:27518` | Target phrase hidden as blank tiles (`_ _ _ _ _`); learner recalls from situational cue | Speak from memory or use rescue hint |
| **03e / 03f · Mini-Roleplay** | `2677:27548` / `2677:27574` | Short in-character exchange with goal tracker (`Goal 1`, `Goal 2`) and rescue chips | Complete roleplay goals |
| **03g · Voice — No Captions** | `7352:15550` | Full-screen **animated AI Tutor avatar** (matching the selected tutor) with transcripts hidden; if video is enabled, shows the learner's self-camera preview overlay | Pure listening & speaking; toggle captions or camera anytime |
| **04 · Lesson Complete — Evaluation** | `2677:27395` | Completion summary (~5 min), score, helpful recasts, and **Practiced phrases** with 1-tap bookmarking (**Save** / **Save all**) | Save phrases to Vocabulary, continue |

---

## 4. How It Works

- **Avatars & Video in Voice Lessons:**
  - **Voice-Only (Camera Off):** Displays the active **AI Tutor's avatar** in the call top bar when Captions are On (`03a`–`03f`), and the **full-screen animated AI Tutor avatar** when Captions are Off (`03g`).
  - **Video Enabled (Camera On):** Keeps the **AI Tutor's animated, lip-synced avatar/video** active and adds the **learner's live camera** preview (falling back to the learner's avatar if camera permission/feed is unavailable).
- **Personal list stays uncluttered:** The root Topics list shows only topics the learner created or explicitly saved from Community/Official explore.
- **No upfront upload wall:** When creating a topic, Zuno starts a chat right away; learners can describe what they want in plain text or attach itineraries, notes, or PDFs at any turn mid-conversation.
- **Structured 5-stage learning:** Every lesson guides the learner naturally from hearing & shadowing phrases (`03a`–`03b`), swapping words (`03c`), recalling from memory (`03d`), to using them in a quick mini-roleplay (`03e`–`03f`).
- **One-tap Vocabulary capture:** On the Lesson Evaluation screen (`04`), learners can bookmark individual phrases or tap **Save all** to add them straight to their Vocabulary SRS deck.

---

## 5. Edge Cases & Empty / Error States

- **Empty library (`06`):** Replaces search with a clear empty state and **Explore** CTA; `+` remains in the top bar.
- **No community search match (`12`):** Offers a 1-tap **Create “<query>”** button that opens the Chat Builder pre-seeded with the user's search term.
- **Saved vs. Owned menus (`13` vs. `14`):** Saved community topics cannot have their settings edited and show **Report** + **Remove** instead of **Topic settings** + **Delete**.
- **Private topic sharing:** **Share** is hidden from the owner overflow menu while visibility is set to `Only me`.
