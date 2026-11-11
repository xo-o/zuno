import { Hono } from "hono";
import { z } from "zod";
import { sendEmail } from "../lib/email.js";
import { clientIp, createLimiter } from "../lib/http.js";

const TOPICS = {
  account: "Account",
  billing: "Billing & Pro",
  voice: "Voice",
  bug: "Bug report",
  other: "Other",
} as const;

const contactLimit = createLimiter({
  windowMs: 60 * 60_000,
  max: 5,
  message: "Too many messages. Please try again later.",
});

const contactSchema = z.object({
  name: z.string({ error: "Enter your name." }).trim().min(1, "Enter your name.").max(100, "Name can be up to 100 characters."),
  email: z.string({ error: "Enter a valid email address." }).trim().pipe(z.email("Enter a valid email address.").max(254)),
  topic: z.enum(Object.keys(TOPICS) as [keyof typeof TOPICS, ...(keyof typeof TOPICS)[]], { error: "Pick a topic." }),
  message: z
    .string({ error: "Write a message of at least 10 characters." })
    .trim()
    .min(10, "Write a message of at least 10 characters.")
    .max(5000, "Messages can be up to 5,000 characters."),
});

/** Contact form on heyzuno.app/contact, forwarded to the support inbox. Public. */
export const contactRoutes = new Hono().post("/", async (c) => {
  const raw = await c.req.json().catch(() => undefined);
  const parsed = contactSchema.safeParse(raw ?? {});
  if (!parsed.success) return c.json({ error: parsed.error.issues[0]?.message ?? "Invalid request." }, 400);

  contactLimit(c, clientIp(c));

  const { name, email, topic, message } = parsed.data;
  // sendEmail escapes every field before it goes into the HTML.
  await sendEmail({
    to: process.env.SUPPORT_EMAIL ?? "support@heyzuno.app",
    replyTo: email,
    subject: `[Zuno contact · ${TOPICS[topic]}] ${name}`,
    heading: `${TOPICS[topic]} — ${name}`,
    paragraphs: [`Name: ${name}`, `Email: ${email}`, `Topic: ${TOPICS[topic]} (${topic})`, "Message:", message],
  });
  return c.json({ ok: true });
});
