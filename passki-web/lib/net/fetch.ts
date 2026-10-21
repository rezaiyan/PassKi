import {
  promises as dnsPromises,
  type LookupAddress as DnsLookupAddress,
  type LookupOptions,
} from "node:dns";
import { request } from "node:https";
import type { Fetched } from "@/lib/checks/types";
import { MAX_WELL_KNOWN_BYTES } from "@/lib/checks/common";
import {
  resolvePublic,
  UnsafeTargetError,
  validateDomain,
  validateUrl,
} from "./ssrf";

export const FETCH_TIMEOUT_MS = 10_000;
const USER_AGENT = "PassKi-Setup/1 (+https://passki.alirezaiyan.com)";

type LookupCallback = (
  err: NodeJS.ErrnoException | null,
  address: string | DnsLookupAddress[],
  family?: number,
) => void;

/**
 * `lookup` for `https.request`: resolves A/AAAA and fails unless every address is public. The socket connects
 * only to addresses this function returned, so the validated IP is the one used (no DNS-rebinding window).
 */
export function guardedLookup(hostname: string, options: LookupOptions, callback: LookupCallback): void {
  const resolve = (h: string) => dnsPromises.lookup(h, { all: true, verbatim: true, family: options.family ?? 0 });
  resolvePublic(hostname, resolve).then(
    (list) => (options.all ? callback(null, list) : callback(null, list[0].address, list[0].family)),
    (e: NodeJS.ErrnoException) => {
      if (e instanceof UnsafeTargetError) e.code = "EBLOCKED";
      callback(e, []);
    },
  );
}

export interface SafeFetchOptions {
  method?: "GET" | "POST";
  body?: string;
  accept?: string;
  maxBytes?: number;
  timeoutMs?: number;
}

/**
 * One guarded HTTPS request. Never follows redirects (a 3xx is returned with `redirectedTo`), caps the body,
 * and never throws: failures come back as `{ status: 0, error }`.
 */
export function safeFetch(
  rawUrl: string,
  options: SafeFetchOptions = {},
): Promise<Fetched> {
  let url: URL;
  try {
    url = validateUrl(rawUrl);
  } catch (e) {
    return Promise.resolve({
      url: rawUrl,
      status: 0,
      contentType: null,
      body: "",
      error: (e as Error).message,
    });
  }
  const maxBytes = options.maxBytes ?? MAX_WELL_KNOWN_BYTES;
  const timeoutMs = options.timeoutMs ?? FETCH_TIMEOUT_MS;
  const href = url.toString();
  return new Promise((resolve) => {
    let settled = false;
    const done = (value: Fetched) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve(value);
    };
    const fail = (error: string) =>
      done({ url: href, status: 0, contentType: null, body: "", error });
    const req = request(
      url,
      {
        method: options.method ?? "GET",
        lookup: guardedLookup as never,
        headers: {
          "user-agent": USER_AGENT,
          accept: options.accept ?? "application/json, */*;q=0.5",
          "accept-encoding": "identity",
          ...(options.body !== undefined
            ? {
                "content-type": "application/json",
                "content-length": Buffer.byteLength(options.body),
              }
            : {}),
        },
      },
      (res) => {
        const status = res.statusCode ?? 0;
        const contentType =
          (res.headers["content-type"] as string | undefined) ?? null;
        const location = res.headers.location ?? null;
        const chunks: Buffer[] = [];
        let size = 0;
        let truncated = false;
        res.on("data", (chunk: Buffer) => {
          if (truncated) return;
          if (size + chunk.length > maxBytes) {
            chunks.push(chunk.subarray(0, maxBytes - size));
            size = maxBytes;
            truncated = true;
            res.destroy();
            finish();
            return;
          }
          chunks.push(chunk);
          size += chunk.length;
        });
        const finish = () =>
          done({
            url: href,
            status,
            contentType,
            body: Buffer.concat(chunks).toString("utf8"),
            redirectedTo: status >= 300 && status <= 399 ? location : null,
            truncated,
            bytes: size,
          });
        res.on("end", finish);
        res.on("error", (e) => (truncated ? finish() : fail(e.message)));
      },
    );
    const timer = setTimeout(() => {
      req.destroy();
      fail(`timed out after ${timeoutMs / 1000} s`);
    }, timeoutMs);
    req.on("error", (e) => fail(e.message));
    if (options.body !== undefined) req.write(options.body);
    req.end();
  });
}

/** The only well-known files the verifier may fetch for a user-supplied domain. */
export type WellKnownFile = "assetlinks.json" | "apple-app-site-association";

export function wellKnownUrl(rpId: string, file: WellKnownFile): string {
  return `https://${validateDomain(rpId)}/.well-known/${file}`;
}

export function digitalAssetLinksUrl(rpId: string): string {
  const params = new URLSearchParams({
    "source.web.site": `https://${validateDomain(rpId)}`,
    relation: "delegate_permission/common.get_login_creds",
  });
  return `https://digitalassetlinks.googleapis.com/v1/statements:list?${params}`;
}

export function appleCdnUrl(rpId: string): string {
  return `https://app-site-association.cdn-apple.com/a/v1/${validateDomain(rpId)}`;
}

/** What the check runner needs from the network; tests inject fixtures instead. */
export interface Fetcher {
  wellKnown(rpId: string, file: WellKnownFile): Promise<Fetched>;
  digitalAssetLinks(rpId: string): Promise<Fetched>;
  appleCdn(rpId: string): Promise<Fetched>;
  /** A developer-supplied endpoint (SRV checks). */
  endpoint(url: string, method: "GET" | "POST"): Promise<Fetched>;
}

export const networkFetcher: Fetcher = {
  wellKnown: (rpId, file) => safeFetch(wellKnownUrl(rpId, file)),
  digitalAssetLinks: (rpId) => safeFetch(digitalAssetLinksUrl(rpId)),
  appleCdn: (rpId) => safeFetch(appleCdnUrl(rpId)),
  endpoint: (url, method) =>
    safeFetch(url, {
      method,
      body:
        method === "POST"
          ? JSON.stringify({ username: "passki-setup-check" })
          : undefined,
      maxBytes: 64 * 1024,
    }),
};
