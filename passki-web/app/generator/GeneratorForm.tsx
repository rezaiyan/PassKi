"use client";

import { useState } from "react";
import {
  CheckIcon,
  CopyIcon,
  DownloadIcon,
  FilesIcon,
} from "@/components/Icons";

interface GeneratedFile {
  path: string;
  content: string;
  description: string;
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }
  return (
    <button type="button" className="ghost" onClick={copy}>
      {copied ? <CheckIcon size={14} /> : <CopyIcon />}{" "}
      {copied ? "Copied" : "Copy"}
    </button>
  );
}

const dirOf = (path: string) =>
  path.includes("/") ? path.slice(0, path.lastIndexOf("/") + 1) : "";
const nameOf = (path: string) => path.slice(path.lastIndexOf("/") + 1);

/** The generated set as a file tree (grouped by folder) beside a single-file viewer. */
function Explorer({ files }: { files: GeneratedFile[] }) {
  const [active, setActive] = useState(files[0]?.path);
  const current = files.find((f) => f.path === active) ?? files[0];
  const dirs = [...new Set(files.map((f) => dirOf(f.path)))];
  return (
    <div className="explorer">
      <nav className="tree" aria-label="Generated files">
        {dirs.map((dir) => (
          <div key={dir || "root"}>
            {dir && <div className="tree-dir">{dir}</div>}
            {files
              .filter((f) => dirOf(f.path) === dir)
              .map((f) => (
                <button
                  key={f.path}
                  type="button"
                  aria-current={f.path === current?.path}
                  onClick={() => setActive(f.path)}
                >
                  {nameOf(f.path)}
                </button>
              ))}
          </div>
        ))}
      </nav>
      {current && (
        <div className="viewer">
          <div className="viewer-head">
            <div>
              <div className="file-path">{current.path}</div>
              <div className="file-desc">{current.description}</div>
            </div>
            <CopyButton key={current.path} text={current.content} />
          </div>
          <pre>{current.content}</pre>
        </div>
      )}
    </div>
  );
}

/** Verifier link that re-uses the generator inputs and runs at once. */
function verifyHref(input: Record<string, unknown> | null) {
  const q = new URLSearchParams({ run: "1" });
  const add = (key: string, value: unknown) =>
    value && q.set(key, String(value));
  add("rpId", input?.rpId);
  add("package", input?.packageName);
  add("fingerprints", input?.fingerprints);
  add("teamId", input?.teamId);
  add("bundleId", input?.bundleId);
  return `/verify?${q}`;
}

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
      const res = await fetch("/api/generate", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
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
    const res = await fetch("/api/generate?format=zip", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(last),
    });
    if (!res.ok) return setError((await res.json()).error);
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = `passki-setup-${last.rpId}.zip`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="workspace">
      <form className="panel" onSubmit={onSubmit}>
        <fieldset>
          <legend>
            <span className="platform-dot" />
            Domain
          </legend>
          <div className="field">
            <label htmlFor="rpId">RP ID</label>
            <input
              id="rpId"
              name="rpId"
              required
              placeholder="example.com"
              autoComplete="off"
            />
            <span className="hint">
              The domain your passkeys belong to, without https://
            </span>
          </div>
        </fieldset>
        <fieldset>
          <legend>
            <span className="platform-dot android" />
            Android <span className="optional">optional</span>
          </legend>
          <div className="field">
            <label htmlFor="packageName">Package name</label>
            <input
              id="packageName"
              name="packageName"
              placeholder="com.example.app"
              autoComplete="off"
            />
          </div>
          <div className="field">
            <label htmlFor="fingerprints">SHA-256 fingerprints</label>
            <textarea
              id="fingerprints"
              name="fingerprints"
              placeholder="AB:CD:…:EF"
            />
            <span className="hint">
              Paste <code>./gradlew passkiFingerprints</code> output, or one
              fingerprint per line.
            </span>
          </div>
        </fieldset>
        <fieldset>
          <legend>
            <span className="platform-dot ios" />
            iOS <span className="optional">optional</span>
          </legend>
          <div className="fields">
            <div>
              <label htmlFor="teamId">Team ID</label>
              <input
                id="teamId"
                name="teamId"
                placeholder="ABCDE12345"
                autoComplete="off"
              />
            </div>
            <div>
              <label htmlFor="bundleId">Bundle ID</label>
              <input
                id="bundleId"
                name="bundleId"
                placeholder="com.example.app"
                autoComplete="off"
              />
            </div>
          </div>
          <label className="toggle" style={{ marginTop: 14 }}>
            <input type="checkbox" name="developerMode" />{" "}
            <span>
              Add <code>?mode=developer</code> to the entitlement
            </span>
          </label>
        </fieldset>
        <div className="panel-foot">
          <button type="submit" disabled={busy} style={{ width: "100%" }}>
            {busy ? "Generating…" : "Generate files"}
          </button>
        </div>
      </form>

      <section className="output" aria-live="polite">
        {error && (
          <p className="alert error" role="alert">
            {error}
          </p>
        )}
        {files ? (
          <>
            <div className="output-head">
              <h2>{String(last?.rpId ?? "")}</h2>
              <span className="muted small">{files.length} files</span>
              <span className="spacer" />
              <a className="button secondary" href={verifyHref(last)}>
                Verify the domain
              </a>
              <button type="button" onClick={downloadZip}>
                <DownloadIcon /> Download zip
              </button>
            </div>
            <Explorer files={files} />
          </>
        ) : (
          <div className="empty">
            <div>
              <FilesIcon size={32} />
              <strong>Your files will appear here</strong>
              <p>
                Fill in the domain and at least one platform. You get
                assetlinks.json, apple-app-site-association, the entitlement, an
                xcconfig line and server origins.
              </p>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
