"use client";

import { useEffect, useState } from "react";

interface Entry {
  appId: string;
  expiresAt: string;
}
interface Registered extends Entry {
  deleteToken: string;
  entitlement: string;
}

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
      body: JSON.stringify({
        teamId: String(f.get("teamId")).trim().toUpperCase(),
        bundleId: String(f.get("bundleId")).trim(),
      }),
    });
    const json = await res.json();
    if (!res.ok) return setError(json.error);
    setMine(json);
    refresh();
  }

  async function onDelete() {
    if (!mine) return;
    const res = await fetch("/api/demo/ios-apps", {
      method: "DELETE",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ appId: mine.appId, token: mine.deleteToken }),
    });
    if (!res.ok) return setError((await res.json()).error);
    setMine(null);
    refresh();
  }

  return (
    <>
      <form onSubmit={onSubmit}>
        <div className="fields">
          <div>
            <label htmlFor="teamId">Team ID</label>
            <input
              id="teamId"
              name="teamId"
              required
              pattern="[A-Za-z0-9]{10}"
              placeholder="ABCDE12345"
              autoComplete="off"
            />
          </div>
          <div>
            <label htmlFor="bundleId">Bundle ID</label>
            <input
              id="bundleId"
              name="bundleId"
              required
              placeholder="com.example.passki.sample"
              autoComplete="off"
            />
          </div>
        </div>
        <div className="actions" style={{ marginTop: 14 }}>
          <button type="submit">Add for 7 days</button>
          <span className="muted small">
            Up to 5 per day from one IP address.
          </span>
        </div>
      </form>
      {error && (
        <p className="alert error" role="alert">
          {error}
        </p>
      )}
      {mine && (
        <div
          className="alert info"
          style={{ display: "block" }}
          aria-live="polite"
        >
          <p>
            Added <code>{mine.appId}</code> until{" "}
            {new Date(mine.expiresAt).toUTCString()}. Your entitlement:
          </p>
          <pre style={{ marginBottom: 12 }}>{mine.entitlement}</pre>
          <p className="token small">
            Removal token, shown only once: <code>{mine.deleteToken}</code>
          </p>
          <button type="button" className="secondary" onClick={onDelete}>
            Remove now
          </button>
        </div>
      )}
      <h3 style={{ marginTop: 28 }}>Active entries on {rpId}</h3>
      {entries.length === 0 ? (
        <p className="muted small">No apps registered right now.</p>
      ) : (
        <ul className="entries panel" style={{ boxShadow: "none" }}>
          {entries.map((e) => (
            <li key={e.appId}>
              <code>{e.appId}</code>{" "}
              <span className="muted">
                Expires {new Date(e.expiresAt).toUTCString()}
              </span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
