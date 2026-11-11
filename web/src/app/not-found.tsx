import type { Metadata } from "next";
import Link from "next/link";
import { VoiceOrb } from "@/components/landing/voice-orb";
import { Container, SitePage } from "@/components/site/site-page";
import { buttonVariants } from "@/components/ui/button";
import { ROUTES } from "@/lib/site";
import { cn } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Page not found — Zuno",
};

export default function NotFound() {
  return (
    <SitePage>
      <Container className="flex flex-col items-center gap-6 pt-20 pb-24 text-center lg:pt-[140px] lg:pb-[160px]">
        {/* Visible sphere is 64% of the canvas: 175 × 0.64 ≈ the 112px Figma orb. */}
        <VoiceOrb size={175} dots={360} className="-my-8" />
        <p className="text-[32px] leading-[38px] font-semibold tracking-[-1.1px] text-faint md:text-hero md:leading-[74px] md:tracking-[-2.5px]">
          404
        </p>
        <h1 className="max-w-[760px] text-[26px] leading-8 font-semibold tracking-[-0.8px] text-balance md:text-heading md:leading-[52px] md:tracking-[-1.5px]">
          This page took a wrong turn
        </h1>
        <p className="max-w-[560px] text-body text-pretty text-muted-foreground md:text-lead">
          The link may be broken or the page may have moved. Let’s get you back
          to practising.
        </p>
        <div className="flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
          <Link
            href={ROUTES.home}
            className={cn(
              buttonVariants({ size: "pill-md" }),
              "md:h-13 md:px-6",
            )}
          >
            Back to home
          </Link>
          <Link
            href={ROUTES.contact}
            className={cn(
              buttonVariants({ variant: "outline", size: "pill-md" }),
              "md:h-13 md:px-6",
            )}
          >
            Contact support
          </Link>
        </div>
      </Container>
    </SitePage>
  );
}
