import { Hono } from "hono";
import { z } from "zod";
import {
  accountCounts,
  consumeDeletionToken,
  createDeletionToken,
  deleteAccount,
  findDeletionTokenUser,
  findUserByEmail,
} from "../lib/account.js";
import { sendEmail } from "../lib/email.js";
import { clientIp, createLimiter } from "../lib/http.js";

const INVALID_LINK = "This link is invalid or has expired. Request a new one.";
const TOO_MANY = "Too many requests. Please try again later.";

const emailSchema = z.email().max(254);

const requestIpLimit = createLimiter({ windowMs: 60 * 60_000, max: 10, message: TOO_MANY });
const requestEmailLimit = createLimiter({ windowMs: 60 * 60_000, max: 3, message: TOO_MANY });
const tokenIpLimit = createLimiter({ windowMs: 15 * 60_000, max: 30, message: TOO_MANY });

function webBaseUrl() {
  return (process.env.WEB_BASE_URL ?? "https://heyzuno.app").replace(/\/+$/, "");
}

async function jsonBody(c: { req: { json: () => Promise<unknown> } }) {
  const body = await c.req.json().catch(() => undefined);
  return body && typeof body === "object" ? (body as Record<string, unknown>) : {};
}

/**
 * Public account deletion from the website (heyzuno.app/delete-account): the learner requests an emailed
 * link, reviews what will be removed, then confirms by typing DELETE. Deletion is the same as DELETE /api/me.
 */
export const accountDeletionRoutes = new Hono()
  .post("/request", async (c) => {
    const body = await jsonBody(c);
    const parsed = emailSchema.safeParse(typeof body.email === "string" ? body.email.trim() : undefined);
    if (!parsed.success) return c.json({ error: "Enter a valid email address." }, 400);
    const email = parsed.data.toLowerCase();

    requestIpLimit(c, clientIp(c));
    requestEmailLimit(c, email);

    // Always the same answer, so the form can't be used to find out who has an account.
    const row = await findUserByEmail(email);
    if (row) {
      const token = await createDeletionToken(row.id);
      const url = `${webBaseUrl()}/delete-account/confirm?token=${encodeURIComponent(token)}`;
      sendEmail({
        to: row.email,
        subject: "Confirm your Zuno account deletion",
        heading: "Confirm account deletion",
        paragraphs: [
          "We received a request to delete your Zuno account and all of its data.",
          "Tap the button below to review and confirm. The link expires in 30 minutes and works once. Nothing is deleted until you confirm.",
        ],
        button: { label: "Review and delete", url },
        footerHtml: "If you didn't request this, you can safely ignore this email. Your account stays as it is.",
      }).catch((err) => console.error("Account deletion request email failed:", err));
    }
    return c.json({ ok: true });
  })
  .get("/:token", async (c) => {
    tokenIpLimit(c, clientIp(c));
    c.header("Cache-Control", "no-store");
    const row = await findDeletionTokenUser(c.req.param("token"));
    if (!row) return c.json({ error: INVALID_LINK }, 404);
    return c.json({ email: row.email, counts: await accountCounts(row.id) });
  })
  .post("/:token", async (c) => {
    tokenIpLimit(c, clientIp(c));
    const body = await jsonBody(c);
    if (body.confirm !== "DELETE") return c.json({ error: "Type DELETE to confirm." }, 400);

    const userId = await consumeDeletionToken(c.req.param("token"));
    const deleted = userId ? await deleteAccount(userId) : undefined;
    if (!deleted) return c.json({ error: INVALID_LINK }, 404);

    const { email } = deleted.user;
    sendEmail({
      to: email,
      subject: "Your Zuno account has been deleted",
      heading: "Your account has been deleted",
      paragraphs: [
        "Your Zuno account and all of its data — chats, topics, roleplays, files and settings — have been deleted.",
        "Copies in our backups are cleared within 30 days.",
        "If you subscribed to Zuno Pro through Google Play, deleting your account doesn't cancel the subscription. Cancel it in Google Play under Payments & subscriptions.",
      ],
    }).catch((err) => console.error("Account deletion email failed:", err));
    return c.json({ email });
  });
