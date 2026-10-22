import type { Metadata } from "next";
import Link from "next/link";
import { Bricolage_Grotesque, IBM_Plex_Sans, JetBrains_Mono } from "next/font/google";
import { BrandMark, GitHubIcon } from "@/components/Icons";
import { Nav } from "@/components/Nav";
import { catalog } from "@/lib/catalog";
import "./globals.css";
import "./trustlink.css";

const bricolage = Bricolage_Grotesque({ subsets: ["latin"], variable: "--font-bricolage", display: "swap" });
const plex = IBM_Plex_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-plex",
  display: "swap",
});
const jetbrains = JetBrains_Mono({
  subsets: ["latin"],
  weight: ["400", "600"],
  variable: "--font-jetbrains",
  display: "swap",
});

const REPO = "https://github.com/rezaiyan/PassKi";

export const metadata: Metadata = {
  title: { default: "PassKi: passkeys for Kotlin Multiplatform", template: "%s · PassKi" },
  description:
    "Generate and verify passkey domain association files (assetlinks.json, apple-app-site-association) for PassKi apps.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className={`${bricolage.variable} ${plex.variable} ${jetbrains.variable}`}>
      <body>
        <header className="topbar">
          <div className="container topbar-inner">
            <Link className="brand" href="/">
              <BrandMark />
              <span>PassKi</span>
            </Link>
            <Nav />
            <div className="topbar-end">
              <span className="version" title="Check catalog version">
                catalog v{catalog.version}
              </span>
              <a href={REPO}>
                <GitHubIcon /> GitHub
              </a>
            </div>
          </div>
        </header>
        <main>{children}</main>
        <footer className="footer">
          <div className="container footer-inner">
            <p>
              PassKi is an open-source Kotlin Multiplatform passkey library.
              Everything here works from public data (package names, certificate
              fingerprints, Team IDs), so you never paste a secret. Shared
              reports and demo data expire on their own.
            </p>
            <nav className="footer-links" aria-label="Footer">
              <Link href="/checks">Check reference</Link>
              <Link href="/demo">Demo server</Link>
              <a href={REPO}>Source on GitHub</a>
              <a href={`${REPO}/blob/main/LICENSE`}>Apache 2.0</a>
            </nav>
          </div>
        </footer>
      </body>
    </html>
  );
}
