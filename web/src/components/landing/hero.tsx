import { ArrowRight, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { HomeScreen } from "./app-screens";
import { Phone, ScaledScreen, ScreenImage } from "./phone";

const cta = "max-md:h-11 max-md:w-full max-md:px-5";

export function Hero() {
  return (
    <section className="px-4 pt-2 md:pt-0 lg:px-8">
      <div className="mx-auto flex h-[806px] max-w-[1376px] flex-col items-center gap-7 overflow-hidden rounded-3xl border border-input bg-card px-5 pt-7 md:h-[1000px] md:gap-16 md:rounded-[40px] md:px-6 md:pt-20 lg:h-[1120px]">
        <div className="flex w-full max-w-[760px] shrink-0 flex-col items-center gap-4 text-center md:gap-6">
          <Badge
            variant="outline"
            className="h-8 gap-1.5 border-input bg-card px-3 leading-4"
          >
            <Sparkles data-icon="inline-start" />
            New · Talk about photos &amp; documents
          </Badge>
          <h1 className="text-[32px]/[38px] font-semibold tracking-[-1.1px] md:text-[56px]/[62px] md:tracking-[-1.5px] lg:text-hero lg:tracking-[-2.5px]">
            Your quiet space to <br className="hidden lg:block" />
            master any language.
          </h1>
          <p className="max-w-[640px] text-body text-pretty text-muted-foreground md:text-lead">
            Zuno is a calm AI language partner. Talk out loud or type, get
            gentle corrections in the moment, and build real confidence — one
            conversation at a time.
          </p>
          <div className="flex w-full flex-col gap-2 md:w-auto md:flex-row md:gap-3">
            <a
              href="#get-the-app"
              className={cn(buttonVariants({ size: "pill-lg" }), cta)}
            >
              Start talking — it’s free
              <ArrowRight data-icon="inline-end" />
            </a>
            <a
              href="#how-it-works"
              className={cn(
                buttonVariants({
                  variant: "outline",
                  size: "pill-lg",
                }),
                cta,
              )}
            >
              See how it works
            </a>
          </div>
          <p className="text-footnote text-faint">
            Available on iOS and Android · No credit card needed
          </p>
        </div>

        <div className="flex shrink-0 items-start justify-center gap-10">
          <Phone className="mt-24 hidden [--s:0.74] lg:block">
            <ScreenImage
              src="/screens/chat.png"
              alt="A Free Talk chat with a gentle correction"
            />
          </Phone>
          <Phone className="[--s:0.605] md:[--s:0.74] lg:[--s:0.86]">
            <ScaledScreen>
              <HomeScreen />
            </ScaledScreen>
          </Phone>
          <Phone className="mt-24 hidden [--s:0.74] lg:block">
            <ScreenImage
              src="/screens/topic.png"
              alt="A topic with lesson progress"
            />
          </Phone>
        </div>
      </div>
    </section>
  );
}
