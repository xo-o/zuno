import { betterAuth, type BetterAuthOptions } from "better-auth";
import { drizzleAdapter } from "better-auth/adapters/drizzle";
import { bearer } from "better-auth/plugins/bearer";
import { magicLink } from "better-auth/plugins/magic-link";
import { db } from "../db/index.js";
import { sendMagicLinkEmail } from "./email.js";

const MAGIC_LINK_TTL_SECONDS = 60 * 15;

/**
 * Public URL the magic link points to. Android App Links intercept this path and
 * hand the token to the app; browsers without the app land on the fallback page.
 */
function magicLinkUrl(token: string) {
  const url = new URL("/auth/verify", process.env.BETTER_AUTH_URL);
  url.searchParams.set("token", token);
  return url.toString();
}

const googleClientIds = [
  process.env.GOOGLE_WEB_CLIENT_ID,
  process.env.GOOGLE_ANDROID_CLIENT_ID,
  process.env.GOOGLE_IOS_CLIENT_ID,
].filter((id): id is string => Boolean(id));

const socialProviders: BetterAuthOptions["socialProviders"] = {
  google: {
    clientId: googleClientIds,
    clientSecret: process.env.GOOGLE_CLIENT_SECRET as string,
  },
};

// Sign in with Apple activates once its credentials are configured.
if (process.env.APPLE_CLIENT_ID && process.env.APPLE_CLIENT_SECRET) {
  socialProviders.apple = {
    clientId: process.env.APPLE_CLIENT_ID,
    clientSecret: process.env.APPLE_CLIENT_SECRET,
    appBundleIdentifier: process.env.APPLE_APP_BUNDLE_IDENTIFIER,
  };
}

export const auth = betterAuth({
  appName: "Zuno",
  database: drizzleAdapter(db, { provider: "pg" }),
  trustedOrigins: socialProviders.apple ? ["https://appleid.apple.com"] : [],
  session: {
    expiresIn: 60 * 60 * 24 * 30,
    updateAge: 60 * 60 * 24,
  },
  account: {
    accountLinking: {
      enabled: true,
      trustedProviders: ["google", "apple"],
    },
  },
  socialProviders,
  plugins: [
    bearer({ requireSignature: true }),
    magicLink({
      expiresIn: MAGIC_LINK_TTL_SECONDS,
      storeToken: "hashed",
      sendMagicLink: async ({ email, token }) => {
        await sendMagicLinkEmail({ to: email, url: magicLinkUrl(token) });
      },
    }),
  ],
});

export type Session = typeof auth.$Infer.Session;
