# ZunoAI — Pending design-sync tasks (Android + Backend)

Figma file: https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI (page **Mobile**)

Each task is self-contained and must ship **fully functional**: backend (schema + routes + jobs) and Android (UI + API + state). All new user-facing strings go into `strings.xml` for all 8 app locales (en, es, fr, de, it, pt-rBR, nl, pl).

## Codebase primer (read first)

**Android** — `android/app/src/main/java/com/talkie/ai/`
- Jetpack Compose only, Material3, **no** navigation lib: routes are `sealed interface ZunoRoute` in `ui/ZunoApp.kt` (helpers `navigate`, `replace`, `openSection`, `pop`).
- No DI. ViewModels: `viewmodel/ZunoViewModel.kt`, `viewmodel/VoiceSessionViewModel.kt`, `auth/AuthViewModel.kt` (StateFlow + SharedFlow).
- Networking: OkHttp + `org.json` in `data/ZunoApi.kt` (base `${AUTH_BASE_URL}/api`, Bearer token). Models in `data/ZunoModels.kt`.
- Design tokens: `ui/theme/ZunoTheme.kt` (`ZunoColors`, `ZunoSpace`, `ZunoRadius`, `ZunoType`, Lexend).
- Components: `ui/components/` — `Actions.kt` (ZunoButton, ZunoIconButton, ZunoChip, ZunoTextField), `Navigation.kt` (top bars, ZunoSessionRow), `Collections.kt` (ZunoOverflowMenu, ZunoMenuAction, ZunoListGroup), `Attachments.kt` (private `ZunoSheet`, `ZunoToast`, attach/reminder sheets), `Chat.kt`. Settings rows: `ZunoSettingsGroup`, `ZunoSettingsRow`, `ZunoSwitch` in `ui/screens/SettingsScreens.kt`.
- Toasts: design uses the pill `ZunoToast` (icon + text + optional action). Many flows still use platform `Toast.makeText` via `toast()` in `ZunoApp.kt` — new flows must use `ZunoToast`.
- No shared dialog component exists (raw `AlertDialog` in 2 places).

**Backend** — `backend/src/`
- Hono 4 + Better Auth (bearer, magic link, Google) + Drizzle/Postgres + Zod + R2 (`lib/storage.ts`) + Gemini (`lib/tutor.ts`) + Resend (`lib/email.ts`).
- Schema: `db/app-schema.ts`, `db/auth-schema.ts`. Applied with `npm run db:push` (no migrations folder).
- Routes: `routes/{profile,conversations,topics,roleplays,attachments}.ts`, mounted in `index.ts`. Rate limiting helper in `lib/http.ts`. Serializers in `lib/serializers.ts`.
- Languages: `lib/languages.ts` (`LANGUAGE_CODES = ["es","en"]`, `APP_LANGUAGE_CODES` = 8, `LEVELS` A1–C2).

## Suggested order / dependencies

| # | Task | Depends on |
|---|---|---|
| T0 | Shared UI primitives (dialog, toast host, menu, sheet) | — |
| T1 | Chat actions (rename, pin, share, report, delete) | T0 |
| T2 | Settings — sign-out dialog + signed-out flow | T0 |
| T3 | Settings — learning language (35 langs, search, switch, per-language progress) | T0 |
| T4 | Settings — App language visual sync + full string externalisation | — |
| T5 | Push notifications infrastructure (FCM) | — |
| T6 | Scheduled practice sessions | T0, T5 |
| T7 | Server-side "Remind me to continue" | T5 |
| T8 | Notification preferences (server-backed) | T5 |
| T9 | Preferences & learning goals (server-backed, used by tutor) | — |
| T10 | Attachments visual QA | — |
| T11 | Account: delete account, download data, change photo, plan | — |

---

## T0 — Shared UI primitives

**Figma (references):**
- Dialog: [Rename](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5526) · [Delete](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2170-6666) · [Sign out](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-9199) · [Switch language](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-8316)
- Menu: [Chat actions · 1 · Menu open](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5407)
- Toast: [Renamed](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5710) · [Signed out](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-9267)

**Android**
- `ui/components/Dialogs.kt` → `ZunoDialog(title, body?, content?, confirmLabel, onConfirm, dismissLabel, onDismiss, destructive = false, confirmEnabled = true)`: dark surface card, rounded `ZunoRadius`, title + body, right-aligned `Cancel` (ghost pill) + confirm (white pill, or red when `destructive`). Optional `content` slot for a text field (rename).
- Make `ZunoSheet` (in `Attachments.kt`) public / move to `ui/components/Sheets.kt` for reuse.
- Restyle `ZunoOverflowMenu` / `ZunoMenuAction` to support a leading icon and `destructive` (red) style, dark surface, matching the menu frame.
- Toast host: a single app-level `ZunoToast` overlay driven by a `SharedFlow<ToastEvent(message, icon?, actionLabel?, onAction?)>` exposed from `ZunoViewModel` (or a small `ToastController`), so any screen can emit a toast, and toasts survive navigation (e.g. delete → Home).

**Acceptance:** components render per Figma; previews added; existing grammar/edit-name `AlertDialog`s migrated to `ZunoDialog`.

---

## T1 — Chat actions (Rename, Pin, Share, Report, Delete)

**Figma:** section [09 · Chat actions](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5406)
1. [Menu open](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5407)
2. [Rename](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5526)
3. [Renamed](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5710)
4. [Pinned](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5768)
5. [Drawer · Pinned](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5826)
6. [Menu · Pinned chat](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2169-5991)
7. [Share · Only me](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2170-6065)
8. [Share · Public access](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2170-6223)
9. [Report](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2170-6403)
10. [Report · Reason picked](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2170-6505)
11. [Report sent](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2170-6608)
12. [Delete](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2170-6666)
13. [Deleted → Home](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2170-6764)

**Current state:** chat overflow menu built in `ui/ZunoApp.kt` (~L884–912) has "Remind me to continue", "Turn into a topic", lesson/roleplay items and "Delete conversation" (deletes immediately, no confirm). Rename/Pin/Share/Report don't exist anywhere. Backend `conversation` table has `title` but no pinned/visibility/share columns; delete is a hard delete (`DELETE /api/conversations/:id`).

### Backend
Schema (`db/app-schema.ts`):
- `conversation`: add `pinned_at timestamp null`, `visibility text not null default 'private'` (`private|public`), `share_token text unique null`, `title_edited boolean not null default false` (stop auto-titling once user renamed — guard in `lib/conversations.ts` auto-title logic).
- New table `chat_report`: `id`, `user_id` → user, `conversation_id` → conversation (on delete cascade), `reason text` (`wrong_correction|wrong_language_level|offensive|unsafe|other`), `details text null` (≤1000 chars), `status text default 'open'`, `created_at`.

Routes (`routes/conversations.ts`, all owner-scoped, 404 if not owner):
- `PATCH /api/conversations/:id` body `{ title?: string(1..80, trimmed), pinned?: boolean }` → returns serialized conversation. Setting title sets `title_edited = true`. `pinned=true` sets `pinned_at = now()`, false nulls it.
- `PUT /api/conversations/:id/share` body `{ visibility: "private"|"public" }` → `{ visibility, shareUrl | null }`. On first `public`, generate a URL-safe random `share_token` (≥ 22 chars). Switching to private keeps the token but the public page must 404. `shareUrl = ${PUBLIC_BASE_URL}/s/${token}`.
- `POST /api/conversations/:id/report` body `{ reason, details? }` → `201 { id }`. Rate-limit (e.g. 10/hour/user via `lib/http.ts`). Optionally email the team via Resend if `REPORTS_EMAIL` env is set.
- `GET /api/conversations/recent`: include `pinned: boolean`, `pinnedAt`, `visibility`; order pinned first (by `pinned_at desc`), then `updated_at desc`. Pinning applies to conversation rows (Free Talk; for topic/roleplay rows pin the underlying conversation).
- `GET /api/conversations/:id`: include `pinned`, `visibility`, `shareUrl`.
- Public page `GET /s/:token` (in `index.ts`, no auth): server-rendered read-only HTML of the chat (title, kind, messages with translations; no attachments' private URLs — use short-lived signed URLs for images or omit). 404 when token unknown or visibility ≠ public. Add basic OG meta tags.
- `DELETE /api/conversations/:id` unchanged (reports cascade).

### Android
- `data/ZunoApi.kt`: `renameConversation(id, title)`, `setPinned(id, pinned)`, `setShare(id, visibility): ShareResult`, `reportConversation(id, reason, details)`. Extend models (`ZunoModels.kt`) for session/conversation with `pinned`, `visibility`, `shareUrl`.
- `ZunoViewModel`: corresponding functions, optimistic update of current conversation title + recent sessions list, rollback + error toast on failure.
- Chat menu (top-right ⋮, `ZunoOverflowMenu` with icons): **Rename**, **Pin / Unpin**, **Share**, **Report**, divider, **Delete** (red). Keep contextual items (Mark lesson complete, Back to topic, Start over, Turn into a topic) below the design items. Remove "Remind me to continue" from the menu (it lives in the attach sheet).
- Rename: `ZunoDialog` "Rename chat" with prefilled, focused, all-selected text field, keyboard up; Save disabled when blank/unchanged → top bar title updates → toast "Chat renamed".
- Pin: toast "Chat pinned to the top" (pin icon); Unpin toast "Chat unpinned". Menu label toggles.
- Drawer (`ui/screens/AppDrawer.kt`): new **Pinned** section above "Recent sessions" (hidden when empty); pinned rows removed from Recent; row shows pin icon + kind meta.
- Share sheet: title "Share"; two selectable rows in a group — "Only me / Visible only to you" (lock icon) and "Public access / Anyone with the link can view" (globe icon), check on selected; buttons **Share** (secondary) and **Copy link** (primary). Both disabled while Only me. Selecting Public calls API, then Share → `Intent.ACTION_SEND` chooser with the URL; Copy link → clipboard + toast "Link copied".
- Report sheet: title "Report chat", subtitle "What went wrong? Reports help Zuno teach better.", 5 radio rows (Wrong correction or translation / Wrong language or level / Offensive or inappropriate / Unsafe or harmful advice / Something else), multiline "Add more details (optional)", **Submit report** disabled until a reason is picked → closes sheet → toast "Thanks — we'll review this chat".
- Delete: `ZunoDialog` "Delete chat?" body "“{title}” and its corrections will be deleted. Words saved to Vocabulary stay. This can't be undone." Cancel / **Delete** (red) → API → navigate to Home (clear chat from back stack) → toast "Chat deleted" (trash icon) → row removed from drawer. Also clear the conversation's draft (`DraftStore`) and cancel any local reminder for it (`reminders/Reminders.kt`).

**Acceptance:** every frame above reproducible; actions persist across app restart and devices; public link opens in a browser while public and 404s after switching to Only me; report rows stored in DB.

---

## T2 — Settings: sign-out confirmation + signed-out flow

**Figma:** [Settings · 8 · Sign out](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-9199) · [Settings · 9 · Signed out → Sign in](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-9267) · overview [Settings · 1](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2148-4711) / [Settings · 2 · Scrolled](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2148-4940)

**Current state:** `onSignOut = { authViewModel.signOut() }` (`ZunoApp.kt` ~L972) signs out immediately; the `AuthState.SignedOut` handler (~L359) navigates to **Onboarding** with no toast. Settings header falls back to hard-coded "Noah Carter" and plan "Free".

### Android
- Tap "Sign out" → `ZunoDialog` "Sign out?" body "You can sign back in anytime. Your progress, vocabulary and chats stay saved to your account." Cancel / **Sign out** (red).
- Confirm → existing sign-out (revoke + clear token + clear VM + clear reminders + end voice) → `replace` back stack with `Auth(SignIn)` (not Onboarding) → toast "You've signed out" (pill at top, per frame 9).
- Session-expired / 401 sign-outs keep current behaviour but also land on Sign in.
- Remove the "Noah Carter" fallback (show skeleton/blank while loading).

### Backend
- None required (Better Auth `POST /api/auth/sign-out` already revokes). On sign-out also delete the device's push token (see T5: `DELETE /api/devices/:token`) once T5 lands.

---

## T3 — Settings: learning language (35 languages, search, switch, per-language progress)

**Figma:**
- [Settings · 5 · Learning](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2153-5222)
- [Settings · 5a · Learning language](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-6626)
- [Settings · 5a · Learning language — full list](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-7335)
- [Settings · 5b · Search languages](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-8045)
- [Settings · 5c · Switch language](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-8316)
- [Settings · 5d · Learning — Japanese](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2177-9075)

**Current state:** `enum TargetLanguage` (`data/ZunoModels.kt`) has only Spanish/English; the Language row cycles on tap. Accent, daily goal and focus are local-only (`SharedPreferences`). Backend accepts `es|en` only; `user_profile` has a single `level`.

**Languages (35, A→Z by English name; native / English):** العربية Arabic · বাংলা Bengali · 粵語 Cantonese · 中文（普通话） Chinese (Mandarin) · Čeština Czech · Dansk Danish · Nederlands Dutch · English · Suomi Finnish · Français French · Deutsch German · Ελληνικά Greek · עברית Hebrew · हिन्दी Hindi · Magyar Hungarian · Bahasa Indonesia Indonesian · Italiano Italian · 日本語 Japanese · 한국어 Korean · Bahasa Melayu Malay · Norsk Norwegian · فارسی Persian · Polski Polish · Português Portuguese · Română Romanian · Русский Russian · Español Spanish · Kiswahili Swahili · Svenska Swedish · Tagalog · ไทย Thai · Türkçe Turkish · Українська Ukrainian · اردو Urdu · Tiếng Việt Vietnamese.

### Backend
- `lib/languages.ts`: expand `LANGUAGE_CODES` to the 35 codes (`ar, bn, yue, zh, cs, da, nl, en, fi, fr, de, el, he, hi, hu, id, it, ja, ko, ms, no, fa, pl, pt, ro, ru, es, sw, sv, tl, th, tr, uk, ur, vi`), each with `{ code, englishName, nativeName, accents: [{id, label}], defaultAccent, rtl }` (e.g. es: Latin America / Spain; en: US / UK; ja: Standard (Tokyo); pt: Brazil / Portugal; fr: France / Canada; zh: Mainland; ar: Modern Standard…). Make sure `LANGUAGE_NAMES` used by `lib/tutor.ts` covers all.
- New table `user_language_progress`: PK (`user_id`, `language`), `level text default 'B1'`, `accent text`, `level_checked_at timestamp null`, `last_active_at`, timestamps. Backfill from existing `user_profile` rows.
- `user_profile`: add `accent text null` (current accent), keep `target_language`, `level` as the *current* language's values (denormalised for the tutor).
- `GET /api/languages` → list above (public or authed).
- `PATCH /api/me`:
  - `targetLanguage` change: persist current `level/accent` into `user_language_progress` for the old language, then load (or create with `levelCheckedAt = null`) the row for the new language into `user_profile`.
  - Accept `accent` (validated against the language's accents) and `level` (also write-through to progress).
- `GET /api/me`: include `accent`, `levelChecked: boolean`, `languages: [{ language, level, accent, levelChecked, lastActiveAt }]`.
- Per-language scoping: `GET /api/conversations/recent`, `GET /api/topics`, `GET /api/roleplays` filter by `language = profile.target_language` (columns already exist). New Free Talks/topics/roleplays already store `language`.
- Tutor prompts (`lib/tutor.ts`): include accent ("Use {accent} {language}"); ensure prompts work for non-Latin scripts (romanisation hints for ja/zh/ko/ar/hi/th when level ≤ A2).
- LiveKit / voice agent (`index.ts` `/api/livekit/token` and `voice-agent/agent.py`): pass language + accent; verify STT/TTS voices exist for each language or fall back gracefully (document unsupported ones).

### Android
- Replace `enum TargetLanguage` with server-driven `LearningLanguage` model (`GET /api/languages`, cached), keep a bundled fallback list.
- New route `SettingsLearningLanguage` (5a): intro text, search field, **Learning now** group (current language + "Spanish · B1" meta + check), **All languages** group A→Z, native name as title + English name as subtitle; long list scrolls (frame "full list").
- Search (5b): filters by English or native name, diacritic/case-insensitive; shows "N results"; keyboard open.
- Tap a language (5c) → `ZunoDialog` "Switch to {Language}?" body "Your {current} progress, vocabulary and chats are saved — you can switch back anytime. Next, a 5-minute chat with Zuno finds your {Language} level." Cancel / **Switch**. If the language was learned before, body omits the level-check sentence.
- On switch (5d) → back to Learning screen showing new language, its default accent, toast "Now learning {Language}"; if `levelChecked=false` the level card is replaced by **Find my level · 5-minute chat with Zuno in {Language}** (starts the level-check Free Talk; mark `levelCheckedAt` when the level-check flow sets a level).
- Accent row opens a single-select sheet/screen with the language's accents (server-backed, remove local `learn_accent_idx`).
- Home selector, drawer profile meta ("Spanish · B1") and recent sessions refresh after switch.

**Acceptance:** all 35 languages selectable and searchable; switching back restores previous level/accent and that language's chats/topics/roleplays; tutor replies in the chosen language/accent.

---

## T4 — App language visual sync + full string externalisation

**Figma:** [Settings · 4 · App language](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2153-5078) · [Settings · 1 · Overview](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2148-4711)

**Current state:** switching works (`selectAppLanguage` in `ZunoApp.kt`, `AppCompatDelegate.setApplicationLocales`, PATCH `/me` `nativeLanguage`). But only `SettingsScreens.kt`, `HomeScreen.kt`, `AppDrawer.kt` use `stringResource`; Chat, Auth, Collections, Create, Topic, Welcome screens and all components are hard-coded English.

**Android**
- Match frame 4: intro "Menus, hints and Zuno's explanations appear in this language. Your practice language doesn't change.", rows native name + English name subtitle, check on selected.
- Externalise **every** hard-coded string in `ui/screens/*` and `ui/components/*` (incl. toasts, content descriptions, reminder options) into `res/values*/strings.xml` and translate to the 8 locales. Use plurals where needed.
- Add a lint check / CI grep to block new hard-coded `Text("...")`.

**Backend:** none (already stored as `user_profile.native_language`). Verify server-generated texts (topic titles' translations, explanations, reminder/push copy from T5–T8, share page) use `native_language`.

---

## T5 — Push notifications infrastructure (FCM)

**Figma (consumers):** [Attach · 7 · Reminder notification](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2116-3903) · [Scheduled · 9 · Time to practice](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-4871)

**Current state:** no FCM; reminders are local AlarmManager (`reminders/Reminders.kt`). No device/push tables on backend.

### Backend
- Table `device`: `id`, `user_id`, `platform` (`android`), `push_token unique`, `app_language`, `timezone` (IANA), `last_seen_at`, timestamps.
- `POST /api/devices` `{ pushToken, platform, timezone, appLanguage }` (upsert); `DELETE /api/devices/:pushToken`.
- `lib/push.ts`: Firebase Admin SDK (`FIREBASE_SERVICE_ACCOUNT` env), `sendToUser(userId, { title, body, data })`, prune tokens on `UNREGISTERED`.
- Job runner: a durable DB-backed queue (`job` table: `id, type, run_at, payload jsonb, status, attempts, locked_at`) polled every 30 s with `FOR UPDATE SKIP LOCKED`, started from `index.ts` alongside `startAttachmentJobs`. Used by T6/T7/T8.
- Add env vars to `docker-compose.yml` / README.

### Android
- Add Firebase Messaging (google-services plugin, `google-services.json` instructions). `ZunoMessagingService`: `onNewToken` → `POST /api/devices`; `onMessageReceived` → build notification on channel by `data.type` (`reminder`, `schedule`, `progress`, `news`) with deep-link extras (`conversationId`, `scheduleId`, `topicId`, `roleplayId`) handled by `MainActivity` → `openChatRequest` / new route requests.
- Register token after sign-in, delete on sign-out (T2). Request `POST_NOTIFICATIONS` on Android 13+ at first relevant action.

---

## T6 — Scheduled practice sessions (NEW feature)

**Figma:** section [07 · Scheduled](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-3586)
1. [Drawer](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-3587)
2. [Empty](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-3735)
3. [List](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-3785)
4. [Choose (Topics)](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-3993)
5. [Choose (Roleplays)](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-4109)
6. [When (Daily)](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2121-4506)
7. [Repeat menu](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2121-4715)
8. [Once](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2121-4926)
9. [Weekly (multiple days)](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2121-5122)
10. [Monthly](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2121-5318)
11. [Added](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-4415)
12. [Item actions](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-4635)
13. [Time to practice (notification)](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2117-4871)

**Current state:** not implemented anywhere (drawer has no "Scheduled" item).

### Backend
- Table `schedule`: `id`, `user_id`, `target_kind` (`topic|roleplay`), `topic_id` / `roleplay_id` (FK, cascade delete), `repeat` (`once|daily|weekdays|weekly|monthly`), `time_of_day` (`HH:mm`), `date` (for once), `weekdays int[]` (0–6, weekly), `month_day int` (1–31, clamp to month end), `timezone`, `duration_min`, `active bool`, `remind_before_min int null` (10 default), `notify_email bool`, `skip_dates date[]`, `next_run_at`, `language`, timestamps.
- `lib/schedules.ts`: `computeNextRun(schedule, now)` (TZ-aware incl. DST; unit tests for all repeat types + skip dates).
- Routes `routes/schedules.ts`:
  - `GET /api/schedules` → upcoming occurrences grouped-ready (`nextRunAt`, target title, kind, meta: "Topic · Lesson 2", "You: Customer · Zuno: Barista", duration, repeat label).
  - `POST /api/schedules`, `PATCH /api/schedules/:id`, `DELETE /api/schedules/:id`.
  - `POST /api/schedules/:id/skip-next` ("Skip tomorrow only").
  - `POST /api/schedules/:id/start` → opens/creates the right conversation (next unfinished topic lesson via existing lesson start logic, or roleplay open) → `{ conversationId }`.
  - `GET /api/schedules/targets` → topics (with "Next: Lesson X of 5 · N min" / "Completed · Review") and roleplays (roles + duration) for step 1.
- Jobs (T5 queue): at `next_run_at - remind_before_min` send push "Roleplay in 10 min: {title}" / body "You: Customer · Zuno: Barista. Tap to start now or snooze." (+ email via Resend if `notify_email`); at `next_run_at` advance `next_run_at`. Respect notification prefs (T8 `scheduled_sessions`). Once-schedules deactivate after firing.

### Android
- Drawer: add **Scheduled** item (calendar icon) between Free Talk and Topics.
- `ScheduledScreen`: empty state (calendar icon, "Nothing scheduled", "Plan practice ahead. Pick a topic or roleplay and Zuno will remind you when it's time.", **Schedule** pill bottom-right); list grouped **Today / Tomorrow / Later this week / Later** with time + duration column, title, kind meta, repeat badge; the soonest today item shows a start (→) button.
- `NewScheduleScreen` (2 steps): Step 1 segmented **Topics / Roleplays**, single-select list, **Continue** disabled until selected. Step 2: selected item card, summary line ("Daily · 9:00 AM", "Weekly · Mon, Wed, Fri · 9:00 AM", "Monthly · Day 7 · 9:00 AM", "Once · Sun, Nov 8, 9:00 AM"), **Repeat** dropdown (Once/Daily/Weekdays/Weekly/Monthly), Date (once), day chips M–S multi-select (weekly), day-of-month grid 1–31 (monthly), Time picker; **More**: Active switch ("Paused schedules don't run"), Remind me before ("10 minutes before it starts"), Notify by email; **Schedule** button.
- After create: back to list, new item highlighted, toast "Scheduled · Daily at 9:00 AM" with **Undo** (deletes).
- Item tap → bottom sheet: title + "Roleplay · Daily at 9:00 AM · 15 min", **Start now**, **Edit time & repeat** (opens step 2 prefilled), **Skip tomorrow only**, **Delete schedule** (red, confirm via `ZunoDialog`).
- Notification tap → start session (`/start`) and open chat. Optional snooze action (+10 min) via notification action → `PATCH`.

---

## T7 — Server-side "Remind me to continue"

**Figma:** [Attach · 5 · Remind me to continue](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2111-3544) · [Attach · 6 · Reminder set](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2116-3831) · [Attach · 7 · Reminder notification](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2116-3903) · [Attach · 8 · Chat reopened as left](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2116-3928)

**Current state:** UI done; scheduling is local-only (`reminders/Reminders.kt`, AlarmManager + SharedPreferences) → lost on reinstall/other devices; drafts are local (`DraftStore`).

### Backend
- Table `chat_reminder`: `id`, `user_id`, `conversation_id` (cascade), `remind_at`, `status` (`pending|sent|cancelled`), `created_at`.
- `POST /api/conversations/:id/reminders` `{ remindAt }` → `{ id }`; `DELETE /api/reminders/:id` (Undo); `GET /api/reminders?status=pending`.
- Job: at `remind_at` push `Continue “{title}”` / `You left off at: “{last assistant message}” Tap to jump back in.` with `conversationId`.
- Optional: `PUT /api/conversations/:id/draft` `{ text, attachmentIds }` so "reopens exactly as left" works cross-device.

### Android
- `setReminder` → API first; keep AlarmManager as offline fallback only when FCM unavailable; dedupe by reminder id. Undo → `DELETE`. Remove local reminders on sign-out (already) and server ones on chat delete (cascade).

---

## T8 — Notification preferences (server-backed)

**Figma:** [Settings · 7 · Notifications](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2153-5545)

**Current state:** screen exists (`SettingsNotificationsScreen`) but values are local-only and nothing reads them; no daily reminder is ever sent.

### Backend
- Table `notification_prefs` (PK `user_id`): `daily_reminder bool`, `reminder_time HH:mm` (default 19:00), `timezone`, `scheduled_sessions bool`, `progress_milestones bool`, `new_content bool`, `weekly_summary_email bool`, `product_news_email bool`.
- `GET /api/me/notifications`, `PATCH /api/me/notifications`.
- Jobs: daily practice reminder at user-local `reminder_time` (skip if user practiced today; copy localized by `native_language`); weekly summary email (Resend) — words learned, minutes practiced, sessions; progress milestone pushes (e.g. topic completed, streak N). Unsubscribe link in emails (`GET /api/email/unsubscribe?token=`).
- All senders (T6, T7, this) check prefs.

### Android
- Bind screen to API (optimistic, error toast rollback); remove local prefs; time picker updates `reminder_time` and device timezone. Footer note "You can also turn notifications off in your phone's settings." links to system notification settings.

---

## T9 — Preferences & learning goals (server-backed, used by tutor)

**Figma:** [Settings · 3 · Preferences](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2151-5353) · [Settings · 5 · Learning](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2153-5222)

**Current state:** voice, teaching style, strict mode, hands-free, suggest replies, show translations, teach in target language, daily goal and focus are stored only in `zuno_prefs` SharedPreferences; nothing reaches the backend or tutor.

### Backend
- `user_profile` (or `user_preferences` table): `tutor_voice`, `teaching_style` (`gentle|balanced|strict`…), `strict_mode`, `hands_free`, `suggest_replies`, `show_translations`, `teach_in_target`, `daily_goal_min`, `focus` (`conversation|travel|work|exams`).
- `GET/PATCH /api/me` include/accept these (Zod-validated).
- `lib/tutor.ts`: inject teaching style / strict mode (blunt corrections), teach-in-target (explanations in target language instead of native), focus (topic bias); skip `translation` generation when `show_translations=false`; `suggest_replies` gates `/api/tutor/hint`.
- `/api/livekit/token` + `voice-agent/agent.py`: use `tutor_voice` and hands-free settings.

### Android
- Bind both screens to the API; remove local storage; chat UI hides translations when off; hint chips obey `suggest_replies`; hands-free toggles voice call VAD mode.

---

## T10 — Attachments visual QA

**Figma:** section [06 · Attachments](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2109-2823)
[Sheet open](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2109-2824) · [Composer with attachments](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2109-2980) · [Uploading & error](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2109-3121) · [Sent photo → contextual reply](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2111-3106) · [Remind me to continue](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2111-3544) · [Reminder set](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2116-3831) · [Reminder notification](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2116-3903) · [Chat reopened as left](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2116-3928) · [New topic with material](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2111-4074) · [Material added & analysed](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2111-4153)

**Current state:** functionally complete end-to-end (upload pipeline, retry, link reading, topic analysis, local reminders). Needs a pixel pass.

**Android checklist:** sheet title "Add to this chat", 3 tiles (Camera/Photos/Files), "More" group (Paste a link / Remind me to continue with subtitles); composer attachment chips (thumbnail + PDF card "PDF · 2.4 MB"); uploading spinner overlay & "Failed · Tap to retry" + send disabled while uploading/failed; context status "Read menu photo · 4 items found"; glossary rows; reminder sheet copy + chips; "Reminder set · Tonight 9:00 PM · Undo" pill toast; notification title/body copy per frame 7; New topic material counter "Learning material · 4", file meta "Read · 12 pages · 3 sections" / "Uploading · 64%", "Found in your material" chips + helper text, CTA "Create topic · 5 lessons".

**Backend:** expose page/section counts in `AttachmentDigest` if missing (for "Read · 12 pages · 3 sections").

---

## T11 — Account: delete account, download data, change photo, plan

**Figma:** [Settings · 6 · Account](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2153-5368) · [Settings · 1 · Overview](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2148-4711)

**Current state:** Change photo, Plan, Restore purchases, Download my data, Delete account, About, Contact support are "coming soon" toasts. No plan/subscription data on backend.

### Backend
- Avatar: `POST /api/me/avatar` → presigned R2 PUT (image ≤ 5 MB) + `POST /api/me/avatar/complete` → sets `user.image`; `DELETE /api/me/avatar`.
- Delete account: `DELETE /api/me` → deletes R2 files, all app rows, sessions, devices, then the Better Auth user (or use Better Auth `deleteUser`); confirmation email.
- Download data: `POST /api/me/export` → job builds JSON/ZIP (profile, conversations, messages, topics, roleplays, schedules) to R2 and emails a signed link; `GET /api/me/export/:id` status.
- Plan: `user_profile.plan` (`free|pro`), `plan_expires_at`; `GET /api/me` returns it. (Google Play Billing verification endpoint `POST /api/billing/google/verify` — separate follow-up once paywall design exists.)
- "Signed in with Google": `GET /api/me` returns linked providers from `account` table.

### Android
- Change photo: Photo picker → crop/compress → upload → avatar updates in Settings, drawer, header.
- Delete account: `ZunoDialog` (destructive, type-to-confirm optional) → API → sign out flow → Sign in + toast.
- Download my data: request → toast "We'll email you a download link".
- Plan row shows real plan; Restore purchases disabled until billing exists; Upgrade banner hidden for Pro.
- About: version, Terms, Privacy (web links). Contact support: `mailto:` intent with app/device info.
