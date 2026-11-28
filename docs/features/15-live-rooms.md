# 15 · Live Rooms

**Figma Section:** [`15 · Live Rooms`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2465-10229) (`2465:10229`)

---

## 1. Overview

Live Rooms are small-group live speaking sessions for **up to 4 members** (max **10 minutes** per room). Learners can drop into **Zuno-guided system rooms** with live conversation prompts, join **public** learner rooms, join **private** rooms via invite code (`ZUNO-482`), or host their own room with full audio/video and caption controls.

---

## 2. User Flow

```text
Live Rooms Browse (1) — Tabs: All | Zuno | Public | Code
  ├── Tap System-Programmed Room ─► System Room Detail (6) ─► Join live room ─► Active Room (7 / 9)
  ├── Tap "Code" tab ─► Private Code Sheet (5)
  │                       ├── Enter code (e.g., ZUNO-482) ─► Join ─► Lobby (4) or Active Room (7)
  │                       └── Create a new private room ─► Create Room (2)
  └── Tap "+" ─► Create Room (2): Topic, Language, Level, Max 2–4 members, Public/Private
                    └── Create room ─► Creator Lobby (3) ─► Invite / Start room now
                                           │                  │
                                           │                  ▼
                            (Guest joins) ─┴─► Participant Lobby (4) ─► Active Room (7 Captions / 9 No Captions)
                                                                           └── Host Controls (8) ─► End room
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 · Browse** | `3091:14069` | Tabs (**All**, **Zuno**, **Public**, **Code**), Search, `+` Create; **Live now · Featured** Zuno room card (`3/4 seats`, avatar stack, **Join live room**); **More rooms** list | Filter, search, join featured/list room, create room |
| **2 · Create** | `2703:12485` | Topic input, **Language**, **Language level**, **Max members** (up to 4, 10 min max), **Visibility** (**Public** vs. **Private**), **Create room** CTA | Configure and create room |
| **3 · Creator Lobby** | `2703:12625` | Status (*Waiting to start*), visibility & language badges, **Members (X of 4)** with **Host** / **Ready** badges, open seat invite row, **Start room now**, **Copy invite link** | Invite partners, copy link/code, **Start room now** |
| **4 · Participant Lobby** | `2703:12741` | Status (*Waiting for host*), host name (*"Hosted by Lucía M. · Starts when host is ready"*), members list, **Leave lobby** | Wait for host to start or leave |
| **5 · Private Code** | `2704:12642` | Bottom sheet (*Join private room*) with code input (`ZUNO-482`), **Join room** CTA, and **Create a new private room** shortcut | Join by code or create private room |
| **6 · System Room Detail** | `2704:12733` | **SYSTEM PROGRAMMED** badge, description, key stats (**10 min**, **4 max**, **Public**), **What you'll discuss** prompt checklist, joined avatars, **Join live room** CTA | Review prompts, share, join room |
| **7 · Active Room** | `2704:12814` | Top bar with member avatar stack & timer (`Live room · 04:18`), live transcript bubbles with **Translate** / **0.75×**, turn status pill, **Round Coach Card** with starter phrase chips & **Hint**, call controls | Speak, tap starter phrases, tap **Hint** / next question, mute, camera, speaker, captions, **End room** |
| **8 · Host Controls** | `2704:12995` | Active room with host camera enabled and `⋮` host controls (invite members, next prompt question, switch Public/Private, **End room for everyone**) | Manage room live as host |
| **9 · Active Room — No Captions** | `7353:15741` | Full-screen 2×2 participant grid showing each member's **avatar** (or **live camera video** when enabled), **Host** badge, live mic status (`Speaking`, `Muted`, `Listening`), and CEFR level | Speak & watch participants without transcript overlay |

---

## 4. How It Works

- **Avatars vs. Video per Seat (`7`–`9`):**
  - **Voice-Only (Cameras Off):** Displays up to 4 **member avatars** in the call top bar when Captions are On (`7`), and the **2×2 grid of member avatars** when Captions are Off (`9`).
  - **Video Enabled:** Each seat in the grid (`9`) independently switches from the member's **avatar** to their **live camera video** when their camera is on (or shows **Zuno's animated avatar** in Zuno-guided rooms), while members with cameras off continue showing their **avatars**.
- **Small-group focus:** Rooms are capped at **4 participants** and **10 minutes** so everyone gets real speaking time rather than getting lost in a large crowd.
- **Zuno-guided vs. User-hosted rooms:**
  - **System-programmed rooms** are always open to drop in anytime and display curated discussion prompts (`6`) with Zuno moderating questions (`7`).
  - **User-created rooms** start in a **Lobby** (`3`–`4`) until the host taps **Start room now**.
- **In-room speaking support (`7`):** Learners who hesitate can tap one of the **Round Coach Card** starter chips (e.g., *“Siempre pido…”*) or tap **Hint** to advance to a fresh prompt, plus **Translate** and **0.75×** on any message.
- **Host moderation (`8`):** During an active room, the host can invite someone into an open seat, advance to the next prompt, toggle visibility between **Public** and **Private**, or end the room for everyone.

---

## 5. Edge Cases & Empty / Error States

- **No matching rooms:** Displays *"No public rooms open yet"* or *"No matching rooms"* with a quick **Create room** CTA.
- **Room full (`4/4 seats`):** Full rooms cannot accept additional members until a seat opens.
- **Invalid or expired room code (`5`):** Shows an error inside the private-code sheet so the learner can re-enter the code.
- **Host leaves lobby or ends room:** Ending a room closes the session cleanly for all connected participants.
