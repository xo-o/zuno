import Image from "next/image";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** `responsive` shrinks to the 78% mobile-nav lockup below `md`. */
export function Logo({
  className,
  responsive = false,
}: {
  className?: string;
  responsive?: boolean;
}) {
  return (
    <span
      className={cn(
        "flex items-center gap-2.5",
        responsive && "max-md:gap-[7.5px]",
        className,
      )}
    >
      <Image
        src="/logo-mark.svg"
        alt=""
        width={34}
        height={34}
        priority
        className={cn(responsive && "max-md:size-[26px]")}
      />
      <span
        className={cn(
          "font-brand text-[30px] leading-[1.267] font-medium tracking-[-0.6px] text-white",
          responsive && "max-md:text-[23.4px] max-md:tracking-[-0.47px]",
        )}
      >
        Zuno
      </span>
    </span>
  );
}

export function Eyebrow({ children }: { children: ReactNode }) {
  return (
    <p className="text-eyebrow font-medium text-faint uppercase">{children}</p>
  );
}

export function SectionHeader({
  eyebrow,
  title,
  lead,
  align = "center",
  className,
}: {
  eyebrow: string;
  title: string;
  lead?: string;
  align?: "center" | "start";
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col gap-4",
        align === "center"
          ? "mx-auto max-w-[800px] items-center text-center"
          : "max-w-[520px] items-start",
        className,
      )}
    >
      <Eyebrow>{eyebrow}</Eyebrow>
      <h2 className="text-[26px]/8 font-semibold tracking-[-0.8px] md:text-heading md:tracking-[-1.5px]">
        {title}
      </h2>
      {lead && (
        <p className="max-w-[620px] text-body text-pretty text-muted-foreground md:text-lead">
          {lead}
        </p>
      )}
    </div>
  );
}
