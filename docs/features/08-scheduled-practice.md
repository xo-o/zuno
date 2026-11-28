# 08 · Scheduled Practice

**Figma Section:** [`08 · Scheduled`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2117-3586) (`2117:3586`)

---

## 1. Overview

Scheduled Practice helps learners build a consistent speaking habit by planning Topics or Roleplays ahead of time—either one-off or on a recurring cadence—and sending timely pre-session reminders that launch straight into practice.

---

## 2. User Flow

```text
Scheduled Tab / Drawer
  ├── No schedules ─► Empty State (2) + Suggested Routines (tap to prefill or create)
  └── Has schedules ─► Upcoming List (3) grouped by Today / Tomorrow / Later this week
                         ├── Tap "Start" on next session ─► Opens Topic Lesson or Roleplay
                         ├── Tap a row ─► Item Actions Sheet (8): Start now | Skip tomorrow only | Delete
                         └── Tap "+" / Schedule session ─► Step 1 of 2: Choose Topic (4) or Roleplay (5)
                                                                │
                                                                ▼
                                                           Step 2 of 2: Set Repeat & Time
                                                             ├── Daily (6) · Once (6c) · Weekdays
                                                             ├── Weekly multi-day (6d) · Monthly (6e)
                                                             └── Active / 10-min reminder / Email toggles
                                                                │
                                                                ▼
                                                           Added to List + Toast (7) ─► Pre-session Notification (9)
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **2 · Empty** | `2117:3735` | *"Nothing scheduled"* empty state + **Schedule session** CTA + **Suggested routines** (*Morning warm-up*, *Ordering at a café*, *Vocabulary Recall Sprint*) | Schedule custom session or pick a suggested routine |
| **3 · List** | `2117:3785` | Upcoming sessions grouped under **Today**, **Tomorrow**, and **Later this week**; today's next item features a **Start** shortcut | **Start** next session, tap any item for actions, tap `+` |
| **4 · Choose (Topics)** | `2117:3993` | Step 1 of 2: Segmented toggle (**Topics** \| **Roleplays**) showing personal topics with next lesson & duration | Pick a topic, tap **Continue** |
| **5 · Choose (Roleplays)** | `2117:4109` | Step 1 of 2: **Roleplays** segment showing role pairs and duration | Pick a roleplay, tap **Continue** |
| **6 · When (Daily)** | `2121:4506` | Step 2 of 2: Selected item summary, live schedule preview (*"Daily · 9:00 AM"*), **Repeat**, **Time**, and **More** toggles (**Active**, **Remind me before** 10 min, **Notify by email**) | Adjust repeat/time/toggles, save schedule |
| **6b · Repeat menu** | `2121:4715` | Dropdown options: **Once**, **Daily**, **Weekdays**, **Weekly**, **Monthly** | Select recurrence |
| **6c · Once** | `2121:4926` | Shows **Date** + **Time** pickers (*"Once · Sun, Nov 8, 9:00 AM"*) | Pick date & time |
| **6d · Weekly (multiple days)** | `2121:5122` | Shows multi-select weekday pills (`M T W T F S S`) + **Time** (*"Weekly · Mon, Wed, Fri · 9:00 AM"*) | Toggle one or more days |
| **6e · Monthly** | `2121:5318` | Shows day-of-month grid (`1`–`31`) + **Time** (*"Monthly · Day 7 · 9:00 AM"*) | Pick day of month |
| **7 · Added** | `2117:4415` | Updated upcoming list with confirmation toast | View updated schedule |
| **8 · Item actions** | `2117:4635` | Bottom sheet on tapping a scheduled row: **Start now**, **Skip tomorrow only**, **Delete schedule** | Run now, skip once, or delete |
| **9 · Time to practice** | `2117:4871` | Lock screen reminder 10 minutes before start (*"Roleplay in 10 min: Ordering at a café"*) | Tap to start practice or snooze |

---

## 4. How It Works

- **Two-step creation:** Learners always pick *what* they want to practice first (from their Topics or Roleplays, `4`–`5`), then set *when* (`6`–`6e`).
- **Flexible recurrence (`6b`–`6e`):**
  - **Once:** Specific calendar date + time.
  - **Daily / Weekdays:** Time only.
  - **Weekly:** One or more selected days of the week + time.
  - **Monthly:** Day of the month (`1`–`31`) + time.
- **Pause without deleting (`6`):** Turning off **Active** pauses a schedule (*"Paused schedules don't run"*) while keeping its configuration saved.
- **Skip next occurrence (`8`):** Tapping **Skip tomorrow only** skips the next occurrence of a recurring schedule without deleting the routine.
- **Pre-session reminders (`6`, `9`):** Enabled by default to notify **10 minutes before** start (with an optional **Notify by email** toggle).

---

## 5. Edge Cases & Empty / Error States

- **No schedules (`2`):** Surfaces **Suggested routines** so beginners aren't staring at a blank screen.
- **Continue disabled (`4`–`5`):** **Continue** remains disabled until one Topic or Roleplay is selected.
- **Invalid weekly selection (`6d`):** At least one weekday must remain selected for a Weekly schedule.
- **Shortcuts from elsewhere:** Users can also jump straight into Step 2 from a Topic or Roleplay's `⋮` **Schedule practice** action.
