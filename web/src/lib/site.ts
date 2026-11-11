export const SITE_DOMAIN = "heyzuno.app";
export const SITE_URL = `https://${SITE_DOMAIN}`;

/** Zuno backend (Hono). Override with NEXT_PUBLIC_API_URL for local development. */
export const API_URL = (
  process.env.NEXT_PUBLIC_API_URL ?? "https://api.heyzuno.app"
).replace(/\/$/, "");

export const SUPPORT_EMAIL = "support@heyzuno.app";
export const PRIVACY_EMAIL = "support@heyzuno.app";

export const LEGAL_UPDATED = "November 10, 2026";

export const ROUTES = {
  home: "/",
  pricing: "/pricing",
  contact: "/contact",
  faq: "/contact#faq",
  deleteAccount: "/delete-account",
  terms: "/terms",
  privacy: "/privacy",
  cookies: "/cookies",
  getTheApp: "/#get-the-app",
} as const;
