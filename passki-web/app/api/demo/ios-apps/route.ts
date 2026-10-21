import { getDb } from "@/lib/db";
import { demoConfig } from "@/lib/demo/config";
import { deleteIosApp, IOS_MAX_ACTIVE, listIosApps, registerIosApp } from "@/lib/demo/iosApps";
import { clientIp, errorResponse, json, readJson } from "@/lib/http";

export const dynamic = "force-dynamic";

/** GET → active self-service entries (app ID + expiry). */
export function GET() {
  const cfg = demoConfig();
  return json({ rpId: cfg.rpId, permanent: cfg.appleAppIds, entries: listIosApps(getDb()), maxActive: IOS_MAX_ACTIVE });
}

/** POST {teamId, bundleId} → {appId, expiresAt, deleteToken, entitlement}. 7-day TTL, 5/day per IP, global cap. */
export async function POST(req: Request) {
  try {
    return json(registerIosApp(getDb(), await readJson(req), clientIp(req)), 201);
  } catch (e) {
    return errorResponse(e);
  }
}

/** DELETE {appId, token} → 204. */
export async function DELETE(req: Request) {
  try {
    deleteIosApp(getDb(), await readJson(req));
    return new Response(null, { status: 204 });
  } catch (e) {
    return errorResponse(e);
  }
}
