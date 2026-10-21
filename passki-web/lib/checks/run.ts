import type { Fetcher } from "@/lib/net/fetch";
import { checkAasa } from "./aasa";
import { checkAssetLinks, checkDigitalAssetLinks } from "./assetlinks";
import type { CheckResult, VerifyInput, VerifyReport } from "./types";

export function summarize(results: CheckResult[]): VerifyReport["summary"] {
  const summary = { pass: 0, warn: 0, fail: 0, skip: 0 };
  for (const r of results) summary[r.status]++;
  return summary;
}

/** Fetches the four documents for [input.rpId] in parallel and runs AND-01..04 and IOS-01..03. */
export async function runVerify(
  input: VerifyInput,
  fetcher: Fetcher,
  now: Date = new Date(),
): Promise<VerifyReport> {
  const { rpId } = input;
  const [assetLinks, dal, aasa, cdn] = await Promise.all([
    fetcher.wellKnown(rpId, "assetlinks.json"),
    fetcher.digitalAssetLinks(rpId),
    fetcher.wellKnown(rpId, "apple-app-site-association"),
    fetcher.appleCdn(rpId),
  ]);
  const results = [
    ...checkAssetLinks(rpId, assetLinks, input.android),
    ...checkDigitalAssetLinks(rpId, dal, input.android),
    ...checkAasa(rpId, aasa, cdn, input.ios),
  ];
  return {
    rpId,
    checkedAt: now.toISOString(),
    input,
    results,
    summary: summarize(results),
  };
}
