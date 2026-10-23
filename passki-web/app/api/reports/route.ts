import { getDb, HOUR } from "@/lib/db";
import { errorResponse, json, limited, readJson } from "@/lib/http";
import { saveReport } from "@/lib/reports";

/** POST a report as /api/verify returned it (signed) → {id, url, expiresAt}; stored for 7 days, read-only at /r/<id>. */
export async function POST(req: Request) {
  try {
    const body = await readJson(req, 256 * 1024);
    const tooMany = limited(req, "reports", 10, HOUR);
    if (tooMany) return tooMany;
    const { id, expiresAt } = saveReport(getDb(), body);
    return json({ id, url: `/r/${id}`, expiresAt }, 201);
  } catch (e) {
    return errorResponse(e);
  }
}
