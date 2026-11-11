import { Lock } from "lucide-react";
import type { Metadata } from "next";
import {
  AsideCard,
  asideLinkClass,
  Bullet,
  BulletList,
  Callout,
  LegalLayout,
  LegalSection,
  LegalTable,
  type LegalTableColumn,
  P,
  PageHeader,
  Strong,
  SubHeading,
  SummaryCard,
  TextLink,
  type TocItem,
} from "@/components/site/legal";
import { SitePage } from "@/components/site/site-page";
import {
  LEGAL_UPDATED,
  PRIVACY_EMAIL,
  ROUTES,
  SITE_DOMAIN,
  SUPPORT_EMAIL,
} from "@/lib/site";

export const metadata: Metadata = {
  title: "Privacy Policy — Zuno",
  description:
    "What Zuno collects when you practise a language, why we need it, who helps us run the service, and how to delete your account at any time.",
  alternates: { canonical: "/privacy" },
};

const TOC: TocItem[] = [
  { id: "who-we-are", label: "Who we are" },
  { id: "information-we-collect", label: "Information we collect" },
  { id: "microphone-voice", label: "Microphone & voice" },
  { id: "how-we-use-information", label: "How we use information" },
  { id: "ai-service-providers", label: "AI & service providers" },
  { id: "sharing", label: "Sharing" },
  { id: "data-retention", label: "Data retention" },
  { id: "your-choices-rights", label: "Your choices & rights" },
  { id: "deleting-your-account", label: "Deleting your account" },
  { id: "children", label: "Children" },
  { id: "security", label: "Security" },
  { id: "international-transfers", label: "International transfers" },
  { id: "changes-to-this-policy", label: "Changes" },
  { id: "contact-us", label: "Contact us" },
];

const PROVIDERS: { name: string; purpose: string; data: string }[] = [
  {
    name: "Google (Gemini)",
    purpose:
      "Generates tutor replies, corrections, translations and topic lessons",
    data: "Messages, transcripts, file and link contents",
  },
  {
    name: "Deepgram",
    purpose: "Speech-to-text during voice sessions",
    data: "Live audio stream",
  },
  {
    name: "Cartesia",
    purpose: "Text-to-speech for Zuno’s voice",
    data: "Text of Zuno’s replies",
  },
  {
    name: "LiveKit",
    purpose: "Real-time audio transport",
    data: "Live audio stream, session metadata",
  },
  {
    name: "Cloudflare R2",
    purpose: "File storage",
    data: "Photos, documents, profile photo",
  },
  {
    name: "Firebase Cloud Messaging",
    purpose: "Push notifications",
    data: "Push token, notification content",
  },
  {
    name: "Resend",
    purpose: "Sign-in links and service emails",
    data: "Email address, email content",
  },
  {
    name: "Cloud hosting & database",
    purpose: "Runs our servers and stores account data",
    data: "All account and learning data",
  },
];

function Mail({ email }: { email: string }) {
  return <TextLink href={`mailto:${email}`}>{email}</TextLink>;
}

const PROVIDER_COLUMNS: LegalTableColumn[] = [
  { label: "Provider", className: "w-[216px]" },
  { label: "Purpose", className: "w-[276px]" },
  { label: "Data involved" },
];

export default function PrivacyPage() {
  return (
    <SitePage>
      <PageHeader
        eyebrow="Legal"
        title="Privacy Policy"
        meta={[
          `Last updated ${LEGAL_UPDATED}`,
          `Effective ${LEGAL_UPDATED}`,
          `${SITE_DOMAIN}${ROUTES.privacy}`,
        ]}
        compact
        lead="This policy explains what Zuno collects when you practise a language with us, why we need it, who helps us run the service, and the choices you have — including how to delete your account at any time."
      />
      <LegalLayout
        toc={TOC}
        aside={
          <AsideCard
            title="Privacy questions?"
            body="Write to us and a real person will reply within 30 days."
          >
            <a href={`mailto:${PRIVACY_EMAIL}`} className={asideLinkClass}>
              {PRIVACY_EMAIL}
            </a>
          </AsideCard>
        }
      >
        <SummaryCard
          title="The short version"
          items={[
            "We use your voice, messages and files only to run your lessons — never to sell ads.",
            "Voice is streamed in real time to transcribe and reply; we keep the text, not the recording.",
            "We never sell your personal data or share it for advertising.",
            <>
              Delete your account in the app or at{" "}
              <TextLink href={ROUTES.deleteAccount}>
                {`${SITE_DOMAIN}${ROUTES.deleteAccount}`}
              </TextLink>{" "}
              — your data goes with it.
            </>,
          ]}
        />

        <LegalSection id="who-we-are" number={1} title="Who we are">
          <P>
            Zuno (“Zuno”, “we”, “us”) is an AI language partner available on
            Android and the web at{" "}
            <TextLink href={ROUTES.home}>{SITE_DOMAIN}</TextLink>. Zuno AI is
            the controller of the personal data described in this policy.
          </P>
          <P>
            This policy applies to the Zuno Android app, our website and any
            related services that link to it.
          </P>
        </LegalSection>

        <LegalSection
          id="information-we-collect"
          number={2}
          title="Information we collect"
        >
          <SubHeading>Information you give us</SubHeading>
          <BulletList>
            <Bullet lead="Account details">
              your name, email address and profile photo, or the basic profile
              Google shares when you choose “Continue with Google”.
            </Bullet>
            <Bullet lead="Learning profile">
              the languages you practise, your level, accent, daily goal, focus
              and app preferences.
            </Bullet>
            <Bullet lead="Conversations">
              messages you type or speak, Zuno’s replies, corrections,
              translations and saved vocabulary.
            </Bullet>
            <Bullet lead="Files and links">
              photos, PDFs, documents and web links you add to a chat or topic.
            </Bullet>
            <Bullet lead="Schedules and reminders">
              practice times, repeat rules and reminder settings you create.
            </Bullet>
            <Bullet lead="Support and reports">
              messages you send us and chats you report.
            </Bullet>
          </BulletList>
          <SubHeading>Information collected automatically</SubHeading>
          <BulletList>
            <Bullet lead="Device information">
              device model, Android version, app version, language, time zone
              and a push notification token.
            </Bullet>
            <Bullet lead="Usage and diagnostics">
              sessions started, features used, errors and performance data that
              help us keep Zuno reliable.
            </Bullet>
          </BulletList>
          <Callout icon={<Lock />}>
            Zuno does not collect your precise location, contacts, call logs or
            advertising ID.
          </Callout>
        </LegalSection>

        <LegalSection
          id="microphone-voice"
          number={3}
          title="Microphone & voice"
        >
          <P>
            Zuno asks for microphone access only when you start speaking
            practice. You can deny or revoke it at any time in your phone’s
            settings and keep practising by typing.
          </P>
          <BulletList>
            <Bullet>
              <Strong>While a voice session is live,</Strong> your audio is
              streamed over an encrypted connection to transcribe your speech
              and generate Zuno’s spoken reply.
            </Bullet>
            <Bullet>
              <Strong>We store</Strong> the text transcript of the conversation,
              so you can review it and pick up where you left off.
            </Bullet>
            <Bullet>
              <Strong>We do not use</Strong> your voice to identify you, build a
              voiceprint or for advertising.
            </Bullet>
          </BulletList>
        </LegalSection>

        <LegalSection
          id="how-we-use-information"
          number={4}
          title="How we use information"
        >
          <BulletList>
            <Bullet lead="Provide the service">
              run Free Talk, Topics, Roleplays and voice sessions, and give
              corrections and translations.
            </Bullet>
            <Bullet lead="Personalise practice">
              adapt lessons to your level, goals, accent and teaching-style
              preferences.
            </Bullet>
            <Bullet lead="Send notifications you ask for">
              scheduled-practice reminders, “remind me to continue” and, if
              enabled, weekly summaries.
            </Bullet>
            <Bullet lead="Keep Zuno safe">
              prevent abuse, review reported chats and secure accounts.
            </Bullet>
            <Bullet lead="Improve Zuno">
              fix bugs and understand which features help learners, using
              aggregated or de-identified data where possible.
            </Bullet>
          </BulletList>
          <P>
            We process data because it is necessary to provide the service you
            asked for, with your consent (for example microphone and
            notifications), and for our legitimate interest in keeping Zuno
            secure and working well.
          </P>
        </LegalSection>

        <LegalSection
          id="ai-service-providers"
          number={5}
          title="AI & service providers"
        >
          <P>
            Zuno relies on trusted providers that process data on our behalf,
            under contracts that limit use to providing their service to us. We
            do not allow them to use your content to train their own models
            where that choice is available to us.
          </P>
          <LegalTable
            columns={PROVIDER_COLUMNS}
            rows={PROVIDERS.map((provider) => ({
              key: provider.name,
              cells: [provider.name, provider.purpose, provider.data],
            }))}
          />
        </LegalSection>

        <LegalSection id="sharing" number={6} title="Sharing">
          <P>
            We do not sell or rent your personal data, and we do not share it
            for targeted advertising. We share it only:
          </P>
          <BulletList>
            <Bullet>
              With the service providers listed above, to run Zuno.
            </Bullet>
            <Bullet lead="When you choose to">
              for example, by making a chat public with “Share · Public access”.
              Anyone with the link can view that chat until you switch it back
              to “Only me”.
            </Bullet>
            <Bullet lead="To comply with law">
              or to protect the rights, safety and property of our users and
              Zuno.
            </Bullet>
            <Bullet lead="As part of a business transfer">
              such as a merger, with notice to you.
            </Bullet>
          </BulletList>
        </LegalSection>

        <LegalSection id="data-retention" number={7} title="Data retention">
          <BulletList>
            <Bullet lead="Account, chats, topics and files">
              kept while your account is active, or until you delete them.
            </Bullet>
            <Bullet lead="Deleted accounts">
              removed from our live systems immediately and from backups within
              30 days.
            </Bullet>
            <Bullet lead="Unsent uploads">
              automatically deleted after a short period.
            </Bullet>
            <Bullet lead="Chat reports">
              kept for up to 12 months to investigate safety issues.
            </Bullet>
          </BulletList>
        </LegalSection>

        <LegalSection
          id="your-choices-rights"
          number={8}
          title="Your choices & rights"
        >
          <P>
            Depending on where you live, you may have the right to access,
            correct, download, delete or restrict the use of your personal data,
            and to object to processing or withdraw consent.
          </P>
          <BulletList>
            <Bullet lead="Download your data">
              Settings → Account → Download my data. We email you a link to a
              copy.
            </Bullet>
            <Bullet lead="Edit your profile">Settings → Account.</Bullet>
            <Bullet lead="Notifications">
              Settings → Notifications, or your phone’s settings. Every email
              has an unsubscribe link.
            </Bullet>
            <Bullet lead="Microphone">
              revoke anytime in your phone’s settings.
            </Bullet>
          </BulletList>
          <P>
            You can also contact <Mail email={PRIVACY_EMAIL} />. You have the
            right to complain to your local data protection authority.
          </P>
        </LegalSection>

        <LegalSection
          id="deleting-your-account"
          number={9}
          title="Deleting your account"
        >
          <P>You can delete your account and associated data at any time:</P>
          <BulletList>
            <Bullet lead="In the app">
              Settings → Account → Delete account.
            </Bullet>
            <Bullet lead="On the web">
              <TextLink href={ROUTES.deleteAccount}>
                {`${SITE_DOMAIN}${ROUTES.deleteAccount}`}
              </TextLink>
              , without needing the app installed.
            </Bullet>
          </BulletList>
          <P>
            Deletion removes your profile, chats, topics, roleplays, schedules,
            uploaded files and push tokens. It can’t be undone.
          </P>
        </LegalSection>

        <LegalSection id="children" number={10} title="Children">
          <P>
            Zuno is not directed to children under 13 (or the minimum age in
            your country) and we do not knowingly collect their data. If you
            believe a child has given us personal data, contact us and we will
            delete it.
          </P>
        </LegalSection>

        <LegalSection id="security" number={11} title="Security">
          <P>
            We protect data with encryption in transit (TLS), access controls,
            signed short-lived links for private files and least-privilege
            access for our team. No system is perfectly secure, so we also ask
            you to keep your email account secure.
          </P>
        </LegalSection>

        <LegalSection
          id="international-transfers"
          number={12}
          title="International transfers"
        >
          <P>
            Our providers may process data in countries other than yours,
            including the United States. Where required, we rely on safeguards
            such as the European Commission’s Standard Contractual Clauses.
          </P>
        </LegalSection>

        <LegalSection
          id="changes-to-this-policy"
          number={13}
          title="Changes to this policy"
        >
          <P>
            We’ll update the date at the top when this policy changes. For
            significant changes we will notify you in the app or by email before
            they take effect.
          </P>
        </LegalSection>

        <LegalSection id="contact-us" number={14} title="Contact us">
          <P>
            Questions or requests about privacy: <Mail email={PRIVACY_EMAIL} />.
            General support: <Mail email={SUPPORT_EMAIL} /> or{" "}
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
