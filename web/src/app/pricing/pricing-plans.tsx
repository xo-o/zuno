"use client";

import { Toggle } from "@base-ui/react/toggle";
import { ToggleGroup } from "@base-ui/react/toggle-group";
import { Sparkles } from "lucide-react";
import { type ReactNode, useState } from "react";
import { CheckItem } from "@/components/site/legal";
import { Container } from "@/components/site/site-page";
import { buttonVariants } from "@/components/ui/button";
import { ROUTES } from "@/lib/site";
import { cn } from "@/lib/utils";

type Billing = "monthly" | "yearly";

const FREE_FEATURES = [
  "10 minutes of voice practice a day",
  "Unlimited typed Free Talk",
  "3 Topics and 3 Roleplays",
  "Gentle live corrections & translations",
  "Scheduled practice reminders",
];

const PRO_FEATURES = [
  "Unlimited voice conversations",
  "Unlimited Topics and Roleplays",
  "Learn from photos, PDFs and links",
  "All 30+ languages and accents",
  "Detailed feedback and level checks",
  "Priority support",
];

const PRO_PRICING: Record<
  Billing,
  { price: string; note: string; finePrint: string }
> = {
  yearly: {
    price: "$4.99",
    note: "Billed $59.88 yearly · or $9.99 monthly",
    finePrint:
      "Free for 7 days, then $59.88 per year. Renews automatically. Cancel anytime in Google Play at least 24 hours before renewal.",
  },
  monthly: {
    price: "$9.99",
    note: "Billed $9.99 monthly · or $4.99/month yearly",
    finePrint:
      "Free for 7 days, then $9.99 per month. Renews automatically. Cancel anytime in Google Play at least 24 hours before renewal.",
  },
};

export function PricingPlans() {
  const [billing, setBilling] = useState<Billing>("yearly");
  const pro = PRO_PRICING[billing];

  return (
    <>
      <div className="flex justify-center px-4 pt-5 pb-8 md:pb-12">
        <BillingToggle value={billing} onChange={setBilling} />
      </div>
      <Container className="flex flex-col items-center justify-center gap-6 pt-6 pb-14 md:pb-24 lg:flex-row lg:items-start">
        <PlanCard
          name="Free"
          price="$0"
          period="forever"
          note="No credit card needed."
          cta="Get the app"
          ctaVariant="outline"
          featuresTitle="Includes"
          features={FREE_FEATURES}
        />
        <PlanCard
          featured
          name="Pro"
          badge={
            <span className="flex h-8 items-center gap-1.5 rounded-full border border-input bg-foreground/10 px-3 text-footnote font-medium text-foreground">
              <Sparkles aria-hidden="true" className="size-3.5" />
              Most popular
            </span>
          }
          price={pro.price}
          period="/ month"
          note={pro.note}
          cta="Start 7-day free trial"
          ctaVariant="default"
          featuresTitle="Everything in Free, plus"
          features={PRO_FEATURES}
          finePrint={pro.finePrint}
        />
      </Container>
    </>
  );
}

function BillingToggle({
  value,
  onChange,
}: {
  value: Billing;
  onChange: (value: Billing) => void;
}) {
  const item =
    "group flex cursor-pointer items-center gap-2 rounded-full px-[18px] py-2 text-sm leading-[18px] font-medium whitespace-nowrap text-muted-foreground outline-none transition-colors hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 data-pressed:bg-primary data-pressed:text-primary-foreground";

  return (
    <ToggleGroup
      aria-label="Billing period"
      value={[value]}
      onValueChange={(next) => {
        const selected = next[0];
        if (selected === "monthly" || selected === "yearly") onChange(selected);
      }}
      className="flex items-center gap-1 rounded-full border border-input bg-card p-1"
    >
      <Toggle value="monthly" className={item}>
        Monthly
      </Toggle>
      <Toggle value="yearly" className={item}>
        Yearly
        <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] leading-[14px] font-medium tracking-[0.2px] text-foreground group-data-pressed:bg-primary-foreground/10 group-data-pressed:text-primary-foreground">
          Save 50%
        </span>
      </Toggle>
    </ToggleGroup>
  );
}

function PlanCard({
  name,
  badge,
  price,
  period,
  note,
  cta,
  ctaVariant,
  featuresTitle,
  features,
  finePrint,
  featured = false,
}: {
  name: string;
  badge?: ReactNode;
  price: string;
  period: string;
  note: string;
  cta: string;
  ctaVariant: "default" | "outline";
  featuresTitle: string;
  features: string[];
  finePrint?: string;
  featured?: boolean;
}) {
  return (
    <section
      aria-label={`${name} plan`}
      className={cn(
        "flex w-full max-w-[440px] flex-col gap-7 rounded-3xl border px-5 py-7 md:rounded-[40px] md:p-10 lg:flex-1",
        featured ? "border-border-strong bg-muted" : "border-input bg-card",
      )}
    >
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-lg/6 font-semibold tracking-[-0.3px] text-foreground md:text-title md:tracking-[-0.4px]">
          {name}
        </h2>
        {badge}
      </div>
      <div
        className="flex flex-col gap-1.5"
        aria-live={featured ? "polite" : undefined}
      >
        <p className="flex items-baseline gap-2">
          <span className="text-[26px]/8 font-semibold tracking-[-0.8px] text-foreground md:text-heading md:tracking-[-1.5px]">
            {price}
          </span>
          <span className="text-body text-faint">{period}</span>
        </p>
        <p className="text-sm leading-5 text-muted-foreground">{note}</p>
      </div>
      <a
        href={ROUTES.getTheApp}
        className={cn(
          buttonVariants({ variant: ctaVariant, size: "pill-md" }),
          "w-full md:h-13 md:px-6",
        )}
      >
        {cta}
      </a>
      <div className="h-px w-full bg-border" />
      <div className="flex flex-col gap-3.5">
        <h3 className="text-eyebrow font-medium text-faint uppercase">
          {featuresTitle}
        </h3>
        <ul className="flex flex-col gap-3.5">
          {features.map((feature) => (
            <CheckItem
              key={feature}
              className="md:items-center [&>span:last-child]:pt-0"
            >
              {feature}
            </CheckItem>
          ))}
        </ul>
      </div>
      {finePrint && <p className="text-footnote text-faint">{finePrint}</p>}
    </section>
  );
}
