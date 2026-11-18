# ZunoAI — Onboarding & Passwordless Auth Flow Specification (`01 · Onboarding`)

**Figma Reference:** [`01 · Onboarding`](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2544-20596) (`2544:20596`)  
**Target Stack:** Android (`Kotlin / Jetpack Compose` + `AuthViewModel` + `OnboardingViewModel` + `VoiceSessionViewModel`), Backend (`Node.js / Hono / Better Auth / Resend`), Voice Worker (`Python LiveKit Agents`)

---

## 1. End-to-End Flow Overview

```text
[1 · Splash] ──► [2 · Onboarding] ──► [3 · Sign In / 4 · Sign Up]
                                              │
                         ┌────────────────────┴────────────────────┐
                         ▼ (Google OAuth)                          ▼ (Email Magic Link + flowId)
                 SignedIn Session                       [4c · Check Your Email] (polls /poll?flowId=…)
                         │                                         │
                         │                                         ▼
                         │                              [4d · Magic Link Email] (Resend HTML)
                         │                                         │ (Tap "Sign in to Zuno")
                         │                                         ▼
                         │                    ┌────────────────────────────────────────┐
                         │                    │ Android App Link intercepted?          │
                         │                    └───────┬────────────────────────┬───────┘
                         │                            │ YES (this phone)       │ NO (browser / another device)
                         │                            ▼                        ▼
                         │                    App calls verifyMagicLink   [4e · Browser GET /auth/verify]
                         │                            │                        ├─► "Confirm sign-in on my phone" (POST)
                         │                            │                        │     └─► [4f · Phone Signed In] ──► Poll succeeds
                         │                            │                        ├─► "Open Zuno on this device" (zuno://…)
                         │                            │                        └─► Expired/used token ──► [4g · Link Expired]
                         └────────────────────────────┴────────────────────────┘
                                                      │
                                      (onboardingCompleted == false)
                                                      ▼
                                         [5a · Target Language & Accent]
                                                      │ (Mic granted → Voice; Declined → Chat)
                                                      ▼
                  ┌───────────────────────────────────────────────────────────────────────┐
                  │ 5b · Name ──► 5c · Level ──► 5d · Goals                               │
                  │   • Voice mode: Portrait Stage (top) + top-aligned scrollable chat    │
                  │   • Chat mode:  Synced LazyColumn + quick chips + composer            │
                  │   • Bidirectional switch ("Type instead" ◄──► "Speak instead")        │
                  └───────────────────────────────────┬───────────────────────────────────┘
                                                      ▼
                                         [5e · Preparing Path] (POST /api/me/onboarding)
                                                      │
                                                      ▼
                                         [5f · Starting Path Ready] ──► First Lesson / Home
```

---

## 2. User Stories & Acceptance Criteria

### US-1 — Brand Entry (`1 · Splash`, `2 · Onboarding`)
**As a** new or returning learner,  
**I want** a fast, calm brand launch and clear entry into sign-in/sign-up,  
**so that** I understand Zuno's value before authenticating.

**Acceptance Criteria:**
- `1 · Splash` (`2544:20597`) animates `Logo/Mark` fade-in (`500ms`) and routes automatically once session refresh resolves.
- `2 · Onboarding` (`2787:15926`) displays mark, `"Zuno"` wordmark, tagline, primary `"Get started"` CTA, and clickable `Terms` / `Privacy Policy` links.

---

### US-2 — Passwordless Sign-In / Sign-Up (`3`, `3b`, `4`, `4b`, `4c`)
**As a** learner,  
**I want** to sign in or create an account with a single-use email magic link or Google,  
**so that** I never have to manage a password.

**Acceptance Criteria:**
- `3 · Sign In` (`2544:20614`) and `4 · Sign Up` (`2544:20650`) validate email format inline, disable actions during requests, and transition to `"Sending link…"` (`3b` / `4b`) with helper text `"We’ll send a magic link to this address."`.
- Sending generates a client `flowId` (`UUID`), calls `POST /api/auth/sign-in/magic-link`, opens `4c · Check Your Email` (`2544:20686`), and polls `GET /api/auth/magic-link/poll?flowId=…` every 2 seconds (5-minute timeout).
- `4c` supports `"Open email app"`, `"Resend link"` with a 30-second cooldown, and `"Use a different one"` (cancels polling and returns to `3`/`4`).

---

### US-3 — Magic Link Email & Cross-Device Browser Verification (`4d`, `4e`, `4f`, `4g`)
**As a** learner opening my sign-in email (on my phone or a laptop browser),  
**I want** a clear email card and an explicit choice in the browser to either authorize my waiting phone or open Zuno on the current device,  
**so that** email link scanners never consume my token and cross-device login feels safe and effortless.

**Acceptance Criteria:**
- **`4d · Magic Link Email`** (`2966:12290`, `backend/src/lib/email.ts`): Dark `bg/elevated` card (`#161618` / `1px` border) with Zuno lockup, `15 min` indicator, `SIGNING IN AS` identity strip (`email` + `Single-use` badge), `"Sign in to Zuno"` primary pill CTA, and ignore-if-unrequested security note.
- **`4e · Browser — Verify & Choose Device`** (`2966:12328`, `backend/src/lib/verify-page.ts` `GET /auth/verify`):
  - `GET /auth/verify` **never** redeems the token (protects against email prefetchers/scanners).
  - On mobile User-Agent, attempts `zuno://auth/verify?token=…` handoff while keeping the page usable if the user stays in browser.
  - When a pending `flowId` exists, presents two clear actions:
    1. Primary **`Confirm sign-in on my phone`** (`POST /auth/verify`): redeems token via Better Auth, calls `completeFlow(flowId, signedBearerToken, user)`, and renders `4f`.
    2. Secondary **`Open Zuno on this device`** (`zuno://auth/verify?token=…`): hands the still-unused token to the Android app on this device (`AuthViewModel.handleMagicLink`).
- **`4f · Browser — Phone Authorized`** (`2966:12373`): Confirms `"You’re signed in"` + `"Zuno on your phone is signing in now."` + authorized account strip; the waiting Android app completes sign-in on its next poll.
- **`4g · Browser — Link Expired`** (`2966:12399`): Expired/already-used tokens render `"This link has expired"` with a `"Back to Zuno app"` (`zuno://`) action and clear instructions to request a new link.
- On Android, if an already-redeemed or expired `zuno://auth/verify?token=…` deep link opens the app: `handleMagicLink` first falls back to `awaitVerifiedFlow(flowId)` if a local pending flow matches, and otherwise surfaces a clear error message on `AuthScreen` (never silently dropping the user).

---

### US-4 — Target Language & Accent Selection (`5a`)
**As a** new learner,  
**I want** to pick the language I'm learning and its regional accent before speaking with Sofía,  
**so that** my onboarding and starter lessons use the right dialect.

**Acceptance Criteria:**
- `5a · Target Language` (`2544:20712`) lists all 35 supported languages (popular languages surfaced first, A–Z thereafter), supports diacritic-insensitive live search, and displays regional accent chips (e.g., `Latin America` / `Spain`) when `accents.size > 1`.
- Tapping `"Continue"` requests microphone permission if not yet granted: granted enters `5b` in **Voice** mode; declined enters `5b` in **Chat** mode.

---

### US-5 — Progressive Conversational Voice Onboarding (`5b`, `5c`, `5d`)
**As a** learner meeting Sofía for the first time,  
**I want** Sofía's portrait cleanly framed at the top with conversation bubbles appearing right beneath her as we talk,  
**so that** the UI feels like a modern live video/voice call without text cards blocking her face or leaving awkward empty gaps.

**Acceptance Criteria:**
- **Vertical Stage Layout** (replacing legacy floating overlay cards):
  1. **`Portrait Stage Card`**: Top `296dp` max (clamped `(maxHeight * 0.36f).coerceIn(200.dp, 296.dp)`), `ContentScale.Crop` (`TopCenter`), with a **`28.dp` top scrim** and **`52.dp` light bottom feather scrim** (`0% → 22% → 68% → 100%` into `BgCanvas`) so Sofía's face and shoulders remain bright and unobstructed.
  2. **Scrollable `Conversation` (`Modifier.weight(1f)`)**: **Top-aligned** (`Arrangement.Top` / `MIN`) `LazyColumn` directly beneath the portrait (`paddingHorizontal = 20dp`, `spacedBy(8dp)`):
     - Bubbles start immediately below the portrait with **zero blank space above them**.
     - As the conversation progresses (`5b` Name → `5c` Level → `5d` Goals), prior turns remain visible and scroll naturally once content exceeds the viewport.
     - Assistant turns use `ZunoAssistantBubble` with step-aware sender labels (`"Sofía · Your Spanish tutor"`, `"Sofía · Spanish level"`, `"Sofía · Learning goals"`).
     - Completed user turns use `ZunoUserBubble`.
     - While the user is speaking, a live **`You · Speaking`** `ZunoUserBubble` appears **progressively** at the bottom of the list only when `liveTranscript.isNotBlank()` (never showing an empty placeholder box beforehand).
  3. **Contextual Quick Options** (optional on `5c` Level / `5d` Goals): Compact horizontal `ZunoChip` row (`Beginner / Intermediate / Advanced` on `5c`; `Work / Travel / Interviews / Daily chat` on `5d`) positioned cleanly above `Call controls` only when helpful, leaving `5b` uncluttered.
  4. **Bottom `Call controls`**: `Mic` toggle (reflecting `Listening` / `Speaking` state), `"Type instead"` pill (switches seamlessly to Chat mode), and `→` Advance button (enabled once a live transcript is captured).

---

### US-6 — Synced Text Chat Fallback (`5b (Chat)`, `5c (Chat)`, `5d (Chat)`)
**As a** learner in a quiet place or without mic access,  
**I want** to type or tap chips and switch back and forth between typing and speaking,  
**so that** I never lose my onboarding progress.

**Acceptance Criteria:**
- Both Voice and Chat modes read and mutate `OnboardingViewModel.state.chatHistory`.
- Switching via `"Type instead"` / `"Speak instead"` pauses/resumes voice playback & STT cleanly while keeping all prior turns intact.
- `5c (Chat)` displays single-tap `Beginner / Intermediate / Advanced` chips; `5d (Chat)` supports multi-select goal chips + free-text note.

---

### US-7 — Path Generation & Ready Launch (`5e`, `5f`)
**As a** learner finishing setup,  
**I want** to see my personalized starter curriculum generated and jump straight into Lesson 1,  
**so that** I experience immediate value.

**Acceptance Criteria:**
- `5e · Preparing Path` (`2544:21444`) calls `POST /api/me/onboarding`, handles retry on network error, and enforces a smooth minimum visual transition (`~900ms`).
- `5f · Starting Path Ready` (`2544:21856`) greets the learner by first name, highlights the starter topic card with `"Start first lesson"`, lists recommended next topics, and offers `"Go to Home"`.

---

## 3. Architecture & Design System Best Practices

| Area | Decision | Rationale |
|---|---|---|
| **Shared State (`OnboardingViewModel`)** | Single `chatHistory: List<OnboardingChatItem>` across Voice and Chat modes | Eliminates duplicated state and ensures `"Type instead"` ↔ `"Speak instead"` transitions keep every turn. |
| **Voice Stage Alignment** | `Column` with `Portrait Stage Card` (`~296dp`) + `LazyColumn(weight(1f), reverseLayout = false)` (`MIN` top-aligned) + `Call controls` | Keeps Sofía completely unobstructed, prevents bubbles from floating over the portrait, and eliminates black gaps above bubbles when only 1–2 turns exist. |
| **Progressive Live Transcript** | Render `You · Speaking` `ZunoUserBubble` only while `liveTranscript.isNotBlank()` and auto-scroll on `history.size` / `liveTranscript.isNotEmpty()` | Bubbles appear organically as the user speaks rather than occupying static empty cards. |
| **Token & Component Reuse** | Use `ZunoColors`, `ZunoSpace`, `ZunoRadius`, `ZunoType`, `ZunoAssistantBubble`, `ZunoUserBubble`, `ZunoChip`, `ZunoButton`, `ZunoOnboardingTopBar` | 100% parity with Figma `Foundations` (`5:2`) and `Components` (`5:3`). |
| **Localization** | All visible copy in `res/values*/strings_features.xml` across all 8 locales (`en, es, fr, de, it, pt-rBR, nl, pl`) | Matches app-wide i18n requirement. |
| **Web / Email Security (`4d–4g`)** | `GET /auth/verify` is strictly read-only; token redemption happens only on explicit `POST /auth/verify` or `zuno://auth/verify` in-app | Prevents email security scanners from burning single-use magic link tokens. |
