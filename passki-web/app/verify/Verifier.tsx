"use client";

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ResultGroups, Summary } from "@/components/Results";
import { TrustLink } from "@/components/TrustLink";
import type { VerifyReport } from "@/lib/checks/types";

const LIVE_INTERVAL_MS = 10_000;
const FIELDS = [
  "rpId",
  "packageName",
  "fingerprints",
  "teamId",
  "bundleId",
] as const;
const PARAM: Record<(typeof FIELDS)[number], string> = {
  rpId: "rpId",
  packageName: "package",
  fingerprints: "fingerprints",
  teamId: "teamId",
  bundleId: "bundleId",
};
type ServerReport = Pick<
  VerifyReport,
  "rpId" | "checkedAt" | "results" | "summary"
>;

async function post<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
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
  const [rpId, setRpId] = useState(params.get("rpId") ?? "");
  const body = useRef<Record<string, string> | null>(null);
  const hasAppParams = ["package", "fingerprints", "teamId", "bundleId"].some(
    (k) => params.get(k),
  );

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

  // Links from the home page and the generator ask for an immediate run with ?run=1.
  useEffect(() => {
    if (params.get("run") !== "1" || !params.get("rpId")) return;
    body.current = Object.fromEntries(
      FIELDS.map((k) => [k, params.get(PARAM[k]) ?? ""]),
    );
    run();
  }, []);

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    body.current = Object.fromEntries(
      FIELDS.map((k) => [k, String(f.get(k) ?? "").trim()]),
    );
    setRpId(body.current.rpId);
    setReport(null);
    setShare(null);
    setServer(null);
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

  const shownRpId = report?.rpId ?? (rpId || "yourdomain.com");
  return (
    <>
      <form className="panel query" onSubmit={onSubmit}>
        <div className="query-row">
          <div>
            <label htmlFor="rpId">RP ID</label>
            <input
              id="rpId"
              name="rpId"
              required
              defaultValue={params.get("rpId") ?? ""}
              placeholder="example.com"
              autoComplete="off"
              spellCheck={false}
            />
          </div>
          <button type="submit" disabled={busy}>
            {busy ? "Checking…" : "Run checks"}
          </button>
        </div>
        <details open={hasAppParams}>
          <summary>Add app details to check your apps are listed</summary>
          <div className="query-fields">
            <div>
              <label htmlFor="packageName">Android package</label>
              <input
                id="packageName"
                name="packageName"
                defaultValue={params.get("package") ?? ""}
                placeholder="com.example.app"
              />
            </div>
            <div>
              <label htmlFor="fingerprints">SHA-256 fingerprints</label>
              <textarea
                id="fingerprints"
                name="fingerprints"
                defaultValue={params.get("fingerprints") ?? ""}
                placeholder="AB:CD:…:EF"
              />
            </div>
            <div>
              <label htmlFor="teamId">Apple Team ID</label>
              <input
                id="teamId"
                name="teamId"
                defaultValue={params.get("teamId") ?? ""}
                placeholder="ABCDE12345"
              />
            </div>
            <div>
              <label htmlFor="bundleId">iOS bundle ID</label>
              <input
                id="bundleId"
                name="bundleId"
                defaultValue={params.get("bundleId") ?? ""}
                placeholder="com.example.app"
              />
            </div>
          </div>
        </details>
        <div className="query-foot">
          <label className="toggle">
            <input
              type="checkbox"
              checked={live}
              onChange={(e) => setLive(e.target.checked)}
              disabled={!body.current}
            />
            <span>Re-check every 10 s while you deploy</span>
          </label>
          <span>
            Fingerprints, packages and Team IDs are public. Nothing here is
            secret.
          </span>
        </div>
      </form>

      {error && (
        <p className="alert error" role="alert">
          {error}
        </p>
      )}

      <section className="verify-results" aria-live="polite">
        <div className="stage">
          <TrustLink
            key={report?.checkedAt ?? "none"}
            rpId={shownRpId}
            results={report?.results}
            running={busy && !report}
          />
          <div className="stage-foot">
            {report ? (
              <>
                <span>
                  {live ? <b>Live · </b> : null}Checked{" "}
                  {new Date(report.checkedAt).toLocaleTimeString()}
                </span>
                <span>
                  Each phone shows its passkey sheet only when every check on
                  its link passes.
                </span>
              </>
            ) : busy ? (
              <span>
                Fetching files from {shownRpId}, Google Digital Asset Links and
                Apple&apos;s CDN…
              </span>
            ) : (
              <span>
                Enter your RP ID and run the checks. Each phone lights up when
                its side of the link is right.
              </span>
            )}
          </div>
        </div>

        {report && (
          <div className="verify-cols">
            <div>
              <Summary summary={report.summary} />
              <ResultGroups
                results={[...report.results, ...(server?.results ?? [])]}
              />
            </div>
            <aside>
              <div className="panel">
                <div className="panel-head">
                  <h3>Share with your team</h3>
                </div>
                <div className="panel-body">
                  <p className="muted small">
                    A read-only link to this report. It expires after 7 days.
                  </p>
                  {share ? (
                    <p className="share">
                      <a href={share}>{share}</a>
                    </p>
                  ) : (
                    <button
                      type="button"
                      className="secondary"
                      onClick={onShare}
                    >
                      Create link
                    </button>
                  )}
                </div>
              </div>
              <form
                className="panel"
                style={{ marginTop: 20 }}
                onSubmit={onServerCheck}
              >
                <div className="panel-head">
                  <h3>
                    Server checks <span className="optional">optional</span>
                  </h3>
                </div>
                <div className="panel-body">
                  <p className="muted small">
                    Confirms your WebAuthn backend returns the same RP ID and
                    accepts your apps&apos; origins.
                  </p>
                  <div className="field">
                    <label htmlFor="optionsUrl">Registration options URL</label>
                    <input
                      id="optionsUrl"
                      name="optionsUrl"
                      placeholder="https://api.example.com/webauthn/register/options"
                    />
                  </div>
                  <div className="field">
                    <label htmlFor="method">Method</label>
                    <select id="method" name="method" defaultValue="POST">
                      <option>POST</option>
                      <option>GET</option>
                    </select>
                  </div>
                  <div className="field">
                    <label htmlFor="serverConfigUrl">
                      server-config.json URL{" "}
                      <span className="optional">optional</span>
                    </label>
                    <input
                      id="serverConfigUrl"
                      name="serverConfigUrl"
                      placeholder="https://api.example.com/passki/server-config"
                    />
                    <span className="hint">
                      Returns <code>{"{rpId, origins}"}</code>.
                    </span>
                  </div>
                  <button type="submit">Check server</button>
                  {serverError && (
                    <p className="alert error" role="alert">
                      {serverError}
                    </p>
                  )}
                  {server && (
                    <p className="muted small" style={{ marginTop: 12 }}>
                      Server results are listed with the others.
                    </p>
                  )}
                </div>
              </form>
            </aside>
          </div>
        )}
      </section>
    </>
  );
}
