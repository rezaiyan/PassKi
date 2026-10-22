import type { Metadata } from "next";
import { demoConfig } from "@/lib/demo/config";
import { IosRegistration } from "./IosRegistration";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Demo server" };

const ENDPOINTS: [string, string, string][] = [
  [
    "POST",
    "/api/demo/register/options",
    "{username?} → creation options; user.id is your demo user handle (24 h)",
  ],
  [
    "POST",
    "/api/demo/register/verify",
    "RegistrationResponseJSON, or {response, capture: {platform}} to share it as a test fixture",
  ],
  [
    "POST",
    "/api/demo/login/options",
    "{userHandle?} → request options (discoverable without a handle)",
  ],
  [
    "POST",
    "/api/demo/login/verify",
    "AuthenticationResponseJSON → verdict with signCount",
  ],
  ["GET", "/api/demo/server-config", "{rpId, origins} accepted by the demo"],
  [
    "GET",
    "/api/demo/fixtures?platform=android",
    "opt-in captured registration responses (last 20 per platform, 7 days)",
  ],
  ["GET", "/.well-known/assetlinks.json", "demo package + fingerprints"],
  [
    "GET",
    "/.well-known/apple-app-site-association",
    "base app IDs + self-service entries",
  ],
];

export default function DemoPage() {
  const cfg = demoConfig();
  return (
    <>
      <header className="page-band">
        <div className="container page-band-inner">
          <h1>Demo server</h1>
          <p className="lead">
            A real WebAuthn relying party (SimpleWebAuthn) for the PassKi
            sample: server-side challenges that are single use and expire in 5
            minutes, signature verification and a clear verdict. Demo users are
            anonymous and expire after 24 hours.
          </p>
        </div>
      </header>
      <div className="container page-body">
        <div className="split">
          <section className="panel">
            <div className="panel-head">
              <h2>Configuration</h2>
            </div>
            <div className="panel-body">
              <dl className="kv">
                <dt>RP ID</dt>
                <dd>
                  <code>{cfg.rpId}</code>
                </dd>
                <dt>Android package</dt>
                <dd>
                  <code>{cfg.androidPackage}</code>
                </dd>
                <dt>Accepted origins</dt>
                <dd>
                  <pre>{cfg.origins.join("\n")}</pre>
                </dd>
              </dl>
            </div>
          </section>
          <section className="panel">
            <div className="panel-head">
              <h2>Add your iOS app</h2>
            </div>
            <div className="panel-body">
              <p className="muted small">
                Your Team ID differs from ours, so the sample can&apos;t be
                signed for our AASA entry. Register your app ID and it is listed
                in{" "}
                <a href="/.well-known/apple-app-site-association">
                  the demo AASA
                </a>{" "}
                for 7 days. Use{" "}
                <code>webcredentials:{cfg.rpId}?mode=developer</code> in your
                entitlements and turn on Settings &gt; Developer &gt; Associated
                Domains Development, so the device reads the file directly
                instead of Apple&apos;s CDN.
              </p>
              <IosRegistration rpId={cfg.rpId} />
            </div>
          </section>
        </div>

        <h2>API</h2>
        <div className="panel table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Method</th>
                <th scope="col">Path</th>
                <th scope="col">What it does</th>
              </tr>
            </thead>
            <tbody>
              {ENDPOINTS.map(([m, p, d]) => (
                <tr key={p}>
                  <td>
                    <span className={`tag method ${m.toLowerCase()}`}>{m}</span>
                  </td>
                  <td>
                    <code>{p}</code>
                  </td>
                  <td className="muted">{d}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}
