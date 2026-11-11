import { CircleAlert } from "lucide-react";
import type { Metadata } from "next";
import {
  AsideCard,
  asideLinkClass,
  Bullet,
  BulletList,
  Callout,
  LegalLayout,
  LegalSection,
  P,
  PageHeader,
  Strong,
  SummaryCard,
  TextLink,
  type TocItem,
} from "@/components/site/legal";
import { SitePage } from "@/components/site/site-page";
import { LEGAL_UPDATED, ROUTES, SITE_DOMAIN, SUPPORT_EMAIL } from "@/lib/site";

export const metadata: Metadata = {
  title: "Terms of Service — Zuno",
  description:
    "The agreement between you and Zuno when you use our app and website, written as plainly as we can.",
  alternates: { canonical: "/terms" },
};

const TOC: TocItem[] = [
  { id: "accepting-these-terms", label: "Accepting these terms" },
  { id: "your-account", label: "Your account" },
  { id: "using-zuno", label: "Using Zuno" },
  { id: "ai-generated-content", label: "AI-generated content" },
  { id: "your-content", label: "Your content" },
  { id: "pro-and-billing", label: "Pro & billing" },
  { id: "ending-your-account", label: "Ending your account" },
  { id: "changes", label: "Changes" },
  { id: "disclaimers-and-liability", label: "Disclaimers & liability" },
  { id: "governing-law", label: "Governing law" },
  { id: "contact", label: "Contact" },
];

const SUMMARY = [
  "Zuno is an AI tutor. Its replies can be wrong — double-check anything important.",
  "Your chats and files stay yours. You give us permission to process them to run Zuno.",
  "Be kind: no illegal, harmful or abusive use, and don’t try to break the service.",
  "Pro is billed through Google Play and you can cancel anytime in your Play account.",
];

function SupportEmail() {
  return <TextLink href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</TextLink>;
}

function PrivacyLink() {
  return <TextLink href={ROUTES.privacy}>Privacy Policy</TextLink>;
}

export default function TermsPage() {
  return (
    <SitePage>
      <PageHeader
        eyebrow="Legal"
        title="Terms of Service"
        meta={[
          `Last updated ${LEGAL_UPDATED}`,
          `Effective ${LEGAL_UPDATED}`,
          `${SITE_DOMAIN}/terms`,
        ]}
        compact
        lead="These terms are the agreement between you and Zuno when you use our app and website. Please read them — we’ve kept them as plain as we can."
      />
      <LegalLayout
        toc={TOC}
        aside={
          <AsideCard
            title="Questions about these terms?"
            body="We’re happy to explain anything here."
          >
            <a href={`mailto:${SUPPORT_EMAIL}`} className={asideLinkClass}>
              {SUPPORT_EMAIL}
            </a>
          </AsideCard>
        }
      >
        <SummaryCard title="The short version" items={SUMMARY} />

        <LegalSection
          id="accepting-these-terms"
          number={1}
          title="Accepting these terms"
        >
          <P>
            By creating an account or using Zuno you agree to these Terms and to
            our <PrivacyLink />. If you don’t agree, please don’t use Zuno.
          </P>
          <P>
            You must be at least 13 years old (or the minimum age in your
            country). If you’re under 18, you need permission from a parent or
            guardian.
          </P>
        </LegalSection>

        <LegalSection id="your-account" number={2} title="Your account">
          <BulletList>
            <Bullet>
              Sign in with a magic link sent to your email, or with Google.
            </Bullet>
            <Bullet>
              Keep your email account secure — anyone with access to it can sign
              in to Zuno.
            </Bullet>
            <Bullet>
              You’re responsible for activity on your account. Tell us right
              away at <SupportEmail /> if you think it was misused.
            </Bullet>
          </BulletList>
        </LegalSection>

        <LegalSection id="using-zuno" number={3} title="Using Zuno">
          <P>
            Zuno gives you a personal, non-transferable licence to use the app
            and website for your own language learning. You agree not to:
          </P>
          <BulletList>
            <Bullet>
              Use Zuno for anything illegal, or to harass, threaten, or harm
              others.
            </Bullet>
            <Bullet>
              Generate or share content that is sexual involving minors,
              hateful, violent or that infringes others’ rights.
            </Bullet>
            <Bullet>
              Try to reverse engineer, scrape, overload or bypass security or
              usage limits.
            </Bullet>
            <Bullet>Resell Zuno or use it to build a competing product.</Bullet>
            <Bullet>
              Upload files or links you don’t have the right to share.
            </Bullet>
          </BulletList>
        </LegalSection>

        <LegalSection
          id="ai-generated-content"
          number={4}
          title="AI-generated content"
        >
          <P>
            Zuno uses AI models to create replies, corrections, translations,
            lessons and voice. AI can make mistakes, sound confident when it’s
            wrong, or miss cultural nuance.
          </P>
          <Callout icon={<CircleAlert aria-hidden="true" />}>
            Zuno is a learning tool. It is not a source of professional,
            medical, legal, financial or safety advice, and it isn’t a certified
            translation service.
          </Callout>
          <P>
            If a reply is wrong or inappropriate, use <Strong>Report</Strong> in
            the chat menu so we can review it.
          </P>
        </LegalSection>

        <LegalSection id="your-content" number={5} title="Your content">
          <P>
            You own the messages, recordings, photos and documents you add
            (“your content”). You give Zuno a limited licence to host, process,
            transcribe and transform your content only to provide and improve
            the service for you, as described in our <PrivacyLink />.
          </P>
          <P>
            If you make a chat public, anyone with the link can view it until
            you set it back to “Only me”.
          </P>
        </LegalSection>

        <LegalSection
          id="pro-and-billing"
          number={6}
          title="Zuno Pro & billing"
        >
          <BulletList>
            <Bullet lead="Subscriptions">
              Zuno Pro is a monthly or yearly subscription purchased through
              Google Play. Prices are shown before you buy.
            </Bullet>
            <Bullet lead="Free trials">
              if offered, you’re charged when the trial ends unless you cancel
              at least 24 hours before.
            </Bullet>
            <Bullet lead="Auto-renewal">
              your plan renews automatically until you cancel in Google Play →
              Payments &amp; subscriptions.
            </Bullet>
            <Bullet lead="Refunds">
              are handled under Google Play’s refund policy and applicable law.
            </Bullet>
            <Bullet lead="Changes">
              we’ll tell you in advance about price changes, and you can cancel
              before they apply.
            </Bullet>
          </BulletList>
        </LegalSection>

        <LegalSection
          id="ending-your-account"
          number={7}
          title="Ending your account"
        >
          <P>
            You can stop using Zuno and delete your account at any time in{" "}
            <Strong>Settings → Account → Delete account</Strong>, or at{" "}
            <TextLink href={ROUTES.deleteAccount}>
              {`${SITE_DOMAIN}${ROUTES.deleteAccount}`}
            </TextLink>
            . Deleting your account does not cancel a Google Play subscription —
            cancel it in Google Play first.
          </P>
          <P>
            We may suspend or close accounts that break these terms or put
            others at risk. Where reasonable, we’ll warn you first and explain
            why.
          </P>
        </LegalSection>

        <LegalSection
          id="changes"
          number={8}
          title="Changes to Zuno and these terms"
        >
          <P>
            Zuno is evolving. We may add, change or remove features. If we make
            material changes to these terms, we’ll notify you in the app or by
            email before they take effect.
          </P>
        </LegalSection>

        <LegalSection
          id="disclaimers-and-liability"
          number={9}
          title="Disclaimers & liability"
        >
          <P>
            Zuno is provided “as is”. To the extent the law allows, we don’t
            promise the service will be uninterrupted or error-free, and we
            aren’t liable for indirect or consequential losses. Nothing in these
            terms limits rights you have under consumer law that can’t be
            waived.
          </P>
        </LegalSection>

        <LegalSection id="governing-law" number={10} title="Governing law">
          <P>
            These terms are governed by the laws of the country where Zuno AI is
            established, unless the consumer law of your country of residence
            gives you additional protection.
          </P>
        </LegalSection>

        <LegalSection id="contact" number={11} title="Contact">
          <P>
            Questions about these Terms: <SupportEmail /> or{" "}
            <TextLink href={ROUTES.contact}>
              {`${SITE_DOMAIN}${ROUTES.contact}`}
            </TextLink>
            .
          </P>
        </LegalSection>
      </LegalLayout>
    </SitePage>
  );
}
