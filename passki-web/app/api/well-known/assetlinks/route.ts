import { getDb } from "@/lib/db";
import { demoAssetLinks } from "@/lib/demo/androidApps";

export const dynamic = "force-dynamic";

/** Served at /.well-known/assetlinks.json (rewrite): the demo package + env fingerprints + non-expired self-service entries. */
export function GET() {
  return new Response(demoAssetLinks(getDb()), {
    headers: { "content-type": "application/json", "cache-control": "public, max-age=300" },
  });
}
