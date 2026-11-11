import { Lock, Mail, Trash2 } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import { Eyebrow } from "@/components/landing/brand";
import { type FaqEntry, FaqList } from "@/components/site/faq";
import { TextLink } from "@/components/site/legal";
import { Container, IconTile, SitePage } from "@/components/site/site-page";
import { PRIVACY_EMAIL, ROUTES, SITE_DOMAIN, SUPPORT_EMAIL } from "@/lib/site";
import { ContactForm } from "./contact-form";

export const metadata: Metadata = {
  title: "Contact & Help — Zuno",
  description:
    "Questions about your account, billing or how Zuno works? Write to us — a real person reads every message.",
  alternates: { canonical: "/contact" },
};

const CONTACT_CARDS: {
  icon: ReactNode;
  title: string;
  body: string;
  href: string;
}[] = [
  {
    icon: <Mail />,
    title: "Email support",
    body: `${SUPPORT_EMAIL} · We reply within 1–2 business days.`,
    href: `mailto:${SUPPORT_EMAIL}`,
  },
  {
    icon: <Lock />,
    title: "Privacy requests",
    body: `${PRIVACY_EMAIL} · Access, download or correct your data.`,
    href: `mailto:${PRIVACY_EMAIL}`,
  },
  {
    icon: <Trash2 />,
    title: "Delete your account",
    body: `Do it in the app or at ${SITE_DOMAIN}/delete-account.`,
    href: ROUTES.deleteAccount,
  },
];

const FAQS: FaqEntry[] = [
  {
    question: "I didn’t get my sign-in email",
    answer:
      "Check spam and promotions, and make sure you typed the right address. Links expire after 15 minutes — tap “Resend link” in the app to get a new one.",
  },
  {
    question: "Zuno can’t hear me",
    answer:
      "Allow microphone access in Android Settings → Apps → Zuno → Permissions, and make sure no other app (a call, recorder or voice assistant) is using the mic. Then restart the voice session.",
  },
  {
    question: "How do I cancel Zuno Pro?",
    answer:
      "Pro is billed through Google Play. Open the Play Store → Profile → Payments & subscriptions → Subscriptions → Zuno → Cancel. You keep Pro until the end of the period you’ve paid for.",
  },
  {
    question: "How do I download my data?",
    answer: (
      <>
        Email{" "}
        <TextLink href={`mailto:${PRIVACY_EMAIL}`}>{PRIVACY_EMAIL}</TextLink>{" "}
        from the address on your account and we’ll send you a copy of your data.
      </>
    ),
  },
  {
    question: "How do I delete my account?",
    answer: (
      <>
        In the app, go to Settings → Account → Delete account, or visit{" "}
        <TextLink href={ROUTES.deleteAccount}>
          {SITE_DOMAIN}/delete-account
        </TextLink>{" "}
        — no app needed.
      </>
    ),
  },
  {
    question: "Which languages can I practise?",
    answer:
      "Over 30 languages and accents, from Spanish and Japanese to Swahili. Switch anytime in the app’s settings.",
  },
];

const cardClass =
  "group flex items-start gap-4 rounded-[20px] border border-border bg-card p-4 transition-colors outline-none hover:border-input hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/30 lg:flex-col lg:gap-5 lg:rounded-3xl lg:p-6";

function ContactCard({ icon, title, body, href }: (typeof CONTACT_CARDS)[0]) {
  const content = (
    <>
      <IconTile size="sm" className="size-11 border-border">
        {icon}
      </IconTile>
      <div className="flex min-w-0 flex-1 flex-col gap-2 text-body lg:w-full lg:flex-none">
        <p className="text-label font-semibold text-foreground">{title}</p>
        <p className="text-muted-foreground [overflow-wrap:anywhere]">{body}</p>
      </div>
    </>
  );
  return href.startsWith("mailto:") ? (
    <a href={href} className={cardClass}>
      {content}
    </a>
  ) : (
    <Link href={href} className={cardClass}>
      {content}
    </Link>
  );
}

export default function ContactPage() {
  return (
    <SitePage>
      <Container className="flex flex-col items-start gap-5 pt-12 pb-8 md:pb-12 lg:pt-20">
        <Eyebrow>Support</Eyebrow>
        <h1 className="text-[26px]/8 font-semibold tracking-[-0.8px] text-balance md:text-heading md:tracking-[-1.5px]">
          How can we help?
        </h1>
        <p className="max-w-[760px] text-body text-pretty text-muted-foreground md:text-lead">
          Questions about your account, billing or how Zuno works? Write to us —
          a real person reads every message.
        </p>
      </Container>

      <Container className="grid gap-3 pb-10 md:pb-16 lg:grid-cols-3 lg:gap-6">
        {CONTACT_CARDS.map((card) => (
          <ContactCard key={card.title} {...card} />
        ))}
      </Container>

      <Container className="flex flex-col gap-10 pb-16 md:gap-16 md:pb-24 xl:flex-row xl:items-start lg:pb-[120px]">
        <div className="w-full rounded-3xl border border-input bg-card px-5 py-7 md:rounded-[40px] md:p-10 xl:w-[520px] xl:shrink-0">
          <ContactForm />
        </div>

        <section
          id="faq"
          aria-labelledby="faq-title"
          className="flex min-w-0 flex-1 scroll-mt-8 flex-col"
        >
          <div className="flex flex-col gap-3 pb-6">
            <Eyebrow>Help &amp; FAQ</Eyebrow>
            <h2
              id="faq-title"
              className="text-2xl leading-[30px] font-semibold tracking-[-0.8px] text-foreground"
            >
              Common questions
            </h2>
          </div>
          <FaqList items={FAQS} />
        </section>
      </Container>
    </SitePage>
  );
}
