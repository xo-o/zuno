"use client";

import { Check, CircleAlert, Clock, Lock, Mail, Trash2 } from "lucide-react";
import { type FormEvent, type ReactNode, useEffect, useState } from "react";
import { Eyebrow } from "@/components/landing/brand";
import { Field, Input } from "@/components/site/form";
import { Strong, TextLink } from "@/components/site/legal";
import { Container, IconTile, StatusCard } from "@/components/site/site-page";
import { Button } from "@/components/ui/button";
import { ApiError, requestAccountDeletion } from "@/lib/api";
import { SUPPORT_EMAIL } from "@/lib/site";

const RESEND_COOLDOWN_SECONDS = 30;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const APP_STEPS = [
  {
    title: "Open Settings",
    body: "Tap the menu in the top-left corner, then your profile or Settings.",
  },
  {
    title: "Go to Account",
    body: "Scroll to the bottom of the Account screen.",
  },
  {
    title: "Tap Delete account",
    body: "Confirm, and your account is deleted right away.",
  },
];

const DELETED_NOW = [
  "Profile, name, email and photo",
  "All chats, corrections, translations and saved vocabulary",
  "Topics, roleplays, schedules and reminders",
  "Uploaded photos, documents and links",
  "Preferences, learning progress and push tokens",
];

const KEPT_FOR_A_WHILE = [
  { lead: "Encrypted backups", body: "erased automatically within 30 days." },
  {
    lead: "Chat reports",
    body: "kept up to 12 months for safety reviews, without your profile.",
  },
  {
    lead: "Purchase records",
    body: "kept by Google Play and as required by tax law.",
  },
];

export function DeleteAccountRequest() {
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((value) => value - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  async function send(address: string) {
    setPending(true);
    setError(null);
    try {
      await requestAccountDeletion(address);
      setSentTo(address);
      setCooldown(RESEND_COOLDOWN_SECONDS);
      window.scrollTo({ top: 0 });
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : "Something went wrong. Please try again.",
      );
    } finally {
      setPending(false);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const address = email.trim();
    if (!EMAIL_PATTERN.test(address)) {
      setError("Enter a valid email address.");
      return;
    }
    void send(address);
  }

  if (sentTo) {
    return (
      <Container className="flex justify-center pt-14 pb-16 md:pt-24 md:pb-[120px]">
        <StatusCard className="items-center text-center">
          <IconTile size="lg">
            <Mail />
          </IconTile>
          <div className="flex w-full flex-col gap-2">
            <h1 className="text-2xl leading-[30px] font-semibold tracking-[-0.8px]">
              Check your email
            </h1>
            <p className="text-body text-muted-foreground">
              We sent a confirmation link to
            </p>
            <p className="text-body font-medium tracking-[-0.1px] break-all text-foreground">
              {sentTo}
            </p>
          </div>
          <p className="text-sm leading-5 text-faint">
            Open it on any device to confirm deletion. The link expires in 30
            minutes. Didn’t get it? Check spam, or resend.
          </p>
          {error && (
            <p role="alert" className="text-sm leading-5 text-destructive">
              {error}
            </p>
          )}
          <Button
            variant="outline"
            size="pill-md"
            className="w-full disabled:opacity-40 md:h-13 md:px-6"
            disabled={cooldown > 0 || pending}
            onClick={() => send(sentTo)}
          >
            {pending
              ? "Sending…"
              : cooldown > 0
                ? `Resend link in ${cooldown}s`
                : "Resend link"}
          </Button>
          <Button
            variant="ghost"
            size="pill-md"
            onClick={() => {
              setEmail(sentTo);
              setSentTo(null);
              setError(null);
            }}
          >
            Use a different email
          </Button>
        </StatusCard>
      </Container>
    );
  }

  return (
    <>
      <Container className="flex flex-col items-start gap-10 pt-12 pb-14 md:pb-24 lg:pt-20 xl:flex-row xl:gap-24">
        <div className="flex w-full min-w-0 flex-1 flex-col gap-6">
          <Eyebrow>Account</Eyebrow>
          <h1 className="text-[26px] leading-8 font-semibold tracking-[-0.8px] md:text-heading md:leading-[52px] md:tracking-[-1.5px]">
            Delete your Zuno account
          </h1>
          <p className="text-body text-pretty text-muted-foreground md:text-lead">
            Permanently delete your account and all the data that comes with it.
            Do it in the app in a few taps — or right here, without installing
            anything.
          </p>
          <div className="flex w-full flex-col gap-5 rounded-3xl border border-input bg-card px-5 py-6 md:p-8">
            <Eyebrow>Option 1 · In the app</Eyebrow>
            <ol className="flex flex-col gap-5">
              {APP_STEPS.map((step, index) => (
                <li key={step.title} className="flex flex-col gap-5">
                  <span className="flex size-11 items-center justify-center rounded-full border border-border-strong text-label font-semibold">
                    {index + 1}
                  </span>
                  <div className="flex flex-col gap-2">
                    <h2 className="text-lg leading-6 font-semibold tracking-[-0.3px] md:text-title md:leading-7 md:tracking-[-0.4px]">
                      {step.title}
                    </h2>
                    <p className="text-body text-muted-foreground">
                      {step.body}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </div>

        <form
          noValidate
          onSubmit={onSubmit}
          className="flex w-full flex-col gap-6 rounded-3xl border border-input bg-card px-5 py-7 md:rounded-[40px] md:p-10 xl:w-[520px] xl:shrink-0"
        >
          <Eyebrow>Option 2 · On the web</Eyebrow>
          <div className="flex flex-col gap-2">
            <h2 className="text-lg leading-6 font-semibold tracking-[-0.3px] md:text-title md:leading-7 md:tracking-[-0.4px]">
              Request deletion by email
            </h2>
            <p className="text-body text-muted-foreground">
              Enter the email you use with Zuno. We’ll send a confirmation link
              — nothing is deleted until you confirm.
            </p>
          </div>
          <Field
            label="Email address"
            htmlFor="delete-email"
            helper="Signed in with Google? Use your Google email."
            error={error}
          >
            <Input
              id="delete-email"
              type="email"
              name="email"
              autoComplete="email"
              inputMode="email"
              placeholder="name@example.com"
              value={email}
              aria-invalid={error ? true : undefined}
              onChange={(event) => {
                setEmail(event.target.value);
                if (error) setError(null);
              }}
            />
          </Field>
          <Button
            type="submit"
            size="pill-md"
            className="w-full md:h-13 md:px-6"
            disabled={pending}
          >
            {pending ? "Sending…" : "Send confirmation link"}
          </Button>
          <p className="flex items-start gap-2 text-footnote text-faint">
            <Lock className="size-4 shrink-0" aria-hidden="true" />
            For your security, the link expires in 30 minutes and works only
            once.
          </p>
        </form>
      </Container>

      <Container className="flex flex-col gap-8 pb-14 md:pb-24">
        <div className="flex flex-col gap-3">
          <Eyebrow>What happens to your data</Eyebrow>
          <h2 className="text-[26px] leading-8 font-semibold tracking-[-0.8px] md:text-heading md:leading-[52px] md:tracking-[-1.5px]">
            Deleted means deleted.
          </h2>
        </div>
        <div className="grid gap-6 md:grid-cols-2 md:items-start">
          <DataCard icon={<Trash2 />} title="Deleted right away">
            {DELETED_NOW.map((item) => (
              <DataRow key={item} icon={<Check />}>
                {item}
              </DataRow>
            ))}
          </DataCard>
          <DataCard icon={<Clock />} title="Kept for a limited time">
            {KEPT_FOR_A_WHILE.map((item) => (
              <DataRow key={item.lead} icon={<Clock />}>
                <Strong>{`${item.lead} — `}</Strong>
                {item.body}
              </DataRow>
            ))}
          </DataCard>
        </div>
        <div className="flex items-start gap-3 rounded-[20px] border border-input bg-card px-6 py-5 text-body text-muted-foreground">
          <CircleAlert className="mt-px size-5 shrink-0" aria-hidden="true" />
          <p>
            <Strong>Have Zuno Pro? </Strong>
            Deleting your account doesn’t cancel your subscription. Cancel it
            first in Google Play → Payments &amp; subscriptions.
          </p>
        </div>
        <p className="text-body text-faint">
          Can’t access your email anymore? Contact{" "}
          <TextLink href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</TextLink>{" "}
          and we’ll verify your identity and delete your account for you.
        </p>
      </Container>
    </>
  );
}

function DataCard({
  icon,
  title,
  children,
}: {
  icon: ReactNode;
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-4 rounded-3xl border border-input bg-card px-5 py-6 md:p-8">
      <div className="flex items-center gap-3">
        <IconTile size="sm" className="[&_svg]:size-[17px]">
          {icon}
        </IconTile>
        <h3 className="text-body-lg font-medium tracking-[-0.2px]">{title}</h3>
      </div>
      <ul className="flex flex-col gap-4">{children}</ul>
    </div>
  );
}

function DataRow({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <li className="flex items-start gap-3 text-body text-muted-foreground [&>svg]:mt-px [&>svg]:size-5 [&>svg]:shrink-0 [&>svg]:text-foreground">
      {icon}
      <span className="min-w-0 flex-1">{children}</span>
    </li>
  );
}
