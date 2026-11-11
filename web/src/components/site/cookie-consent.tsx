"use client";

import { Info } from "lucide-react";
import Link from "next/link";
import { type ComponentProps, useEffect, useId, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { ROUTES } from "@/lib/site";
import { cn } from "@/lib/utils";

export const CONSENT_COOKIE = "zuno_cookie_consent";
const CONSENT_VERSION = 1;
const CONSENT_MAX_AGE = 60 * 60 * 24 * 365;
const OPEN_EVENT = "zuno:cookie-settings";
/** Dispatched on window with the saved consent as `detail` whenever it changes. */
export const CONSENT_CHANGE_EVENT = "zuno:cookie-consent";

export type CookieConsent = {
  v: number;
  preferences: boolean;
  analytics: boolean;
  ts: number;
};

type Choices = Pick<CookieConsent, "preferences" | "analytics">;

export function readCookieConsent(): CookieConsent | null {
  if (typeof document === "undefined") return null;
  const raw = document.cookie
    .split("; ")
    .find((part) => part.startsWith(`${CONSENT_COOKIE}=`))
    ?.slice(CONSENT_COOKIE.length + 1);
  if (!raw) return null;
  try {
    const value = JSON.parse(decodeURIComponent(raw)) as Partial<CookieConsent>;
    if (value.v !== CONSENT_VERSION) return null;
    return {
      v: CONSENT_VERSION,
      preferences: value.preferences === true,
      analytics: value.analytics === true,
      ts: typeof value.ts === "number" ? value.ts : 0,
    };
  } catch {
    return null;
  }
}

function writeCookieConsent(choices: Choices) {
  const consent: CookieConsent = {
    v: CONSENT_VERSION,
    ...choices,
    ts: Date.now(),
  };
  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  // biome-ignore lint/suspicious/noDocumentCookie: Cookie Store API isn't available in every supported browser.
  document.cookie = `${CONSENT_COOKIE}=${encodeURIComponent(
    JSON.stringify(consent),
  )}; Max-Age=${CONSENT_MAX_AGE}; Path=/; SameSite=Lax${secure}`;
  window.dispatchEvent(
    new CustomEvent<CookieConsent>(CONSENT_CHANGE_EVENT, { detail: consent }),
  );
}

/** Reopens the cookie banner with the preferences panel expanded. */
export function openCookieSettings() {
  window.dispatchEvent(new Event(OPEN_EVENT));
}

export function ManageCookiesButton({
  className,
  children = "Open cookie settings",
  ...props
}: Omit<ComponentProps<"button">, "onClick" | "type">) {
  return (
    <button
      type="button"
      onClick={openCookieSettings}
      className={cn(
        "self-start rounded-sm text-[13px] leading-4 font-medium text-foreground underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50",
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}

/** First-visit consent card, fixed bottom-right. Mount once in the root layout. */
export function CookieConsent() {
  const [open, setOpen] = useState(false);
  const [managing, setManaging] = useState(false);
  const [hasConsent, setHasConsent] = useState(false);
  const [choices, setChoices] = useState<Choices>({
    preferences: false,
    analytics: false,
  });
  const titleRef = useRef<HTMLParagraphElement>(null);
  const titleId = useId();
  const bodyId = useId();

  useEffect(() => {
    if (!readCookieConsent()) setOpen(true);

    const onOpen = () => {
      const current = readCookieConsent();
      setHasConsent(current !== null);
      setChoices({
        preferences: current?.preferences ?? false,
        analytics: current?.analytics ?? false,
      });
      setManaging(true);
      setOpen(true);
      requestAnimationFrame(() => titleRef.current?.focus());
    };
    window.addEventListener(OPEN_EVENT, onOpen);
    return () => window.removeEventListener(OPEN_EVENT, onOpen);
  }, []);

  if (!open) return null;

  const save = (next: Choices) => {
    writeCookieConsent(next);
    setHasConsent(true);
    setManaging(false);
    setOpen(false);
  };

  const back = () => {
    if (hasConsent) setOpen(false);
    setManaging(false);
  };

  return (
    <section
      aria-labelledby={titleId}
      aria-describedby={bodyId}
      onKeyDown={(event) => {
        if (event.key === "Escape" && hasConsent) back();
      }}
      className="fixed inset-x-4 bottom-4 z-50 flex max-h-[calc(100dvh-2rem)] flex-col gap-4 overflow-y-auto rounded-3xl border border-input bg-elevated p-6 shadow-[0_16px_24px_rgb(0_0_0/0.5)] sm:inset-x-auto sm:right-8 sm:bottom-8 sm:w-[520px] sm:max-w-[calc(100vw-4rem)]"
    >
      <div className="flex items-center gap-2.5">
        <Info aria-hidden="true" className="size-5 shrink-0 text-foreground" />
        <p
          ref={titleRef}
          id={titleId}
          tabIndex={-1}
          className="text-body-lg font-medium tracking-[-0.2px] text-foreground outline-none"
        >
          {managing ? "Cookie settings" : "We use a few cookies"}
        </p>
      </div>

      <p id={bodyId} className="text-sm leading-5 text-muted-foreground">
        {managing
          ? "Choose which optional cookies we may use. You can change this anytime. Read "
          : "Essential cookies keep you signed in. With your OK, we’ll also use anonymous analytics to improve the site. Read "}
        <Link
          href={ROUTES.cookies}
          className="underline underline-offset-2 transition-colors hover:text-foreground"
        >
          our Cookie Policy
        </Link>
        .
      </p>

      {managing && (
        <ul className="flex flex-col rounded-[20px] border border-border bg-card">
          <ToggleRow
            label="Essential"
            description="Sign-in, security and remembering this choice. Always on."
            checked
            disabled
          />
          <ToggleRow
            label="Preferences"
            description="Remembers settings like your website language."
            checked={choices.preferences}
            onChange={(preferences) =>
              setChoices((prev) => ({ ...prev, preferences }))
            }
          />
          <ToggleRow
            label="Analytics"
            description="Counts visits and pages anonymously so we can improve the site."
            checked={choices.analytics}
            onChange={(analytics) =>
              setChoices((prev) => ({ ...prev, analytics }))
            }
          />
        </ul>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="ghost"
          size="pill"
          onClick={managing ? back : () => setManaging(true)}
        >
          {managing ? (hasConsent ? "Cancel" : "Back") : "Manage"}
        </Button>
        <span aria-hidden="true" className="min-w-px flex-1" />
        {managing ? (
          <Button
            variant="outline"
            size="pill"
            className="dark:border-input dark:bg-card"
            onClick={() => save({ preferences: true, analytics: true })}
          >
            Accept all
          </Button>
        ) : (
          <Button
            variant="outline"
            size="pill"
            className="dark:border-input dark:bg-card"
            onClick={() => save({ preferences: false, analytics: false })}
          >
            Reject non-essential
          </Button>
        )}
        <Button
          size="pill"
          onClick={() =>
            save(managing ? choices : { preferences: true, analytics: true })
          }
        >
          {managing ? "Save choices" : "Accept all"}
        </Button>
      </div>
    </section>
  );
}

function ToggleRow({
  label,
  description,
  checked,
  disabled,
  onChange,
}: {
  label: string;
  description: string;
  checked: boolean;
  disabled?: boolean;
  onChange?: (checked: boolean) => void;
}) {
  const labelId = useId();
  const descriptionId = useId();
  return (
    <li className="flex items-center gap-4 border-border px-4 py-3 not-first:border-t">
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span
          id={labelId}
          className="text-sm leading-[18px] font-medium text-foreground"
        >
          {label}
        </span>
        <span id={descriptionId} className="text-footnote text-faint">
          {description}
        </span>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={labelId}
        aria-describedby={descriptionId}
        disabled={disabled}
        onClick={() => onChange?.(!checked)}
        className={cn(
          "relative inline-flex h-6 w-10 shrink-0 items-center rounded-full border border-transparent p-0.5 transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50",
          checked ? "bg-primary" : "bg-border-strong",
        )}
      >
        <span
          aria-hidden="true"
          className={cn(
            "size-5 rounded-full shadow-sm transition-transform",
            checked
              ? "translate-x-4 bg-primary-foreground"
              : "translate-x-0 bg-foreground",
          )}
        />
      </button>
    </li>
  );
}
