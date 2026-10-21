import { demoConfig } from "@/lib/demo/config";
import { assetLinks } from "@/lib/generator";

export const dynamic = "force-dynamic";

/** Served at /.well-known/assetlinks.json (rewrite): the demo package + env fingerprints. */
export function GET() {
  const cfg = demoConfig();
  return new Response(assetLinks([{ packageName: cfg.androidPackage, fingerprints: cfg.androidFingerprints }]), {
    headers: { "content-type": "application/json", "cache-control": "public, max-age=300" },
  });
}
