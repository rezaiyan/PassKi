import { getDb } from "@/lib/db";
import {
  ANDROID_MAX_ACTIVE,
  deleteAndroidApp,
  listAndroidApps,
  registerAndroidApp,
} from "@/lib/demo/androidApps";
import { demoConfig } from "@/lib/demo/config";
import { clientIp, errorResponse, json, readJson } from "@/lib/http";

export const dynamic = "force-dynamic";

/** GET → active self-service entries (package, fingerprint, expiry). */
export function GET() {
  const cfg = demoConfig();
  return json({
    rpId: cfg.rpId,
    permanent: { packageName: cfg.androidPackage, fingerprints: cfg.androidFingerprints },
    entries: listAndroidApps(getDb()),
    maxActive: ANDROID_MAX_ACTIVE,
  });
}

/**
 * POST {packageName, fingerprints[]} → {packageName, fingerprints, origins, created, expiresAt, deleteToken?}.
 * 201 when something new was listed, 200 when every pair was already listed. 7-day TTL, 5/day per IP, global cap.
 */
export async function POST(req: Request) {
  try {
    const result = registerAndroidApp(getDb(), await readJson(req), clientIp(req));
    return json(result, result.created > 0 ? 201 : 200);
  } catch (e) {
    return errorResponse(e);
  }
}

/** DELETE {packageName, token} → 204. */
export async function DELETE(req: Request) {
  try {
    deleteAndroidApp(getDb(), await readJson(req));
    return new Response(null, { status: 204 });
  } catch (e) {
    return errorResponse(e);
  }
}
