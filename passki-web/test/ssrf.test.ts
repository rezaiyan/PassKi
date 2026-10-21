import { describe, expect, it } from "vitest";
import {
  appleCdnUrl,
  digitalAssetLinksUrl,
  safeFetch,
  wellKnownUrl,
} from "@/lib/net/fetch";
import {
  isPublicAddress,
  resolvePublic,
  validateDomain,
  validateUrl,
} from "@/lib/net/ssrf";

describe("isPublicAddress", () => {
  it.each([
    "8.8.8.8",
    "1.1.1.1",
    "148.230.109.213",
    "2606:4700:4700::1111",
    "2a00:1450:4001:80b::200e",
  ])("should allow %s", (ip) => {
    expect(isPublicAddress(ip)).toBe(true);
  });

  it.each([
    "127.0.0.1",
    "10.1.2.3",
    "172.16.0.1",
    "172.31.255.255",
    "192.168.1.1",
    "169.254.169.254",
    "100.64.0.1",
    "0.0.0.0",
    "224.0.0.1",
    "255.255.255.255",
    "198.18.0.1",
    "192.0.2.1",
    "::1",
    "::",
    "fe80::1",
    "fc00::1",
    "fd12:3456::1",
    "ff02::1",
    "::ffff:127.0.0.1",
    "::ffff:7f00:1",
    "::ffff:8.8.8.8",
    "64:ff9b::a00:1",
    "2001:db8::1",
    "2002:7f00:1::1",
    "not-an-ip",
  ])("should block %s", (ip) => {
    expect(isPublicAddress(ip)).toBe(false);
  });
});

describe("validateDomain", () => {
  it.each([
    ["alirezaiyan.com", "alirezaiyan.com"],
    ["Passki.AlirezaIyan.com.", "passki.alirezaiyan.com"],
    ["https://alirezaiyan.com/", "alirezaiyan.com"],
    ["  example.co.uk ", "example.co.uk"],
    ["bücher.de", "xn--bcher-kva.de"],
  ])("should accept %s", (input, expected) => {
    expect(validateDomain(input)).toBe(expected);
  });

  it.each([
    "",
    "localhost",
    "foo.localhost",
    "printer.local",
    "db.internal",
    "router.home.arpa",
    "1.2.3.4",
    "[::1]",
    "0x7f000001",
    "2130706433",
    "127.1",
    "intranet",
    "http://alirezaiyan.com",
    "https://alirezaiyan.com:8443",
    "https://alirezaiyan.com/path",
    "https://user@alirezaiyan.com",
    "alirezaiyan.com/evil",
    "alirezaiyan.com:443",
    "-bad.com",
    "a..b.com",
    "foo.123",
    "x.test",
    "a".repeat(64) + ".com",
  ])("should reject %j", (input) => {
    expect(() => validateDomain(input)).toThrow();
  });
});

describe("validateUrl", () => {
  it("should accept https URLs on the default port with any path", () => {
    expect(
      validateUrl(
        "https://api.example.com/webauthn/register/options?x=1",
      ).toString(),
    ).toBe("https://api.example.com/webauthn/register/options?x=1");
    expect(validateUrl("https://api.example.com:443/a").port).toBe("");
  });

  it.each([
    "http://api.example.com/a",
    "https://api.example.com:8080/a",
    "https://127.0.0.1/a",
    "https://u:p@api.example.com/",
    "file:///etc/passwd",
    "ftp://x.com",
  ])("should reject %s", (url) => {
    expect(() => validateUrl(url)).toThrow();
  });
});

describe("resolvePublic", () => {
  it("should return addresses when all are public", async () => {
    const out = await resolvePublic("alirezaiyan.com", async () => [
      { address: "148.230.109.213", family: 4 },
    ]);
    expect(out).toHaveLength(1);
  });

  it("should reject when any resolved address is private (DNS rebinding / mixed answers)", async () => {
    await expect(
      resolvePublic("evil.com", async () => [
        { address: "8.8.8.8", family: 4 },
        { address: "10.0.0.1", family: 4 },
      ]),
    ).rejects.toThrow(/non-public/);
    await expect(
      resolvePublic("evil.com", async () => [{ address: "::1", family: 6 }]),
    ).rejects.toThrow(/non-public/);
    await expect(resolvePublic("empty.com", async () => [])).rejects.toThrow(
      /does not resolve/,
    );
  });
});

describe("fixed fetch targets", () => {
  it("should build only the allowed URLs", () => {
    expect(wellKnownUrl("Alirezaiyan.com", "assetlinks.json")).toBe(
      "https://alirezaiyan.com/.well-known/assetlinks.json",
    );
    expect(appleCdnUrl("alirezaiyan.com")).toBe(
      "https://app-site-association.cdn-apple.com/a/v1/alirezaiyan.com",
    );
    expect(digitalAssetLinksUrl("alirezaiyan.com")).toBe(
      "https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https%3A%2F%2Falirezaiyan.com&relation=delegate_permission%2Fcommon.get_login_creds",
    );
    expect(() => wellKnownUrl("localhost", "assetlinks.json")).toThrow();
  });

  it("should refuse unsafe URLs without any network access", async () => {
    for (const url of [
      "http://example.com/",
      "https://127.0.0.1/",
      "https://localhost/x",
      "https://example.com:22/",
    ]) {
      const r = await safeFetch(url);
      expect(r.status).toBe(0);
      expect(r.error).toBeTruthy();
    }
  });
});
