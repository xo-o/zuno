# Zuno Backend API

Fast, lightweight Node.js & TypeScript API built with [Hono](https://hono.dev), [Better Auth](https://better-auth.com), and [Drizzle ORM](https://orm.drizzle.team).

Features:
- **Better Auth**: Google Sign-In & Magic Links via Resend. Sign in with Apple ready to enable.
- **LiveKit Token Service**: Issues JWT access tokens for WebRTC voice rooms; the token metadata carries the tutor instructions for the conversation (Free Talk, roleplay or lesson).
- **AI tutor (Gemini)**: Generates 5-lesson topics and roleplay scenes, replies in chat with translations, glossary and per-message feedback, live typing hints and grammar explanations. Supports learning Spanish or English.
- **Digital Asset Links**: Native Android App Links verification (`/.well-known/assetlinks.json`).
- **High Performance**: Native TypeScript bundled with `tsup`, running on Node.js without frontend overhead.

## Setup

```bash
cp .env.example .env    # Fill in database, Better Auth, and LiveKit credentials
npm install
npm run db:push         # Push schema to PostgreSQL (or db:generate + db:migrate)
npm run dev             # Start dev server with hot reload on port 3000
```

To build and run in production:

```bash
npm run build
npm start
```

## Docker

```bash
docker build -t zuno-backend .
docker run -p 3000:3000 --env-file .env zuno-backend
```

## API Endpoints

| Purpose | Route | Description |
| --- | --- | --- |
| Health Check | `GET /health` | Returns `{ "status": "ok" }` |
| Send Magic Link | `POST /api/auth/sign-in/magic-link` | `{ "email": "user@example.com" }` |
| Redeem Magic Link | `GET /api/auth/magic-link/verify?token=…` | Issues session token in `set-auth-token` header |
| Google Sign-In | `POST /api/auth/sign-in/social` | `{ "provider": "google", "idToken": { "token": "..." } }` |
| Current Session | `GET /api/auth/get-session` | With `Authorization: Bearer <token>` |
| Languages | `GET /api/languages` | Learning languages (code, English/native name, accents, RTL). Public |
| User Profile | `GET /api/me` | `{ user, profile, languages, providers }` — profile has language, level, accent, `levelChecked`, preferences and plan; `languages` is per-language progress |
| Update Profile | `PATCH /api/me` | `{ name?, targetLanguage?, nativeLanguage?, level?, accent?, tutorVoice?, teachingStyle?, strictMode?, handsFree?, suggestReplies?, showTranslations?, teachInTarget?, dailyGoalMin?, focus? }`. Switching language saves the old language's level/accent and restores the new one's |
| Notifications | `GET` · `PATCH /api/me/notifications` | Daily reminder (+ local `reminderTime`, `timezone`), scheduled sessions, milestones, new content, weekly summary and news emails |
| Avatar | `POST /api/me/avatar` → PUT to R2 → `POST /api/me/avatar/complete` · `DELETE /api/me/avatar` | Change photo (JPEG/PNG/WebP ≤ 5 MB) |
| Delete Account | `DELETE /api/me` | Deletes R2 files and the user (all app data cascades), emails a confirmation |
| Devices | `POST /api/devices` · `DELETE /api/devices/:pushToken` | Register / remove this install's FCM token (`{ pushToken, platform, timezone, appLanguage }`) |
| Recent Sessions | `GET /api/conversations/recent` | Drawer list for the current language: pinned rows first, then Free Talk threads, topics and roleplays by latest activity |
| Topics | `GET /api/topics` · `POST /api/topics` | List · create from `{ prompt, attachmentIds?, parts? }` (Gemini builds 5 lessons, optionally from learning material) |
| Topic Material | `POST /api/topics/analyze` | `{ prompt?, attachmentIds }` → `{ parts: [{ label, suggested }] }` sub-topics found in the material |
| Topic | `GET` · `DELETE /api/topics/:id` · `POST /api/topics/:id/reset` | Detail with lesson states · delete · reset progress |
| Lesson | `POST /api/topics/:id/lessons/:lessonId/start` · `…/complete` | Open (or resume) the lesson conversation · mark done |
| Roleplays | `GET /api/roleplays` · `POST /api/roleplays` | List · create from `{ scenario, userRole?, aiRole? }` and open the first run |
| Roleplay | `POST /api/roleplays/:id/open` · `…/restart` · `DELETE /api/roleplays/:id` | Resume latest run · new run · delete |
| Free Talk | `POST /api/conversations` | New Free Talk thread (titled after the first reply). `{ levelCheck: true }` starts "Find my level" |
| Conversation | `GET` · `PATCH` · `DELETE /api/conversations/:id` | Thread with messages · rename / pin `{ title?, pinned? }` · delete |
| Share | `PUT /api/conversations/:id/share` | `{ visibility: "private" \| "public" }` → `{ visibility, shareUrl }`; public chats render at `GET /s/:token` |
| Report | `POST /api/conversations/:id/report` | `{ reason, details? }` (10/hour); emailed to `REPORTS_EMAIL` when set |
| Remind Me | `POST /api/conversations/:id/reminders` · `GET /api/reminders?status=pending` · `DELETE /api/reminders/:id` | "Remind me to continue" push at `{ remindAt }` · list · undo |
| Draft | `GET` · `PUT /api/conversations/:id/draft` | Unsent composer text and attachment ids, synced across devices |
| Schedules | `GET` · `POST /api/schedules` · `PATCH` · `DELETE /api/schedules/:id` | Scheduled practice (topic lesson or roleplay; once/daily/weekdays/weekly/monthly, time zone aware) |
| Schedule Actions | `GET /api/schedules/targets` · `POST /api/schedules/:id/skip-next` · `…/snooze` · `…/start` | Pickable topics/roleplays · skip the next occurrence · remind again later · open the session |
| Send Message | `POST /api/conversations/:id/messages` | `{ text, viaVoice?, attachmentIds? }` → `{ userMessage (with feedback), reply, lessonCompleted }` |
| Attachments | `POST /api/attachments` · `…/:id/complete` · `GET` · `DELETE /api/attachments/:id` | Presigned R2 upload → complete → poll until `ready` |
| Shared Link | `POST /api/attachments/link` | `{ url }` article or YouTube link; read in the background like a file (poll `GET` until `ready`) |
| Voice Transcript | `POST /api/conversations/:id/transcript` | `{ turns: [{ role, text }] }` saves turns spoken in a live voice session |
| Explain Grammar | `POST /api/messages/:id/explain` | Cached explanation of a tutor message |
| Typing Hint | `POST /api/tutor/hint` | `{ text }` → `{ hint \| null }` |
| LiveKit Voice Token | `POST /api/livekit/token` | `{ conversationId? }`, returns `{ token, url, room, identity }` |

All `/api/*` routes except auth require `Authorization: Bearer <token>`. AI routes are rate limited per user.

## Environment

In addition to the auth, database and LiveKit variables in `.env.example`:

- `GEMINI_API_KEY` (required): Google AI Studio key used for all tutor features.
- `GEMINI_MODEL` (optional, default `gemini-3.8-flash`).
- `PUBLIC_BASE_URL` (required in production): public origin used in share and unsubscribe links, e.g. `https://api.usezuno.rest`.
- `FIREBASE_SERVICE_ACCOUNT` (optional): Firebase Admin service account JSON (raw or base64). Without it push notifications are skipped.
- `REPORTS_EMAIL` (optional): receives an email for every chat report.
- Background jobs (reminders, schedules, daily reminder, weekly summary) run only with `NODE_ENV=production` (set in the Dockerfile) or `RUN_JOBS=1`, so a local dev server never claims production jobs. `DISABLE_JOBS=1` turns them off in production.

Background jobs live in the `job` table and are polled every 30 s with `FOR UPDATE SKIP LOCKED`, so several instances can share the queue. Run `npm test` for the schedule recurrence tests.

Schema changes: run `npm run db:push` against the target database.

For local testing without email, `npx tsx scripts/dev-session.ts create` prints a bearer token for a throwaway user (`delete` removes it).
| Asset Links | `GET /.well-known/assetlinks.json` | Android App Links verification |
| Verify Fallback | `GET /auth/verify?token=…&flowId=…` · `POST /auth/verify` | Browser landing page: forwards phones to `zuno://` without redeeming the token; "Confirm sign-in on my phone" (POST) redeems it for the waiting app |
