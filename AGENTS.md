# ZunoAI Repository Guide for Agents

## Monorepo Layout

- `android/` — Native Android app (Kotlin & Jetpack Compose). See `android/AGENTS.md` for UI layout/spacing rules and build/test commands.
- `backend/` — Node.js + TypeScript + Hono + Better Auth + Drizzle ORM API server.
- `voice-agent/` — Python LiveKit Agents real-time voice worker.
- `web/` — Next.js web app (see `web/AGENTS.md`).
- `docs/` — End-to-end feature flow specifications (`ONBOARDING_AND_AUTH_FLOW_SPEC.md`, `PEER_CHAT_AND_NOTIFICATIONS_FLOW.md`, `VOICE_LESSON_FLOW_SPEC.md`, `FREE_TALK_FLOW_SPEC.md`, `TOPICS_AND_ROLEPLAYS_FLOW_SPEC.md`, `ROLEPLAY_VOICE_FLOW_SPEC.md`, `VOCABULARY_SRS_FLOW_SPEC.md`).

## Figma Design Source of Truth

- File: `ZunoAI-Oficial` (`fileKey = W5oO2EsEMdnPtWIuNfbM7r`)
- Top-level pages: `Foundations` (`5:2`), `Components` (`5:3`), `Mobile` (`5:4`), `Web` (`2178:14428`).
- Mobile screens use a `402×874` canvas, while responsive horizontal layout tokens live in the **`Layout`** variable collection (`Compact (360dp)` default mode vs `Standard (402pt)` mode):
  - `layout/screen-gutter`: `16` (Compact) / `24` (Standard)
  - `layout/topbar-x`: `6` / `12` (aligns 24px icons inside 44px touch targets with the screen gutter)
  - `layout/topbar-pill-end`: `16` / `20`
  - `layout/card-x`: `12` / `14`, `layout/card-x-end-menu`: `6` / `8`, `layout/row-gap`: `10` / `12`
  - `layout/chip-x`: `12` / `14`, `layout/chip-gap`: `6` / `8`, `layout/tab-track-pad`: `3` / `4`
- Do **not** modify `Spacing` primitives (`space/24`, etc.) to adjust horizontal gutters—they are shared with vertical gaps/padding. Bind horizontal layout properties to the `Layout` collection instead.
