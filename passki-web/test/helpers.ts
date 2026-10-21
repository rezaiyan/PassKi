import { readFileSync } from "node:fs";
import { join } from "node:path";
import type { Fetched } from "@/lib/checks/types";
import type { Fetcher } from "@/lib/net/fetch";

export const FIXTURES = join(__dirname, "fixtures");
export const fixture = (name: string) =>
  readFileSync(join(FIXTURES, name), "utf8");

export const PKG = "com.alirezaiyan.passki";
export const FP =
  "C9:64:0F:6A:68:90:AF:6C:26:49:DC:BB:AB:94:79:23:93:5C:FC:10:8B:DF:03:20:78:04:60:5C:5B:79:49:03";
export const TEAM = "VFCFJC7Y5J";
export const BUNDLE = "com.alirezaiyan.passki.PassKi";

export function ok(
  url: string,
  body: string,
  contentType: string | null = "application/json",
): Fetched {
  return {
    url,
    status: 200,
    contentType,
    body,
    bytes: Buffer.byteLength(body),
  };
}

export function status(
  url: string,
  code: number,
  extra: Partial<Fetched> = {},
): Fetched {
  return {
    url,
    status: code,
    contentType: "text/html",
    body: "<html></html>",
    ...extra,
  };
}

export function fakeFetcher(
  overrides: Partial<
    Record<"assetlinks" | "aasa" | "dal" | "cdn", Fetched>
  > = {},
  rpId = "alirezaiyan.com",
): Fetcher {
  const al = `https://${rpId}/.well-known/assetlinks.json`;
  const aa = `https://${rpId}/.well-known/apple-app-site-association`;
  return {
    wellKnown: async (_rp, file) =>
      file === "assetlinks.json"
        ? (overrides.assetlinks ?? ok(al, fixture("assetlinks.live.json")))
        : (overrides.aasa ?? ok(aa, fixture("aasa.live.json"))),
    digitalAssetLinks: async () =>
      overrides.dal ??
      ok(
        "https://digitalassetlinks.googleapis.com/v1/statements:list",
        fixture("dal.live.json"),
      ),
    appleCdn: async () =>
      overrides.cdn ??
      ok(
        `https://app-site-association.cdn-apple.com/a/v1/${rpId}`,
        fixture("aasa.live.json"),
      ),
    endpoint: async (url) => status(url, 404),
  };
}
