"use client";

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ResultsTable, Summary } from "@/components/Results";
import type { VerifyReport } from "@/lib/checks/types";

const LIVE_INTERVAL_MS = 10_000;
type ServerReport = Pick<VerifyReport, "rpId" | "checkedAt" | "results" | "summary">;

async function post<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
  const json = await res.json();
  if (!res.ok) throw new Error(json.error ?? `HTTP ${res.status}`);
  return json as T;
}

export function Verifier() {
  const params = useSearchParams();
  const [report, setReport] = useState<VerifyReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState(false);
  const [share, setShare] = useState<string | null>(null);
  const [server, setServer] = useState<ServerReport | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const body = useRef<Record<string, unknown> | null>(null);

  async function run() {
    if (!body.current) return;
    setBusy(true);
    try {
      setReport(await post<VerifyReport>("/api/verify", body.current));
      setError(null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (!live) return;
    const id = setInterval(run, LIVE_INTERVAL_MS);
    return () => clearInterval(id);
  }, [live]);

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    body.current = Object.fromEntries(["rpId", "packageName", "fingerprints", "teamId", "bundleId"].map((k) => [k, String(f.get(k) ?? "")]));
    setShare(null);
    run();
  }

  async function onShare() {
    if (!report) return;
    try {
      const { url } = await post<{ url: string }>("/api/reports", report);
      setShare(new URL(url, location.origin).toString());
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function onServerCheck(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    try {
      setServer(
        await post<ServerReport>("/api/server-check", {
          rpId: body.current?.rpId ?? params.get("rpId") ?? "",
          optionsUrl: f.get("optionsUrl"),
          method: f.get("method"),
          serverConfigUrl: f.get("serverConfigUrl"),
          packageName: body.current?.packageName,
          fingerprints: body.current?.fingerprints,
        }),
      );
      setServerError(null);
    } catch (err) {
      setServerError((err as Error).message);
    }
  }

  return (
    <>
      <form className="card" onSubmit={onSubmit}>
        <div className="row">
          <div>
            <label htmlFor="rpId">RP ID (domain)</label>
            <input id="rpId" name="rpId" required defaultValue={params.get("rpId") ?? ""} placeholder="example.com" autoComplete="off" />
          </div>
          <div>
            <label htmlFor="packageName">Android package <small>(optional)</small></label>
            <input id="packageName" name="packageName" defaultValue={params.get("package") ?? ""} placeholder="com.example.app" />
          </div>
        </div>
        <div style={{ marginBottom: 14 }}>
          <label htmlFor="fingerprints">SHA-256 fingerprints to expect <small>(optional; passkiFingerprints output works)</small></label>
          <textarea id="fingerprints" name="fingerprints" defaultValue={params.get("fingerprints") ?? ""} />
        </div>
        <div className="row">
          <div>
            <label htmlFor="teamId">Apple Team ID <small>(optional)</small></label>
            <input id="teamId" name="teamId" defaultValue={params.get("teamId") ?? ""} placeholder="ABCDE12345" />
          </div>
          <div>
            <label htmlFor="bundleId">iOS bundle ID <small>(optional)</small></label>
            <input id="bundleId" name="bundleId" defaultValue={params.get("bundleId") ?? ""} placeholder="com.example.app" />
          </div>
        </div>
        <div className="actions">
          <button type="submit" disabled={busy}>{busy ? "Checking…" : "Run checks"}</button>
          <label className="check">
            <input type="checkbox" checked={live} onChange={(e) => setLive(e.target.checked)} disabled={!body.current} /> Live re-check every 10 s
          </label>
        </div>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      {report && (
        <section aria-live="polite">
          <div className="actions" style={{ marginTop: 24 }}>
            <h2 style={{ margin: 0 }}>{report.rpId}</h2>
            <span className="muted">checked {new Date(report.checkedAt).toLocaleTimeString()}</span>
            <button type="button" className="secondary" onClick={onShare}>Share report</button>
          </div>
          {share && (
            <p>
              Read-only link (7 days): <a href={share}>{share}</a>
            </p>
          )}
          <Summary summary={report.summary} />
          <ResultsTable results={report.results} />

          <h2>Server checks (optional)</h2>
          <form className="card" onSubmit={onServerCheck}>
            <div className="row">
              <div>
                <label htmlFor="optionsUrl">Registration-options URL <small>(https)</small></label>
                <input id="optionsUrl" name="optionsUrl" placeholder="https://api.example.com/webauthn/register/options" />
              </div>
              <div>
                <label htmlFor="method">Method</label>
                <select id="method" name="method" defaultValue="POST">
                  <option>POST</option>
                  <option>GET</option>
                </select>
              </div>
            </div>
            <div className="row">
              <div>
                <label htmlFor="serverConfigUrl">server-config.json URL <small>(optional, {"{rpId, origins}"})</small></label>
                <input id="serverConfigUrl" name="serverConfigUrl" placeholder="https://api.example.com/passki/server-config" />
              </div>
            </div>
            <button type="submit">Check server</button>
          </form>
          {serverError && <p className="error" role="alert">{serverError}</p>}
          {server && (
            <>
              <Summary summary={server.summary} />
              <ResultsTable results={server.results} />
            </>
          )}
        </section>
      )}
    </>
  );
}
