"use client";

import { useRef, useState } from "react";
import { TrustLink, type LinkResult } from "@/components/TrustLink";
import type { VerifyReport } from "@/lib/checks/types";

const DEMO_DOMAIN = "passki.alirezaiyan.com";
const EXAMPLE: LinkResult[] = [
  "AND-01",
  "AND-02",
  "AND-03",
  "AND-04",
  "IOS-01",
  "IOS-02",
  "IOS-03",
].map((id) => ({ id, status: "pass" }));

/** "https://Example.com/path" → "example.com"; the API validates the rest. */
const toRpId = (raw: string) =>
  raw
    .trim()
    .toLowerCase()
    .replace(/^https?:\/\//, "")
    .replace(/[/?#].*$/, "");

export function HeroCheck() {
  const [rpId, setRpId] = useState("example.com");
  const [report, setReport] = useState<VerifyReport | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [run, setRun] = useState(0);
  const input = useRef<HTMLInputElement>(null);

  async function check(domain: string) {
    if (!domain) return;
    setRpId(domain);
    setReport(null);
    setError(null);
    setRunning(true);
    setRun((n) => n + 1);
    try {
      const res = await fetch("/api/verify", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ rpId: domain }),
      });
      const json = await res.json();
      if (!res.ok) throw new Error(json.error ?? `HTTP ${res.status}`);
      setReport(json);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setRunning(false);
      setRun((n) => n + 1);
    }
  }

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    check(toRpId(input.current?.value ?? ""));
  }

  function tryDemo() {
    if (input.current) input.current.value = DEMO_DOMAIN;
    check(DEMO_DOMAIN);
  }

  const s = report?.summary;
  return (
    <>
      <form className="hero-check" onSubmit={onSubmit}>
        <label htmlFor="hero-rpId" className="sr-only">
          Your domain
        </label>
        <div className="hero-input">
          <span aria-hidden="true">https://</span>
          <input
            ref={input}
            id="hero-rpId"
            name="rpId"
            placeholder="yourdomain.com"
            autoComplete="off"
            autoCapitalize="off"
            spellCheck={false}
            required
          />
          <button type="submit" disabled={running}>
            {running ? "Checking…" : "Check domain"}
          </button>
        </div>
        <p className="hero-try">
          No domain handy? Check{" "}
          <button type="button" onClick={tryDemo} disabled={running}>
            {DEMO_DOMAIN}
          </button>
          , the demo server.
        </p>
      </form>

      <div className="stage">
        <TrustLink
          key={run}
          rpId={rpId}
          results={report ? report.results : running || error ? null : EXAMPLE}
          running={running}
        />
        <div className="stage-foot" aria-live="polite">
          {error ? (
            <span className="stage-error">{error}</span>
          ) : running ? (
            <span>
              Fetching {rpId}&apos;s files, Google Digital Asset Links and
              Apple&apos;s CDN…
            </span>
          ) : report && s ? (
            <>
              <span>
                <b>{s.pass}</b> passed
                {s.warn ? (
                  <>
                    , <b>{s.warn}</b> warnings
                  </>
                ) : null}
                {s.fail ? (
                  <>
                    , <b className="bad">{s.fail}</b> failed
                  </>
                ) : null}
                {s.skip ? <>, {s.skip} skipped</> : null} on {report.rpId}
              </span>
              <a href={`/verify?rpId=${encodeURIComponent(report.rpId)}&run=1`}>
                Open the full report with fixes
              </a>
            </>
          ) : (
            <>
              <span>
                A correctly set-up domain: both platforms show their passkey
                sheet.
              </span>
              <a href="/checks">What each check means</a>
            </>
          )}
        </div>
      </div>
    </>
  );
}
