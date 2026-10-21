import { BlockList, isIP } from "node:net";
import { domainToASCII } from "node:url";

/**
 * SSRF guard for every server-side fetch: only HTTPS on port 443 to a syntactically valid public
 * domain whose every resolved A/AAAA address is public. See {@link guardedLookup} for DNS pinning.
 */

const BLOCKED_SUFFIXES = [
  "localhost",
  "local",
  "localdomain",
  "internal",
  "intranet",
  "lan",
  "home",
  "corp",
  "private",
  "home.arpa",
  "arpa",
  "test",
  "example",
  "invalid",
  "onion",
  "alt",
];

// Separate lists: a BlockList with IPv6 rules also matches IPv4 via ::ffff:0:0/96.
const blocked4 = new BlockList();
const blocked6 = new BlockList();
for (const [net, prefix] of [
  ["0.0.0.0", 8],
  ["10.0.0.0", 8],
  ["100.64.0.0", 10],
  ["127.0.0.0", 8],
  ["169.254.0.0", 16],
  ["172.16.0.0", 12],
  ["192.0.0.0", 24],
  ["192.0.2.0", 24],
  ["192.88.99.0", 24],
  ["192.168.0.0", 16],
  ["198.18.0.0", 15],
  ["198.51.100.0", 24],
  ["203.0.113.0", 24],
  ["224.0.0.0", 4],
  ["240.0.0.0", 4],
] as const) {
  blocked4.addSubnet(net, prefix, "ipv4");
}
for (const [net, prefix] of [
  ["::", 128],
  ["::1", 128],
  ["::ffff:0:0", 96], // IPv4-mapped: checked via the embedded IPv4 below
  ["64:ff9b::", 96], // NAT64
  ["64:ff9b:1::", 48],
  ["100::", 64], // discard
  ["2001::", 23], // IETF protocol assignments incl. Teredo
  ["2001:db8::", 32], // documentation
  ["2002::", 16], // 6to4
  ["3fff::", 20], // documentation
  ["5f00::", 16], // SRv6
  ["fc00::", 7], // unique local
  ["fe80::", 10], // link-local
  ["fec0::", 10], // site-local (deprecated)
  ["ff00::", 8], // multicast
] as const) {
  blocked6.addSubnet(net, prefix, "ipv6");
}

/** True when [address] (IPv4 or IPv6 literal) is a globally routable unicast address. */
export function isPublicAddress(address: string): boolean {
  const family = isIP(address);
  if (family === 4) return !blocked4.check(address, "ipv4");
  if (family !== 6) return false;
  // IPv4-mapped/compatible forms (::ffff:a.b.c.d, ::a.b.c.d) never come from public DNS; refuse them outright.
  if (/^[0-9a-f:]*:(\d+\.){3}\d+$/i.test(address)) return false;
  return !blocked6.check(address, "ipv6");
}

export class UnsafeTargetError extends Error {}

const LABEL = /^(?!-)[a-z0-9-]{1,63}(?<!-)$/;

/**
 * Normalises a user-entered RP ID/domain (`Example.COM`, `https://example.com/`) to an ASCII hostname,
 * or throws {@link UnsafeTargetError}. Rejects IP literals, ports, paths, single-label and reserved names.
 */
export function validateDomain(input: string): string {
  let value = input.trim().toLowerCase();
  if (!value) throw new UnsafeTargetError("enter a domain");
  if (value.includes("://")) {
    let url: URL;
    try {
      url = new URL(value);
    } catch {
      throw new UnsafeTargetError(`not a valid URL: ${input}`);
    }
    if (url.protocol !== "https:")
      throw new UnsafeTargetError("only https is allowed");
    if (
      url.username ||
      url.password ||
      url.port ||
      (url.pathname !== "/" && url.pathname !== "") ||
      url.search ||
      url.hash
    ) {
      throw new UnsafeTargetError(
        "enter just the domain (no port, path or query)",
      );
    }
    value = url.hostname;
  }
  if (value.endsWith(".")) value = value.slice(0, -1);
  if (value.startsWith("[") || isIP(value))
    throw new UnsafeTargetError("IP addresses are not allowed; enter a domain");
  if (/[/:@?#\s\\%]/.test(value))
    throw new UnsafeTargetError(`not a domain: ${input}`);
  const ascii = domainToASCII(value);
  if (!ascii) throw new UnsafeTargetError(`not a domain: ${input}`);
  if (isIP(ascii) || /^[0-9.]+$/.test(ascii) || /^0x/i.test(ascii))
    throw new UnsafeTargetError("IP addresses are not allowed; enter a domain");
  if (ascii.length > 253) throw new UnsafeTargetError("domain is too long");
  const labels = ascii.split(".");
  if (labels.length < 2)
    throw new UnsafeTargetError(
      "enter a fully qualified domain (e.g. example.com)",
    );
  if (!labels.every((l) => LABEL.test(l)))
    throw new UnsafeTargetError(`not a domain: ${input}`);
  const tld = labels[labels.length - 1];
  if (!/^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$/.test(tld))
    throw new UnsafeTargetError(`not a public top-level domain: .${tld}`);
  for (const suffix of BLOCKED_SUFFIXES) {
    if (ascii === suffix || ascii.endsWith(`.${suffix}`))
      throw new UnsafeTargetError(`.${suffix} names are not allowed`);
  }
  return ascii;
}

/** Validates a full URL for a server-side fetch: https, default port, no credentials, public domain host. */
export function validateUrl(input: string): URL {
  let url: URL;
  try {
    url = new URL(input.trim());
  } catch {
    throw new UnsafeTargetError("not a valid URL");
  }
  if (url.protocol !== "https:")
    throw new UnsafeTargetError("only https URLs are allowed");
  if (url.username || url.password)
    throw new UnsafeTargetError("credentials in URLs are not allowed");
  if (url.port && url.port !== "443")
    throw new UnsafeTargetError("only the default HTTPS port (443) is allowed");
  url.hostname = validateDomain(url.hostname);
  url.hash = "";
  return url;
}

export interface LookupAddress {
  address: string;
  family: number;
}

export type Resolver = (hostname: string) => Promise<LookupAddress[]>;

/**
 * Resolves [hostname] with [resolve] and returns its addresses only if every one is public.
 * Rejecting when any address is private stops DNS answers that mix public and internal targets.
 */
export async function resolvePublic(
  hostname: string,
  resolve: Resolver,
): Promise<LookupAddress[]> {
  const addresses = await resolve(hostname);
  if (addresses.length === 0)
    throw new UnsafeTargetError(`${hostname} does not resolve`);
  const bad = addresses.find((a) => !isPublicAddress(a.address));
  if (bad)
    throw new UnsafeTargetError(
      `${hostname} resolves to a non-public address (${bad.address})`,
    );
  return addresses;
}
