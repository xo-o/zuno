"use client";

import { CircleAlert, CircleCheck, LoaderCircle, Trash2 } from "lucide-react";
import Link from "next/link";
import { type FormEvent, useEffect, useState } from "react";
import { Field, Input } from "@/components/site/form";
import { IconTile, StatusCard } from "@/components/site/site-page";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  ApiError,
  confirmAccountDeletion,
  type DeletionPreview,
  getDeletionPreview,
} from "@/lib/api";
import { ROUTES } from "@/lib/site";
import { cn } from "@/lib/utils";

type State =
  | { kind: "loading" }
  | { kind: "invalid"; message: string }
  | { kind: "confirm"; preview: DeletionPreview }
  | { kind: "deleted"; email: string };

const INVALID_LINK = "This link is invalid or has expired. Request a new one.";

export function ConfirmLoading() {
  return (
    <StatusCard className="items-center py-16 text-center" aria-busy="true">
      <LoaderCircle
        className="size-6 animate-spin text-muted-foreground"
        aria-hidden="true"
      />
      <p className="text-body text-muted-foreground">Checking your link…</p>
    </StatusCard>
  );
}

function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? "" : "s"}`;
}

export function ConfirmDeletion({ token }: { token: string | null }) {
  const [state, setState] = useState<State>(
    token ? { kind: "loading" } : { kind: "invalid", message: INVALID_LINK },
  );

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    getDeletionPreview(token)
      .then((preview) => {
        if (!cancelled) setState({ kind: "confirm", preview });
      })
      .catch((err) => {
        if (cancelled) return;
        setState({
          kind: "invalid",
          message:
            err instanceof ApiError && err.status !== 404
              ? err.message
              : INVALID_LINK,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  if (state.kind === "loading") return <ConfirmLoading />;

  if (state.kind === "invalid") {
    return (
      <StatusCard className="items-center text-center">
        <IconTile size="lg">
          <CircleAlert />
        </IconTile>
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl leading-[30px] font-semibold tracking-[-0.8px]">
            This link can’t be used
          </h1>
          <p className="text-body text-muted-foreground">{state.message}</p>
        </div>
        <Link
          href={ROUTES.deleteAccount}
          className={cn(
            buttonVariants({ size: "pill-md" }),
            "w-full md:h-13 md:px-6",
          )}
        >
          Request a new link
        </Link>
      </StatusCard>
    );
  }

  if (state.kind === "deleted") {
    return (
      <StatusCard className="items-center text-center">
        <IconTile size="lg">
          <CircleCheck />
        </IconTile>
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl leading-[30px] font-semibold tracking-[-0.8px]">
            Your account has been deleted
          </h1>
          <p className="text-body break-words text-muted-foreground">
            We’ve emailed a confirmation to {state.email}. Backups are cleared
            within 30 days. Thanks for learning with Zuno.
          </p>
        </div>
        <Link
          href={ROUTES.home}
          className={cn(
            buttonVariants({ size: "pill-md" }),
            "w-full md:h-13 md:px-6",
          )}
        >
          Back to home
        </Link>
      </StatusCard>
    );
  }

  return (
    <ConfirmForm
      token={token ?? ""}
      preview={state.preview}
      onDeleted={(email) => setState({ kind: "deleted", email })}
      onInvalid={(message) => setState({ kind: "invalid", message })}
    />
  );
}

function ConfirmForm({
  token,
  preview,
  onDeleted,
  onInvalid,
}: {
  token: string;
  preview: DeletionPreview;
  onDeleted: (email: string) => void;
  onInvalid: (message: string) => void;
}) {
  const [typed, setTyped] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const confirmed = typed.trim() === "DELETE";
  const { chats, topics, roleplays } = preview.counts;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!confirmed) {
      setError("Type DELETE to confirm.");
      return;
    }
    setPending(true);
    setError(null);
    try {
      const result = await confirmAccountDeletion(token);
      onDeleted(result.email);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        onInvalid(INVALID_LINK);
        return;
      }
      setError(
        err instanceof ApiError
          ? err.message
          : "Something went wrong. Please try again.",
      );
      setPending(false);
    }
  }

  return (
    <StatusCard>
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-6">
        <IconTile size="md">
          <Trash2 />
        </IconTile>
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl leading-[30px] font-semibold tracking-[-0.8px]">
            Delete your account?
          </h1>
          <p className="text-body break-words text-muted-foreground">
            This permanently deletes the Zuno account for {preview.email} —
            including {plural(chats, "chat")}, {plural(topics, "topic")},{" "}
            {plural(roleplays, "roleplay")} and all uploaded files. This can’t
            be undone.
          </p>
        </div>
        <Field
          label="Type DELETE to confirm"
          htmlFor="confirm-delete"
          error={error}
        >
          <Input
            id="confirm-delete"
            name="confirm"
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            value={typed}
            aria-invalid={error ? true : undefined}
            onChange={(event) => {
              setTyped(event.target.value);
              if (error) setError(null);
            }}
          />
        </Field>
        <div className="flex flex-col-reverse gap-3 sm:flex-row">
          <Link
            href={ROUTES.home}
            className={cn(
              buttonVariants({ variant: "outline", size: "pill-md" }),
              "sm:flex-1 md:h-13 md:px-6",
            )}
          >
            Keep my account
          </Link>
          <Button
            type="submit"
            variant="danger"
            size="pill-md"
            className="sm:flex-1 md:h-13 md:px-6"
            disabled={!confirmed || pending}
          >
            {pending ? "Deleting…" : "Delete permanently"}
          </Button>
        </div>
      </form>
    </StatusCard>
  );
}
