# ZunoAI — Topics & Roleplays Discovery, Community Sharing & Flows Specification (`03 · Topics` & `04 · Roleplays`)

- **Figma References:**
  - [`03 · Topics`](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2677-27327) (`2677:27327`)
  - [`04 · Roleplays`](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2076-2686) (`2076:2686`)
  - [`07 · Attachments` (Mid-Chat Builder Uploads)](https://www.figma.com/design/TeAncQWV4v2VSmxNGDGIxJ/ZunoAI?node-id=2109-2823) (`2109:2823` · `3011:14037`, `3011:14115`)

**Target Stack:** Android (`Kotlin / Jetpack Compose` + `ZunoViewModel`), Backend (`Node.js / Hono / Drizzle / Gemini`), Voice Worker (`Python LiveKit Agents`)

---

## 1. End-to-End Flow Overview

Both **Topics** and **Roleplays** keep the root library screens clean and focused **solely on the user's own added and saved items** (no `All | Mine | Saved | Public` ownership filter clutter).

- **Empty State (`06`, `Roleplays · 1`):** Invites learners to either **Create** or **Browse community** (Zuno Official + community-shared items).
- **`+` Add Choice Drawer (`07`, `Roleplays · 4`):** Tapping **`+`** opens a bottom sheet offering:
  1. **Create a new topic / roleplay** → **Interactive Creation Chat with Sofía** (`10a` → `10b`, `Roleplays · 3a` → `3b`): Sofía greets the learner first, asks what the topic or scene should be about, accepts text/voice/uploaded assets (PDFs, photos, notes) as context, and once ready presents a draft card with **`Refine`** or **`Create topic` / `Create roleplay`**.
  2. **Find community topics / roleplays** → Dedicated **`Explore topics`** / **`Explore roleplays`** view (`08`, `Roleplays · 5`) with search and **category tabs** (`All`, `Travel`, `Food`, `Work`, `Daily life` / `Housing`).
- **Post-Creation Settings (`10c`, `Roleplays · 3c`):** Creating the topic/roleplay routes to **`Topic settings`** / **`Roleplay settings`** to configure visibility (`Only me` vs `Public to community`), copy/share the link (`/t/:token`, `/r/:token`), and launch **`Start Lesson 1`** / **`Start live roleplay`**.
- **Community Preview & Save (`09`, `Roleplays · 6`):** Learners inspect the 5-lesson syllabus or roleplay roles before adding it to their own library (`+ Add to my Topics` / `Save` / `Start live roleplay`).
- **Share & Visibility (`11`, `Roleplays · 8`):** Owners toggle `Only me` vs `Public to community` and share/copy public links (`/t/:token`, `/r/:token`).

```text
  ┌───────────────────────────────────────── TOPICS FLOW (`03 · Topics`) ─────────────────────────────────────────┐
  │                                                                                                               │
  │  [06 · Empty] ──► "+ Create topic" ──► [10a · Chat Greeting] ──► [10b · Draft Card (Refine | Create)]     │
  │       │                                                                    │                                  │
  │       │                                                                    ▼ ("Create topic")                 │
  │       │                                                          [10c · Topic Settings]                       │
  │       │                                                           (Visibility, Share link, Start Lesson 1)    │
  │       ├──► "Explore" ────────► [08 · Explore Topics]                                                          │
  │       │                           (Search + Category tabs)                                                    │
  │       │                             ├──► Match ──► [09 · Preview] ──► "+ Add to my Topics"                    │
  │       │                             └──► Empty ──► [12 · Browse Empty] ──► "+ Create “<query>”"               │
  │       │                                                                                                       │
  │  [01 · Topics — List] (User's added/saved topics only)                                                        │
  │       ├──► Tap "+" ──► [07 · Add Topic Sheet] (Create new | Find community topics)                            │
  │       └──► Tap row ──► [02 · Topic — In progress] (⋮) ──► [11 · Share & Visibility Sheet]                     │
  │                              │                                                                                │
  │                              ▼                                                                                │
  │                        [03a..03f · Voice Lesson] ──► [04 · Evaluation (Save to Vocab)] ──► [05 · Updated]     │
  └───────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────── ROLEPLAYS FLOW (`04 · Roleplays`) ─────────────────────────────────────┐
  │                                                                                                               │
  │  [1 · Empty] ──► "+ New roleplay" ──► [3a · Chat Greeting] ───► [3b · Draft Card (Refine | Create)]           │
  │       │                                                                    │                                  │
  │       │                                                                    ▼ ("Create roleplay")              │
  │       │                                                          [3c · Roleplay Settings]                     │
  │       │                                                           (Visibility, Share link, Start live)        │
  │       └──► "Explore" ─────────► [5 · Explore Roleplays]                                                       │
  │                                   (Search + Category tabs)                                                    │
  │                                     ├──► Match ──► [6 · Preview] ──► "Start live" / "Save" / "Share"          │
  │                                     └──► Empty ──► [9 · Browse Empty] ──► "+ Create “<query>”"                │
  │                                                                                                               │
  │  [2 · Roleplays — List] (User's added/saved roleplays only)                                                   │
  │       ├──► Tap "+" ──► [4 · Add Roleplay Sheet] (Create new | Find community roleplays)                       │
  │       └──► Tap ⋮ ────► [7 · Overflow (Public)] / [10 · Overflow (Saved)] / [11 · Overflow (Private)]         │
  │                              └──► [8 · Share & Visibility Sheet]                                              │
  └───────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Screen Inventory

### 2.1 `03 · Topics` (`2677:27327`)

| Frame | Figma Node | Description | Key Elements |
| :--- | :--- | :--- | :--- |
| **`Topics · 0 · Recommended`** | `2677:27328` | Personalized starter recommendations | Top bar (`Search` + `+`), starter topics + `Create custom topic`. |
| **`01 · Topics — List`** | `2677:27365` | User's added/saved topics only | Clean list with no ownership filter pills; `Search` + `+` in top bar; `Public` indicator in row metadata. |
| **`02 · Topic — In progress`** | `2677:27375` | Active topic syllabus | Trailing `⋮` overflow action on `Top Bar` + `Public` visibility status in metadata. |
| **`03a`–`03f` · Voice Lesson** | `2677:27447`–`2677:27574` | 5-stage interactive voice lesson | See `docs/VOICE_LESSON_FLOW_SPEC.md`. |
| **`04 · Lesson complete — Evaluation`** | `2677:27395` | Post-lesson evaluation | `Save all` bookmark action on `Practiced phrases` + 1-tap `Show save` bookmark toggles per `Phrase Row`. |
| **`05 · Topic — Updated`** | `2677:27421` | Topic progress after lesson | Trailing `⋮` overflow action + `Public` visibility status in metadata. |
| **`06 · Topics — Empty`** *(New)* | `2998:12928` | Empty state (0 user topics) | Top bar (`+` only), optically centered `Empty State` (`No topics yet`), `24px` gap, and centered `168px` **`Explore`** (`Icon/search`, `Secondary`) pill CTA. |
| **`07 · Topics — Add Choice Sheet`** *(New)* | `3001:13792` | Bottom drawer from `+` | Modal bottom sheet (`Add topic`) with 2 `Sheet Row` options: **`Create a new topic`** (`Chat with Sofía & upload notes or PDFs`) and **`Find community topics`** (`Browse by category & add shared paths`). |
| **`08 · Topics — Browse Community`** *(New)* | `2998:13067` | Dedicated `Explore topics` view | `Back` + `Explore topics` top bar, `Search Field` (`café`), **Category tabs** (`All`, `Travel`, `Food`, `Work`, `Daily life`), grouped `ZUNO OFFICIAL` & `COMMUNITY TOPICS` cards. |
| **`09 · Topics — Community Preview`** *(New)* | `2998:13198` | Preview before adding | `Community topic` top bar (`Share` action), creator attribution (`By @carlos.v · 5 lessons · 142 learners`), **`+ Add to my Topics`**, and 5-lesson `Syllabus` preview. |
| **`10a · Topics — Chat Builder (Greeting)`** *(New)* | `3007:13787` | Sofía opens the conversation | Sofía greets first (`"Hey Noah! What should your new topic be about?"`), offers topic chips (`Madrid trip`, `Job interview`, `Café & food`), and `+` attach `Composer` (no upfront upload form). |
| **`10b · Topics — Chat Builder (Draft Ready)`** *(Updated)* | `3005:13754` | Multi-turn + mid-chat PDF → Draft | Text-first turns (`"Madrid trip next week"` → Sofía follow-up), followed by a **mid-conversation** `Madrid_Itinerary.pdf` upload (`Read Madrid_Itinerary.pdf · 4 themes found`), and inline **`DRAFT TOPIC · 5 LESSONS`** card (`Refine` \| `Create topic`). |
| **`10c · Topics — Topic Settings`** *(New)* | `3007:13865` | Post-creation configure screen | Shown immediately after **`Create topic`**: summary card, **`Only me`** vs **`Public to community`** choices, `Share` / `Copy link` (`heyzuno.app/t/madrid-trip`), and **`Start Lesson 1`** primary CTA. |
| **`11 · Topics — Share & Visibility`** *(New)* | `2998:13327` | Share & visibility bottom sheet | `Only me` vs `Public to community` (`Searchable by learners · Anyone with link`), `heyzuno.app/t/parada-autobus · 34 learners added`, **`Share`** + **`Copy link`**. |
| **`12 · Topics — Browse Empty`** *(New)* | `2998:13398` | No match in `Explore topics` | `Explore topics` top bar, `Search Field` (`astrophysics`), Category tabs, `No topics for “astrophysics”`, **`+ Create “astrophysics”`**. |
| **`13 · Topics — Overflow (Owner Public)`** *(New)* | `3018:13926` | Context menu on own public topic | `Start next lesson`, `Schedule practice`, `Pin`, **`Share`** (shown because Public), `Topic settings`, divider, `Delete` (red). |
| **`14 · Topics — Overflow (Saved Community)`** *(New)* | `3018:13969` | Context menu on saved topic | `Start next lesson`, `Schedule practice`, `Pin`, `Share`, **`Report`**, divider, **`Remove`** (red; no `Topic settings`). |

### 2.2 `04 · Roleplays` (`2076:2686`)

| Frame | Figma Node | Description | Key Elements |
| :--- | :--- | :--- | :--- |
| **`Roleplays · 1 · Empty`** *(Updated)* | `2065:2693` | Empty state (0 user roleplays) | Top bar (`+` only), optically centered `Empty State` (`No roleplays yet`), `24px` gap, and centered `168px` **`Explore`** (`Icon/search`, `Secondary`) pill CTA. |
| **`Roleplays · 2 · List`** *(Updated)* | `2026:2322` | User's added/saved roleplays only | Clean list with no ownership filter pills; `Public` indicator in row metadata (`Tenant / Landlord · Public`). |
| **`Roleplays · 3a · Chat Builder (Greeting)`** *(New)* | `3008:13872` | Sofía opens the conversation | Sofía greets first (`"Hey Noah! What kind of scene do you want to practice?"`), offers chips (`Startup interview`, `Renting a flat`, `Doctor’s visit`) + `+` attach `Composer`. |
| **`Roleplays · 3b · Chat Builder (Draft Ready)`** *(Updated)* | `3005:13865` | Multi-turn + mid-chat JD → Draft | Text-first turns (`"Friendly product interview in Madrid"` → Sofía follow-up), followed by a **mid-conversation** `Startup_JD_Madrid.pdf` upload, and inline **`DRAFT ROLEPLAY`** card (`Refine` | `Create roleplay`). |
| **`Roleplays · 3c · Roleplay Settings`** *(New)* | `3008:13950` | Post-creation configure screen | Shown immediately after **`Create roleplay`**: summary card, **`Only me`** vs **`Public to community`** choices, `Share` / `Copy link` (`heyzuno.app/r/startup-interview`), and **`Start live roleplay`** primary CTA (launches directly into Live Voice Roleplay; see [`docs/ROLEPLAY_VOICE_FLOW_SPEC.md`](./ROLEPLAY_VOICE_FLOW_SPEC.md)). |

### 2.3 `07 · Attachments` — Merged Builder Upload Flow (`2109:2823`, Row 2)

| Frame | Figma Node | Description | Key Elements |
| :--- | :--- | :--- | :--- |
| **`Attach · Builder 1 · Mid-chat file`** *(Replaced upfront form)* | `3011:14037` | Upload files anytime mid-chat | Ongoing `New topic` / `New roleplay` conversation after initial text turns, with photo + `Madrid_Itinerary.pdf` queued in `Composer · Attachments`. |
| **`Attach · Builder 2 · File analysed → Draft`** *(Replaced upfront form)* | `3011:14115` | Attachment digested into draft | Sofía reads the mid-chat PDF (`Read Madrid_Itinerary.pdf · 4 themes found`) and updates the inline `DRAFT TOPIC` / `DRAFT ROLEPLAY` card (`Refine` | `Create`). |
| **`Roleplays · 4 · Add Choice Sheet`** *(New)* | `3001:13982` | Bottom drawer from `+` | Modal bottom sheet (`Add roleplay`) with 2 `Sheet Row` options: **`Create a new roleplay`** (`Describe a scene & attach context in chat`) and **`Find community roleplays`** (`Browse real-life scenes by category`). |
| **`Roleplays · 5 · Browse Community`** *(New)* | `2999:13332` | Dedicated `Explore roleplays` view | `Back` + `Explore roleplays` top bar, `Search Field` (`airport`), **Category tabs** (`All`, `Travel`, `Food`, `Work`, `Housing`), grouped `ZUNO OFFICIAL` & `COMMUNITY SCENARIOS` cards. |
| **`Roleplays · 6 · Community Preview`** *(New)* | `2999:13467` | Preview roles & start/save | `Community roleplay` top bar (`⋮`), `Public · B1` + `214 practiced` chips, `Lost luggage at Barajas`, `Created by @mateo.r · Spanish`, `SCENARIO`, `Your role` / `Sofía's role`, **`Start live roleplay`**, **`Save`**, **`Share`**. |
| **`Roleplays · 7 · Overflow (Owner Public)`** *(Updated)* | `2999:13573` | Context menu on own public roleplay | `Start over`, `Schedule practice`, `Pin`, **`Share`** (shown because Public), `Roleplay settings`, divider, `Delete` (red). No edit/duplicate action. |
| **`Roleplays · 8 · Share & Visibility`** *(New)* | `2999:13612` | Share & visibility bottom sheet | `Share roleplay` sheet (`Only me` vs `Public to community`), `heyzuno.app/r/renting-madrid · 42 learners practiced`, **`Share`** + **`Copy link`**. |
| **`Roleplays · 9 · Browse Empty`** *(New)* | `2999:13681` | No match in `Explore roleplays` | `Explore roleplays` top bar, `Search Field` (`space station`), Category tabs, `No scenarios for “space station”`, **`+ Create “space station”`**. |
| **`Roleplays · 10 · Overflow (Saved Community)`** *(New)* | `3018:14187` | Context menu on saved roleplay | `Start over`, `Schedule practice`, `Pin`, `Share`, **`Report`**, divider, **`Remove`** (red). |
| **`Roleplays · 11 · Overflow (Owner Private)`** *(New)* | `3018:14299` | Context menu on `Only me` roleplay | `Start over`, `Schedule practice`, `Pin`, `Roleplay settings`, divider, `Delete` (red) — **`Share` omitted while Private**. |

---

## 3. User Stories & Acceptance Criteria

### US-1 — Clean Personal Libraries (`01 · Topics — List`, `Roleplays · 2 · List`)
- **As a** learner opening Topics or Roleplays,  
  **I want** to see only the topics and roleplays I've created or explicitly added,  
  **so that** my personal study list stays focused and uncluttered.
- **Acceptance Criteria:**
  - Root `Topics` and `Roleplays` lists show only user-owned or saved/forked items (no `All | Mine | Saved | Public` ownership chips).
  - Top bar `Search` filters the user's library (or offers a shortcut to `Explore topics` / `Explore roleplays` when unmatched).

### US-2 — `+` Bottom Drawer Choice (`07 · Add Topic Sheet`, `Roleplays · 4 · Add Roleplay Sheet`)
- **As a** learner tapping `+` in Topics or Roleplays,  
  **I want** a clear bottom sheet asking whether I want to create a new one or browse community-created ones,  
  **so that** I can either generate custom content or pick something proven in seconds.
- **Acceptance Criteria:**
  - Tapping `+` always opens the 2-option modal bottom sheet (`Create a new …` vs `Find community …`).
  - Empty views (`06`, `Roleplays · 1`) surface the same two actions directly (`+ Create` primary + `Browse community` secondary).

### US-3 — Dedicated Community Explore with Category Tabs (`08`, `12`, `Roleplays · 5`, `Roleplays · 9`)
- **As a** learner looking for inspiration,  
  **I want** a dedicated `Explore topics` / `Explore roleplays` view with live search and category tabs,  
  **so that** I can browse Zuno Official and Community-shared content by real-life domain without mixing it into my personal list.
- **Acceptance Criteria:**
  - Category tabs (`All`, `Travel`, `Food`, `Work`, `Daily life` for Topics; `All`, `Travel`, `Food`, `Work`, `Housing` for Roleplays) filter Zuno Official + Community items in the user's active `targetLanguage`.
  - Live search matches title, translation, scenario, and `@authorHandle`, with a **`+ Create “<query>”`** CTA when no matches exist.

### US-4 — Interactive Chat Creation & Post-Creation Settings (`10a`–`10c`, `Roleplays · 3a`–`3c`)
- **As a** learner creating a topic or roleplay,  
  **I want** Sofía to greet me and converse naturally while I describe what I want and upload files, and then configure sharing/visibility only *after* creation,  
  **so that** building feels effortless and uncluttered.
- **Acceptance Criteria:**
  - Selecting **`Create a new topic`** or **`Create a new roleplay`** opens the builder (`10a`, `Roleplays · 3a`) where Sofía greets first, asks an open question, and offers starter chips + `+` attach composer — with **no upfront file-upload form** required at the beginning.
  - Users start by chatting naturally, and can attach PDFs, photos, notes, or links **at any turn mid-conversation** (`Attach · Builder 1 / 2` in `07 · Attachments`). Sofía digests uploaded assets (`Context Status` pill) and updates the inline **`DRAFT TOPIC`** / **`DRAFT ROLEPLAY`** card (`10b`, `Roleplays · 3b`) with **`Refine`** (stay in chat) and **`Create topic`** / **`Create roleplay`** (accept).
  - Accepting routes directly to **`Topic settings`** (`10c`) or **`Roleplay settings`** (`Roleplays · 3c`), where the creator configures `Only me` vs `Public to community`, shares/copies the link, or taps **`Start Lesson 1`** / **`Start live roleplay`**.
  - Existing AI chats can also pivot into creation (`"Turn this chat into a topic"` / `"Turn this into a roleplay"`), seeding the builder with the current conversation and attachments.

### US-5 — Community Preview, Save, Visibility & Sharing (`09`, `11`, `Roleplays · 6`, `7`, `8`)
- **As a** creator or learner,  
  **I want** to preview community items before adding them, toggle `Make public in Community` on my own creations, and share public links,  
  **so that** useful paths and scenarios are easy to publish, discover, and reuse.
- **Acceptance Criteria:**
  - `Add to my Topics` / `Save` clones the curriculum/scenario into the user's personal library (`sourceTopicId` / `sourceRoleplayId`) and increments the creator's adoption counter.
  - `Only me` vs `Public to community` controls both Community search indexing and public link access (`/t/:token`, `/r/:token`).
  - Topics created from private user attachments strip raw attachment files when published, exposing only the generated curriculum.

### US-6 — Context Menus (No Edit, Share Only When Public) (`13`, `14`, `Roleplays · 7`, `10`, `11`)
- **As a** learner managing my Topics or Roleplays,  
  **I want** clear context actions without confusing edit options or broken private share links,  
  **so that** I can launch, schedule, pin, share public items, report bad community items, or delete/remove cleanly.
- **Acceptance Criteria:**
  - **Immutable after creation:** Neither Topics nor Roleplays expose `Edit` or `Duplicate & edit` in context menus.
  - **`Share` gated by visibility:** `Share` only appears when `visibility == "public"` (or on saved Community/Zuno items, which are public). Private (`Only me`) items hide `Share` and expose `Topic settings` / `Roleplay settings` to publish first.
  - **Owner items (`13`, `Roleplays · 7`, `11`):** `Start next lesson` / `Start over` (launches live voice), `Chat` *(Roleplays: opens text transcript)*, `Schedule practice` (opens `08 · Scheduled`), `Pin` / `Unpin`, `Share` *(if Public)*, `Topic settings` / `Roleplay settings`, divider, `Delete`.
  - **Saved Community items (`14`, `Roleplays · 10`):** `Start next lesson` / `Start over` (launches live voice), `Chat` *(Roleplays)*, `Schedule practice`, `Pin` / `Unpin`, `Share`, `Report` (`Icon/flag`), divider, `Remove`.
  - **Explore cards (`08`, `Roleplays · 5`):** Show a `>` chevron (opening Preview) instead of `⋮`.

---

## 4. Backend Schema & API Contract (`backend/`)

### 4.1 Drizzle Schema Additions (`backend/src/db/app-schema.ts`)

Extend `topic` and `roleplay` with category, visibility, sharing, attribution, and saved-fork tracking:

```typescript
// Additions to `topic` table:
category: text("category", { enum: ["travel", "food", "work", "daily_life", "grammar", "culture"] }).default("daily_life").notNull(),
visibility: text("visibility", { enum: ["private", "public"] }).default("private").notNull(),
sourceKind: text("source_kind", { enum: ["user", "system", "community"] }).default("user").notNull(),
shareToken: text("share_token").unique(),
sourceTopicId: text("source_topic_id"),        // set when saved/forked from a system or community topic
authorHandle: text("author_handle"),          // cached public creator handle, e.g. "@carlos.v"
learnersCount: integer("learners_count").default(0).notNull(),
pinnedAt: timestamp("pinned_at"),
publishedAt: timestamp("published_at"),
// Index: (language, visibility, category, learnersCount desc)

// Additions to `roleplay` table:
goals: jsonb("goals").$type<RoleplayGoalItem[]>(), // 3 practice goals + target-language samplePhrase (lazily backfilled if null)
category: text("category", { enum: ["travel", "food", "work", "housing", "health", "social"] }).default("social").notNull(),
visibility: text("visibility", { enum: ["private", "public"] }).default("private").notNull(),
sourceKind: text("source_kind", { enum: ["user", "system", "community"] }).default("user").notNull(),
shareToken: text("share_token").unique(),
sourceRoleplayId: text("source_roleplay_id"),  // set when saved/forked from a system or community roleplay
authorHandle: text("author_handle"),
practicedCount: integer("practiced_count").default(0).notNull(),
pinnedAt: timestamp("pinned_at"),
publishedAt: timestamp("published_at"),
// Index: (language, visibility, category, practicedCount desc)
```

### 4.2 HTTP Routes (`backend/src/routes/topics.ts`, `roleplays.ts`, `index.ts`)

- **Conversational Builder (`POST /api/topics/builder/turn`, `POST /api/roleplays/builder/turn`)**
  - Accepts `{ builderSessionId?, message, attachmentIds[] }`.
  - Runs Gemini with the full builder chat history + extracted `AttachmentDigest` payloads, returning `{ reply, draft: TopicDraft | RoleplayDraft }` (title, inferred `category`, 5-lesson outline or `userRole`/`aiRole`/`scenario`).
  - Confirming the draft calls `POST /api/topics` or `POST /api/roleplays` with `{ ...draft, visibility, attachmentIds }` (attaching uploaded materials to the topic while stripping private raw files if `visibility = "public"`).
- **Personal Library (User's Added & Saved Items Only)**
  - `GET /api/topics` — Returns only the authenticated user's own + saved topics (`userId = user.id`).
  - `GET /api/roleplays` — Returns only the authenticated user's own + saved roleplays (`userId = user.id`).
- **Dedicated Community & System Explore**
  - `GET /api/topics/discover?category=all|travel|food|work|daily_life&q=...` — Returns `system` + `public` community topics matching `profile.targetLanguage`, `category`, and `q`, ordered by `learnersCount desc, publishedAt desc`.
  - `GET /api/roleplays/discover?category=all|travel|food|work|housing&q=...` — Returns `system` + `public` community roleplays matching `profile.targetLanguage`, `category`, and `q`, ordered by `practicedCount desc, publishedAt desc`.
- **Save / Fork to Library & Live Voice Session**
  - `POST /api/topics/:id/save` — Clones a public/system topic + its 5 `topic_lesson` rows (`curriculum` included) into the current user's library (`sourceTopicId = id`), increments `learnersCount`, and returns the new `TopicDetail`. Idempotent if already saved.
  - `POST /api/roleplays/:id/save` — Clones a public/system roleplay into the user's library (`sourceRoleplayId = id`), increments `practicedCount`, and returns the saved `Roleplay`.
  - `POST /api/roleplays/:id/session` — Ensures 3 practice `goals` exist, resolves/creates the `roleplay` `conversation`, and mints a LiveKit token (`mode = "roleplay"`) for direct entry into Live Voice Roleplay (`docs/ROLEPLAY_VOICE_FLOW_SPEC.md`).
- **Pin, Visibility, Share & Report**
  - `PATCH /api/topics/:id` body `{ pinned: boolean }` — Pins/unpins topic to top of user's list (`pinnedAt`).
  - `PATCH /api/roleplays/:id` body `{ pinned: boolean }` — Pins/unpins roleplay to top of user's list (`pinnedAt`).
  - `PUT /api/topics/:id/share` body `{ visibility: "private" | "public" }` → `{ visibility, shareUrl | null, learnersCount }`.
  - `PUT /api/roleplays/:id/share` body `{ visibility: "private" | "public" }` → `{ visibility, shareUrl | null, practicedCount }`.
  - `POST /api/topics/:id/report` / `POST /api/roleplays/:id/report` body `{ reason, details? }` — Reports inappropriate public community content.
- **Public Share Pages (`index.ts`, no auth)**
  - `GET /t/:token` — Public preview HTML + OG tags + `zuno://topic/:token` App Link to open `09 · Topics — Community Preview`. Returns `404` if `visibility !== "public"`.
  - `GET /r/:token` — Public preview HTML + OG tags + `zuno://roleplay/:token` App Link to open `Roleplays · 6 · Community Preview`. Returns `404` if `visibility !== "public"`.

---

## 5. Android Architecture & Deep-Link Notes

- **Routes (`ZunoApp.kt`):**
  - `ZunoRoute.Topics` (user's library + `+` `AddTopicSheet` bottom drawer)
  - `ZunoRoute.ExploreTopics(initialQuery?)` (`08` / `12` with search + category tabs)
  - `ZunoRoute.CommunityTopicPreview(topicIdOrToken)` (`09`)
  - `ZunoRoute.Roleplays` (user's library + `+` `AddRoleplaySheet` bottom drawer)
  - `ZunoRoute.ExploreRoleplays(initialQuery?)` (`Roleplays · 5` / `9` with search + category tabs)
  - `ZunoRoute.CommunityRoleplayPreview(roleplayIdOrToken)` (`Roleplays · 6`)
- **Deep Links (`AndroidManifest.xml`):**
  - Handle `https://heyzuno.app/t/:token` / `zuno://topic/:token` → `CommunityTopicPreview`.
  - Handle `https://heyzuno.app/r/:token` / `zuno://roleplay/:token` → `CommunityRoleplayPreview`.
- **Moderation & Safety:**
  - Auto-assigned `category` during Gemini topic/roleplay creation (editable or inferred), plus keyword moderation before indexing `visibility = "public"` items into Community Explore.
