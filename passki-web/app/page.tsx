import Link from "next/link";

export default function Home() {
  return (
    <>
      <h1>Passkey setup, verified end to end</h1>
      <p className="lead">Generate every file your domain and apps need, then watch each check turn green, with the same check IDs as <code>./gradlew passkiCheck</code> and the on-device doctor.</p>
      <div className="grid">
        <div className="card">
          <h2 style={{ marginTop: 0 }}><Link href="/generator">Generator</Link></h2>
          <p>rpId, package + SHA-256 (paste <code>passkiFingerprints</code> output), Team ID and bundle ID → assetlinks.json, AASA, entitlements, xcconfig line, server origins. Download as zip.</p>
        </div>
        <div className="card">
          <h2 style={{ marginTop: 0 }}><Link href="/verify">Verifier</Link></h2>
          <p>Fetches your live <code>.well-known</code> files, Google Digital Asset Links and Apple&apos;s CDN server-side and runs AND-01…04 / IOS-01…03. Live re-check while you deploy; share a read-only report.</p>
        </div>
        <div className="card">
          <h2 style={{ marginTop: 0 }}><Link href="/demo">Demo relying party</Link></h2>
          <p>A real WebAuthn server (SimpleWebAuthn) for the PassKi sample app, plus self-service iOS registration on the demo domain.</p>
        </div>
      </div>
    </>
  );
}
