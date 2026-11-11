import {
  AlarmClock,
  AudioLines,
  BookOpen,
  Camera,
  Check,
  FileText,
  Languages,
  Lock,
  type LucideIcon,
  Mic,
  TrendingUp,
  Users,
  Volume2,
} from "lucide-react";
import type { ReactNode } from "react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { SpeakingScreen } from "./app-screens";
import { SectionHeader } from "./brand";
import { Phone, ScaledScreen, ScreenImage } from "./phone";
import { VoiceOrb } from "./voice-orb";

const cta = "max-md:h-11 max-md:w-full max-md:px-5";

const container = "mx-auto w-full max-w-[1440px] px-4 md:px-10 xl:px-[120px]";

function IconChip({
  icon: Icon,
  className,
}: {
  icon: LucideIcon;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full border border-border bg-muted",
        className,
      )}
    >
      <Icon className="size-[45%]" strokeWidth={1.75} />
    </span>
  );
}

/* Stats */

const STATS = [
  { value: "30+", label: "languages to practice" },
  { value: "24/7", label: "a patient partner, always on" },
  { value: "< 1 s", label: "live hints while you talk" },
  { value: "5 min", label: "is all a session takes" },
];

export function Stats() {
  return (
    <section className={cn(container, "pt-8 pb-4 lg:pt-24 lg:pb-6")}>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-6 border-y py-6 md:grid-cols-4 lg:py-12">
        {STATS.map((stat) => (
          <div
            key={stat.value}
            className="flex flex-col-reverse items-center justify-end gap-1 text-center lg:min-h-20"
          >
            <dt className="text-body text-faint">{stat.label}</dt>
            <dd className="text-[26px]/8 font-semibold tracking-[-0.8px] md:text-heading md:tracking-[-1.5px]">
              {stat.value}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/* Features */

const FEATURES: {
  icon: LucideIcon;
  title: string;
  body: string;
  screen: ReactNode;
}[] = [
  {
    icon: AudioLines,
    title: "Free Talk",
    body: "Speak or type about anything. Zuno answers naturally, translates when you need it and nudges you toward native phrasing.",
    screen: (
      <ScaledScreen>
        <SpeakingScreen />
      </ScaledScreen>
    ),
  },
  {
    icon: BookOpen,
    title: "Topics",
    body: "Guided lesson paths for real situations — from asking for directions to nailing a job interview. Track progress as you go.",
    screen: (
      <ScreenImage
        src="/screens/topics.png"
        alt="Your topics with lesson progress"
      />
    ),
  },
  {
    icon: Users,
    title: "Roleplays",
    body: "Step into a scene: you’re the customer, Zuno’s the barista. Rehearse the moments that matter before they happen.",
    screen: (
      <ScreenImage
        src="/screens/roleplays.png"
        alt="Roleplay scenarios with your role and Zuno’s"
      />
    ),
  },
];

export function Features() {
  return (
    <section
      id="features"
      className={cn(
        container,
        "flex scroll-mt-8 flex-col gap-8 py-14 lg:gap-16 lg:py-[120px]",
      )}
    >
      <SectionHeader
        eyebrow="Three ways to practice"
        title="Conversation first. Grammar follows."
        lead="Every mode is built around speaking — so you learn the language you’ll actually use."
      />
      <div className="grid gap-4 md:grid-cols-3 lg:gap-6">
        {FEATURES.map((feature) => (
          <Card
            key={feature.title}
            className="gap-0 rounded-3xl border py-0 ring-0 lg:rounded-[40px]"
          >
            <div className="flex h-[300px] justify-center overflow-hidden bg-background pt-6 lg:h-[360px] lg:pt-10">
              <Phone className="[--s:0.559] md:[--s:0.48] lg:[--s:0.66]">
                {feature.screen}
              </Phone>
            </div>
            <CardHeader className="gap-4 px-5 pt-5 pb-6 lg:px-8 lg:pt-6 lg:pb-8">
              <IconChip icon={feature.icon} className="size-12" />
              <div className="flex flex-col gap-2">
                <CardTitle className="text-[18px]/6 font-semibold tracking-[-0.3px] lg:text-title lg:tracking-[-0.4px]">
                  {feature.title}
                </CardTitle>
                <CardDescription className="text-body">
                  {feature.body}
                </CardDescription>
              </div>
            </CardHeader>
          </Card>
        ))}
      </div>
    </section>
  );
}

/* Split sections */

function SplitSection({
  id,
  eyebrow,
  title,
  lead,
  checks,
  screen,
  reverse = false,
}: {
  id?: string;
  eyebrow: string;
  title: string;
  lead: string;
  checks: string[];
  screen: ReactNode;
  reverse?: boolean;
}) {
  return (
    <section id={id} className={cn(container, "py-14 lg:py-24")}>
      <div className="grid items-center gap-8 lg:grid-cols-2 lg:gap-24">
        <div
          className={cn(
            "flex flex-col gap-6 lg:gap-10",
            reverse && "lg:order-last",
          )}
        >
          <SectionHeader
            eyebrow={eyebrow}
            title={title}
            lead={lead}
            align="start"
          />
          <ul className="flex flex-col gap-4">
            {checks.map((check) => (
              <li
                key={check}
                className="flex items-start gap-3 text-body-lg text-muted-foreground lg:items-center"
              >
                <IconChip icon={Check} className="size-7" />
                {check}
              </li>
            ))}
          </ul>
        </div>
        <div className="flex h-[360px] justify-center overflow-hidden rounded-3xl border bg-card pt-6 md:h-[520px] md:pt-16 lg:h-[640px] lg:rounded-[40px]">
          <Phone className="[--s:0.596] md:[--s:0.7] lg:[--s:0.8]">
            {screen}
          </Phone>
        </div>
      </div>
    </section>
  );
}

export function LiveFeedback() {
  return (
    <SplitSection
      eyebrow="Live feedback"
      title="Gentle corrections, right when you need them."
      lead="Zuno listens like a patient friend — it keeps the conversation flowing and quietly shows you a more natural way to say it."
      checks={[
        "Native-sounding alternatives for every reply",
        "Translations and grammar explanations on tap",
        "Listen to any message at a natural pace",
      ]}
      screen={
        <ScreenImage
          src="/screens/live-hint.png"
          alt="A live accent hint while typing"
        />
      }
    />
  );
}

export function ScheduledPractice() {
  return (
    <SplitSection
      reverse
      eyebrow="Scheduled practice"
      title="Practice that fits into your day."
      lead="Plan short sessions around your routine. Zuno reminds you when it’s time — and picks up right where you left off."
      checks={[
        "Daily, weekly or one-off sessions",
        "Calm reminders — never nagging",
        "Resume any topic or roleplay in one tap",
      ]}
      screen={
        <ScreenImage
          src="/screens/scheduled.png"
          alt="Upcoming scheduled practice sessions"
        />
      }
    />
  );
}

/* Highlights */

const HIGHLIGHTS: { icon: LucideIcon; title: string; body: string }[] = [
  {
    icon: Camera,
    title: "Snap a photo",
    body: "Menus, signs, homework — send it and talk about it.",
  },
  {
    icon: FileText,
    title: "Learn from your material",
    body: "Upload notes or a PDF and Zuno turns it into a topic.",
  },
  {
    icon: AlarmClock,
    title: "Reminders that respect you",
    body: "A gentle nudge at the time you choose. That’s it.",
  },
  {
    icon: Languages,
    title: "30+ languages",
    body: "Switch anytime — progress stays with each language.",
  },
  {
    icon: Lock,
    title: "Private by design",
    body: "Your conversations are yours. Share only if you choose.",
  },
  {
    icon: Mic,
    title: "Voice or text",
    body: "Talk out loud, or type quietly on the train.",
  },
  {
    icon: Volume2,
    title: "Hear it right",
    body: "Natural audio for every reply, at the pace you need.",
  },
  {
    icon: TrendingUp,
    title: "See your progress",
    body: "Lessons and topics completed, all at a glance.",
  },
];

export function Highlights() {
  return (
    <section
      id="highlights"
      className={cn(
        container,
        "flex scroll-mt-8 flex-col gap-8 py-14 lg:gap-16 lg:py-[120px]",
      )}
    >
      <SectionHeader
        eyebrow="And a little more"
        title="Small touches that keep you talking."
      />
      <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4 lg:gap-6">
        {HIGHLIGHTS.map((item) => (
          <Card
            key={item.title}
            className="flex-row items-start gap-4 rounded-[20px] border p-4 ring-0 lg:flex-col lg:items-stretch lg:gap-5 lg:rounded-3xl lg:p-6"
          >
            <IconChip icon={item.icon} className="size-11" />
            <CardHeader className="min-w-0 flex-1 gap-2 px-0 lg:flex-none">
              <CardTitle className="text-label font-semibold">
                {item.title}
              </CardTitle>
              <CardDescription className="text-body">
                {item.body}
              </CardDescription>
            </CardHeader>
          </Card>
        ))}
      </div>
    </section>
  );
}

/* How it works */

const STEPS = [
  {
    title: "Pick your language",
    body: "Choose from 30+ languages and tell Zuno your level and goals.",
  },
  {
    title: "Talk or type",
    body: "Hold the orb to speak, or just type. Zuno replies in seconds — out loud if you like.",
  },
  {
    title: "Get better every day",
    body: "Live hints, translations and gentle corrections turn every chat into progress.",
  },
];

export function HowItWorks() {
  return (
    <section
      id="how-it-works"
      className={cn(
        container,
        "flex scroll-mt-8 flex-col gap-8 py-14 lg:gap-16 lg:py-[120px]",
      )}
    >
      <SectionHeader
        eyebrow="How it works"
        title="From hello to fluent, in three steps."
      />
      <Card className="grid gap-6 rounded-3xl border px-5 py-6 ring-0 md:grid-cols-3 md:gap-8 md:p-8 lg:gap-16 lg:rounded-[40px] lg:p-12">
        {STEPS.map((step, i) => (
          <CardHeader key={step.title} className="gap-5 px-0">
            <span className="flex size-11 items-center justify-center rounded-full border border-border-strong text-label font-semibold">
              {i + 1}
            </span>
            <div className="flex flex-col gap-2">
              <CardTitle className="text-[18px]/6 font-semibold tracking-[-0.3px] lg:text-title lg:tracking-[-0.4px]">
                {step.title}
              </CardTitle>
              <CardDescription className="text-body">
                {step.body}
              </CardDescription>
            </div>
          </CardHeader>
        ))}
      </Card>
    </section>
  );
}

/* Testimonial */

export function Testimonial() {
  return (
    <section
      className={cn(
        container,
        "flex flex-col items-center gap-7 py-14 lg:gap-10 lg:py-[120px]",
      )}
    >
      <blockquote className="max-w-[920px] text-center text-[20px]/7 font-medium tracking-[-0.5px] md:text-quote md:tracking-[-1px]">
        “I studied Spanish for years but froze whenever I had to speak. After a
        month with Zuno, I ordered dinner in Madrid without rehearsing it
        first.”
      </blockquote>
      <figure className="flex items-center gap-3">
        <Avatar className="size-9">
          <AvatarFallback className="bg-primary text-sm font-medium text-primary-foreground">
            M
          </AvatarFallback>
        </Avatar>
        <figcaption className="flex flex-col gap-0.5">
          <span className="text-label font-semibold">María L.</span>
          <span className="text-footnote text-faint">
            Learning Spanish · B1
          </span>
        </figcaption>
      </figure>
    </section>
  );
}

/* Final CTA */

export function FinalCta() {
  return (
    <section id="get-the-app" className="scroll-mt-8 px-4 lg:px-8">
      <div className="mx-auto flex max-w-[1376px] flex-col items-center justify-center gap-4 overflow-hidden rounded-3xl border border-input bg-card px-5 py-10 text-center md:min-h-[600px] md:gap-6 md:rounded-[40px] md:px-6 md:py-16">
        <div className="flex h-[78px] items-center justify-center md:h-28">
          <VoiceOrb
            size={175}
            dots={420}
            className="scale-[0.714] md:scale-100"
          />
        </div>
        <h2 className="text-[32px]/[38px] font-semibold tracking-[-1.1px] md:text-[56px]/[62px] md:tracking-[-1.5px] lg:text-hero lg:tracking-[-2.5px]">
          Start your first
          <br /> conversation today.
        </h2>
        <p className="text-body text-muted-foreground md:text-lead">
          Free to try. Five minutes is all it takes.
        </p>
        <div className="flex w-full flex-col gap-2 md:w-auto md:flex-row md:gap-3">
          <a
            href="#get-the-app"
            className={cn(buttonVariants({ size: "pill-lg" }), cta)}
          >
            Download for iOS
          </a>
          <a
            href="#get-the-app"
            className={cn(
              buttonVariants({ variant: "outline", size: "pill-lg" }),
              cta,
            )}
          >
            Get it on Android
          </a>
        </div>
      </div>
    </section>
  );
}
