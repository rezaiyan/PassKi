import { getDb } from "@/lib/db";
import { FIXTURE_PLATFORMS, listFixtures } from "@/lib/demo/rp";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** GET ?platform=android|ios|web|other → captured registration fixtures (opt-in, last N per platform, 7 days). */
export function GET(req: Request) {
  const platform = new URL(req.url).searchParams.get("platform") ?? undefined;
  if (platform && !(FIXTURE_PLATFORMS as readonly string[]).includes(platform)) return json({ error: "unknown platform" }, 400);
  return json({ fixtures: listFixtures(getDb(), platform) });
}
