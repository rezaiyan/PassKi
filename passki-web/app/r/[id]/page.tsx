import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ResultsTable, Summary } from "@/components/Results";
import { getDb } from "@/lib/db";
import { loadReport } from "@/lib/reports";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Shared report", robots: { index: false } };

export default async function ReportPage({ params }: { params: Promise<{ id: string }> }) {
  const report = loadReport(getDb(), (await params).id);
  if (!report) notFound();
  return (
    <>
      <h1>Setup report: {report.rpId}</h1>
      <p className="lead">
        Checked {new Date(report.checkedAt).toUTCString()} · read-only, expires {new Date(report.expiresAt).toUTCString()}.{" "}
        <a href={`/verify?rpId=${encodeURIComponent(report.rpId)}`}>Re-run the checks</a>
      </p>
      <p className="notice">Messages come from the shared report as submitted; titles, severities and docs links come from the PassKi check catalog.</p>
      <Summary summary={report.summary} />
      <ResultsTable results={report.results} />
    </>
  );
}
