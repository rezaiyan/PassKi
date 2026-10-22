import type { Metadata } from "next";
import { Suspense } from "react";
import { Verifier } from "./Verifier";

export const metadata: Metadata = { title: "Verifier" };

export default function VerifyPage() {
  return (
    <>
      <header className="page-band">
        <div className="container page-band-inner">
          <h1>Verifier</h1>
          <p className="lead">
            Runs AND-01 to AND-04 and IOS-01 to IOS-03 against your live domain
            from our server, since browsers can&apos;t fetch another
            domain&apos;s <code>.well-known</code> files.
          </p>
        </div>
      </header>
      <div className="container page-body">
        <Suspense>
          <Verifier />
        </Suspense>
      </div>
    </>
  );
}
