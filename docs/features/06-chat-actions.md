# 06 · Chat Actions

**Figma Section:** [`06 · Chat actions`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2169-5406) (`2169:5406`)

---

## 1. Overview

Chat Actions give learners simple ways to organize, share, flag, and clean up their AI conversations from the top-right `⋮` menu inside any AI chat.

---

## 2. User Flow

```text
AI Chat Top Bar ─► Tap "⋮" ─► Menu Open (1)
                                 ├── Rename (2) ─► Save ─► Title updates + toast (3)
                                 ├── Pin / Unpin (4 / 6) ─► Moves to "Pinned" group in Chats (5)
                                 ├── Share ─► Only me (7) ──► Switch to Public access (8) ─► Share / Copy link
                                 ├── Report (9) ─► Pick reason + optional details (10) ─► Submit ─► Toast (11)
                                 └── Delete (12) ─► Confirm ─► Returns Home + toast (13)
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 · Menu open** | `2169:5407` | Overflow menu with **Rename**, **Pin**, **Share**, **Report**, divider, and red **Delete** | Choose action |
| **2 · Rename** | `2169:5526` | Modal dialog (*Rename chat*) prefilled with the current title and keyboard raised | Edit name, **Cancel**, or **Save** |
| **3 · Renamed** | `2169:5710` | Chat screen with updated title in the top bar and confirmation toast | Continue chatting |
| **4 · Pinned** | `2169:5768` | Toast confirming the chat was pinned | Dismiss or keep chatting |
| **5 · Chats · Pinned** | `2169:5826` | Conversations list showing a **Pinned** section pinned above **Recent** | Open pinned chat |
| **6 · Menu · Pinned chat** | `2169:5991` | Overflow menu on a pinned chat swaps **Pin** for **Unpin** | Tap **Unpin** |
| **7 · Share · Only me** | `2170:6065` | Share bottom sheet defaulting to **Only me** (*"Visible only to you"*), with **Share** and **Copy link** disabled | Switch to **Public access** |
| **8 · Share · Public access** | `2170:6223` | **Public access** selected (*"Anyone with the link can view"*); **Share** and **Copy link** enabled | Tap **Share** or **Copy link** |
| **9 · Report** | `2170:6403` | Bottom sheet (*Report chat*) asking *"What went wrong? Reports help Zuno teach better."* with tutor-specific reasons and disabled **Submit report** | Pick a reason |
| **10 · Report · Reason picked** | `2170:6505` | Selected reason (*Wrong language or level*, *Offensive or inappropriate*, *Unsafe or harmful advice*, or *Something else*) + optional details enables **Submit report** | Add details (optional), **Submit report** |
| **11 · Report sent** | `2170:6608` | Sheet closes and a toast thanks the learner | Continue |
| **12 · Delete** | `2170:6666` | Destructive dialog (*Delete chat?*): *"‘Café en Madrid’ and its corrections will be deleted. Words saved to Vocabulary stay. This can’t be undone."* | **Cancel** or **Delete** |
| **13 · Deleted → Home** | `2170:6764` | Returns the user to **Home** with a confirmation toast | Continue from Home |

---

## 4. How It Works

- **Private by default (`7` → `8`):** Every chat starts as **Only me**. **Share** and **Copy link** stay disabled until the learner explicitly switches visibility to **Public access**.
- **Saved Vocabulary is preserved (`12`):** Deleting a chat permanently removes the conversation messages and inline corrections, while keeping any words or phrases the learner already bookmarked in **Vocabulary**.
- **Pinning (`4`–`6`):** Pinned chats move to a dedicated **Pinned** section at the top of the Conversations list and Sidebar Drawer for quick access.

---

## 5. Edge Cases & Empty / Error States

- **Empty rename:** Saving a blank title is prevented; cancelling leaves the original title unchanged.
- **Public link revoked:** Switching a previously shared chat back to **Only me** immediately prevents anyone else from viewing the shared link.
- **Unpinning:** Opening `⋮` on a pinned chat always reflects its live state (**Unpin** instead of **Pin**).
- **Incomplete report:** **Submit report** stays disabled until at least one reason is chosen.
