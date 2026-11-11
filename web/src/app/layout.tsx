import type { Metadata } from "next";
import { Geist_Mono, Lexend, Outfit } from "next/font/google";
import "./globals.css";
import { CookieConsent } from "@/components/site/cookie-consent";
import { cn } from "@/lib/utils";

const lexend = Lexend({ subsets: ["latin"], variable: "--font-sans" });

const outfit = Outfit({
  subsets: ["latin"],
  weight: "500",
  variable: "--font-brand",
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Zuno — Your quiet space to master any language",
  description:
    "Zuno is a calm AI language partner. Talk out loud or type, get gentle corrections in the moment, and build real confidence — one conversation at a time.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={cn(
        "dark h-full antialiased font-sans",
        lexend.variable,
        outfit.variable,
        geistMono.variable,
      )}
    >
      <body className="min-h-full flex flex-col">
        {children}
        <CookieConsent />
      </body>
    </html>
  );
}
