# 14 · Community & Partners

**Figma Section:** [`14 · Community & Partners`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2464-10058) (`2464:10058`)

---

## 1. Overview

Community & Partners connects learners studying the same language for mutual accountability and friendly motivation—combining a **Partners** directory (saved partners and people currently **Open to speak**) with a **Weekly League** leaderboard based on speaking minutes and XP.

---

## 2. User Flow

```text
Sidebar Drawer ─► Community
                    ├── Partners tab (14.1)
                    │     ├── Toggle "Open to speak" visibility
                    │     ├── Your partners ─► Tap "Talk" (1:1 chat/call) or "Invite"
                    │     └── Online now ─► Tap learner to view profile, message, or add partner
                    └── League tab (14.2)
                          ├── View Weekly speaking rank & time remaining
                          ├── Top 3 podium (1st, 2nd, 3rd XP)
                          └── Standings list ("Top 10 advance") with user's highlighted row
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **14.1 · Community — Partners** | `3131:13712` | Segmented tabs (**Partners** \| **League**); **Open to speak** toggle card (*"Visible to Spanish B1–B2 learners"*); **Your partners** section (with shared goal progress, last spoke time, **Talk** button, and **Invite**); **Online now** list of fellow learners | Toggle **Open to speak**, tap **Talk**, invite a partner, browse online learners |
| **14.2 · Community — League** | `3131:13835` | **Weekly speaking** rank card (`Your rank · #4`, countdown e.g. `3d 14h left`); Top 3 podium; **Standings** list (*Top 10 advance*) showing rank, minutes spoken, and XP | Track weekly rank and speaking volume |

---

## 4. How It Works

- **Open to speak discovery (`14.1`):** When turned on, the learner appears to compatible learners (matching target language and nearby CEFR levels) as available for 1:1 practice. Turning it off hides them immediately (synced with **Account → Privacy & visibility**).
- **Your partners (`14.1`):** Highlights saved partners, their recent activity or weekly speaking goal progress, and a 1-tap **Talk** button to jump straight into a 1:1 chat or call.
- **Weekly League (`14.2`):** Ranks active learners each week by XP earned from speaking practice (`min spoken` + `XP`). The top 10 learners at the end of the weekly countdown advance.

---

## 5. Edge Cases & Empty / Error States

- **No saved partners yet:** Shows **Invite** in the **Your partners** header alongside the **Online now** learners so anyone can add their first partner.
- **No one online right now:** Displays a helpful message suggesting AI Friends, Live Rooms, or turning on **Open to speak** so others can find them.
- **Blocked users:** Anyone blocked in Privacy & Safety never appears in Partners or Online now.
