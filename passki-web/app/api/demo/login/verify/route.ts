import { getDb } from "@/lib/db";
import { demoConfigFor } from "@/lib/demo/androidApps";
import { verifyAuthentication } from "@/lib/demo/rp";
import { errorResponse, json, limited, readJson } from "@/lib/http";

/** POST AuthenticationResponseJSON (or {response}) → verdict with the new signCount. */
export async function POST(req: Request) {
  try {
    const tooMany = limited(req, "demo-login-verify", 60);
    if (tooMany) return tooMany;
    const body = await readJson(req);
    const response = (body.response && typeof body.response === "object" && "id" in body.response ? body.response : body) as never;
    return json(await verifyAuthentication(getDb(), response, demoConfigFor(getDb())));
  } catch (e) {
    return errorResponse(e);
  }
}
