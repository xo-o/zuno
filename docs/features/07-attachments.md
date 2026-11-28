# 07 · Attachments & Chat Reminders

**Figma Section:** [`07 · Attachments`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2109-2823) (`2109:2823`)

---

## 1. Overview

Attachments let learners bring real-world materials—photos (menus, signs, screenshots), PDFs, files, and links—directly into any AI chat or Topic/Roleplay builder. Learners can also set a **"Remind me to continue"** nudge from the same `+` sheet so they can step away and pick up the exact conversation later.

---

## 2. User Flow

```text
Chat / Builder Composer ─► Tap "+" ─► Attachment Sheet (1)
  ├── Sources: Camera · Photos · Files · Paste a link
  │     └── Queued in Composer (2)
  │           ├── Uploading / Error (3) ─► Retry or remove failed item (Send disabled)
  │           └── Ready ─► Send with question
  │                 ├── In AI Chat ─► Zuno reads image/PDF and replies in context (4)
  │                 └── In Topic/Roleplay Builder ─► Mid-chat upload (Builder 1) ─► Draft updated (Builder 2)
  └── Learning actions: "Remind me to continue"
        └── Pick when (5) ─► Set reminder ─► Toast confirms, chat stays intact (6)
                                                    │
                                                    ▼ (At chosen time)
                                          Push Notification (7) ─► Tap ─► Chat reopens as left (8)
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 · Sheet open** | `2109:2824` | Bottom sheet (*Add to this chat*) split into **Sources** (`Camera`, `Photos`, `Files`, `Paste a link`) and **Learning actions** (`Remind me to continue`) | Pick a source or set a reminder |
| **2 · Composer with attachments** | `2109:2980` | Photo thumbnail + PDF chip queued above the text input (*"¿Qué significa «cortado»?"*) | Remove items, type question, send |
| **3 · Uploading & error** | `2109:3121` | Photo showing upload progress while PDF shows failed upload error state; Send button disabled | Retry failed upload or remove it |
| **4 · Sent photo → contextual reply** | `2111:3106` | Zuno reads the uploaded menu photo (*"En tu foto cuesta 1,50 €"*) and surfaces inline glossary chips (`cortar — to cut`, `café solo — espresso`) | Listen, explain grammar, save glossary words |
| **5 · Remind me to continue** | `2111:3544` | Bottom sheet explaining tapping the reminder reopens the exact chat + time chips (`In 1 hour`, `Tonight · 9:00 PM`, `Tomorrow · 8:00 AM`, `Pick a time`) | Choose time, tap **Set reminder** |
| **6 · Reminder set** | `2116:3831` | Returns to the chat untouched with a confirmation toast | Keep chatting or leave |
| **7 · Reminder notification** | `2116:3903` | Lock screen push notification quoting where the learner left off | Tap notification |
| **8 · Chat reopened as left** | `2116:3928` | Exact same chat opens with full history, attachments, and any unsent draft preserved | Continue seamlessly |
| **Builder 1 · Mid-chat file** | `3011:14037` | Uploading a photo + PDF (`Madrid_Itinerary.pdf`) mid-conversation inside Topic/Roleplay creation | Send files to update draft |
| **Builder 2 · File analysed → Draft updated** | `3011:14115` | Zuno digests the uploaded file and updates the inline **DRAFT TOPIC** / **DRAFT ROLEPLAY** card | **Refine** or **Create** |

---

## 4. How It Works

- **Contextual multimodal coaching (`4`):** Zuno reads text inside uploaded photos, menus, slides, and PDFs, answers the learner's specific question, and highlights useful vocabulary directly below its reply.
- **Upload gate (`3`):** While any attachment is still uploading or in an error state, **Send** stays disabled so half-uploaded files are never sent.
- **No upfront upload barrier in Builders (`Builder 1`–`2`):** Learners don't have to upload files before starting Topic or Roleplay creation; they can chat naturally first and attach context files at any turn.
- **Exact-state reminders (`5`–`8`):** Setting a *"Remind me to continue"* reminder does not alter the chat thread. When the push notification fires and is tapped, the app opens that exact conversation with its scroll history, attachments, and draft intact.

---

## 5. Edge Cases & Empty / Error States

- **Failed upload (`3`):** Displays an error state directly on the failed attachment chip with an option to **Retry** or **Remove** before sending.
- **Custom reminder time (`5`):** Selecting **Pick a time** opens a date/time selector when preset options (`In 1 hour`, `Tonight`, `Tomorrow`) don't fit.
- **Paste a link (`1`):** Allows pasting an article or video URL so Zuno can summarize and discuss it in the target language.
