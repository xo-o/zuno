# ZunoAI — Feature Functional Specifications

**Design Reference:** [ZunoAI-V2 — Mobile (`5:4`)](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=5-4)

These documents describe how every feature in ZunoAI works from a learner's point of view — screens, user flows, functional rules, and edge cases — written in plain, non-technical language.

---

## Feature Index

| # | Feature | Figma Section | Spec |
| :- | :--- | :--- | :--- |
| 01 | **Onboarding & Sign-In** | `01 · Onboarding` (`2544:20596`) | [01-onboarding.md](./01-onboarding.md) |
| 02 | **Home & Free Talk** | `02 · Home` (`2032:2367`) | [02-home-and-free-talk.md](./02-home-and-free-talk.md) |
| 03 | **Topics & Guided Lessons** | `03 · Topics` (`2677:27327`) | [03-topics.md](./03-topics.md) |
| 04 | **Roleplays** | `04 · Roleplays` (`2076:2686`) | [04-roleplays.md](./04-roleplays.md) |
| 05 | **Chats (AI Friends & People)** | `05 · Chats` (`2837:24615`) | [05-chats.md](./05-chats.md) |
| 06 | **Chat Actions** | `06 · Chat actions` (`2169:5406`) | [06-chat-actions.md](./06-chat-actions.md) |
| 07 | **Attachments & Chat Reminders** | `07 · Attachments` (`2109:2823`) | [07-attachments.md](./07-attachments.md) |
| 08 | **Scheduled Practice** | `08 · Scheduled` (`2117:3586`) | [08-scheduled-practice.md](./08-scheduled-practice.md) |
| 09 | **Settings & App Updates** | `09 · Settings` (`2148:4710`) | [09-settings.md](./09-settings.md) |
| 10 | **Account & Privacy** | `10 · Account` (`2017:1921`) | [10-account.md](./10-account.md) |
| 11 | **Paywall & Zuno Pro** | `11 · Paywall` (`2365:8835`) | [11-paywall.md](./11-paywall.md) |
| 12 | **Vocabulary & Voice Sprint** | `12 · Vocabulary` (`2462:9514`) | [12-vocabulary.md](./12-vocabulary.md) |
| 13 | **Progress** | `13 · Progress` (`2463:9797`) | [13-progress.md](./13-progress.md) |
| 14 | **Community & Partners** | `14 · Community & Partners` (`2464:10058`) | [14-community-and-partners.md](./14-community-and-partners.md) |
| 15 | **Live Rooms** | `15 · Live Rooms` (`2465:10229`) | [15-live-rooms.md](./15-live-rooms.md) |
| 16 | **Ongoing Call & System UI** | `16 · Ongoing Call & System` (`7173:11858`) | [16-ongoing-call-and-system.md](./16-ongoing-call-and-system.md) |

---

## Shared App Behaviors

### Navigation Structure
- **Home** is the main landing screen, centered around holding or tapping the **Horizon** glow to speak with Zuno, or tapping **Type instead** for text Free Talk.
- **Top Bar (Home):** Menu button on the left opens the **Sidebar Drawer**; streak counter and active language/level pill sit on the right.
- **Sidebar Drawer:** Houses the user's profile summary, **Notifications** bell, **Practice** destinations (`Free Talk`, `Topics`, `Roleplays`, `Vocabulary`, `Scheduled`), **Live & social** destinations (`Chats`, `Live rooms`, `Community & Partners`, `Progress`), **Recent sessions**, a **Zuno Pro** upgrade banner (when on Free), **Settings**, and **+ New chat**.

### Captions, Avatars & Video in Calls
Learners can toggle **Captions** on or off during live calls, and what appears on screen adapts to the call type and whether video is enabled:

| Call Type | Voice-Only (Camera Off) | Video Enabled (Camera On) |
| :--- | :--- | :--- |
| **Home Free Talk** | **Audio-only:** Captions Off shows the Horizon glow; Captions On shows the AI Tutor's **avatar** in the top bar. | **Video is disabled on Home.** Tapping the camera icon shows: *"Audio-only on Home · Video in Chats & Lessons"*. |
| **Chats — AI Friends** (`Sofía`, `Mateo`, etc.) | Shows the chosen **AI Friend's avatar** (top bar when Captions On; animated avatar/Horizon when Captions Off). | Shows the **AI Friend's animated, lip-synced avatar/video** paired with the **learner's live camera** (or learner's avatar if camera is off). Captions Off uses a full-screen 2-tile stack. |
| **Topics Lessons, Roleplays & Voice Sprints** | Shows the active **AI Tutor's avatar** in the top bar (Captions On) or **full-screen animated AI Tutor avatar** (Captions Off). | Shows the **AI Tutor's animated avatar/video** alongside the **learner's live camera** preview (or learner's avatar if camera is off). |
| **Chats — 1:1 People** | Shows **both participants' avatars** (top bar when Captions On; full-screen stacked 2-avatar cards when Captions Off). | Each person's tile independently shows **live camera video** when their camera is on, or their **avatar** when their camera is off. |
| **Live Rooms** (up to 4 members) | Shows up to 4 **member avatars** in the top bar (Captions On) or a **2×2 avatar grid** (Captions Off). | Each seat independently shows **live camera video** (or Zuno's animated avatar in Zuno-guided rooms) when camera is on, or the member's **avatar** when camera is off. |

- **Captions On:** Displays live transcript bubbles, instant **Translate** and **0.75×** slow replay chips, turn-status indicators (`Listening`, `Sofía speaking`, `Hearing you…`), and contextual coaching cards.
- **Captions Off (Avatar Only):** Hides transcript bubbles and coaching cards for pure listening, speaking, or video/avatar immersion.

### Bilingual Coaching Principle
- **Free Talk** defaults to the learner's own language so they can ask questions, explore grammar, or check pronunciation freely, switching into the target language whenever they practice or request it (unless **Teach in target language** is enabled in Preferences).
- **Topics** blend explanations in the learner's language with structured target-language speaking drills.
- **Roleplays** stay in the target language 90%+ of the time in character, offering brief explanations in the learner's language only when the learner asks for help.

### Language-Scoped Progress
- Switching the learning language in Settings preserves all chats, topics, roleplays, vocabulary decks, and streaks in each previous language so learners can switch back anytime without losing progress.
