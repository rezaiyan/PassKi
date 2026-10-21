import { getDb } from "@/lib/db";
import { demoAasa } from "@/lib/demo/iosApps";

export const dynamic = "force-dynamic";

/** Served at /.well-known/apple-app-site-association (rewrite): base app IDs + non-expired self-service entries. */
export function GET() {
  return new Response(demoAasa(getDb()), {
    headers: { "content-type": "application/json", "cache-control": "public, max-age=60" },
  });
}
