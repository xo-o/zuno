"use client";

import { Accordion } from "@base-ui/react/accordion";
import { ChevronDown } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export type FaqEntry = { question: string; answer: ReactNode };

/** Web/FAQ Item list: one open at a time, first open by default. */
export function FaqList({
  items,
  defaultOpen = 0,
  className,
}: {
  items: FaqEntry[];
  defaultOpen?: number | null;
  className?: string;
}) {
  return (
    <Accordion.Root
      defaultValue={defaultOpen === null ? [] : [defaultOpen]}
      hiddenUntilFound
      className={cn("flex w-full flex-col", className)}
    >
      {items.map((item, index) => (
        <Accordion.Item
          key={item.question}
          value={index}
          className="border-t border-border"
        >
          <Accordion.Header>
            <Accordion.Trigger className="group flex w-full cursor-pointer items-center gap-4 py-6 text-left text-body-lg font-medium tracking-[-0.2px] text-foreground outline-none focus-visible:underline focus-visible:decoration-faint focus-visible:underline-offset-4 data-panel-open:pb-3">
              <span className="min-w-0 flex-1">{item.question}</span>
              <ChevronDown
                aria-hidden="true"
                className="size-5 shrink-0 text-muted-foreground transition-transform duration-200 group-data-panel-open:rotate-180"
              />
            </Accordion.Trigger>
          </Accordion.Header>
          <Accordion.Panel className="h-(--accordion-panel-height) overflow-hidden text-body text-muted-foreground transition-[height] duration-200 ease-out data-ending-style:h-0 data-starting-style:h-0">
            <div className="pb-6">{item.answer}</div>
          </Accordion.Panel>
        </Accordion.Item>
      ))}
    </Accordion.Root>
  );
}
