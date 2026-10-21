import { getDb } from "@/lib/db";
import { registrationOptions } from "@/lib/demo/rp";
import { errorResponse, json, limited, readJson } from "@/lib/http";


/** POST {username?} → PublicKeyCredentialCreationOptionsJSON; `user.id` is the demo user handle (24 h). */
export async function POST(req: Request) {
  try {
    const body = await readJson(req);
    const tooMany = limited(req, "demo-register", 30);
    if (tooMany) return tooMany;
    return json(await registrationOptions(getDb(), body));
  } catch (e) {
    return errorResponse(e);
  }
}
