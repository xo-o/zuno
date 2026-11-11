import { Lock } from "lucide-react";
import type { Metadata } from "next";
import { ManageCookiesButton } from "@/components/site/cookie-consent";
import {
  AsideCard,
  Bullet,
  BulletList,
  Callout,
  LegalLayout,
  LegalSection,
  LegalTable,
  type LegalTableColumn,
  P,
  PageHeader,
  TextLink,
  type TocItem,
} from "@/components/site/legal";
import { SitePage } from "@/components/site/site-page";
import { LEGAL_UPDATED, PRIVACY_EMAIL, ROUTES, SITE_DOMAIN } from "@/lib/site";

export const metadata: Metadata = {
  title: "Cookie Policy — Zuno",
  description:
    "The few cookies Zuno’s website uses, what each one does, how long it lasts and how to control them.",
  alternates: { canonical: ROUTES.cookies },
};

const TOC: TocItem[] = [
  { id: "what-cookies-are", label: "What cookies are" },
  { id: "cookies-we-use", label: "Cookies we use" },
  { id: "your-choices", label: "Your choices" },
  { id: "changes-contact", label: "Changes & contact" },
];

const COLUMNS: LegalTableColumn[] = [
  { label: "Name", className: "w-[216px]" },
  { label: "Type", className: "w-[136px]" },
  { label: "Purpose" },
  { label: "Duration", className: "w-[130px]" },
];

const COOKIES = [
  {
    name: "better-auth.session_token",
    type: "Essential",
    purpose: `Keeps you signed in on ${SITE_DOMAIN}`,
    duration: "30 days",
  },
  {
    name: "better-auth.csrf",
    type: "Essential",
    purpose: "Protects sign-in and forms from cross-site attacks",
    duration: "Session",
  },
  {
    name: "zuno_cookie_consent",
    type: "Essential",
    purpose: "Remembers your cookie choice",
    duration: "12 months",
  },
  {
    name: "zuno_locale",
    type: "Preference",
    purpose: "Remembers your website language",
    duration: "12 months",
  },
  {
    name: "Analytics (if enabled)",
    type: "Analytics",
    purpose: "Counts visits and pages anonymously so we can improve the site",
    duration: "Up to 13 months",
  },
];

export default function CookiesPage() {
  return (
    <SitePage>
      <PageHeader
        eyebrow="Legal"
        title="Cookie Policy"
        meta={[`Last updated ${LEGAL_UPDATED}`, `${SITE_DOMAIN}/cookies`]}
        compact
        lead="Zuno’s website uses a small number of cookies and similar technologies. Here’s exactly what they are and how to control them. The Android app doesn’t use browser cookies."
      />
      <LegalLayout
        toc={TOC}
        aside={
          <AsideCard
            title="Manage cookies"
            body="Change your choice anytime from the cookie banner, or clear cookies in your browser."
          >
            <ManageCookiesButton />
          </AsideCard>
        }
      >
        <LegalSection id="what-cookies-are" number={1} title="What cookies are">
          <P>
            Cookies are small text files a website stores in your browser.
            Similar technologies include local storage. We use them to keep you
            signed in, remember your choices and understand how the site is
            used.
          </P>
        </LegalSection>

        <LegalSection id="cookies-we-use" number={2} title="Cookies we use">
          <LegalTable
            columns={COLUMNS}
            rows={COOKIES.map((cookie) => ({
              key: cookie.name,
              cells: [
                cookie.name,
                cookie.type,
                cookie.purpose,
                cookie.duration,
              ],
            }))}
          />
          <Callout icon={<Lock aria-hidden="true" />}>
            We don’t use advertising or cross-site tracking cookies.
          </Callout>
        </LegalSection>

        <LegalSection id="your-choices" number={3} title="Your choices">
          <BulletList>
            <Bullet lead="Essential cookies">
              are always on because the site can’t work without them.
            </Bullet>
            <Bullet lead="Preference and analytics cookies">
              are only set if you allow them in the cookie banner.
            </Bullet>
            <Bullet lead="Browser controls">
              you can also block or delete cookies in your browser settings;
              some features may stop working.
            </Bullet>
          </BulletList>
        </LegalSection>

        <LegalSection id="changes-contact" number={4} title="Changes & contact">
          <P>
            We’ll update this page when the cookies we use change. Questions:{" "}
            <TextLink href={`mailto:${PRIVACY_EMAIL}`}>
              {PRIVACY_EMAIL}
            </TextLink>
            . See our <TextLink href={ROUTES.privacy}>Privacy Policy</TextLink>{" "}
            for how we handle personal data.
          </P>
        </LegalSection>
      </LegalLayout>
    </SitePage>
  );
}
