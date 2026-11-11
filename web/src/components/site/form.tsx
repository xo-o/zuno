import type { ComponentProps, ReactNode } from "react";
import { cn } from "@/lib/utils";

const fieldClass =
  "w-full rounded-2xl border border-input bg-card px-4 text-body text-foreground transition-colors outline-none placeholder:text-faint focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/30 disabled:opacity-50 aria-invalid:border-destructive/60";

export function Input({ className, ...props }: ComponentProps<"input">) {
  return <input className={cn(fieldClass, "h-13", className)} {...props} />;
}

export function Textarea({ className, ...props }: ComponentProps<"textarea">) {
  return (
    <textarea
      className={cn(
        fieldClass,
        "min-h-[104px] resize-y border-border bg-muted p-4 text-body-lg",
        className,
      )}
      {...props}
    />
  );
}

export function Field({
  label,
  htmlFor,
  helper,
  error,
  children,
  className,
}: {
  label: string;
  htmlFor?: string;
  helper?: ReactNode;
  error?: string | null;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col gap-2", className)}>
      <label
        htmlFor={htmlFor}
        className="text-sm leading-[18px] font-medium text-foreground"
      >
        {label}
      </label>
      {children}
      {error ? (
        <p role="alert" className="text-footnote text-destructive">
          {error}
        </p>
      ) : (
        helper && <p className="text-footnote text-faint">{helper}</p>
      )}
    </div>
  );
}
