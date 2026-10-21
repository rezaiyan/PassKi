import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "PassKi Setup", template: "%s · PassKi Setup" },
  description: "Generate and verify passkey domain association files (assetlinks.json, apple-app-site-association) for PassKi apps.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <header className="site">
          <nav aria-label="Main">
            <Link className="brand" href="/">PassKi Setup</Link>
            <Link href="/generator">Generator</Link>
            <Link href="/verify">Verifier</Link>
            <Link href="/checks">Checks</Link>
            <Link href="/demo">Demo RP</Link>
          </nav>
        </header>
        <main>{children}</main>
        <footer className="site">
          PassKi is an open-source Kotlin Multiplatform passkey library. Fingerprints, package names and Team IDs are public data; nothing
          here needs secrets. Shared reports and demo data expire automatically.
        </footer>
      </body>
    </html>
  );
}
