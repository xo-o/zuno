# Zuno AI — Peer Chat, 1:1 Calls & Push Notifications Flow

**Figma Reference:** [`Chats` (`2837:24615`)](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2837-24615) · [`Chats · 17 · Call Invite — Notification & Missed` (`2876:16836`)](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2876-16836)  
**Target Stack:** Android (`Kotlin / Jetpack Compose` + `FirebaseMessagingService` + `LiveKit`), Backend (`Node.js / Hono / Drizzle / Firebase Admin SDK`)

---

## 1. Core Product Rules

1. **System Notifications for Peer Messages & Call Invites (Out-of-Chat Only)**
   - When User A sends a 1:1 message or invites/pings User B to a 1:1 call, the backend sends a **high-priority data-only FCM push** to all of User B's registered devices (unless User B has muted that conversation).
   - On Android, `ZunoMessagingService` checks whether User B is **currently viewing that exact conversation in the foreground**:
     - **User IS in that chat** (`isAppInForeground == true` AND active route is `PeerChat(X)` or `PeerCall(X)`):
       - **Suppress** the Android system notification.
       - Emit an in-app `PeerPushEvent` so `PeerChatScreen` refreshes immediately (showing the new message bubble or `CallRoomInviteCard` + `IncomingInviteNotificationBanner`) without waiting for the 2-second poll.
     - **User is NOT in that chat** (app is backgrounded/killed, OR user is on `Home`, `Chats` list, `Settings`, an AI `Chat`, or a *different* `PeerChat(Y)`):
       - **Post** the Android system notification on the dedicated `messages` or `call_invites` channel.
       - Also emit `PeerPushEvent` so if `ChatsScreen` is open, its unread dot and snippet update immediately.
2. **Automatic Notification Cleanup**
   - Opening `PeerChat(X)` or `PeerCall(X)` cancels any active system notifications tagged `peer:<X>` in the notification shade.
3. **Per-Chat Mute Enforcement**
   - Both `sendPeerMessage` (including `triggerSeededReply`) and `startPeerCall` / `pingPeerCall` check `mutedByUserA` / `mutedByUserB` for the recipient before calling `sendToUser`.

---

## 2. End-to-End Flows

### 2.1 Peer Message Flow

```text
User A (Sender)                     Backend (Hono)                        User B (Recipient Android)
───────────────                     ──────────────                        ──────────────────────────
POST /api/chats/people/:id/messages
        │
        ├──────────────────────────► Insert peer_message
        │                            Update lastMessageText & unreadCount
        │                            Check recipient mute flag
        │                            (if not muted) ──► FCM data-only
        │                                               type="peer_message"
        │                                                      │
        │                                                      ▼
        │                                        ZunoMessagingService.onMessageReceived
        │                                          ├─ Emit PeerPushEvent.Message(convId)
        │                                          └─ Is app foreground & in PeerChat(convId)?
        │                                                ├─ YES ─► Suppress system notification;
        │                                                │         PeerChatScreen refreshes live
        │                                                └─ NO  ─► Post system notification
        │                                                          Channel: "messages" (HIGH)
        │                                                          Tap ─► DeepLink.PeerChat(convId)
```

### 2.2 1:1 Call Invite & Missed Invite Lifecycle

```text
User A (Host)                       Backend (Hono)                        User B (Guest Android)
─────────────                       ──────────────                        ──────────────────────
Tap Phone / "Invite to talk"
POST /api/chats/people/:id/call ──► Mark prior waiting calls "missed"
        │                           Create peer_call_room (status="waiting")
        │                           Insert peer_message (kind="call_invite")
        │                           Increment guest unreadCount
        │                           Mint LiveKit JWT (host)
        │                           (if not muted) ──► FCM data-only
        │                                              type="peer_invite"
        ▼                                                     │
Enters PeerCallScreen (waiting)                               ▼
  └─ Can tap "Ping" ──► POST /calls/:callId/ping      ZunoMessagingService.onMessageReceived
                        (sends peer_invite push)        ├─ Emit PeerPushEvent.CallInvite
                                                        └─ Is app foreground & in PeerChat(convId)?
                                                             ├─ YES ─► In-app banner + Invite Card
                                                             └─ NO  ─► System notification
                                                                       Channel: "call_invites" (HIGH)
                                                                       Actions: [Decline] [Join]
                                                                       Body tap ─► Open PeerChat(convId)
                                                                       [Join]   ─► DeepLink(joinCall=true) ─► PeerCallScreen
                                                                       [Decline]─► PeerCallActionReceiver ─► POST /calls/:id/decline
```

When the host ends the call before the guest joins (or the guest declines), `peer_call_room.status` transitions to `"ended"` or `"declined"` and `call_invite` messages transition to `"missed_invite"` / `"call_summary"`, which `PeerChatScreen` renders as `MissedInviteCard` or `PeerCallSummaryCard`.

---

## 3. Truth Table: When Does a System Notification Appear?

Evaluated by `Push.isInPeerChat(peerConversationId)` in `android/app/src/main/java/com/zuno/ai/push/Push.kt`:

| User B App State | Active Route | Incoming Chat ID | `isInPeerChat` | System Notification Posted? | In-App Refresh? |
|---|---|---|---|---|---|
| Killed / Backgrounded (`onStop`) | Any | `conv-1` | `false` | **Yes** | N/A (opened on tap) |
| Foreground (`onStart`) | `Home`, `Chats`, `Settings`, `Topics` | `conv-1` | `false` | **Yes** | Yes (`Chats` overview refreshes) |
| Foreground (`onStart`) | `Chat(ai-conv)` (AI Tutor) | `conv-1` | `false` | **Yes** | Yes |
| Foreground (`onStart`) | `PeerChat(conv-2)` (Different peer) | `conv-1` | `false` | **Yes** | Yes |
| Foreground (`onStart`) | `PeerChat(conv-1)` (Same peer chat) | `conv-1` | **`true`** | **No (suppressed)** | **Yes (instant reload)** |
| Foreground (`onStart`) | `PeerCall(conv-1)` (In that call) | `conv-1` | **`true`** | **No (suppressed)** | Yes |

*(In addition, if User B muted `conv-1`, the backend skips sending the FCM push entirely.)*

---

## 4. Backend Contract (`backend/`)

### 4.1 Database Tables (`backend/src/db/app-schema.ts`)
- `peer_conversation`: 1:1 thread per `(userAId, userBId, language)` (`userAId < userBId`), with `mutedByUserA`, `mutedByUserB`, `deletedByUserA`, `deletedByUserB`, `unreadCountA`, `unreadCountB`, `lastMessageText`, `lastMessageAt`.
- `peer_message`: Messages in a `peer_conversation` with `kind` in `["text", "call_invite", "missed_invite", "call_summary"]`, optional `callRoomId`, and `metadata`.
- `peer_call_room`: LiveKit call room (`livekitRoom`, `hostUserId`, `guestUserId`, `status` in `["waiting", "active", "ended", "declined", "missed"]`, `cameraEnabled`, `durationSec`).
- `device`: Registered Android FCM tokens (`userId`, `pushToken`, `platform`, `timezone`, `appLanguage`, `lastSeenAt`).

### 4.2 Endpoints
- **Devices (`backend/src/routes/devices.ts`)**
  - `POST /api/devices` — Upserts `{ pushToken, platform, timezone, appLanguage }` for the signed-in user.
  - `DELETE /api/devices/:pushToken` — Removes the token on sign-out.
- **Unified Chats & Peer Calls (`backend/src/routes/chats.ts`, `backend/src/lib/peers.ts`)**
  - `GET /api/chats/overview` — Returns `openToSpeak`, `openSpeakers`, `partners`, and unified `conversations` (AI + Peer sorted by `updatedAt`).
  - `PATCH /api/chats/open-to-speak` — Toggles `{ openToSpeak: boolean }`.
  - `POST /api/chats/people/:partnerId/open` — Finds or creates a `peer_conversation` and returns thread detail.
  - `GET /api/chats/people/:conversationId` — Returns thread detail, resets viewer's `unreadCount` to `0`, and returns `activeCall`.
  - `POST /api/chats/people/:conversationId/messages` — Persists message, increments recipient unread count, and sends `peer_message` push (unless muted).
  - `PATCH /api/chats/people/:conversationId` — Sets `{ muted: boolean }` for caller.
  - `POST /api/chats/people/:conversationId/call` — Creates `peer_call_room`, inserts `call_invite` message, increments unread count, mints host LiveKit token, and sends `peer_invite` push (unless muted).
  - `POST /api/chats/calls/:callId/join` — Marks waiting call `"active"` and mints LiveKit token for caller.
  - `POST /api/chats/calls/:callId/decline` — Marks call `"declined"` and updates `call_invite` message to `"missed_invite"`.
  - `POST /api/chats/calls/:callId/ping` — Sends a `peer_invite` reminder push (`peerPingTitle` / `peerPingBody`) to the partner.
  - `POST /api/chats/calls/:callId/end` — Marks call `"ended"`, converts waiting invite into `call_summary`, and updates conversation snippet.

### 4.3 FCM Data-Only Payloads (`backend/src/lib/push.ts`)
All pushes are sent via Firebase Admin `sendEachForMulticast` as **data-only**, `android.priority = "high"` messages so `ZunoMessagingService` always controls channel, gating, grouping, and deep links:

| `data.type` | Channel ID | Category | Required `data` fields | Optional `data` fields |
|---|---|---|---|---|
| `peer_message` | `messages` | `CATEGORY_MESSAGE` | `title`, `body`, `peerConversationId`, `partnerId` | `messageId` |
| `peer_invite` | `call_invites` | `CATEGORY_CALL` | `title`, `body`, `peerConversationId`, `partnerId`, `callId` | — |
| `reminder` | `reminders` | `CATEGORY_REMINDER` | `title`, `body` | `conversationId`, `reminderId` |
| `schedule` | `schedule` | `CATEGORY_REMINDER` | `title`, `body`, `scheduleId` | `topicId`, `roleplayId` |
| `progress` | `progress` | `CATEGORY_REMINDER` | `title`, `body` | `topicId` |
| `news` | `news` | `CATEGORY_RECOMMENDATION` | `title`, `body` | — |

---

## 5. Android Architecture (`android/`)

| File | Responsibility |
|---|---|
| `push/Push.kt` | Defines `Push.Channel`, tracks `isAppInForeground` + `activePeerConversationId`, emits `Push.peerEvents`, builds/posts notifications (`show`), clears per-chat notifications (`clearPeerNotifications`), handles `ZunoMessagingService`, `ScheduleActionReceiver`, and `PeerCallActionReceiver`. |
| `push/DeepLink.kt` | Parses `Intent` extras into `DeepLink.PeerChat(peerConversationId, partnerId, callId, joinCall)`, `Chat`, `Schedule`, `Topic`. |
| `MainActivity.kt` | Updates `Push.setAppForeground(true/false)` in `onStart()` / `onStop()`, ensures channels exist, and forwards launch / `onNewIntent` deep links to `ZunoApp`. |
| `ui/ZunoApp.kt` | Updates `Push.setActivePeerConversation(...)` on route changes, clears peer notifications on entry, collects `Push.peerEvents` for instant silent refresh, runs a 3s silent poll + `ON_RESUME` refresh while `Chats` is open, requests `POST_NOTIFICATIONS` on first `Chats` / `PeerChat` / `Open to speak` use, and handles `DeepLink.PeerChat` (including `joinCall = true`). |
| `ui/screens/PeerChatScreen.kt` | Renders text bubbles, `CallRoomInviteCard`, `MissedInviteCard`, `PeerCallSummaryCard`, in-app `IncomingInviteNotificationBanner`, and polls every 2s as a fallback alongside instant `Push.peerEvents`. |
| `AndroidManifest.xml` | Registers `ZunoMessagingService`, `ScheduleActionReceiver`, and `PeerCallActionReceiver`. |

### Notification Tags & IDs
- Peer notifications use `tag = "peer:<peerConversationId>"`.
- Message notifications use ID `"peer_msg:<peerConversationId>".hashCode()` (updates in place per thread without overwriting call invites).
- Call invite notifications use ID `"peer_call:<callId>".hashCode()` and include **Decline** and **Join** actions.
- Entering `PeerChat(id)` or `PeerCall(id)` calls `Push.clearPeerNotifications(context, id)`, cancelling all active notifications matching `tag == "peer:<id>"`.

---

## 6. Developer Setup & Testing Checklist

1. **Firebase Configuration**
   - Backend: set `FIREBASE_SERVICE_ACCOUNT` in `backend/.env` (raw JSON or base64-encoded service account JSON). Without it, `pushEnabled()` is `false` and `sendToUser` logs and no-ops.
   - Android: `android/app/google-services.json` must be present (`BuildConfig.PUSH_ENABLED == true`).
2. **Verify Device Token Registration**
   - Sign in on Android and grant `POST_NOTIFICATIONS` when prompted (Android 13+).
   - Confirm `POST /api/devices` returned `204` and a row exists in `device` for your `user_id`.
3. **Test "Not in That Chat" Suppression**
   - **Scenario A (In active chat)**: Open a 1:1 `PeerChat` with a partner (or a seeded learner like Lucía). Have the partner send a message or call invite (or send a message to Lucía and stay on the screen for 1s). Verify the reply/invite appears in-app with **no** system notification.
   - **Scenario B (On another screen)**: Send a message to Lucía and immediately tap Back to `Chats` or `Home` within 700ms (or have a second user send a message/invite while you are on `Home` / `Chats` / another `PeerChat`). Verify a system notification appears on the `Messages` or `Call invites` channel, and tapping it navigates straight to that `PeerChat`.
   - **Scenario C (App backgrounded)**: Press Home on Android while viewing `PeerChat(X)` and have a partner send a message/invite. Verify the system notification appears (because `isAppInForeground == false`).
   - **Scenario D (Muted chat)**: Mute the peer conversation in the overflow menu (`Mute chat`) and verify no system notification is sent.
