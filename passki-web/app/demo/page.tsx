import type { Metadata } from "next";
import { demoConfig } from "@/lib/demo/config";
import { IosRegistration } from "./IosRegistration";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Demo relying party" };

const ENDPOINTS: [string, string, string][] = [
  ["POST", "/api/demo/register/options", "{username?} → creation options; user.id is your demo user handle (24 h)"],
  ["POST", "/api/demo/register/verify", "RegistrationResponseJSON, or {response, capture: {platform}} to share it as a test fixture"],
  ["POST", "/api/demo/login/options", "{userHandle?} → request options (discoverable without a handle)"],
  ["POST", "/api/demo/login/verify", "AuthenticationResponseJSON → verdict with signCount"],
  ["GET", "/api/demo/server-config", "{rpId, origins} accepted by the demo"],
  ["GET", "/api/demo/fixtures?platform=android", "opt-in captured registration responses (last 20 per platform, 7 days)"],
  ["GET", "/.well-known/assetlinks.json", "demo package + fingerprints"],
  ["GET", "/.well-known/apple-app-site-association", "base app IDs + self-service entries"],
];

export default function DemoPage() {
  const cfg = demoConfig();
  return (
    <>
      <h1>Demo relying party</h1>
      <p className="lead">
        A real WebAuthn server (SimpleWebAuthn) for the PassKi sample: server-side challenges (5 min, single use), signature verification and a clear
        verdict. Demo users are anonymous and expire after 24 h.
      </p>
      <div className="card">
        <p style={{ marginTop: 0 }}>RP ID <code>{cfg.rpId}</code> · Android package <code>{cfg.androidPackage}</code></p>
        <p>Accepted origins:</p>
        <pre>{cfg.origins.join("\n")}</pre>
      </div>
      <h2>API</h2>
      <div className="table-wrap">
        <table>
          <tbody>
            {ENDPOINTS.map(([m, p, d]) => (
              <tr key={p}><td><code>{m}</code></td><td><code>{p}</code></td><td>{d}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
      <h2>iOS: add your Team ID to the demo domain</h2>
      <p>
        Your Team ID differs from ours, so the sample can&apos;t be signed for our AASA entry. Register your app ID here; it is listed in{" "}
        <a href="/.well-known/apple-app-site-association">the demo AASA</a> for 7 days. Use <code>webcredentials:{cfg.rpId}?mode=developer</code> in your
        entitlements and enable Settings &gt; Developer &gt; Associated Domains Development, so the device reads the file directly instead of Apple&apos;s CDN.
      </p>
      <IosRegistration rpId={cfg.rpId} />
    </>
  );
}
