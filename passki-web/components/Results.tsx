import type { CheckResult } from "@/lib/checks/types";
import { StatusIcon } from "@/components/Icons";

const STATUSES = ["pass", "warn", "fail", "skip"] as const;
const LEGEND = {
  pass: "passed",
  warn: "warnings",
  fail: "failed",
  skip: "skipped",
};

export function Summary({
  summary,
}: {
  summary: { pass: number; warn: number; fail: number; skip: number };
}) {
  return (
    <div className="summary">
      <div className="summary-bar" aria-hidden="true">
        {STATUSES.filter((s) => summary[s] > 0).map((s) => (
          <span key={s} className={s} style={{ flexGrow: summary[s] }} />
        ))}
      </div>
      <ul className="summary-legend" aria-label="Summary">
        {STATUSES.map((s) => (
          <li key={s} className={s}>
            <b>{summary[s]}</b> {LEGEND[s]}
          </li>
        ))}
      </ul>
    </div>
  );
}

export function ResultsTable({ results }: { results: CheckResult[] }) {
  return (
    <ul className="results">
      {results.map((r, i) => (
        <li
          key={`${r.id}-${r.subject ?? ""}-${i}`}
          className={`result ${r.status}`}
        >
          <StatusIcon status={r.status} />
          <div>
            <div className="result-title">
              <a className="id-chip" href={r.docs}>
                {r.id}
              </a>
              <strong>{r.title}</strong>
            </div>
            {(r.status === "fail" || r.status === "warn" || r.subject) && (
              <div className="result-meta">
                {(r.status === "fail" || r.status === "warn") && (
                  <span className={`tag ${r.severity}`}>{r.severity}</span>
                )}
                {r.subject && <span className="tag">{r.subject}</span>}
              </div>
            )}
            <p className="result-msg">{r.message}</p>
            {r.fix && (
              <p className="fix">
                <b>Fix</b> {r.fix}
              </p>
            )}
            {r.hint && (
              <details>
                <summary>Show snippet</summary>
                <pre>{r.hint}</pre>
              </details>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}

const GROUPS: [string, string][] = [
  ["AND-", "Android"],
  ["IOS-", "iOS"],
  ["SRV-", "Server"],
];

/** Results split by platform (by check ID prefix), each with its own pass count. */
export function ResultGroups({ results }: { results: CheckResult[] }) {
  const groups = GROUPS.map(([prefix, label]) => ({ label, items: results.filter((r) => r.id.startsWith(prefix)) }));
  const rest = results.filter((r) => !GROUPS.some(([prefix]) => r.id.startsWith(prefix)));
  if (rest.length) groups.push({ label: "Other", items: rest });
  return (
    <>
      {groups
        .filter((g) => g.items.length)
        .map((g) => (
          <section key={g.label} className="result-group">
            <h3>
              {g.label}{" "}
              <span>
                {g.items.filter((r) => r.status === "pass").length} of {g.items.length} passed
              </span>
            </h3>
            <ResultsTable results={g.items} />
          </section>
        ))}
    </>
  );
}
