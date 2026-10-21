import { getDb } from "@/lib/db";
import { authenticationOptions } from "@/lib/demo/rp";
import { errorResponse, json, limited, readJson } from "@/lib/http";

/** POST {userHandle?} → PublicKeyCredentialRequestOptionsJSON (allowCredentials for that user, else discoverable). */
export async function POST(req: Request) {
  try {
    const body = await readJson(req);
    const tooMany = limited(req, "demo-login", 60);
    if (tooMany) return tooMany;
    return json(await authenticationOptions(getDb(), body));
  } catch (e) {
    return errorResponse(e);
  }
}
