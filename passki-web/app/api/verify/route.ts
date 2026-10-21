import { runVerify } from "@/lib/checks/run";
import { errorResponse, json, limited, readJson } from "@/lib/http";
import { networkFetcher } from "@/lib/net/fetch";
import { verifyInputFrom } from "@/lib/validate";

export const dynamic = "force-dynamic";

/** POST {rpId, packageName?, fingerprints?, teamId?, bundleId?} → AND-01..04 + IOS-01..03 report. */
export async function POST(req: Request) {
  try {
    const input = verifyInputFrom(await readJson(req));
    const tooMany = limited(req, "verify", 20);
    if (tooMany) return tooMany;
    return json(await runVerify(input, networkFetcher));
  } catch (e) {
    return errorResponse(e);
  }
}
