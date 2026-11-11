import type { Metadata } from "next";
import { Suspense } from "react";
import { Container, SitePage } from "@/components/site/site-page";
import { ConfirmDeletion, ConfirmLoading } from "./confirm-deletion";

export const metadata: Metadata = {
  title: "Confirm account deletion — Zuno",
  robots: { index: false, follow: false },
};

async function ConfirmFromLink({
  searchParams,
}: Pick<PageProps<"/delete-account/confirm">, "searchParams">) {
  const { token } = await searchParams;
  return <ConfirmDeletion token={typeof token === "string" ? token : null} />;
}

export default function ConfirmDeletionPage({
  searchParams,
}: PageProps<"/delete-account/confirm">) {
  return (
    <SitePage>
      <Container className="flex justify-center pt-14 pb-16 md:pt-24 md:pb-[120px]">
        <Suspense fallback={<ConfirmLoading />}>
          <ConfirmFromLink searchParams={searchParams} />
        </Suspense>
      </Container>
    </SitePage>
  );
}
