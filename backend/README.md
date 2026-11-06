# Zuno Backend API

Fast, lightweight Node.js & TypeScript API built with [Hono](https://hono.dev), [Better Auth](https://better-auth.com), and [Drizzle ORM](https://orm.drizzle.team).

Features:
- **Better Auth**: Google Sign-In & Magic Links via Resend. Sign in with Apple ready to enable.
- **LiveKit Token Service**: Issues JWT access tokens for WebRTC voice rooms.
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
| User Profile | `GET /api/me` | Protected route returning user data |
| LiveKit Voice Token | `POST /api/livekit/token` | With `Authorization: Bearer <token>`, returns `{ token, url, room, identity }` |
| Asset Links | `GET /.well-known/assetlinks.json` | Android App Links verification |
| Verify Fallback | `GET /auth/verify?token=…` | Web landing fallback redirecting to `zuno://` |
