import { Hero } from "@/components/landing/hero";
import {
  Features,
  FinalCta,
  Highlights,
  HowItWorks,
  LiveFeedback,
  ScheduledPractice,
  Stats,
  Testimonial,
} from "@/components/landing/sections";
import { SiteFooter } from "@/components/landing/site-footer";
import { SiteHeader } from "@/components/landing/site-header";

export default function Home() {
  return (
    <div id="top" className="flex flex-1 flex-col">
      <SiteHeader />
      <main className="flex flex-col">
        <Hero />
        <Stats />
        <Features />
        <LiveFeedback />
        <ScheduledPractice />
        <Highlights />
        <HowItWorks />
        <Testimonial />
        <FinalCta />
      </main>
      <SiteFooter />
    </div>
  );
}
