import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ResultGroups, Summary } from "@/components/Results";
import { TrustLink } from "@/components/TrustLink";
import { getDb } from "@/lib/db";
import { loadReport } from "@/lib/reports";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Shared report",
  robots: { index: false },
};

export default async function ReportPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const report = loadReport(getDb(), (await params).id);
  if (!report) notFound();
  return (
    <>
      <header className="page-band">
        <div className="container page-band-inner" style={{ maxWidth: 968 }}>
          <p className="crumb muted">Shared setup report</p>
          <h1 style={{ fontFamily: "var(--font-mono)" }}>{report.rpId}</h1>
          <p className="lead">
            Checked {new Date(report.checkedAt).toUTCString()}. Read-only;
            expires {new Date(report.expiresAt).toUTCString()}.
          </p>
        </div>
      </header>
      <div className="container page-body" style={{ maxWidth: 968 }}>
        <div className="actions" style={{ marginBottom: 20 }}>
          <a
            className="button"
            href={`/verify?rpId=${encodeURIComponent(report.rpId)}&run=1`}
          >
            Re-run the checks
          </a>
        </div>
        <p className="alert info">
          Messages come from the report as submitted. Titles, severities and
          docs links come from the PassKi check catalog.
        </p>
        <div className="stage" style={{ marginBottom: 28 }}>
          <TrustLink rpId={report.rpId} results={report.results} />
          <div className="stage-foot">
            <span>
              Each phone shows its passkey sheet only when every check on its
              link passes.
            </span>
          </div>
        </div>
        <Summary summary={report.summary} />
        <ResultGroups results={report.results} />
      </div>
    </>
  );
}
