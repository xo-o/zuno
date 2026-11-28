# 13 · Progress

**Figma Section:** [`13 · Progress`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2463-9797) (`2463:9797`)

---

## 1. Overview

Progress gives learners a clear, motivating view of their speaking consistency, daily goals, balanced language skills, and steady movement along the CEFR proficiency scale (`A1`–`C2`).

---

## 2. User Flow

```text
Home (Tap Streak pill) or Sidebar Drawer ─► 13.1 · Progress
                                              ├── View CEFR level & % to next level
                                              ├── Check Streak, Spoken minutes & Words learned
                                              ├── Complete Daily goals (Speak / Use due words / Shadow drills)
                                              ├── Inspect 30-day Skill balance & tap recommended Drill
                                              └── Review weekly speaking bars & 12-week activity heatmap
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **13.1 · Progress** | `3127:14332` | **Level & Summary** (`Spanish · B1`, `68% to B2`, `A1`–`C2` track, `Streak`, `Spoken`, `Words`); **Daily goals** with reset countdown; **Skill balance** 5-axis radar (`Pronunciation`, `Listening`, `Vocabulary`, `Fluency`, `Grammar`) + targeted **Word stress** drill card; **Speaking this week** XP/daily chart; **Last 12 weeks** activity heatmap | Tap **Drill** on weakest skill focus, tap daily goal shortcuts, review stats |

---

## 4. How It Works

- **Language-specific metrics:** All progress cards (CEFR progress bar, spoken minutes, words, skill radar, and daily goals) reflect the learner's currently active learning language.
- **Daily goals with live reset:** Tracks three actionable habits—daily speaking minutes, using due Vocabulary words in chat, and completing shadow drills—alongside a live countdown (*"Resets in 6h"*).
- **Actionable skill balance:** Evaluates the past 30 days across 5 speaking dimensions and surfaces a specific 3-minute **Drill** (such as *Word stress · 72%*) to help strengthen the lowest-scoring area.
- **Long-term consistency:** Combines a weekly daily-minutes bar view (with total XP and daily average) and a 12-week activity heatmap (*Less → More*).

---

## 5. Edge Cases & Empty / Error States

- **New learner (day 1):** Shows `0d` streak, `0m` spoken, baseline level marker, and empty daily goal bars ready to fill after the first session.
- **Language switch:** Switching to a newly started language displays that language's fresh progress while preserving previous languages' stats.
