import { getDb } from "@/lib/db";
import { demoConfig } from "@/lib/demo/config";
import { captureFixture, verifyRegistration } from "@/lib/demo/rp";
import { errorResponse, json, readJson } from "@/lib/http";

/**
 * POST RegistrationResponseJSON, or {response, capture?: {platform}} to opt in to storing the raw response
 * as a PassKi contract-test fixture (last N per platform, 7 days). → verdict.
 */
export async function POST(req: Request) {
  try {
    const body = await readJson(req);
    const response = (body.response && typeof body.response === "object" && "id" in body.response ? body.response : body) as never;
    const verdict = await verifyRegistration(getDb(), response);
    const capture = body.capture as { platform?: unknown } | undefined;
    if (capture && typeof capture.platform === "string") {
      captureFixture(getDb(), capture.platform, { kind: "registration", rpId: demoConfig().rpId, response, verdict });
    }
    return json(verdict);
  } catch (e) {
    return errorResponse(e);
  }
}
