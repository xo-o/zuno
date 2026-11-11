import { Check } from "lucide-react";
import Link from "next/link";
import { type ComponentProps, Fragment, type ReactNode } from "react";
import { Eyebrow } from "@/components/landing/brand";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { Container } from "./site-page";
import { TableOfContents, type TocItem } from "./table-of-contents";

export type { TocItem };

/** Eyebrow, title, dot-separated meta and lead at the top of a content page. */
export function PageHeader({
  eyebrow,
  title,
  meta,
  lead,
  compact = false,
  className,
}: {
  eyebrow: string;
  title: string;
  meta?: string[];
  lead?: ReactNode;
  /** Smaller title and lead on mobile, as on the legal pages. */
  compact?: boolean;
  className?: string;
}) {
  return (
    <Container
      className={cn(
        "flex flex-col items-start gap-5 pt-12 pb-10 lg:pt-20 lg:pb-14",
        className,
      )}
    >
      <Eyebrow>{eyebrow}</Eyebrow>
      <h1
        className={cn(
          "max-w-[900px] font-semibold text-balance md:text-heading md:leading-[52px] md:tracking-[-1.5px]",
          compact
            ? "text-[26px] leading-8 tracking-[-0.8px]"
            : "text-[34px] leading-[42px] tracking-[-1px]",
        )}
      >
        {title}
      </h1>
      {meta && meta.length > 0 && (
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm leading-5 text-faint">
          {meta.map((item, index) => (
            <Fragment key={item}>
              {index > 0 && (
                <span
                  aria-hidden="true"
                  className="size-[3px] shrink-0 rounded-full bg-faint"
                />
              )}
              <span>{item}</span>
            </Fragment>
          ))}
        </p>
      )}
      {lead && (
        <p
          className={cn(
            "max-w-[760px] text-pretty text-muted-foreground md:text-lead",
            compact ? "text-body" : "text-lead",
          )}
        >
          {lead}
        </p>
      )}
    </Container>
  );
}

/** Divider, sticky "On this page" sidebar and the 760px reading column. */
export function LegalLayout({
  toc,
  aside,
  children,
}: {
  toc: TocItem[];
  aside?: ReactNode;
  children: ReactNode;
}) {
  return (
    <>
      <Container>
        <Separator />
      </Container>
      <Container className="flex items-start gap-12 pt-10 pb-16 lg:pt-14 lg:pb-[120px] xl:gap-24">
        <aside className="sticky top-8 hidden w-60 shrink-0 flex-col gap-6 lg:flex">
          <TableOfContents items={toc} />
          {aside}
        </aside>
        <div className="flex w-full max-w-[760px] min-w-0 flex-col gap-10 md:gap-14">
          {children}
          {aside && <div className="flex flex-col lg:hidden">{aside}</div>}
        </div>
      </Container>
    </>
  );
}

export function AsideCard({
  title,
  body,
  children,
}: {
  title: string;
  body: string;
  children?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2 rounded-[20px] border border-border bg-card p-5">
      <p className="text-sm leading-[18px] font-medium text-foreground">
        {title}
      </p>
      <p className="text-sm leading-5 text-muted-foreground">{body}</p>
      {children}
    </div>
  );
}

export function LegalSection({
  id,
  number,
  title,
  children,
}: {
  id: string;
  number: number;
  title: string;
  children: ReactNode;
}) {
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className="flex scroll-mt-8 flex-col gap-4"
    >
      <div className="flex items-baseline gap-3">
        <span className="text-sm leading-[18px] font-medium text-faint tabular-nums">
          {String(number).padStart(2, "0")}
        </span>
        <h2
          id={`${id}-title`}
          className="min-w-0 flex-1 text-lg leading-6 font-semibold tracking-[-0.3px] md:text-title md:leading-7 md:tracking-[-0.4px]"
        >
          {title}
        </h2>
      </div>
      {children}
    </section>
  );
}

export const asideLinkClass =
  "self-start rounded-sm text-[13px] leading-4 font-medium break-all text-foreground underline-offset-4 hover:underline";

export type LegalTableColumn = { label: string; className?: string };

/** Bordered data table; rows stack into cards below `md`. */
export function LegalTable({
  columns,
  rows,
}: {
  columns: LegalTableColumn[];
  rows: { key: string; cells: ReactNode[] }[];
}) {
  const last = columns.length - 1;
  return (
    <div className="overflow-hidden rounded-[20px] border border-input">
      <table className="block w-full border-collapse text-left md:table md:table-fixed">
        <thead className="hidden bg-muted md:table-header-group">
          <tr className="text-[13px] leading-4 font-medium text-faint">
            {columns.map((column, index) => (
              <th
                key={column.label}
                scope="col"
                className={cn(
                  "py-3 pr-4 font-medium",
                  index === 0 && "pl-5",
                  index === last && "pr-5",
                  column.className,
                )}
              >
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="block md:table-row-group">
          {rows.map((row) => (
            <tr
              key={row.key}
              className="flex flex-col gap-1 border-t border-border px-5 py-4 md:table-row md:p-0 md:align-top"
            >
              {row.cells.map((cell, index) =>
                index === 0 ? (
                  <th
                    // biome-ignore lint/suspicious/noArrayIndexKey: cells follow the column order.
                    key={index}
                    scope="row"
                    className="text-body font-medium tracking-[-0.1px] wrap-break-word text-foreground md:py-4 md:pr-4 md:pl-5"
                  >
                    {cell}
                  </th>
                ) : (
                  <td
                    // biome-ignore lint/suspicious/noArrayIndexKey: cells follow the column order.
                    key={index}
                    className={cn(
                      "text-sm leading-5 text-muted-foreground md:py-4 md:pr-4",
                      index === last && "md:pr-5",
                    )}
                  >
                    {cell}
                  </td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function SubHeading({ children }: { children: ReactNode }) {
  return (
    <h3 className="text-body-lg font-medium tracking-[-0.2px] text-foreground">
      {children}
    </h3>
  );
}

export function P({ className, ...props }: ComponentProps<"p">) {
  return (
    <p
      className={cn("text-body-lg text-muted-foreground", className)}
      {...props}
    />
  );
}

/** Emphasised lead-in inside body copy ("Subscriptions — …"). */
export function Strong({ children }: { children: ReactNode }) {
  return <strong className="font-medium text-foreground">{children}</strong>;
}

export const textLinkClass =
  "text-foreground underline decoration-faint underline-offset-4 transition-colors hover:decoration-foreground";

/** Inline link; in-site paths ("/…", "#…") use next/link. */
export function TextLink({
  className,
  href = "",
  ...props
}: ComponentProps<"a"> & { href: string }) {
  const classes = cn(textLinkClass, className);
  if (href.startsWith("/") || href.startsWith("#")) {
    return <Link href={href} className={classes} {...props} />;
  }
  return <a href={href} className={classes} {...props} />;
}

export function BulletList({ children }: { children: ReactNode }) {
  return <ul className="flex flex-col gap-2.5">{children}</ul>;
}

export function Bullet({
  lead,
  children,
}: {
  lead?: string;
  children: ReactNode;
}) {
  return (
    <li className="flex items-start gap-3 text-body-lg text-muted-foreground">
      <span
        aria-hidden="true"
        className="flex h-6 w-1.5 shrink-0 items-center justify-center"
      >
        <span className="size-1.5 rounded-full bg-faint" />
      </span>
      <span className="min-w-0 flex-1">
        {lead && <Strong>{`${lead} — `}</Strong>}
        {children}
      </span>
    </li>
  );
}

export function Callout({
  icon,
  children,
  className,
}: {
  icon: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-[20px] border border-border bg-card px-5 py-4 text-body text-muted-foreground [&>svg]:mt-px [&>svg]:size-5 [&>svg]:shrink-0 [&>svg]:text-muted-foreground",
        className,
      )}
    >
      {icon}
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

/** "The short version" card of check items above the legal sections. */
export function SummaryCard({
  title,
  items,
}: {
  title: string;
  items: ReactNode[];
}) {
  return (
    <div className="flex flex-col gap-4 rounded-3xl border border-input bg-card px-5 py-6 md:p-8">
      <Eyebrow>{title}</Eyebrow>
      <ul className="flex flex-col gap-4">
        {items.map((item, index) => (
          <CheckItem
            // biome-ignore lint/suspicious/noArrayIndexKey: static content.
            key={index}
            className="[&>span+span]:pt-0 md:[&>span+span]:pt-0.5"
          >
            {item}
          </CheckItem>
        ))}
      </ul>
    </div>
  );
}

export function CheckItem({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <li
      className={cn(
        "flex items-start gap-3 text-body-lg text-muted-foreground",
        className,
      )}
    >
      <span className="flex size-7 shrink-0 items-center justify-center rounded-full border border-border bg-muted">
        <Check className="size-4 text-foreground" />
      </span>
      <span className="min-w-0 flex-1 pt-0.5">{children}</span>
    </li>
  );
}
