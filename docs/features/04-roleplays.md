# 04 · Roleplays

**Figma Section:** [`04 · Roleplays`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2076-2686) (`2076:2686`)

---

## 1. Overview

Roleplays are open-ended, real-world speaking simulations where the learner steps into a role (e.g., *Customer*, *Traveler*, *Candidate*) and Zuno plays the counterpart (*Barista*, *Airline Agent*, *Hiring Lead*). Learners can create custom scenarios via chat (including uploading job descriptions or context files), explore community roleplays, and practice live in target-language immersion.

---

## 2. User Flow

```text
Roleplays List (2) or Empty (1)
  ├── Tap "+" ─► Add Choice Sheet (4)
  │                ├── Create a new roleplay ─► Chat Builder Greeting (3a) ─► Draft Ready (3b)
  │                │                               ├── Tap card ─► Draft Details Modal (16)
  │                │                               └── Create roleplay ─► Roleplay Settings (3c) ─► Start live roleplay
  │                └── Find community roleplays ─► Browse Community (5) ─► Preview (6)
  │                                                  ├── Start live roleplay / Save
  │                                                  ├── ⋮ Overflow (12) ─► Added (13) / Report (14 ─► 15)
  │                                                  └── No search results (9) ─► + Create "<query>"
  └── Tap a Roleplay
        ├── Tap row ─► Live Voice Roleplay (in-character practice + 3 mission goals)
        └── Tap ⋮ ──► Overflow: Owner Public (7) | Owner Private (11) | Saved Community (10)
                        ├── Share & Visibility Sheet (8)
                        └── Roleplay Settings (17) — edit title, roles, visibility, share & delete
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 · Empty** | `2065:2693` | Empty state (*"No roleplays yet"*) with centered **Explore** button and `+` in top bar | Tap **Explore** or `+` |
| **2 · List** | `2026:2322` | Personal list of created and saved roleplays with role pairs and `Public` badge where applicable | Tap to start live roleplay, search, tap `+` or `⋮` |
| **3a · Chat Builder (Greeting)** | `3008:13872` | Zuno asks what scene to practice and offers quick chips (*Startup interview*, *Renting a flat*, *Doctor's visit*) + `+` attach | Chat, pick chip, attach PDF/image |
| **3b · Chat Builder (Draft Ready)** | `3005:13865` | Inline **DRAFT ROLEPLAY** card showing title and role pair (*You: Candidate · Zuno: Hiring Lead*) | **Refine**, inspect **Details**, or **Create roleplay** |
| **16 · Draft Details Modal** | `7226:12635` | Source file/language summary, **Your role**, **ZunoAI's role**, and full **CONTEXT** preview before creating | Review roles & context, **Create roleplay** |
| **3c · Roleplay Settings (Post-Creation)** | `3008:13950` | Category badge, summary, `Only me` vs. `Public`, share link (`heyzuno.app/r/...`), and **Start live roleplay** CTA | Set visibility, share/copy link, start live |
| **4 · Add Choice Sheet** | `3001:13982` | Bottom drawer: **Create a new roleplay** or **Find community roleplays** | Pick create or browse |
| **5 · Browse Community** | `3092:13754` | Search + category tabs (`All`, `Travel`, `Food`, `Work`, `Housing`), **Zuno official** & **Community scenarios** | Search, filter, open preview |
| **9 · Browse Empty** | `3092:13864` | *"No scenarios for “space station”"* empty state | Tap **Create “<query>”** |
| **6 / 12 / 13 · Community Preview** | `2999:13467` / `7210:12308` / `7210:12358` | Author, language, **SCENARIO**, **Your role**, **Zuno's role**; `⋮` overflow (`12`: Add, Share, Copy link, Report); card updates to **Start** + toast when saved (`13`) | **Start live roleplay**, **Save**, **Share**, **Report** |
| **14 / 15 · Report Roleplay** | `7210:12397` / `7210:12483` | Reason selection + optional details; submits and shows confirmation toast | Submit report |
| **7 / 10 / 11 · Overflow Menus** | `2999:13573` / `3018:14187` / `3018:14299` | Owner Public (`7`: includes Share); Owner Private (`11`: Share hidden); Saved Community (`10`: includes Report & Remove, no Settings) | Start over, schedule, pin, share, settings, report, delete/remove |
| **8 · Share & Visibility** | `2999:13612` | Toggle `Only me` vs. `Public to community`, view practice count, **Share** & **Copy link** | Change visibility or share link |
| **17 · Roleplay Settings (Edit)** | `7249:12833` | Full settings screen for an existing roleplay | Edit title & roles, change visibility, share link, delete roleplay |

---

## 4. How It Works

- **Avatars & Video during Live Roleplays:**
  - **Voice-Only (Camera Off):** Displays the active **AI Tutor's avatar** in the call top bar when Captions are On, and the **AI Tutor's animated avatar** (or Horizon presence) when Captions are Off.
  - **Video Enabled (Camera On):** Shows the **AI Tutor's live animated avatar/video** paired with the **learner's live camera** (falling back to the learner's **avatar** if the camera is off or unavailable).
- **Immersive target-language default:** During a live roleplay, Zuno stays in character and speaks the target language **90%+ of the time**. If the learner gets stuck and asks a question in their native language, Zuno gives a 1-sentence aside, models the natural target phrase, and smoothly returns to the scene.
- **Gentle implicit recasting:** Instead of stopping the conversation to lecture on grammar, Zuno naturally uses the idiomatic phrasing in its reply and logs a subtle **Native tip** for review.
- **3-goal mission + open practice:** Each roleplay sets 3 progressive scenario goals with sample phrases. Completing all 3 marks the mission complete without cutting off the call—learners can keep practicing as long as they like.
- **Roleplay Settings (`17`):** Owners can edit the title and role descriptions, switch visibility between `Only me` and `Public to community`, copy/share the link, or delete the roleplay.

---

## 5. Edge Cases & Empty / Error States

- **Empty personal list (`1`):** Shows **No roleplays yet** and a direct **Explore** CTA.
- **No community match (`9`):** Offers **+ Create “<query>”** to turn the unmatched search directly into a custom chat-built scenario.
- **Private vs. Public overflow (`7` vs. `11`):** **Share** is hidden when a roleplay is set to `Only me`, and only becomes available after switching to `Public to community`.
- **Saved community roleplays (`10`):** Learners cannot edit someone else's roleplay settings; instead they can **Save**, **Pin**, **Schedule**, **Report**, or **Remove** it from their list.
