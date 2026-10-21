import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { unzipSync, strFromU8 } from "fflate";
import { aasa, assetLinks, entitlementsPlist, generate, origins, serverConfig, xcconfigLine, zip } from "@/lib/generator";
import { androidOrigin, parseFingerprints } from "@/lib/fingerprints";
import { generatorInputFrom, InputError } from "@/lib/validate";
import { BUNDLE, FP, PKG, TEAM } from "./helpers";

const golden = (name: string) => readFileSync(join(__dirname, "..", "..", "docs", "setup", "well-known", name), "utf8");

const PASSKI_FINGERPRINTS_OUTPUT = `
> Task :androidApp:passkiFingerprints
passki signing fingerprints:
  package                | variant | keystore                   | SHA-256
  com.alirezaiyan.passki | debug   | ~/.android/debug.keystore  | ${FP}
  com.alirezaiyan.passki | release | -                          | (no signing config)
  com.example.other      | debug   | other.jks                  | ${FP.toLowerCase().replace(/:/g, "")}
Android WebAuthn origins (server allow-list):
  ${androidOrigin(FP)}
`;

describe("parseFingerprints", () => {
  it("should parse passkiFingerprints output per package", () => {
    const parsed = parseFingerprints(PASSKI_FINGERPRINTS_OUTPUT);
    expect(parsed.byPackage).toEqual({ [PKG]: [FP], "com.example.other": [FP] });
    expect(parsed.unassigned).toEqual([]);
  });

  it("should collect bare fingerprints as unassigned", () => {
    expect(parseFingerprints(`${FP}\n${FP.toLowerCase()}\nnot one`).unassigned).toEqual([FP]);
  });
});

describe("generator outputs", () => {
  it("should produce assetlinks.json semantically equal to docs/setup/well-known/assetlinks.json", () => {
    const out = assetLinks([{ packageName: PKG, fingerprints: [FP] }]);
    expect(JSON.parse(out)).toEqual(JSON.parse(golden("assetlinks.json")));
    expect(out).toBe(golden("assetlinks.json").trimEnd() + "\n");
  });

  it("should produce an AASA semantically equal to the docs one", () => {
    expect(JSON.parse(aasa([`${TEAM}.${BUNDLE}`]))).toEqual(JSON.parse(golden("apple-app-site-association")));
  });

  it("should sort packages and de-duplicate fingerprints like the Gradle plugin", () => {
    const other = "AA:" + FP.slice(3);
    const parsed = JSON.parse(assetLinks([
      { packageName: "z.app", fingerprints: [FP] },
      { packageName: "a.app", fingerprints: [FP, other, FP] },
    ]));
    expect(parsed.map((s: { target: { package_name: string } }) => s.target.package_name)).toEqual(["a.app", "z.app"]);
    expect(parsed[0].target.sha256_cert_fingerprints).toEqual([other, FP]);
    expect(parsed[0].relation).toEqual(["delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"]);
  });

  it("should write entitlements, the xcconfig line and server config", () => {
    expect(entitlementsPlist("example.com")).toContain("<string>webcredentials:example.com</string>");
    expect(entitlementsPlist("example.com", true)).toContain("webcredentials:example.com?mode=developer");
    expect(xcconfigLine()).toBe("CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements");
    expect(origins("example.com", [{ packageName: PKG, fingerprints: [FP] }])).toEqual(["https://example.com", androidOrigin(FP)]);
    expect(JSON.parse(serverConfig("example.com", []))).toEqual({ rpId: "example.com", origins: ["https://example.com"] });
  });

  it("should compute the android origin like the Kotlin plugin", () => {
    expect(androidOrigin(FP)).toBe("android:apk-key-hash:yWQPamiQr2wmSdy7q5R5I5Nc_BCL3wMgeARgXFt5SQM");
  });

  it("should zip every file under passki-setup/", () => {
    const files = generate(generatorInputFrom({ rpId: "example.com", packageName: PKG, fingerprints: PASSKI_FINGERPRINTS_OUTPUT, teamId: TEAM, bundleId: BUNDLE }));
    const names = files.map((f) => f.path);
    expect(names).toEqual(expect.arrayContaining([
      "well-known/assetlinks.json",
      "well-known/apple-app-site-association",
      "ios/iosApp.entitlements",
      "ios/Config.xcconfig.line",
      "server/server-config.json",
      "server/snippets/simplewebauthn.ts",
      "server/snippets/webauthn4j.kt",
      "server/snippets/java-webauthn-server.java",
      "server/snippets/py_webauthn.py",
      "README.txt",
    ]));
    const unzipped = unzipSync(zip(files));
    expect(strFromU8(unzipped["passki-setup/well-known/assetlinks.json"])).toBe(files.find((f) => f.path === "well-known/assetlinks.json")!.content);
    expect(strFromU8(unzipped["passki-setup/server/snippets/simplewebauthn.ts"])).toContain(androidOrigin(FP));
  });

  it("should validate generator input", () => {
    expect(() => generatorInputFrom({ rpId: "localhost", packageName: PKG, fingerprints: FP })).toThrow(InputError);
    expect(() => generatorInputFrom({ rpId: "example.com", packageName: PKG })).toThrow(/no fingerprints/);
    expect(() => generatorInputFrom({ rpId: "example.com", teamId: "abc", bundleId: BUNDLE })).toThrow(/Team ID/);
    expect(() => generatorInputFrom({ rpId: "example.com", bundleId: BUNDLE })).toThrow(/Team ID is required/);
    expect(generatorInputFrom({ rpId: "example.com", bundleId: `${TEAM}.${BUNDLE}` }).appleAppIds).toEqual([`${TEAM}.${BUNDLE}`]);
  });
});
