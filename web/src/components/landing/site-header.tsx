import { Menu } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { ROUTES } from "@/lib/site";
import { cn } from "@/lib/utils";
import { Logo } from "./brand";

export const NAV_LINKS = [
  { label: "Features", href: "/#features" },
  { label: "How it works", href: "/#how-it-works" },
  { label: "Languages", href: "/#highlights" },
  { label: "Pricing", href: ROUTES.pricing },
];

export function SiteHeader() {
  return (
    <header className="mx-auto flex w-full max-w-[1440px] items-center justify-between px-4 py-2.5 md:px-10 md:py-6 lg:px-[120px]">
      <a href="/" aria-label="Zuno home">
        <Logo responsive />
      </a>
      <nav className="hidden items-center gap-10 lg:flex">
        {NAV_LINKS.map((link) => (
          <a
            key={link.href}
            href={link.href}
            className="text-sm leading-[18px] font-medium text-muted-foreground transition-colors hover:text-foreground"
          >
            {link.label}
          </a>
        ))}
      </nav>
      <div className="hidden items-center gap-2 lg:flex">
        <a
          href={ROUTES.getTheApp}
          className={cn(buttonVariants({ variant: "ghost", size: "pill" }))}
        >
          Sign in
        </a>
        <a
          href={ROUTES.getTheApp}
          className={cn(buttonVariants({ size: "pill" }))}
        >
          Get the app
        </a>
      </div>
      <Sheet>
        <div className="flex items-center gap-1 lg:hidden">
          <a
            href={ROUTES.getTheApp}
            className={cn(buttonVariants({ size: "pill" }))}
          >
            Get the app
          </a>
          <SheetTrigger
            render={
              <Button variant="ghost" size="icon" className="rounded-full" />
            }
          >
            <Menu />
            <span className="sr-only">Open menu</span>
          </SheetTrigger>
        </div>
        <SheetContent side="right">
          <SheetHeader>
            <SheetTitle>
              <Logo />
            </SheetTitle>
          </SheetHeader>
          <nav className="flex flex-col gap-1 px-4">
            {NAV_LINKS.map((link) => (
              <SheetClose
                key={link.href}
                nativeButton={false}
                render={
                  // biome-ignore lint/a11y/useAnchorContent: SheetClose renders its children into the link.
                  <a
                    href={link.href}
                    className="rounded-lg px-2 py-3 text-body-lg text-muted-foreground hover:text-foreground"
                  />
                }
              >
                {link.label}
              </SheetClose>
            ))}
          </nav>
          <Separator />
          <div className="flex flex-col gap-2 px-4">
            <a
              href={ROUTES.getTheApp}
              className={cn(buttonVariants({ size: "pill-lg" }))}
            >
              Get the app
            </a>
            <a
              href={ROUTES.getTheApp}
              className={cn(
                buttonVariants({
                  variant: "outline",
                  size: "pill-lg",
                }),
              )}
            >
              Sign in
            </a>
          </div>
        </SheetContent>
      </Sheet>
    </header>
  );
}
