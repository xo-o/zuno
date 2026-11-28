# 05 · Chats (AI Friends & People)

**Figma Section:** [`05 · Chats`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2837-24615) (`2837:24615`)

---

## 1. Overview

Chats is the unified messaging and calling hub for both **AI Friends** (6 distinct AI personalities) and **People** (saved language partners and learners currently **Open to speak**). Learners can text, send voice or video calls, toggle captions during live calls, manage partners, and handle call invites cleanly whether in or out of the app.

---

## 2. User Flow

```text
Chats Hub
  ├── Conversations tab (1) ──► Unified list (Pinned + Recent AI & Peer threads)
  ├── AI Friends tab (2) ─────► Tap personality ─► AI Friend Profile Sheet (10) ─► Active Chat (12)
  │                                                                                  └── Call ─► Video Call (13 Captions / 14 Avatar Only)
  ├── People tab (3) ─────────► Saved Partners + "Open to speak" learners
  │                               └── Tap person ─► Member Profile Sheet (11) ─► Member Chat (15)
  │                                                                                ├── Overflow / Add Partner (16)
  │                                                                                └── Call Invite ─► Waiting (18) ─► 1:1 Call (19 / 20)
  │                                                                                                     └── Missed / Notification (17)
  ├── Search (4 / 5) ─────────► Filter across Conversations, AI Friends & People
  └── Tap "+" ────────────────► New Chat Sheet (9) — Quick-start AI Friends or People
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 · Conversations** | `2842:14971` | Unified inbox grouped into **Pinned** and **Recent** across AI Friends and People | Open chat, search, tap `+` |
| **2 · AI Friends** | `2842:15221` | 6 AI personalities (*Sofía · Warm*, *Mateo · Energetic*, *Valeria · Patient*, *Claire · Crisp*, *Lucas · Thoughtful*, *Ethan · Relaxed*) | Start chat, start call, view profile |
| **3 · People** | `2875:16785` | **Partners** (saved) at top, followed by learners currently **Open to speak** | View profile, message, invite to speak |
| **4 / 5 · Search** | `2875:17055` / `2876:16618` | Live search across Conversations, AI Friends, and People; empty state when nothing matches | Filter or clear search |
| **6 · Empty — Conversations** | `3091:14336` | *"Start a conversation"* with quick-start avatar row and online people link | Tap an AI friend or browse people online |
| **7 / 8 · People Empty States** | `2869:16035` / `2869:15882` | `7`: no saved partners yet (shows Open to speak only); `8`: no people available | Browse or adjust filters |
| **9 · New Chat Sheet** | `2842:15430` | Quick-start bottom sheet with search, AI Friends strip, Partners, and Open to speak | Pick anyone to message |
| **10 · AI Friend Profile Sheet** | `2869:15727` | Personality summary, pace, language, and sample vibe | Start chat or call |
| **11 · Member Profile Sheet** | `2842:16082` | Native language, target level, streak/sessions, interests (*Travel, Daily life, Tech*), Open-to-speak badge | Message, add partner, invite to call |
| **12 · AI Friend — Active Chat** | `2842:15912` | Text thread with inline call summary card (*Video call · 03:42 · Ended · 2 coaching tips*) | Reply, listen, tap **Notes**, start voice or video call |
| **13 · AI Friend — Video Call (Captions)** | `2847:15551` | Active AI Friend call with **AI Friend + learner avatars** in top bar, live transcript bubbles, turn status pill, and coach card | Speak, toggle camera/captions/mute/speaker, end call |
| **14 · AI Friend — Video Call (No Captions)** | `7353:15475` | Full-screen stacked two-tile view: **AI Friend's animated avatar/video** on top (`AI Tutor · Sofía`) and **learner's live camera** (or avatar fallback) on bottom | Practice face-to-face without transcript overlay |
| **15 · Member Chat + Invite Card** | `2842:15989` | 1:1 text chat with *"Lucía is open to speak now"* quick call-invite card | Send message, tap to start 1:1 voice practice |
| **16 · Member Chat — Overflow & Partner Added** | `2876:16944` | Chat overflow menu (Add/Remove Partner, Mute, Block, Report) + confirmation toast | Manage partner relationship |
| **17 · Call Invite — Notification & Missed** | `2876:16836` | System call invite notification with **Join** / **Decline**, and missed-invite card in chat | Join call, decline, or call back |
| **18 · Call Room — Waiting for Partner** | `2862:15675` | 2-up tiles showing dimmed **partner avatar** (*Waiting to join*) and **learner avatar** (or live camera if turned on), plus *"Calling Lucía M…"* and icebreaker prompts | Wait, ping partner, toggle camera/mute, or cancel |
| **19 · Member Call — Voice Mode (Captions)** | `2847:15674` | Active 1:1 call with **both participants' avatars** in the top bar, live transcript bubbles, turn status pill, and coach card | Speak, translate/replay, toggle camera/captions/mute/speaker, end call |
| **20 · Member Call — Voice Mode (No Captions)** | `7353:15600` | Full-screen stacked two-tile view showing **both participants' avatars** (with speaking highlight & mic status), or **live camera video** for any participant whose camera is enabled | Talk hands-free or toggle video per participant |

---

## 4. How It Works

- **Avatars vs. Video in AI Friend Calls (`13`, `14`):**
  - **Voice-Only (Camera Off):** Displays the chosen **AI Friend's avatar** (*Sofía*, *Mateo*, *Valeria*, *Claire*, *Lucas*, or *Ethan*) in the call header when Captions are On, or the AI Friend's animated avatar / Horizon presence when Captions are Off.
  - **Video Enabled (Camera On):** Because AI Friends are AI characters rather than webcam callers, turning video on displays that **AI Friend's live, lip-synced animated avatar/video** alongside the **learner's live camera feed** (falling back to the learner's **avatar** if the learner's camera isn't active). With Captions Off (`14`), both appear in a full-screen stacked two-tile layout; with Captions On (`13`), both avatars appear in the top bar above the live transcript.
- **Avatars vs. Video in 1:1 Peer Calls (`18`–`20`):**
  - **Waiting (`18`):** The invited partner appears as a dimmed **avatar** until they join, while the caller's tile shows their **avatar** (or live camera if toggled on).
  - **Voice-Only (Cameras Off):** Always displays **both participants' avatars**—in the top call bar when Captions are On (`19`), and as full-screen stacked participant avatar tiles with speaking halos when Captions are Off (`20`).
  - **Video Enabled:** Each participant tile independently switches from their **avatar** to their **live camera video** when their camera is on, while anyone with their camera off continues showing their **avatar**.
- **Smart notifications:** Push notifications for peer messages and call invites appear only when the learner is **not** actively inside that exact chat. If they are already viewing that chat, new messages and call cards appear live without buzzing them.
- **Per-chat mute:** Muting a peer conversation stops push notifications for both messages and call invites from that thread.
- **Open to speak & Privacy:** Learners control whether they appear under **Open to speak**, whether partners can send 1:1 call invites, and who can message them in **Account → Privacy & visibility**.
- **Ended call summaries:** When a voice or video call with an AI friend ends, a summary card appears in the chat thread (`Video call · 03:42 · Ended`) and offers to save useful phrases from the call into Vocabulary.

---

## 5. Edge Cases & Empty / Error States

- **Declined or missed call invite (`17`):** If the host ends the call before the partner joins, or the partner taps **Decline**, the active invite turns into a **Missed invite** card inside the chat so either person can retry later.
- **No conversations yet (`6`):** Displays a 5-avatar quick-start row so a new learner can start talking in one tap.
- **No search results (`5`):** Shows a clean empty state prompting the user to adjust their query.
- **Blocked learners:** Blocked users cannot message or send call invites and are hidden from active discovery.
