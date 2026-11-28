# 16 · Ongoing Call & System UI

**Figma Section:** [`16 · Ongoing Call & System`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=7173-11858) (`7173:11858`)

---

## 1. Overview

When a learner locks their phone or switches to another app during any live session (AI Tutor call, 1:1 Peer call, or Live Room), Zuno displays a persistent **Ongoing Call** card on the **Lock Screen** and in the **Notification Panel** so they always know the call is active and can mute, switch audio output, return to the call, or end it in one tap.

---

## 2. User Flow

```text
Active Call (AI Tutor / 1:1 Peer / Live Room)
  └── User leaves app or locks phone
        ├── Lock Screen Ongoing Call Banner (1–5, 8–10)
        │     ├── Tap Banner ─────────► Reopens active call screen immediately
        │     ├── Tap Mute / Unmute ──► Toggles mic live without unlocking/opening app
        │     └── Tap End ────────────► Ends call and clears notification
        └── Notification Panel
              ├── Expanded (6) ───────► Title, subtitle/status, live timer, Mute | Speaker | End
              └── Collapsed (7) ──────► Compact call summary + live timer
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 · Lock Screen — AI Tutor Active** | `7173:11859` | Persistent lock-screen call card for active AI Tutor session with live timer and quick **Mute** / **End** buttons | Tap to return, Mute, End |
| **2 · Lock Screen — Muted** | `7173:11903` | Ongoing call card clearly indicating mic is muted (**Unmute** action active) | Tap **Unmute** or End |
| **3 · Lock Screen — 1:1 Peer Call** | `7173:11947` | Ongoing 1:1 partner call card showing partner identity, duration, and quick actions | Tap to return, Mute, End |
| **4 · Lock Screen — Live Room & Stack** | `7173:11987` | Live Room call card (*"Café Español · Live Room · 12:04 · 6 speaking"*) pinned above stacked message/schedule notifications | Manage room audio or tap stacked notifications |
| **5 · Lock Screen — Call Room Invite** | `7173:12040` | Incoming 1:1 call invite on lock screen with **Join** and **Decline** actions | **Join** or **Decline** |
| **6 · Notification Panel — Expanded** | `7173:12074` | Full expanded shade card with call title, subtitle/status, live chronometer timer, and 3 actions (**Mute/Unmute**, **Speaker/Earpiece**, **End**) | Toggle mic, toggle speaker, end call, open call |
| **7 · Notification Panel — Collapsed** | `7173:12157` | Compact single-line call notification in the shade | Expand for controls or tap to return |
| **8 · Lock Screen — Video Paused** | `7173:12236` | Indicates camera is paused while the audio session continues in the background | Resume in app, Mute, End |
| **9 · Lock Screen — Reconnecting** | `7173:12277` | Shows *Reconnecting…* status while recovering an interrupted network or phone-call audio focus | Wait for auto-recovery or End |
| **10 · Lock Screen — Waiting for Partner** | `7175:12347` | Ringing/waiting status while waiting for the 1:1 partner to join the call room | Open room or Cancel/End |

---

## 4. How It Works

- **Caller identity & avatars (`1`–`4`):** Ongoing call cards show the active **AI Friend's name/avatar**, **1:1 Partner's name/avatar**, or **Live Room** badge and participant count so learners know immediately who is on the line.
- **Visible on lock screen without buzzing:** The ongoing-call notification is marked public and silent—so it stays visible on the lock screen with live controls without making repeated notification sounds or vibrations.
- **Live controls without opening the app (`1`, `2`, `6`):** Tapping **Mute/Unmute** or **Speaker/Earpiece** updates the active call immediately and updates the button label on the notification.
- **Background video pause (`8`):** Leaving the app or locking the phone during a video call (AI Friend, 1:1 Peer, or Live Room) pauses camera video (`Video Paused`) while keeping voice audio active and participant avatars represented.
- **One-tap return:** Tapping the body of the ongoing-call card brings the learner directly back into the exact active call (Voice Lesson, Free Talk, Roleplay, Vocabulary Sprint, 1:1 Call, or Live Room).
- **Automatic cleanup:** Ending the call from either inside the app or via the **End** button on the notification immediately removes the lock-screen card.
- **Audio interruption recovery (`9`):** If a regular phone call or brief network drop interrupts the session, the banner shows **Reconnecting** and restores the mic cleanly once the interruption finishes.

---

## 5. Edge Cases & Empty / Error States

- **Notifications permission denied:** On devices where system notifications are disabled, calls still continue normally in-app, though the lock-screen/shade banner cannot be displayed until notifications are allowed.
- **Already in active chat for peer invite:** Incoming peer call invites do not post a duplicate system notification if the learner is already looking at that partner's chat screen.
- **Video call backgrounded (`8`):** Pausing video when leaving the screen keeps audio active and clearly tells the learner on the lock screen that video is paused.
