import type { Metadata } from "next";
import { SitePage } from "@/components/site/site-page";
import { DeleteAccountRequest } from "./delete-request";

export const metadata: Metadata = {
  title: "Delete your account — Zuno",
  description:
    "Permanently delete your Zuno account and its data — in the app or on the web, without installing anything.",
  alternates: { canonical: "/delete-account" },
};

export default function DeleteAccountPage() {
  return (
    <SitePage>
      <DeleteAccountRequest />
    </SitePage>
  );
}
