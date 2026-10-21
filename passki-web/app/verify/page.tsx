import type { Metadata } from "next";
import { Suspense } from "react";
import { Verifier } from "./Verifier";

export const metadata: Metadata = { title: "Verifier" };

export default function VerifyPage() {
  return (
    <>
      <h1>Verifier</h1>
      <p className="lead">Runs AND-01…04 and IOS-01…03 against your live domain from our server (browsers can&apos;t fetch other domains&apos; <code>.well-known</code> files).</p>
      <Suspense>
        <Verifier />
      </Suspense>
    </>
  );
}
