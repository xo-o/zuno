# 11 · Paywall & Zuno Pro

**Figma Section:** [`11 · Paywall`](https://www.figma.com/design/9ERB1VeMLPgQLUSyfQdsMG/ZunoAI-V2?node-id=2365-8835) (`2365:8835`)

---

## 1. Overview

Paywall presents the **Zuno Pro** membership, clearly contrasting the Free plan (10 minutes of daily AI voice practice) with unlimited AI tutor video calls, unlimited custom topics and roleplays, multimodal practice from photos/PDFs/links, and detailed progress tracking.

---

## 2. User Flow

```text
Upgrade Entry (Sidebar Banner / Settings / Daily Limit Reached)
  └── Paywall · 1 · Plans
        ├── Select Yearly (7-day free trial, Save 50%) or Monthly
        ├── Tap "Restore purchases" ─► Restores active store subscription
        └── Tap "Start 7-day free trial" / Subscribe ─► Store Checkout
                                                            └── Success ─► Welcome to Pro (2)
                                                                             ├── Start practising ─► Home / Practice
                                                                             └── Manage subscription ─► Store Subscriptions
```

---

## 3. Screens & States

| Screen | Figma Node | What It Shows | Key Actions |
| :--- | :--- | :--- | :--- |
| **1 · Plans** | `2365:8836` | Close button, **Zuno Pro** benefits (*Unlimited AI tutor video calls*, *Build as many lessons as you like*, *Turn real material into practice*, *See exactly how you're improving*), **Yearly · $59.88** (*$4.99/month · 7-day free trial*, **Save 50%** badge) vs. **Monthly · $9.99**, primary CTA, renewal fine print, **Terms**, **Privacy Policy**, **Restore purchases** | Choose plan, start free trial, restore purchases, view legal links, close |
| **2 · Welcome to Pro** | `2365:8964` | Confirmation (*"You’re on Zuno Pro"*), 2-day trial-end reminder note, unlocked perks checklist, **Start practising**, and **Manage subscription** | Start practising or manage subscription |

---

## 4. How It Works

- **Free vs. Pro:** Free accounts receive **10 minutes per day** of live AI tutor calls; **Zuno Pro** removes the daily cap and unlocks unlimited custom topics, roleplays, and attachment-based practice across all 30+ languages.
- **Trial transparency:** Selecting the Yearly plan starts a **7-day free trial** and notes that the learner receives a reminder **2 days before** the trial ends.
- **Auto-renewal disclosure:** Clear billing terms explain renewal timing and how to cancel in Google Play at least 24 hours before renewal.
- **Purchase restoration:** Returning subscribers on a new device can tap **Restore purchases** on the Paywall or in Account Settings.

---

## 5. Edge Cases & Empty / Error States

- **Dismissed paywall:** Closing `1 · Plans` returns the user cleanly to where they came from without losing their draft or session.
- **Cancelled or failed store checkout:** Keeps the user on `1 · Plans` with an informative message and allows retrying.
- **No subscription found on Restore:** Notifies the user that no active Zuno Pro subscription was found for the current store account.
