import { getDb } from "@/lib/db";
import { json } from "@/lib/http";
import { loadReport } from "@/lib/reports";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const report = loadReport(getDb(), (await params).id);
  return report ? json(report) : json({ error: "report not found or expired" }, 404);
}
