import { summarize } from "@/lib/checks/run";
import { checkRegistrationOptions, checkServerConfig } from "@/lib/checks/server";
import type { CheckResult } from "@/lib/checks/types";
import { origins } from "@/lib/generator";
import { errorResponse, json, limited, readJson } from "@/lib/http";
import { networkFetcher } from "@/lib/net/fetch";
import { UnsafeTargetError, validateUrl } from "@/lib/net/ssrf";
import { androidFrom, InputError, rpIdOrThrow } from "@/lib/validate";

export const dynamic = "force-dynamic";

function safeUrl(value: unknown, name: string): string | null {
  if (typeof value !== "string" || !value.trim()) return null;
  try {
    return validateUrl(value).toString();
  } catch (e) {
    throw new InputError(`${name}: ${(e as UnsafeTargetError).message}`);
  }
}

/**
 * POST {rpId, optionsUrl?, method?: "GET"|"POST", serverConfigUrl?, packageName?, fingerprints?}
 * → SRV-01/03 from the registration-options endpoint and SRV-01/02 from a server-config.json URL.
 */
export async function POST(req: Request) {
  try {
    const body = await readJson(req);
    const rpId = rpIdOrThrow(body.rpId);
    const optionsUrl = safeUrl(body.optionsUrl, "optionsUrl");
    const configUrl = safeUrl(body.serverConfigUrl, "serverConfigUrl");
    if (!optionsUrl && !configUrl) throw new InputError("enter a registration-options URL and/or a server-config URL");
    const method = body.method === "GET" ? "GET" : "POST";
    const android = androidFrom(body);
    const tooMany = limited(req, "server-check", 10);
    if (tooMany) return tooMany;
    const results: CheckResult[] = [];
    const [opts, conf] = await Promise.all([
      optionsUrl ? networkFetcher.endpoint(optionsUrl, method) : null,
      configUrl ? networkFetcher.endpoint(configUrl, "GET") : null,
    ]);
    if (opts) results.push(...checkRegistrationOptions(rpId, opts));
    if (conf) results.push(...checkServerConfig(rpId, conf, origins(rpId, android)));
    return json({ rpId, checkedAt: new Date().toISOString(), results, summary: summarize(results) });
  } catch (e) {
    return errorResponse(e);
  }
}
