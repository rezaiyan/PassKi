"use client";

import { useState } from "react";

interface GeneratedFile { path: string; content: string; description: string }

export function GeneratorForm() {
  const [files, setFiles] = useState<GeneratedFile[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [last, setLast] = useState<Record<string, unknown> | null>(null);

  const payload = (form: HTMLFormElement) => {
    const f = new FormData(form);
    return {
      rpId: f.get("rpId"),
      packageName: f.get("packageName"),
      fingerprints: f.get("fingerprints"),
      teamId: f.get("teamId"),
      bundleId: f.get("bundleId"),
      developerMode: f.get("developerMode") === "on",
    };
  };

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const body = payload(e.currentTarget);
    try {
      const res = await fetch("/api/generate", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
      const json = await res.json();
      if (!res.ok) throw new Error(json.error ?? `HTTP ${res.status}`);
      setFiles(json.files);
      setLast(body);
    } catch (err) {
      setFiles(null);
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function downloadZip() {
    if (!last) return;
    const res = await fetch("/api/generate?format=zip", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(last) });
    if (!res.ok) return setError((await res.json()).error);
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = `passki-setup-${last.rpId}.zip`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <>
      <form className="card" onSubmit={onSubmit}>
        <div className="row">
          <div>
            <label htmlFor="rpId">RP ID (domain)</label>
            <input id="rpId" name="rpId" required placeholder="example.com" autoComplete="off" />
          </div>
          <div>
            <label htmlFor="packageName">Android package <small>(optional)</small></label>
            <input id="packageName" name="packageName" placeholder="com.example.app" autoComplete="off" />
          </div>
        </div>
        <div style={{ marginBottom: 14 }}>
          <label htmlFor="fingerprints">SHA-256 fingerprints <small>— paste <code>./gradlew passkiFingerprints</code> output or one fingerprint per line</small></label>
          <textarea id="fingerprints" name="fingerprints" placeholder="AB:CD:…:EF" />
        </div>
        <div className="row">
          <div>
            <label htmlFor="teamId">Apple Team ID <small>(optional)</small></label>
            <input id="teamId" name="teamId" placeholder="ABCDE12345" autoComplete="off" />
          </div>
          <div>
            <label htmlFor="bundleId">iOS bundle ID</label>
            <input id="bundleId" name="bundleId" placeholder="com.example.app" autoComplete="off" />
          </div>
        </div>
        <div className="actions">
          <button type="submit" disabled={busy}>{busy ? "Generating…" : "Generate"}</button>
          <label className="check"><input type="checkbox" name="developerMode" /> entitlement with <code>?mode=developer</code></label>
        </div>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      {files && (
        <section className="files" aria-live="polite">
          <div className="actions" style={{ margin: "24px 0 12px" }}>
            <h2 style={{ margin: 0 }}>Files</h2>
            <button type="button" onClick={downloadZip}>Download zip</button>
            <a className="button secondary" href={`/verify?rpId=${encodeURIComponent(String(last?.rpId ?? ""))}`}>Verify the domain</a>
          </div>
          {files.map((f) => (
            <div className="file" key={f.path}>
              <header>
                <code>{f.path}</code>
                <span className="muted">{f.description}</span>
              </header>
              <pre>{f.content}</pre>
            </div>
          ))}
        </section>
      )}
    </>
  );
}
