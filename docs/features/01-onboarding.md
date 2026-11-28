# 01 · Onboarding & Sign-In

**Figma Section:** [`01 · Onboarding`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2544-20596) (`2544:20596`)

---

## 1. Overview

Onboarding gets a learner into Zuno without passwords. Returning users go straight to Home once signed in; new users pick their app language, choose a target language to learn, complete a quick 3-step chat with Zuno (name, level, goals), and receive a personalized 5-lesson starter path.

---

## 2. User Flow

```text
Splash
  ├── Already signed in ───────────────────────────────► Home (or finish setup)
  └── Signed out ─► Welcome ─► Sign In / Sign Up
                                  ├── Google Sign-In ──► Signed In
                                  └── Email Magic Link ► Check Your Email
                                                            │
                                                            ▼
                                                 Magic Link Email (15 min)
                                                    ├── Opened on same phone ────────► Signed In
                                                    └── Opened in browser / PC ──────► Choose Device
                                                                                         ├── Authorize phone ──► Phone signs in
                                                                                         └── Expired / used ───► Link Expired screen

First-Time Setup (new accounts):
  App Language (5a) ─► Target Language (5b) ─► Name (5c) ─► Level (5d) ─► Goals (5e) ─► Building Path (5f) ─► Starter Path Ready (5g)
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 · Splash** | `2544:20597` | Centered Zuno logo mark on dark canvas while checking session status | Automatic routing |
| **2 · Onboarding** | `2787:15926` | Zuno mark, tagline (*"Your quiet space to master any language."*), Terms & Privacy links | **Get started**, view Terms / Privacy Policy |
| **3 / 3b · Sign In** | `2544:20614` / `2544:20632` | Email input, primary CTA, Google option, Sign up link; switches to *"Sending…"* while submitting | Enter email, **Continue with Google**, switch to **Sign up** |
| **4 / 4b · Sign Up** | `2544:20650` / `2544:20668` | *"Create your account"* heading, email input, Google option, Sign in link; switches to *"Sending…"* | Create account with email or Google, switch to **Sign in** |
| **4c · Check Your Email** | `2544:20686` | Confirmation showing the target email and 15-minute expiry notice | Open email app, **Resend link**, **Use a different one** |
| **4d · Magic Link Email** | `2966:12290` | Branded email showing the account email and single-use warning | Tap **Sign in to Zuno** |
| **4e · Browser — Verify & Choose Device** | `2966:12328` | Web verification page when opened outside the app | Authorize Zuno waiting on phone, or open Zuno on this device |
| **4f · Browser — Phone Authorized** | `2966:12373` | *"You're signed in"* confirmation with instruction to close the window | Close browser; phone proceeds automatically |
| **4g · Browser — Link Expired** | `2966:12399` | Clear notice that single-use links expire after 15 minutes | Return to app and request a new link |
| **5a · Your Language** | `7288:12575` | App interface language selector (*"Menus, hints, and Zuno's explanations appear in this language."*) | Pick app language (English, Spanish, French, German, Italian, Portuguese, Dutch, Polish) |
| **5b · Target Language** | `2544:20712` | Searchable list of 35+ languages to practice | Search and pick target language |
| **5c · Step 1 — Name** | `2544:22223` | Chat prompt: *"Hi, I'm Zuno. What should I call you?"* | Type and send name |
| **5d · Step 2 — Level** | `2544:22233` | Zuno greets learner by name and asks experience level | Tap **Beginner**, **Intermediate**, or **Advanced**, or type freely |
| **5e · Step 3 — Goals** | `2544:22248` | Zuno asks why they are learning | Tap goal chips (**Work**, **Travel**, **Interviews**, **Daily chat**) or type custom goals |
| **5f · Preparing Path** | `2544:21444` | Loading spinner with *"Building your path"* | Wait while starter 5-lesson topic is generated |
| **5g · Starting Path Ready** | `2544:21856` | Summary of language, level, goals, Starter Lesson 1 card, and 5-lesson syllabus | **Start first lesson**, **Go to Home**, or tap **Start** on Lesson 1 |

---

## 4. How It Works

- **Passwordless by default:** Users never create or remember a password. Every email sign-in sends a single-use magic link valid for **15 minutes**.
- **Cross-device support:** If a learner requests a link on their phone and clicks it on a laptop browser, the browser page asks them to confirm authorizing their waiting phone—so opening a link never silently signs someone in without confirmation.
- **Chat-only setup:** Steps `5c`–`5e` happen strictly over text + quick chips without prompting for microphone access during onboarding.
- **Personalized starter path:** Using the learner's name, level, and custom goals (e.g., *"Backend interviews in Mexico, plus coffee and travel"*), Zuno builds a custom 5-lesson topic immediately before entering the app.

---

## 5. Edge Cases & Empty / Error States

- **Invalid email:** Inline validation prevents submitting empty or malformed email addresses; action buttons disable while a request is sending (`3b` / `4b`).
- **Wrong email entered:** Tapping **Use a different one** on `4c` stops waiting for verification and returns to Sign In / Sign Up.
- **Resend cooldown:** Resending the magic link has a short cooldown to prevent duplicate emails.
- **Expired or already-used link:** Shows `4g · Link Expired` and tells the user to request a fresh link in the app.
- **Returning users:** Anyone who already completed onboarding skips `5a`–`5g` and lands directly on Home.
