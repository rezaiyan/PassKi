import { getDb } from "@/lib/db";
import { demoConfigFor } from "@/lib/demo/androidApps";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** GET {rpId, origins}: what the demo RP accepts (SRV-01/02 reference), including registered Android apps. */
export function GET() {
  const { rpId, origins } = demoConfigFor(getDb());
  return json({ rpId, origins });
}
