import Image from "next/image";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * Device mockup. Screens are authored at the app's 402×874 frame size and scaled by `--s`,
 * which callers set through `className` (e.g. `[--s:0.66] lg:[--s:0.86]`).
 */
export function Phone({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      className={cn(
        "shrink-0 rounded-[calc(52px*var(--s))] border border-border-strong bg-elevated p-[calc(12px*var(--s))] shadow-[0_40px_80px_rgb(0_0_0/0.5)] [--s:0.86]",
        className,
      )}
    >
      <div className="relative h-[calc(874px*var(--s))] w-[calc(402px*var(--s))] overflow-hidden rounded-[calc(42px*var(--s))] bg-background">
        {children}
      </div>
    </div>
  );
}

/** Renders native 402×874 markup scaled to the phone's screen. */
export function ScaledScreen({ children }: { children: ReactNode }) {
  return (
    <div className="absolute top-0 left-0 h-[874px] w-[402px] origin-top-left [transform:scale(var(--s))]">
      {children}
    </div>
  );
}

export function ScreenImage({ src, alt }: { src: string; alt: string }) {
  return (
    <Image
      src={src}
      alt={alt}
      fill
      sizes="(min-width: 1024px) 346px, 260px"
      className="object-cover object-top"
    />
  );
}
