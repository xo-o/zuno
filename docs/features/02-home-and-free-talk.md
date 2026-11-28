# 02 · Home & Free Talk

**Figma Section:** [`02 · Home`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2032-2367) (`2032:2367`)

---

## 1. Overview

Home is the learner's starting point every time they open Zuno. It centers on a calm, voice-first **Horizon** glow for instant **Free Talk**—an open, relaxed conversation with Zuno in the learner's own language or target language—plus quick access to the sidebar menu, notifications, streaks, and recent sessions.

---

## 2. User Flow

```text
Home (1 · Active)
  ├── Hold or tap Horizon glow
  │     ├── Mic not allowed ─► Mic Permission Dialog (8) ─► Allow / Not now
  │     └── Mic allowed ─────► Connecting (6b) ─► Listening (6a) ─► Speaking (6 No Captions / 7 Captions)
  │                                                                  └── Tap disabled Camera ─► Audio-only Hint (6c)
  ├── Tap "Type instead" ────► Free Talk Text Chat (4)
  ├── Tap Menu (top-left) ───► Sidebar Drawer (5)
  │                              ├── Tap Bell ────────► Notifications (5a / 5b Empty)
  │                              ├── Practice / Social ► Topics, Roleplays, Vocab, Scheduled, Chats, Live Rooms, Community, Progress
  │                              ├── Recent sessions ──► Reopen past chat / roleplay / topic
  │                              └── Upgrade / Settings / + New chat
  ├── Tap Streak pill ───────► Progress
  └── Tap Language pill ─────► Learning Language settings
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **Home · 1 · Active** | `7365:20293` | Top bar (menu, streak pill, language/level pill), animated Horizon glow, greeting (*"Hola Noah, ¿qué practicamos hoy?"*), *"Hold the glow to talk"*, *"Type instead"* | Hold or tap glow to talk, tap **Type instead**, open Menu, tap Streak or Language pill |
| **Home · 4 · Send → Free Talk** | `2054:3142` | Active text chat showing learner turn, feedback pill (*"Natural Spanish · Native tip…"*), Zuno reply with translation, **Listen** & **Explain grammar** chips, text/voice composer | Reply by text, switch to voice, listen, explain grammar, attach files |
| **Home · 5 · Sidebar Drawer** | `3077:14390` | Profile card + notification bell; **Practice** (`Free Talk`, `Topics`, `Roleplays`, `Vocabulary`, `Scheduled`); **Live & social** (`Chats`, `Live rooms`, `Community & Partners`, `Progress`); **Recent sessions**; **Zuno Pro** banner; **Settings** & **New chat** | Jump to any destination, resume a recent session, open notifications, start a new chat |
| **Home · 5a · Notifications** | `7424:14044` | Filter pills (`All`, `Messages`, `Updates`, `Announcements`), grouped by **Today** and **Earlier** (app updates with **Update** button, peer messages, live room invites, announcements, streak milestones) | Filter notifications, tap to jump to update/chat/room, open notification settings |
| **Home · 5b · Notifications — Empty** | `7424:14139` | Empty state explaining what will appear here | Tap **Notification settings** or switch filters |
| **Home · 6b · Connecting** | `11015:14576` | Full-screen Horizon glow with *"Connecting…"* while starting a live voice call | Wait briefly or end call |
| **Home · 6a · Listening** | `11015:14525` | Full-screen glow in **No captions** mode showing *"Listening…"* | Speak naturally |
| **Home · 6 · Speaking (No captions)** | `7383:13309` | Full-screen glow showing *"Speak anytime to interrupt"* | Interrupt by speaking, toggle captions on, mute, or end call |
| **Home · 6c · Tap Video → Audio-only Hint** | `11068:13511` | Disabled camera icon in call controls; tapping it shows hint pill *"Audio-only on Home · Video in Chats & Lessons"* | Continue audio call or switch to Chats / Lessons for video |
| **Home · 7 · Speaking (Captions)** | `2738:13222` | Live transcript view showing AI Tutor's **avatar** + *"Free talk · 00:42"*, spoken bubbles, *"Sofía speaking · Speak to interrupt"*, and audio-only call controls | Read captions, translate/replay turns, mute, toggle speaker/captions, end call |
| **Home · 8 · Mic Blocked → Dialog** | `2044:2943` | Modal prompt: *"Allow microphone access?"* explaining Zuno needs the mic to hear and correct pronunciation | **Allow** or **Not now** |

---

## 4. How It Works

- **Audio-only on Home (`6c`):** Live voice calls launched directly from Home are strictly **audio-only**—video/camera is disabled. Tapping the disabled camera button surfaces the hint *"Audio-only on Home · Video in Chats & Lessons"*.
  - With **Captions Off (`6`, `6a`, `6b`)**, the screen displays the animated Horizon glow (*Connecting…*, *Listening…*, *Speak anytime to interrupt*).
  - With **Captions On (`7`)**, the call header displays the active **AI Tutor's avatar** alongside live conversation captions and coaching chips.
- **Hold-to-talk & Tap-to-talk:**
  - Holding the Horizon glow captures speech immediately and lets the learner slide up to cancel before releasing.
  - A quick tap opens a live two-way Free Talk voice call (`6b` → `6a` → `6` / `7`).
- **Free Talk coaching style:**
  - Starts in the learner's own language (unless **Teach in target language** is enabled) so beginners and intermediate learners can ask *"How do I say…?"* or *"How do you pronounce…?"* comfortably.
  - Switches smoothly into the target language whenever the learner speaks or writes in it.
  - Only shows grammar/phrasing feedback pills (`4`) when the learner actually writes or attempts the target language—never nitpicking questions asked in their native language.
- **Pronunciation help:** When asked how to pronounce a word, Zuno explains the mouth/stress tip clearly, speaks the word naturally, and repeats it slowly.
- **Live badges in Sidebar:** The drawer displays real-time counts at a glance (e.g., `14 due` on Vocabulary, next time on Scheduled, `2 unread` on Chats, `2 active` on Live rooms, `12 online` on Community, `#5` on Progress, and an unread dot on the Notifications bell).

---

## 5. Edge Cases & Empty / Error States

- **Microphone denied (`Home · 8`):** Tapping or holding the glow without mic permission opens the friendly permission dialog; choosing **Not now** keeps the user on Home where **Type instead** still works.
- **Slide-up cancel:** Dragging upward while holding the glow switches the prompt to *"Release to cancel"* and discards the recording on release.
- **No notifications (`Home · 5b`):** Displays a clean caught-up message with a **Notification settings** shortcut.
- **Ongoing call system notification:** During an active call, ongoing call controls (`Mute`, `Speaker`, `End`) live exclusively in the device's system notification drawer and lock screen—not as an in-app widget on Home or other pages. Tapping the top-left Back arrow during a Home voice call ends the call.
