import { getDb } from "@/lib/db";
import { verifyAuthentication } from "@/lib/demo/rp";
import { errorResponse, json, readJson } from "@/lib/http";

/** POST AuthenticationResponseJSON (or {response}) → verdict with the new signCount. */
export async function POST(req: Request) {
  try {
    const body = await readJson(req);
    const response = (body.response && typeof body.response === "object" && "id" in body.response ? body.response : body) as never;
    return json(await verifyAuthentication(getDb(), response));
  } catch (e) {
    return errorResponse(e);
  }
}
