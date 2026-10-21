import { demoConfig } from "@/lib/demo/config";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** GET {rpId, origins}: what the demo RP accepts (SRV-01/02 reference). */
export function GET() {
  const { rpId, origins } = demoConfig();
  return json({ rpId, origins });
}
