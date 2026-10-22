import type { CheckStatus } from "@/lib/checks/types";
import { CheckIcon, CrossIcon, SkipIcon, WarnIcon } from "@/components/Icons";

/**
 * The app ↔ domain ↔ app picture: each link carries the checks that decide whether
 * that platform shows its passkey sheet. Used by the home hero and the verifier.
 */

type SideState = CheckStatus | "idle" | "running";
export interface LinkResult {
  id: string;
  status: CheckStatus;
}

const ANDROID: [string, string][] = [
  ["AND-01", "File reachable"],
  ["AND-02", "Login delegated"],
  ["AND-03", "Signing cert listed"],
  ["AND-04", "Google agrees"],
];
const IOS: [string, string][] = [
  ["IOS-01", "File reachable"],
  ["IOS-02", "App listed"],
  ["IOS-03", "Apple CDN has it"],
];

const RANK: Record<CheckStatus, number> = {
  fail: 3,
  warn: 2,
  pass: 1,
  skip: 0,
};
const worst = (s: CheckStatus[]): CheckStatus | null =>
  s.length ? s.reduce((a, b) => (RANK[b] > RANK[a] ? b : a)) : null;

const STEP_MS = 140;
const START_MS = 250;

function statusOf(
  results: LinkResult[] | null | undefined,
  id: string,
): CheckStatus | null {
  return worst((results ?? []).filter((r) => r.id === id).map((r) => r.status));
}

function sideState(
  results: LinkResult[] | null | undefined,
  ids: string[],
  running: boolean,
): SideState {
  if (running) return "running";
  if (!results) return "idle";
  return (
    worst(
      ids
        .map((id) => statusOf(results, id))
        .filter((s): s is CheckStatus => s !== null),
    ) ?? "idle"
  );
}

function Mark({ status }: { status: CheckStatus | null }) {
  if (status === "pass") return <CheckIcon size={10} />;
  if (status === "fail") return <CrossIcon size={10} />;
  if (status === "warn") return <WarnIcon size={10} />;
  if (status === "skip") return <SkipIcon size={10} />;
  return null;
}

const delay = (ms: number) => ({ "--d": `${ms}ms` }) as React.CSSProperties;

function Link({
  side,
  checks,
  results,
  state,
  offset,
}: {
  side: "android" | "ios";
  checks: [string, string][];
  results?: LinkResult[] | null;
  state: SideState;
  offset: number;
}) {
  return (
    <div className={`tl-link ${side} is-${state}`}>
      <div className="tl-wire" aria-hidden="true">
        <span className="tl-pulse" />
        {checks.map(([id], i) => (
          <span
            key={id}
            className={`tl-dot is-${statusOf(results, id) ?? "idle"}`}
            style={delay(offset + i * STEP_MS)}
          >
            <Mark status={statusOf(results, id)} />
          </span>
        ))}
      </div>
      <ul className="tl-checks">
        {checks.map(([id, label], i) => {
          const s = statusOf(results, id);
          return (
            <li
              key={id}
              className={`is-${s ?? "idle"}`}
              style={delay(offset + i * STEP_MS)}
            >
              <span className="tl-mark">
                <Mark status={s} />
              </span>
              <a href={`/checks/${id}`}>{id}</a>
              <span className="tl-label">{label}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function FaceId() {
  return (
    <svg
      viewBox="0 0 32 32"
      width="30"
      height="30"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M4 10V7a3 3 0 0 1 3-3h3M22 4h3a3 3 0 0 1 3 3v3M28 22v3a3 3 0 0 1-3 3h-3M10 28H7a3 3 0 0 1-3-3v-3" />
      <path d="M11 12v2M21 12v2M16 12v5h-1.5M12 21.5c2.2 1.6 5.8 1.6 8 0" />
    </svg>
  );
}

function KeyGlyph() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="22"
      height="22"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="8" cy="8" r="4.5" />
      <path d="m11.3 11.3 8.2 8.2M16.5 16.5l2-2M14.3 14.3l1.6-1.6" />
    </svg>
  );
}

function Phone({
  os,
  state,
  rpId,
  at,
}: {
  os: "android" | "ios";
  state: SideState;
  rpId: string;
  at: number;
}) {
  const shown = state === "pass" || state === "warn";
  return (
    <div
      className={`tl-phone ${os} is-${state}`}
      style={delay(at)}
      aria-hidden="true"
    >
      <div className="tl-screen">
        <div className="tl-statusbar">
          <span>9:41</span>
          <span className="tl-cam" />
        </div>
        <div className="tl-app">
          <div className="tl-app-logo" />
          <div className="tl-app-title">Welcome back</div>
          <div className="tl-app-field" />
          <div className="tl-app-btn" />
        </div>
        {shown &&
          (os === "android" ? (
            <div className="tl-sheet">
              <span className="tl-handle" />
              <span className="tl-sheet-icon">
                <KeyGlyph />
              </span>
              <b>Use your passkey for {rpId}?</b>
              <span className="tl-account">you@{rpId}</span>
              <span className="tl-sheet-btn">Continue</span>
            </div>
          ) : (
            <div className="tl-sheet">
              <span className="tl-sheet-top">Sign In</span>
              <span className="tl-sheet-icon">
                <FaceId />
              </span>
              <b>Use your passkey for “{rpId}”{"\u2060"}?</b>
              <span className="tl-sheet-btn">Continue</span>
            </div>
          ))}
        {state === "fail" && (
          <div className="tl-toast">
            {os === "android"
              ? "The incoming request cannot be validated"
              : `Application is not associated with domain ${rpId}`}
          </div>
        )}
        {state === "running" && (
          <div className="tl-wait">
            <span /> Checking
          </div>
        )}
      </div>
    </div>
  );
}

export function TrustLink({
  rpId,
  results,
  running = false,
}: {
  rpId: string;
  results?: LinkResult[] | null;
  running?: boolean;
}) {
  const androidIds = ANDROID.map(([id]) => id);
  const iosIds = IOS.map(([id]) => id);
  const android = sideState(results, androidIds, running);
  const ios = sideState(results, iosIds, running);
  const iosOffset = START_MS + 60;
  const androidDone = START_MS + ANDROID.length * STEP_MS + 120;
  const iosDone = iosOffset + IOS.length * STEP_MS + 120;
  const fileState = (id: string) =>
    running ? "running" : (statusOf(results, id) ?? "idle");
  const label = results
    ? `${rpId}: Android ${android}, iOS ${ios}`
    : running
      ? `Checking ${rpId}`
      : `${rpId}: not checked yet`;

  return (
    <figure className="tl" aria-label={label}>
      <Phone os="android" state={android} rpId={rpId} at={androidDone} />
      <Link
        side="android"
        checks={ANDROID}
        results={results}
        state={android}
        offset={START_MS}
      />
      <div className={`tl-domain${rpId.length > 20 ? " long" : ""}`} aria-hidden="true">
        <div className="tl-domain-head">
          <svg
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            aria-hidden="true"
          >
            <rect x="5" y="11" width="14" height="9" rx="2" />
            <path d="M8 11V8a4 4 0 0 1 8 0v3" />
          </svg>
          <span>{rpId}</span>
        </div>
        <div className="tl-domain-path">/.well-known/</div>
        <ul>
          <li className={`is-${fileState("AND-01")}`} style={delay(START_MS)}>
            <span className="tl-mark">
              <Mark status={running ? null : statusOf(results, "AND-01")} />
            </span>
            assetlinks.json
          </li>
          <li className={`is-${fileState("IOS-01")}`} style={delay(iosOffset)}>
            <span className="tl-mark">
              <Mark status={running ? null : statusOf(results, "IOS-01")} />
            </span>
            apple-app-site-association
          </li>
        </ul>
      </div>
      <Link
        side="ios"
        checks={IOS}
        results={results}
        state={ios}
        offset={iosOffset}
      />
      <Phone os="ios" state={ios} rpId={rpId} at={iosDone} />
      <figcaption className="tl-caption">
        <span>Android app</span>
        <span>Your domain</span>
        <span>iOS app</span>
      </figcaption>
    </figure>
  );
}
