# 09 · Settings & App Updates

**Figma Section:** [`09 · Settings`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2148-4710) (`2148:4710`)

---

## 1. Overview

Settings is the control center for the learner's tutoring experience, languages, notification preferences, app updates, support, and sign-out. Switching learning languages saves progress separately per language so learners can explore multiple languages safely.

---

## 2. User Flow

```text
Settings Overview (1 / 1a Update Badge / 2 Scrolled Header)
  ├── Profile / Account ──────► Account (Section 10)
  ├── Zuno Pro Banner ────────► Paywall (Section 11)
  ├── Learning (5) ───────────► Language Picker (5a / 5b Search) ─► Switch Confirm (5c) ─► Level Check Prompt (5d)
  ├── AI tutor (3a) ──────────► Pick from 6 tutors + listen to voice preview
  ├── App language (4) ───────► Single-select UI language
  ├── Preferences (3) ────────► Teaching style, Strict mode, Hands-free, Suggest what to say, Show translations, Teach in target
  ├── Notifications (7) ──────► Daily reminder, Scheduled sessions, Updates, Email digests
  ├── About (10 / 10d) ───────► Version status, Update check, What's New (10a), Legal links
  │                               └── Optional Update Sheet (10b) / Required Update Dialog (10c)
  ├── Contact support ────────► Support form / contact channel
  └── Sign out (8) ───────────► Confirm ─► Signed out to Sign In + toast (9)
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 / 1a / 2 · Overview** | `2148:4711` / `7424:13621` / `2148:4940` | Profile header (collapses to compact bar on scroll `2`), Pro upgrade banner, grouped settings rows, version footer; `1a` highlights **v2.5.0 available** on **About** | Navigate to any settings screen, upgrade, sign out |
| **3 · Preferences** | `2151:5353` | **AI tutor**, **Teaching style** (*Balanced*), **Strict mode**, **Hands-free mode**, **Suggest what to say**, **Show translations**, **Teach in [Language]** | Toggle coaching behaviors |
| **3a · AI Tutor** | `7167:12110` | 6 tutors (*Sofía*, *Valeria*, *Claire*, *Mateo*, *Lucas*, *Ethan*) with personality tag and voice preview button | Preview natural voice and select tutor |
| **4 · App language** | `2153:5078` | Single-select list (*English*, *Español*, *Français*, *Deutsch*, *Italiano*, *Português*, *Nederlands*, *Polski*) | Change interface language |
| **5 · Learning** | `2153:5222` | **Language**, **Accent** (*Latin America*, etc.), **Your level** (`A1`–`C2` picker + **Retake level check**), **Goals** (*Daily goal*, *Focus*, *Interests*) | Adjust language, accent, level, or goals |
| **5a / 5b · Learning language** | `2177:6626` / `2177:8045` | Current language at top + searchable list of 35+ languages | Search and pick a language |
| **5c · Switch language** | `2177:8316` | Confirmation dialog explaining previous progress stays saved and a 5-min chat finds the new level | **Cancel** or **Switch** |
| **5d · Learning — Switched** | `2177:9075` | Updated Learning screen (e.g., *Japanese · Standard (Tokyo)*) prompting **Find my level** + confirmation toast | Start 5-min level check or adjust goals |
| **6 · Account** | `2153:5368` | Entry view to Account settings (see `10 · Account`) | Edit profile, privacy, subscription, data |
| **7 · Notifications** | `2153:5545` | **Practice** (*Daily reminder* + *Reminder time*, *Scheduled sessions*), **Updates** (*Progress & milestones*, *New topics & roleplays*), **Email** (*Weekly summary*, *Product news*) | Toggle push and email preferences |
| **8 / 9 · Sign out** | `2177:9199` / `2177:9267` | Confirm dialog reassuring user their progress stays saved (`8`), returning to Sign In with a toast (`9`) | **Cancel** or **Sign out** |
| **10 / 10d · About** | `7424:13733` / `7435:14587` | App version, live update status (*v2.5.0 available* + **Update** vs. *You're up to date*), **What's New**, **Rate ZunoAI**, **Terms**, **Privacy Policy**, **Open-source licenses** | Update app, view release notes, check for updates |
| **10a · What's New** | `7424:13848` | Chronological release notes grouped by version with **New**, **Improved**, and **Fixed** highlights | Read changes or tap **Update** |
| **10b · Update Sheet (Optional)** | `7424:13917` | Bottom sheet highlighting new features with **Not now** and **Update now** | Update or dismiss |
| **10c · Required Update Dialog** | `7424:13991` | Blocking dialog when an app version is too old to continue safely | Tap **Update now** |

---

## 4. How It Works

- **Unified tutor voice (`3a`):** Picking one of the 6 AI tutors changes the default tutor identity and voice across languages, with a 1-tap voice preview directly in the picker.
- **Language-isolated progress (`5c`–`5d`):** Switching from Spanish to Japanese keeps all Spanish vocabulary, chats, topics, and streaks intact so switching back later restores everything immediately.
- **Coaching toggles (`3`):**
  - **Strict mode:** Direct, blunt corrections instead of gentle recasting.
  - **Hands-free mode:** Speak without tapping the mic button.
  - **Teach in [Target Language]:** Forces explanations in the target language instead of the learner's native language.
- **App update tiers (`10`–`10c`):** Optional updates show a non-blocking badge and dismissible sheet (`10b`), while unsupported older builds show a blocking dialog (`10c`) requiring an update before continuing.

---

## 5. Edge Cases & Empty / Error States

- **No search match in languages (`5b`):** Shows an empty search state prompting the user to clear or refine their query.
- **Up-to-date check (`10d`):** Displays *"You're up to date"* cleanly with an option to re-check.
- **Cancelled language switch (`5c`):** Dismissing the dialog keeps the current language and accent untouched.
- **Signed out (`9`):** Clears the device session cleanly without deleting server-saved learning data.
