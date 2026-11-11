import Link from "next/link";
import { Separator } from "@/components/ui/separator";
import { ROUTES } from "@/lib/site";
import { Eyebrow, Logo } from "./brand";

const COLUMNS = [
  {
    title: "Product",
    links: [
      { label: "Free Talk", href: "/#features" },
      { label: "Topics", href: "/#features" },
      { label: "Roleplays", href: "/#features" },
      { label: "Pricing", href: ROUTES.pricing },
    ],
  },
  {
    title: "Support",
    links: [
      { label: "Contact", href: ROUTES.contact },
      { label: "Help & FAQ", href: ROUTES.faq },
      { label: "Delete account", href: ROUTES.deleteAccount },
    ],
  },
  {
    title: "Legal",
    links: [
      { label: "Terms", href: ROUTES.terms },
      { label: "Privacy", href: ROUTES.privacy },
      { label: "Cookies", href: ROUTES.cookies },
    ],
  },
];

export function SiteFooter() {
  return (
    <footer className="mx-auto flex w-full max-w-[1440px] flex-col gap-8 px-4 pt-10 pb-7 md:gap-16 md:px-10 md:pt-24 md:pb-12 lg:px-[120px]">
      <div className="flex flex-col justify-between gap-8 xl:flex-row xl:gap-12">
        <div className="flex flex-col gap-4">
          <Link href="/" aria-label="Zuno home" className="w-fit">
            <Logo />
          </Link>
          <p className="max-w-[280px] text-body text-muted-foreground">
            Your quiet space to master any language.
          </p>
        </div>
        <div className="flex justify-between gap-6 whitespace-nowrap md:gap-10 lg:gap-24">
          {COLUMNS.map((column) => (
            <nav
              key={column.title}
              aria-label={column.title}
              className="flex flex-col gap-3 md:w-[140px]"
            >
              <Eyebrow>{column.title}</Eyebrow>
              {column.links.map((link) => (
                <Link
                  key={link.label}
                  href={link.href}
                  className="text-body text-muted-foreground transition-colors hover:text-foreground"
                >
                  {link.label}
                </Link>
              ))}
            </nav>
          ))}
        </div>
      </div>
      <div className="flex flex-col gap-6">
        <Separator />
        <div className="flex flex-col justify-between gap-2 text-footnote text-faint md:flex-row">
          <p>© 2026 Zuno AI. All rights reserved.</p>
          <p>Made for quiet learners.</p>
        </div>
      </div>
    </footer>
  );
}
