import type { ReactNode } from "react";
import { SiteFooter } from "@/components/landing/site-footer";
import { SiteHeader } from "@/components/landing/site-header";
import { cn } from "@/lib/utils";

/** Header + main + footer frame shared by every non-landing page. */
export function SitePage({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className="flex flex-1 flex-col">
      <SiteHeader />
      <main className={cn("flex flex-1 flex-col", className)}>{children}</main>
      <SiteFooter />
    </div>
  );
}

/** Max-width gutter matching the landing sections (120px at desktop). */
export function Container({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "mx-auto w-full max-w-[1440px] px-4 md:px-10 lg:px-[120px]",
        className,
      )}
    >
      {children}
    </div>
  );
}

/** Small centered card used by the delete-account steps. */
export function StatusCard({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex w-full max-w-[560px] flex-col gap-6 rounded-3xl border border-input bg-card px-5 py-8 md:rounded-[40px] md:p-12",
        className,
      )}
    >
      {children}
    </div>
  );
}

export function IconTile({
  children,
  size = "lg",
  className,
}: {
  children: ReactNode;
  size?: "sm" | "md" | "lg";
  className?: string;
}) {
  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full border border-input bg-muted text-foreground",
        size === "sm" && "size-10 [&_svg]:size-5",
        size === "md" && "size-14 [&_svg]:size-6",
        size === "lg" && "size-[72px] [&_svg]:size-[31px]",
        className,
      )}
    >
      {children}
    </span>
  );
}
