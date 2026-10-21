import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { catalog, fixText } from "@/lib/catalog";
import { checkAasa } from "@/lib/checks/aasa";
import {
  checkAssetLinks,
  checkDigitalAssetLinks,
} from "@/lib/checks/assetlinks";
import { runVerify } from "@/lib/checks/run";
import {
  checkRegistrationOptions,
  checkServerConfig,
} from "@/lib/checks/server";
import type { CheckResult } from "@/lib/checks/types";
import {
  BUNDLE,
  FP,
  PKG,
  TEAM,
  fakeFetcher,
  fixture,
  ok,
  status,
} from "./helpers";

const RP = "alirezaiyan.com";
const AL = `https://${RP}/.well-known/assetlinks.json`;
const AA = `https://${RP}/.well-known/apple-app-site-association`;
const CDN = `https://app-site-association.cdn-apple.com/a/v1/${RP}`;
const only = (rs: CheckResult[], id: string) => rs.filter((r) => r.id === id);
const one = (rs: CheckResult[], id: string) => {
  const found = only(rs, id);
  expect(found).toHaveLength(1);
  return found[0];
};

describe("catalog", () => {
  it("should be the same file as ../docs/checks/catalog.json when the repo is present", () => {
    const source = join(
      __dirname,
      "..",
      "..",
      "docs",
      "checks",
      "catalog.json",
    );
    const mirror = join(__dirname, "..", "content", "checks", "catalog.json");
    expect(readFileSync(mirror, "utf8")).toBe(readFileSync(source, "utf8"));
  });

  it("should have unique IDs", () => {
    const ids = catalog.checks.map((c) => c.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("should fill fix placeholders and keep unknown ones visible", () => {
    expect(fixText("AND-02", { package: PKG })).toContain(
      `for package ${PKG} to`,
    );
    expect(fixText("IOS-02", { teamId: TEAM })).toContain(
      `"${TEAM}.<bundleId>"`,
    );
  });
});

describe("AND-01..03 assetlinks", () => {
  const expected = [{ packageName: PKG, fingerprints: [FP] }];

  it("should pass all three for the live file", () => {
    const rs = checkAssetLinks(
      RP,
      ok(AL, fixture("assetlinks.live.json")),
      expected,
    );
    expect(rs.map((r) => [r.id, r.status])).toEqual([
      ["AND-01", "pass"],
      ["AND-02", "pass"],
      ["AND-03", "pass"],
    ]);
  });

  it("should compare fingerprints case-insensitively", () => {
    const rs = checkAssetLinks(RP, ok(AL, fixture("assetlinks.live.json")), [
      { packageName: PKG, fingerprints: [FP.toLowerCase()] },
    ]);
    expect(one(rs, "AND-03").status).toBe("pass");
  });

  it("should fail AND-01 on a redirect and skip the dependents", () => {
    const rs = checkAssetLinks(
      RP,
      status(AL, 301, {
        redirectedTo: "https://www.alirezaiyan.com/.well-known/assetlinks.json",
      }),
      expected,
    );
    const and01 = one(rs, "AND-01");
    expect(and01.status).toBe("fail");
    expect(and01.message).toContain("redirects to https://www.alirezaiyan.com");
    expect(and01.fix).toContain(`https://${RP}/.well-known/assetlinks.json`);
    expect(one(rs, "AND-02").status).toBe("skip");
  });

  it("should fail AND-01 on 404, non-JSON-array, oversize and network errors", () => {
    expect(
      one(checkAssetLinks(RP, status(AL, 404), expected), "AND-01").message,
    ).toContain("HTTP 404");
    expect(
      one(checkAssetLinks(RP, ok(AL, '{"a":1}'), expected), "AND-01").message,
    ).toContain("not a JSON array");
    expect(
      one(
        checkAssetLinks(RP, { ...ok(AL, "[]"), truncated: true }, expected),
        "AND-01",
      ).message,
    ).toContain("1 MiB");
    expect(
      one(
        checkAssetLinks(
          RP,
          {
            url: AL,
            status: 0,
            contentType: null,
            body: "",
            error: "ENOTFOUND",
          },
          expected,
        ),
        "AND-01",
      ).status,
    ).toBe("fail");
  });

  it("should warn on a wrong Content-Type but still evaluate statements", () => {
    const rs = checkAssetLinks(
      RP,
      ok(AL, fixture("assetlinks.live.json"), "text/plain"),
      expected,
    );
    expect(one(rs, "AND-01").status).toBe("warn");
    expect(one(rs, "AND-03").status).toBe("pass");
  });

  it("should fail AND-02 when the relation is missing and fill the package into the fix", () => {
    const body = JSON.stringify([
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: PKG,
          sha256_cert_fingerprints: [FP],
        },
      },
    ]);
    const rs = checkAssetLinks(RP, ok(AL, body), expected);
    const and02 = one(rs, "AND-02");
    expect(and02.status).toBe("fail");
    expect(and02.fix).toContain(`for package ${PKG}`);
    expect(and02.hint).toContain('"package_name": "com.alirezaiyan.passki"');
  });

  it("should fail AND-02 for another package", () => {
    const rs = checkAssetLinks(RP, ok(AL, fixture("assetlinks.live.json")), [
      { packageName: "com.other.app", fingerprints: [FP] },
    ]);
    expect(one(rs, "AND-02").message).toContain("for package com.other.app");
  });

  it("should fail AND-03 naming the missing fingerprint", () => {
    const other = "AA:" + FP.slice(3);
    const rs = checkAssetLinks(RP, ok(AL, fixture("assetlinks.live.json")), [
      { packageName: PKG, fingerprints: [FP, other] },
    ]);
    const and03 = one(rs, "AND-03");
    expect(and03.status).toBe("fail");
    expect(and03.message).toContain(other);
    expect(and03.fix).toContain(other);
  });

  it("should downgrade to warn when include statements might cover it", () => {
    const rs = checkAssetLinks(
      RP,
      ok(AL, fixture("assetlinks.include.json")),
      expected,
    );
    const and02 = one(rs, "AND-02");
    expect(and02.status).toBe("warn");
    expect(and02.message).toContain("include statements are not followed");
  });

  it("should report delegated packages without expectations", () => {
    const rs = checkAssetLinks(RP, ok(AL, fixture("assetlinks.live.json")), []);
    expect(one(rs, "AND-02").message).toContain(PKG);
    expect(one(rs, "AND-03").status).toBe("skip");
  });
});

describe("AND-04 Google Digital Asset Links", () => {
  it("should pass when Google lists the package and fingerprint", () => {
    const rs = checkDigitalAssetLinks(RP, ok("x", fixture("dal.live.json")), [
      { packageName: PKG, fingerprints: [FP] },
    ]);
    expect(one(rs, "AND-04").status).toBe("pass");
  });

  it("should warn (catalog severity) when Google doesn't agree", () => {
    const rs = checkDigitalAssetLinks(RP, ok("x", '{"statements":[]}'), [
      { packageName: PKG, fingerprints: [FP] },
    ]);
    expect(one(rs, "AND-04").status).toBe("warn");
    expect(one(rs, "AND-04").severity).toBe("warning");
  });
});

describe("IOS-01..03 apple-app-site-association", () => {
  const expected = [{ teamId: TEAM, bundleId: BUNDLE }];
  const cdn = ok(CDN, fixture("aasa.live.json"));

  it("should pass all three for the live file", () => {
    const rs = checkAasa(RP, ok(AA, fixture("aasa.live.json")), cdn, expected);
    expect(rs.map((r) => [r.id, r.status])).toEqual([
      ["IOS-01", "pass"],
      ["IOS-02", "pass"],
      ["IOS-03", "pass"],
    ]);
  });

  it("should match by bundle ID only when no Team ID is given (doctor semantics)", () => {
    const rs = checkAasa(RP, ok(AA, fixture("aasa.live.json")), cdn, [
      { bundleId: BUNDLE },
    ]);
    expect(one(rs, "IOS-02").message).toContain(`team ID ${TEAM}`);
  });

  it("should fail IOS-02 for another team and fill the fix", () => {
    const rs = checkAasa(RP, ok(AA, fixture("aasa.live.json")), cdn, [
      { teamId: "ABCDE12345", bundleId: BUNDLE },
    ]);
    const ios02 = one(rs, "IOS-02");
    expect(ios02.status).toBe("fail");
    expect(ios02.fix).toContain(`"ABCDE12345.${BUNDLE}"`);
    expect(one(rs, "IOS-03").status).toBe("skip");
  });

  it("should fail IOS-01 on a redirect or invalid JSON", () => {
    expect(
      one(
        checkAasa(RP, status(AA, 302, { redirectedTo: "/x" }), cdn, expected),
        "IOS-01",
      ).message,
    ).toContain("redirects");
    expect(
      one(checkAasa(RP, ok(AA, "[1]"), cdn, expected), "IOS-01").message,
    ).toContain("not a JSON object");
  });

  it("should warn when the file is over 128 KB", () => {
    const big = JSON.stringify({
      webcredentials: { apps: [`${TEAM}.${BUNDLE}`] },
      applinks: { details: [{ paths: ["x".repeat(140 * 1024)] }] },
    });
    const rs = checkAasa(RP, ok(AA, big), cdn, expected);
    expect(one(rs, "IOS-01").status).toBe("warn");
    expect(one(rs, "IOS-01").message).toContain("128 KB");
  });

  it("should compare only the webcredentials entry on the CDN", () => {
    const cdnWithOtherApplinks = ok(
      CDN,
      JSON.stringify({
        applinks: { details: [] },
        webcredentials: { apps: [`${TEAM}.${BUNDLE}`] },
      }),
    );
    expect(
      one(
        checkAasa(
          RP,
          ok(AA, fixture("aasa.live.json")),
          cdnWithOtherApplinks,
          expected,
        ),
        "IOS-03",
      ).status,
    ).toBe("pass");
  });

  it("should warn IOS-03 when the CDN lags or is unavailable", () => {
    const lag = checkAasa(
      RP,
      ok(AA, fixture("aasa.live.json")),
      ok(CDN, '{"webcredentials":{"apps":[]}}'),
      expected,
    );
    expect(one(lag, "IOS-03").status).toBe("warn");
    expect(one(lag, "IOS-03").fix).toContain(
      `webcredentials:${RP}?mode=developer`,
    );
    const down = checkAasa(
      RP,
      ok(AA, fixture("aasa.live.json")),
      status(CDN, 404),
      expected,
    );
    expect(one(down, "IOS-03").message).toContain("unavailable");
  });
});

describe("runVerify", () => {
  it("should pass AND-01..04 and IOS-01..03 for the live-shaped files", async () => {
    const report = await runVerify(
      {
        rpId: RP,
        android: [{ packageName: PKG, fingerprints: [FP] }],
        ios: [{ teamId: TEAM, bundleId: BUNDLE }],
      },
      fakeFetcher(),
    );
    expect(report.results.map((r) => r.id)).toEqual([
      "AND-01",
      "AND-02",
      "AND-03",
      "AND-04",
      "IOS-01",
      "IOS-02",
      "IOS-03",
    ]);
    expect(report.summary).toEqual({ pass: 7, warn: 0, fail: 0, skip: 0 });
    expect(report.results.every((r) => r.docs === `/checks/${r.id}`)).toBe(
      true,
    );
  });
});

describe("SRV checks", () => {
  const good = {
    rp: { id: RP, name: "x" },
    user: {
      id: Buffer.from("user-1").toString("base64url"),
      name: "u",
      displayName: "u",
    },
    challenge: Buffer.alloc(32, 1).toString("base64url"),
    pubKeyCredParams: [{ type: "public-key", alg: -7 }],
  };

  it("should pass SRV-01 and SRV-03 for valid options, also wrapped in publicKey", () => {
    for (const body of [good, { publicKey: good }]) {
      const rs = checkRegistrationOptions(
        RP,
        ok("https://api.example.com/options", JSON.stringify(body)),
      );
      expect(rs.map((r) => r.status)).toEqual(["pass", "pass"]);
    }
  });

  it("should flag a short challenge, a long user.id and a wrong rp.id", () => {
    const bad = {
      ...good,
      rp: { id: "other.com" },
      challenge: Buffer.alloc(8).toString("base64url"),
      user: { id: Buffer.alloc(65).toString("base64url") },
    };
    const rs = checkRegistrationOptions(
      RP,
      ok("https://api.example.com/options", JSON.stringify(bad)),
    );
    expect(one(rs, "SRV-01").message).toContain("other.com");
    expect(one(rs, "SRV-03").message).toContain("challenge is 8 bytes");
    expect(one(rs, "SRV-03").message).toContain("user.id is 65 bytes");
  });

  it("should check server-config origins", () => {
    const origin = "android:apk-key-hash:abc";
    const rs = checkServerConfig(
      RP,
      ok(
        "https://x.com/c",
        JSON.stringify({ rpId: RP, origins: [`https://${RP}`] }),
      ),
      [origin],
    );
    expect(one(rs, "SRV-01").status).toBe("pass");
    expect(one(rs, "SRV-02").message).toContain(origin);
  });
});
