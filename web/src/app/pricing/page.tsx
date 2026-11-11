import type { Metadata } from "next";
import { Eyebrow } from "@/components/landing/brand";
import { type FaqEntry, FaqList } from "@/components/site/faq";
import { TextLink } from "@/components/site/legal";
import { Container, SitePage } from "@/components/site/site-page";
import { SUPPORT_EMAIL } from "@/lib/site";
import { ComparePlans } from "./compare-plans";
import { PricingPlans } from "./pricing-plans";

export const metadata: Metadata = {
  title: "Pricing — Zuno",
  description:
    "Start free with daily voice practice, Topics and Roleplays. Go Pro for unlimited voice conversations and every feature — try it free for 7 days.",
  alternates: { canonical: "/pricing" },
};

const FAQ: FaqEntry[] = [
  {
    question: "How does the free trial work?",
    answer:
      "Pro starts with 7 free days. You won’t be charged if you cancel in Google Play at least 24 hours before the trial ends.",
  },
  {
    question: "How do I cancel?",
    answer:
      "Open Google Play → Profile → Payments & subscriptions → Zuno → Cancel. You keep Pro until the end of the period you paid for.",
  },
  {
    question: "Can I switch between monthly and yearly?",
    answer:
      "Yes — change plans anytime in Google Play. The new price applies from your next billing date.",
  },
  {
    question: "What happens to my data if I go back to Free?",
    answer:
      "Nothing is deleted. Your chats, topics and vocabulary stay; only Pro limits apply again.",
  },
  {
    question: "Do you offer refunds?",
    answer: (
      <>
        Purchases are processed by Google Play and refunds follow Google Play’s
        refund policy.{" "}
        <TextLink href={`mailto:${SUPPORT_EMAIL}`}>Contact us</TextLink> if
        something went wrong.
      </>
    ),
  },
];

export default function PricingPage() {
  return (
    <SitePage>
      <Container className="flex flex-col items-center gap-5 pt-12 text-center lg:pt-20">
        <Eyebrow>Pricing</Eyebrow>
        <h1 className="max-w-[760px] text-[26px]/8 font-semibold tracking-[-0.8px] text-balance text-foreground md:text-heading md:tracking-[-1.5px]">
          Start free. Upgrade when you’re ready.
        </h1>
        <p className="max-w-[620px] text-body text-pretty text-muted-foreground md:text-lead">
          Practise every day on the Free plan. Go Pro for unlimited voice
          conversations and every feature.
        </p>
      </Container>

      <PricingPlans />

      <ComparePlans />

      <Container className="pb-16 md:pb-24 lg:pb-[120px]">
        <section
          aria-labelledby="pricing-questions"
          className="mx-auto flex w-full max-w-[760px] flex-col gap-6"
        >
          <h2
            id="pricing-questions"
            className="text-2xl leading-[30px] font-semibold tracking-[-0.8px] text-foreground"
          >
            Pricing questions
          </h2>
          <FaqList items={FAQ} />
        </section>
      </Container>
    </SitePage>
  );
}
