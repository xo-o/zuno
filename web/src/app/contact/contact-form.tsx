"use client";

import { Radio } from "@base-ui/react/radio";
import { RadioGroup } from "@base-ui/react/radio-group";
import { Check, CircleAlert } from "lucide-react";
import Link from "next/link";
import { type FormEvent, useEffect, useId, useRef, useState } from "react";
import { Field, Input, Textarea } from "@/components/site/form";
import { IconTile } from "@/components/site/site-page";
import { Button } from "@/components/ui/button";
import { ApiError, type ContactTopic, sendContactMessage } from "@/lib/api";
import { ROUTES } from "@/lib/site";

const TOPICS: { value: ContactTopic; label: string }[] = [
  { value: "account", label: "Account & sign-in" },
  { value: "billing", label: "Billing & Pro" },
  { value: "voice", label: "Voice & microphone" },
  { value: "bug", label: "Report a bug" },
  { value: "other", label: "Something else" },
];

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Errors = Partial<Record<"name" | "email" | "message", string>>;

function validate(values: {
  name: string;
  email: string;
  message: string;
}): Errors {
  const errors: Errors = {};
  if (!values.name) errors.name = "Tell us your name.";
  else if (values.name.length > 100)
    errors.name = "Keep your name under 100 characters.";
  if (!EMAIL_PATTERN.test(values.email))
    errors.email = "Enter a valid email address.";
  if (values.message.length < 10)
    errors.message = "Add a little more detail — at least 10 characters.";
  else if (values.message.length > 5000)
    errors.message = "Keep your message under 5,000 characters.";
  return errors;
}

export function ContactForm() {
  const id = useId();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [topic, setTopic] = useState<ContactTopic>("account");
  const [message, setMessage] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const sentHeadingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (sentTo) sentHeadingRef.current?.focus();
  }, [sentTo]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = {
      name: name.trim(),
      email: email.trim(),
      message: message.trim(),
    };
    const nextErrors = validate(values);
    setErrors(nextErrors);
    setSubmitError(null);
    if (Object.keys(nextErrors).length > 0) {
      const first = (["name", "email", "message"] as const).find(
        (key) => nextErrors[key],
      );
      if (first) document.getElementById(`${id}-${first}`)?.focus();
      return;
    }

    setSubmitting(true);
    try {
      await sendContactMessage({ ...values, topic });
      setSentTo(values.email);
    } catch (error) {
      setSubmitError(
        error instanceof ApiError
          ? error.message
          : "Something went wrong. Please try again.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  function reset() {
    setName("");
    setTopic("account");
    setMessage("");
    setErrors({});
    setSubmitError(null);
    setSentTo(null);
  }

  if (sentTo) {
    return (
      <div className="flex flex-col items-start gap-5 py-2 sm:py-6">
        <IconTile size="md">
          <Check />
        </IconTile>
        <div className="flex flex-col gap-2">
          <h2
            ref={sentHeadingRef}
            tabIndex={-1}
            className="text-title font-semibold text-foreground outline-none"
          >
            Message sent
          </h2>
          <p className="text-body text-muted-foreground [overflow-wrap:anywhere]">
            We’ll reply to <span className="text-foreground">{sentTo}</span>{" "}
            within 1–2 business days.
          </p>
        </div>
        <Button
          variant="ghost"
          size="pill-md"
          className="-ml-5"
          onClick={reset}
        >
          Send another message
        </Button>
      </div>
    );
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <h2 className="text-lg/6 font-semibold tracking-[-0.3px] text-foreground md:text-title md:tracking-[-0.4px]">
          Send us a message
        </h2>
        <p className="text-body text-muted-foreground">
          Use the email on your Zuno account so we can find it.
        </p>
      </div>

      <Field label="Name" htmlFor={`${id}-name`} error={errors.name}>
        <Input
          id={`${id}-name`}
          name="name"
          autoComplete="name"
          placeholder="Your name"
          maxLength={100}
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={errors.name ? true : undefined}
        />
      </Field>

      <Field label="Email address" htmlFor={`${id}-email`} error={errors.email}>
        <Input
          id={`${id}-email`}
          name="email"
          type="email"
          inputMode="email"
          autoComplete="email"
          placeholder="name@example.com"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          aria-invalid={errors.email ? true : undefined}
        />
      </Field>

      <div className="flex flex-col gap-2">
        <span
          id={`${id}-topic`}
          className="text-sm leading-[18px] font-medium text-foreground"
        >
          Topic
        </span>
        <RadioGroup
          aria-labelledby={`${id}-topic`}
          value={topic}
          onValueChange={(value) => setTopic(value as ContactTopic)}
          className="flex flex-wrap gap-2"
        >
          {TOPICS.map((item) => (
            <Radio.Root
              key={item.value}
              value={item.value}
              className="flex h-9 cursor-pointer items-center rounded-full border border-input bg-card px-3.5 text-[13px] leading-4 font-medium text-foreground transition-colors outline-none select-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/30 data-checked:bg-foreground/10"
            >
              {item.label}
            </Radio.Root>
          ))}
        </RadioGroup>
      </div>

      <Field label="Message" htmlFor={`${id}-message`} error={errors.message}>
        <Textarea
          id={`${id}-message`}
          name="message"
          placeholder="Tell us what happened, and include your device model if it’s about the app."
          maxLength={5000}
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          aria-invalid={errors.message ? true : undefined}
        />
      </Field>

      <Button
        type="submit"
        size="pill-md"
        className="w-full md:h-13 md:px-6"
        disabled={submitting}
      >
        {submitting ? "Sending…" : "Send message"}
      </Button>

      {submitError && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-2xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-body text-destructive"
        >
          <CircleAlert className="mt-0.5 size-4 shrink-0" />
          <p className="min-w-0 flex-1">{submitError}</p>
        </div>
      )}

      <p className="text-footnote text-faint">
        By sending, you agree we’ll use your details to reply. See our{" "}
        <Link
          href={ROUTES.privacy}
          className="underline decoration-faint underline-offset-2 transition-colors hover:text-muted-foreground"
        >
          Privacy Policy
        </Link>
        .
      </p>
    </form>
  );
}
