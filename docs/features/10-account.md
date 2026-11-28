# 10 · Account & Privacy

**Figma Section:** [`10 · Account`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2017-1921) (`2017:1921`)

---

## 1. Overview

Account & Privacy lets learners manage their identity (profile photo and name), subscription status, social discoverability and safety controls, personal data export, and permanent account deletion.

---

## 2. User Flow

```text
Account Overview (1)
  ├── Tap Avatar / "Change photo" ─► Photo Sheet (2): Take photo | Choose from library | Remove photo
  ├── Tap Name ────────────────────► Edit Name Dialog (3) ─► Save
  ├── Tap "Restore purchases" ─────► Refreshes subscription status
  ├── Tap "Privacy & visibility" ──► Privacy Screen (4): Open to speak | Allow 1:1 call invites | Who can message me | Blocked learners
  ├── Tap "Download my data" ──────► Requests export ─► Toast: "We'll email you a download link" (5)
  └── Tap "Delete account" ────────► Destructive Confirmation Dialog (6) ─► Permanently deletes account
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 · Overview** | `2960:11889` | Avatar + **Change photo**; **Profile** (`Name`, `Email`, sign-in provider e.g. *Signed in with Google*); **Subscription & Privacy** (`Plan`, `Restore purchases`, `Privacy & visibility`, `Download my data`, `Delete account`) | Edit photo/name, restore purchases, open privacy, export or delete |
| **2 · Change Photo** | `2960:11919` | Bottom sheet (*Profile photo*) with **Take photo**, **Choose from library**, and **Remove photo** | Capture, pick, or remove avatar |
| **3 · Edit Name** | `2960:12039` | Modal dialog (*Edit name*) with text input and raised keyboard | Update display name, **Cancel**, or **Save** |
| **4 · Privacy & Visibility** | `2960:12180` | **Open to speak** toggle, **Allow 1:1 call invites** toggle, **Who can message me** selector (e.g., *Partners*), **Safety → Blocked learners** | Control who can discover, message, and call you; manage blocks |
| **5 · Download My Data** | `2960:12306` | Confirmation toast: *"We’ll email you a download link"* | Wait for export email |
| **6 · Delete Account** | `2960:12341` | Red destructive dialog (*Delete account?*): *"Your profile, chats, topics, and vocabulary will be permanently deleted. This can’t be undone."* | **Cancel** or **Delete** |

---

## 4. How It Works

- **Profile visibility (`4`):**
  - Turning **Open to speak** off removes the learner from the live **Open to speak** discovery list in Chats and Community.
  - Turning **Allow 1:1 call invites** off prevents partners from ringing them for spontaneous voice/video practice.
  - **Who can message me** restricts incoming peer messages (for example, to saved **Partners** only).
- **Blocked learners (`4`):** Blocking a learner removes them from conversations and prevents future messages or call invites; blocked learners can be reviewed and unblocked from **Blocked learners**.
- **Data export (`5`):** Generates a downloadable archive of the learner's account data and sends the link to their verified email address.
- **Permanent deletion (`6`):** Deleting an account requires explicit confirmation and permanently erases profile, chats, custom topics/roleplays, and saved vocabulary.

---

## 5. Edge Cases & Empty / Error States

- **No custom photo set (`2`):** **Remove photo** is only needed when a photo is present; removing it falls back to the learner's initial avatar.
- **Empty display name (`3`):** **Save** rejects blank names.
- **No blocked users (`4`):** **Blocked learners** displays `None`.
- **Accidental delete tap (`6`):** **Cancel** dismisses the dialog immediately with no changes made.
