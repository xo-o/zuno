"use client";

import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";

export type TocItem = { id: string; label: string };

/** "On this page" list that highlights the section currently in view. */
export function TableOfContents({ items }: { items: TocItem[] }) {
  const [active, setActive] = useState(items[0]?.id);

  useEffect(() => {
    const sections = items
      .map((item) => document.getElementById(item.id))
      .filter((el): el is HTMLElement => el !== null);
    if (sections.length === 0) return;

    const update = () => {
      // The last section whose top has passed the upper third of the viewport.
      const line = window.innerHeight / 3;
      let current = sections[0].id;
      for (const section of sections) {
        if (section.getBoundingClientRect().top <= line) current = section.id;
      }
      const atBottom =
        window.innerHeight + window.scrollY >=
        document.documentElement.scrollHeight - 2;
      setActive(atBottom ? sections[sections.length - 1].id : current);
    };

    update();
    window.addEventListener("scroll", update, { passive: true });
    window.addEventListener("resize", update);
    return () => {
      window.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
    };
  }, [items]);

  return (
    <nav aria-label="On this page" className="flex flex-col gap-0.5">
      <p className="px-3 pb-3 text-eyebrow font-medium text-faint uppercase">
        On this page
      </p>
      {items.map((item) => {
        const isActive = item.id === active;
        return (
          <a
            key={item.id}
            href={`#${item.id}`}
            aria-current={isActive ? "location" : undefined}
            className={cn(
              "flex min-h-9 items-center gap-2.5 rounded-xl px-3 py-2 text-sm leading-[18px] font-medium transition-colors",
              isActive
                ? "bg-muted text-foreground"
                : "text-faint hover:text-muted-foreground",
            )}
          >
            <span
              aria-hidden="true"
              className={cn(
                "h-4 w-0.5 shrink-0 rounded-full",
                isActive ? "bg-foreground" : "bg-border",
              )}
            />
            {item.label}
          </a>
        );
      })}
    </nav>
  );
}
