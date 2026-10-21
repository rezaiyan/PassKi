import type { CheckResult } from "@/lib/checks/types";

export function Summary({ summary }: { summary: { pass: number; warn: number; fail: number; skip: number } }) {
  return (
    <div className="summary" aria-label="Summary">
      {(["pass", "warn", "fail", "skip"] as const).map((s) => (
        <span key={s} className={`badge ${s}`}>
          {summary[s]} {s}
        </span>
      ))}
    </div>
  );
}

export function ResultsTable({ results }: { results: CheckResult[] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">Status</th>
            <th scope="col">Check</th>
            <th scope="col">Result and fix</th>
          </tr>
        </thead>
        <tbody>
          {results.map((r, i) => (
            <tr key={`${r.id}-${r.subject ?? ""}-${i}`}>
              <td>
                <span className={`badge ${r.status}`}>{r.status}</span>
              </td>
              <td>
                <a href={r.docs}>
                  <strong>{r.id}</strong>
                </a>{" "}
                {r.title}
                <div className="muted" style={{ fontSize: 13 }}>
                  {r.severity}
                  {r.subject ? ` · ${r.subject}` : ""}
                </div>
              </td>
              <td>
                <div>{r.message}</div>
                {r.fix && <p className="fix"><strong>Fix:</strong> {r.fix}</p>}
                {r.hint && (
                  <details>
                    <summary>Snippet</summary>
                    <pre>{r.hint}</pre>
                  </details>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
