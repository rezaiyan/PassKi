"use client";

import { useEffect, useState } from "react";

interface Entry { appId: string; expiresAt: string }
interface Registered extends Entry { deleteToken: string; entitlement: string }

export function IosRegistration({ rpId }: { rpId: string }) {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [mine, setMine] = useState<Registered | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = async () => {
    const res = await fetch("/api/demo/ios-apps", { cache: "no-store" });
    if (res.ok) setEntries((await res.json()).entries);
  };
  useEffect(() => {
    refresh();
  }, []);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setError(null);
    const res = await fetch("/api/demo/ios-apps", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ teamId: String(f.get("teamId")).trim().toUpperCase(), bundleId: String(f.get("bundleId")).trim() }),
    });
    const json = await res.json();
    if (!res.ok) return setError(json.error);
    setMine(json);
    refresh();
  }

  async function onDelete() {
    if (!mine) return;
    const res = await fetch("/api/demo/ios-apps", { method: "DELETE", headers: { "content-type": "application/json" }, body: JSON.stringify({ appId: mine.appId, token: mine.deleteToken }) });
    if (!res.ok) return setError((await res.json()).error);
    setMine(null);
    refresh();
  }

  return (
    <>
      <form className="card" onSubmit={onSubmit}>
        <div className="row">
          <div>
            <label htmlFor="teamId">Team ID <small>(10 uppercase letters/digits)</small></label>
            <input id="teamId" name="teamId" required pattern="[A-Za-z0-9]{10}" placeholder="ABCDE12345" autoComplete="off" />
          </div>
          <div>
            <label htmlFor="bundleId">Bundle ID</label>
            <input id="bundleId" name="bundleId" required placeholder="com.example.passki.sample" autoComplete="off" />
          </div>
        </div>
        <button type="submit">Add for 7 days</button>
        <span className="muted" style={{ marginLeft: 10 }}>Limit: 5 per day per IP.</span>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      {mine && (
        <div className="card" style={{ marginTop: 16 }} aria-live="polite">
          <p style={{ marginTop: 0 }}>
            Added <code>{mine.appId}</code> until {new Date(mine.expiresAt).toUTCString()}. Entitlement:
          </p>
          <pre>{mine.entitlement}</pre>
          <p className="muted">Keep this token to remove the entry early (it is shown only once): <code>{mine.deleteToken}</code></p>
          <button type="button" className="secondary" onClick={onDelete}>Remove now</button>
        </div>
      )}
      <h3>Active entries on {rpId}</h3>
      {entries.length === 0 ? (
        <p className="muted">None right now.</p>
      ) : (
        <ul>
          {entries.map((e) => (
            <li key={e.appId}><code>{e.appId}</code> <span className="muted">expires {new Date(e.expiresAt).toUTCString()}</span></li>
          ))}
        </ul>
      )}
    </>
  );
}
