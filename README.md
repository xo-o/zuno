# Zuno AI - Real-Time Conversational Voice AI

A low-latency, real-time Voice AI conversational system featuring a native Android application (**Kotlin & Jetpack Compose**), an Auth & API server (**Node.js, TypeScript, Hono, Better Auth & Drizzle ORM**), a real-time Voice AI worker (**Python LiveKit Agents, Silero VAD, Deepgram STT, Google Gemini 2.5 Flash LLM, and Cartesia Sonic-3 Multilingual TTS**), and WebRTC transport via **LiveKit Cloud**.

---

## Architecture Overview

```
 [ Android Native App (Kotlin / Jetpack Compose) ]
         │                                │
  HTTP (Auth/Token)               WebRTC Audio Stream (Opus)
         ▼                                ▼
 [ Backend API (Node.js + Hono) ] [ LiveKit Cloud WebRTC ]
  - Better Auth (OAuth / Magic)           ▲
  - PostgreSQL / Drizzle                  │
  - LiveKit Token Issuer          WebRTC Audio Stream (Opus)
                                          │
                         [ Voice AI Worker (Python) ]
                           ├── Silero VAD (Instant interruption / barge-in)
                           ├── Deepgram STT (Streaming speech-to-text)
                           ├── Google Gemini 2.5 Flash (Conversational LLM)
                           └── Cartesia Sonic-3 Multilingual TTS
```

---

## Project Structure

```text
player/
├── android/                       # Native Android App (Kotlin & Jetpack Compose)
│   ├── build.gradle.kts           # Root Gradle configuration
│   ├── settings.gradle.kts        # Project settings
│   └── app/
│       ├── build.gradle.kts       # App dependencies (LiveKit Android SDK, Compose)
│       └── src/main/java/com/zuno/ai/
│           ├── MainActivity.kt    # App entry point & screen navigation
│           ├── auth/              # Google & Magic Link Auth
│           ├── model/             # State, voice catalog, and transcript models
│           ├── service/           # Token fetcher & client-side fallback
│           ├── viewmodel/         # VoiceSessionViewModel (LiveKit room & data channel), ZunoViewModel
│           └── ui/                # Jetpack Compose screens & components
│
├── backend/                       # Auth & API Server (Node.js TypeScript + Hono + Better Auth)
│   ├── src/index.ts               # Hono HTTP server & route handlers
│   ├── src/lib/auth.ts            # Better Auth configuration
│   ├── src/db/                    # Drizzle ORM schema & migrations
│   ├── Dockerfile                 # Multi-stage production container
│   ├── .env.example               # Environment variables template
│   └── package.json
│
├── voice-agent/                   # Real-time Voice AI Worker (Python LiveKit Agents)
│   ├── agent.py                   # LiveKit conversational & lesson worker
│   ├── lesson_flow.py             # 5-stage 03g interactive voice lesson orchestrator
│   ├── voices.py                  # Cartesia Sonic-3 multilingual voice configuration
│   ├── token_server.py            # Local token dispenser (optional)
│   ├── Dockerfile                 # Voice agent container with WebRTC & audio libraries
│   ├── requirements.txt           # Python dependencies
│   └── .env.example               # Environment variables template
│
└── docker-compose.yml             # Docker stack: PostgreSQL + Backend + Voice Agent
```

---

## Quickstart Guide

### 1. Configure Environment Variables

- In `voice-agent/`:
  ```bash
  cp voice-agent/.env.example voice-agent/.env
  # Fill in LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET, DEEPGRAM_API_KEY, GEMINI_API_KEY
  ```

- In `backend/`:
  ```bash
  cp backend/.env.example backend/.env
  # Fill in database, Better Auth, and LiveKit credentials
  ```

### 2. Start the Backend API (Auth & Token Server)

```bash
cd backend
npm install
npm run dev
```

### 3. Start the Voice AI Worker

In the `voice-agent/` directory:

```bash
cd voice-agent
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python agent.py dev
```

The agent will register with your LiveKit Cloud project and automatically join any room when a user connects.

### 4. Build & Run the Android App

In the `android/` directory (or open `android/` directly in Android Studio):

```bash
cd android

# Assemble debug APK
./gradlew assembleDebug

# Install on connected Android device or emulator
./gradlew installDebug
```

The debug APK is generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## Key Features & Highlights

1. **Sub-second Conversational Latency**: WebRTC (UDP) audio transport using Opus codec with hardware Acoustic Echo Cancellation (AEC).
2. **Instant Barge-in (Interruption)**: When you speak while the AI is talking, Silero VAD detects speech and immediately cuts off playback, allowing natural dialogue.
3. **Dual-Channel Live Transcripts**: Speech text, voice switching, and agent state changes stream in parallel over WebRTC Data Channels.
4. **Native Jetpack Compose UI**: Liquid glass styling and animated voice visualizers built natively for Android.
5. **Secure Authentication**: Better Auth integration supporting Google Sign-In and Magic Links with Digital Asset Links verification.
