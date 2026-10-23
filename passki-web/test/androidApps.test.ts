import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { clock, DAY, openDb, setDb, type Db } from "@/lib/db";
import {
  ANDROID_FINGERPRINTS_PER_APP,
  deleteAndroidApp,
  demoAssetLinks,
  demoConfigFor,
  listAndroidApps,
  registerAndroidApp,
} from "@/lib/demo/androidApps";
import { demoConfig } from "@/lib/demo/config";
import { registrationOptions, verifyRegistration } from "@/lib/demo/rp";
import { androidOrigin } from "@/lib/fingerprints";
import { FP, PKG } from "./helpers";
import { SoftAuthenticator } from "./softAuthenticator";

const cfg = demoConfig({
  PASSKI_RP_ID: "passki.alirezaiyan.com",
  PASSKI_ANDROID_FINGERPRINTS: FP,
});
const MINE =
  "59:E5:92:32:4E:6C:E6:FA:5F:E2:F0:8A:39:18:13:2D:A5:1D:DE:5D:F9:BD:11:F0:5C:E8:23:57:88:E2:06:C0";
const OTHER =
  "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99";
let db: Db;
let t = Date.parse("2026-10-23T12:00:00Z");
const realNow = clock.now;

beforeEach(() => {
  t = Date.parse("2026-10-23T12:00:00Z");
  clock.now = () => t;
  db = openDb(":memory:");
});
afterEach(() => {
  clock.now = realNow;
  db.close();
});

const statements = (text: string) =>
  (
    JSON.parse(text) as {
      target: { package_name: string; sha256_cert_fingerprints: string[] };
    }[]
  ).map(
    (s) => [s.target.package_name, s.target.sha256_cert_fingerprints] as const,
  );

describe("self-service Android registration", () => {
  it("should list the app in the demo assetlinks and accept its origin for 7 days", () => {
    const res = registerAndroidApp(
      db,
      { packageName: "com.example.app", fingerprints: [MINE.toLowerCase()] },
      "1.2.3.4",
      cfg,
    );

    expect(res.created).toBe(1);
    expect(res.fingerprints).toEqual([MINE]);
    expect(res.origins).toEqual([androidOrigin(MINE)]);
    expect(res.expiresAt).toBe("2026-10-30T12:00:00.000Z");
    expect(statements(demoAssetLinks(db, cfg))).toEqual([
      [PKG, [FP]],
      ["com.example.app", [MINE]],
    ]);
    expect(demoConfigFor(db, cfg).origins).toEqual([
      "https://passki.alirezaiyan.com",
      androidOrigin(FP),
      androidOrigin(MINE),
    ]);

    t += 7 * DAY + 1;
    expect(statements(demoAssetLinks(db, cfg))).toEqual([[PKG, [FP]]]);
    expect(demoConfigFor(db, cfg).origins).toEqual(cfg.origins);
  });

  it("should be idempotent: a re-run reports the existing entry and costs no rate limit", () => {
    registerAndroidApp(
      db,
      { packageName: "com.example.app", fingerprints: [MINE] },
      "1.2.3.4",
      cfg,
    );
    for (let i = 0; i < 10; i++) {
      const again = registerAndroidApp(
        db,
        { packageName: "com.example.app", fingerprints: [MINE] },
        "1.2.3.4",
        cfg,
      );
      expect(again.created).toBe(0);
      expect(again.expiresAt).toBe("2026-10-30T12:00:00.000Z");
      expect(again.deleteToken).toBeUndefined();
    }
    const more = registerAndroidApp(
      db,
      { packageName: "com.example.app", fingerprints: [MINE, OTHER] },
      "1.2.3.4",
      cfg,
    );
    expect(more.created).toBe(1);
    expect(listAndroidApps(db)).toHaveLength(2);
  });

  it("should accept a package that shares the demo package name only with a different key", () => {
    expect(() =>
      registerAndroidApp(
        db,
        { packageName: PKG, fingerprints: [FP] },
        "ip",
        cfg,
      ),
    ).toThrow(/already permanently listed/);
    const res = registerAndroidApp(
      db,
      { packageName: PKG, fingerprints: [MINE] },
      "ip",
      cfg,
    );
    expect(res.created).toBe(1);
    expect(statements(demoAssetLinks(db, cfg))).toEqual([
      [PKG, [FP, MINE].sort()],
    ]);
  });

  it("should validate input", () => {
    expect(() =>
      registerAndroidApp(
        db,
        { packageName: "nope", fingerprints: [MINE] },
        "ip",
        cfg,
      ),
    ).toThrow(/package name/);
    expect(() =>
      registerAndroidApp(
        db,
        { packageName: "com.example.app", fingerprints: [] },
        "ip",
        cfg,
      ),
    ).toThrow(/fingerprint/);
    expect(() =>
      registerAndroidApp(
        db,
        { packageName: "com.example.app", fingerprints: ["12:34"] },
        "ip",
        cfg,
      ),
    ).toThrow(/SHA-256/);
    expect(() =>
      registerAndroidApp(
        db,
        { packageName: "com.example.app", fingerprints: MINE },
        "ip",
        cfg,
      ),
    ).toThrow(/fingerprint/);
    const tooMany = Array.from(
      { length: ANDROID_FINGERPRINTS_PER_APP + 1 },
      (_, i) => i.toString(16).padStart(64, "0"),
    );
    expect(() =>
      registerAndroidApp(
        db,
        { packageName: "com.example.app", fingerprints: tooMany },
        "ip",
        cfg,
      ),
    ).toThrow(/at most/);
  });

  it("should rate limit new registrations per IP per day", () => {
    for (let i = 0; i < 5; i++)
      registerAndroidApp(
        db,
        { packageName: `com.example.app${i}`, fingerprints: [MINE] },
        "9.9.9.9",
        cfg,
      );
    expect(() =>
      registerAndroidApp(
        db,
        { packageName: "com.example.app6", fingerprints: [MINE] },
        "9.9.9.9",
        cfg,
      ),
    ).toThrow(/per day/);
    registerAndroidApp(
      db,
      { packageName: "com.example.app6", fingerprints: [MINE] },
      "8.8.8.8",
      cfg,
    );
  });

  it("should enforce the global cap of active entries", () => {
    const insert = db.prepare(
      "INSERT INTO android_apps (package_name, fingerprint, token_hash, created_at, expires_at) VALUES (?, ?, 'h', ?, ?)",
    );
    for (let i = 0; i < 500; i++)
      insert.run(`com.cap.app${i}`, MINE, t, t + DAY);
    expect(() =>
      registerAndroidApp(
        db,
        { packageName: "com.example.app", fingerprints: [MINE] },
        "7.7.7.7",
        cfg,
      ),
    ).toThrow(/full/);
  });

  it("should delete only with the token", () => {
    const res = registerAndroidApp(
      db,
      { packageName: "com.example.app", fingerprints: [MINE] },
      "ip",
      cfg,
    );
    expect(() =>
      deleteAndroidApp(db, { packageName: "com.example.app", token: "wrong" }),
    ).toThrow(/wrong token/);
    deleteAndroidApp(db, {
      packageName: "com.example.app",
      token: res.deleteToken,
    });
    expect(listAndroidApps(db)).toEqual([]);
  });

  it("should verify a registration from a registered app's origin", async () => {
    registerAndroidApp(
      db,
      { packageName: "com.example.app", fingerprints: [MINE] },
      "ip",
      cfg,
    );
    const auth = new SoftAuthenticator(androidOrigin(MINE));
    const options = await registrationOptions(db, {}, cfg);

    await expect(
      verifyRegistration(db, auth.register(options) as never, cfg),
    ).rejects.toThrow(/registration rejected/);
    const verdict = await verifyRegistration(
      db,
      auth.register(await registrationOptions(db, {}, cfg)) as never,
      demoConfigFor(db, cfg),
    );
    expect(verdict.verified).toBe(true);
    expect(verdict.origin).toBe(androidOrigin(MINE));
  });
});

describe("Android registration routes", () => {
  beforeEach(() => setDb(db));
  afterEach(() => setDb(undefined));

  it("should answer 201 for a new entry and 200 for a repeat, and serve it in assetlinks and server-config", async () => {
    const { POST } = await import("@/app/api/demo/android-apps/route");
    const post = () =>
      POST(
        new Request("http://localhost/api/demo/android-apps", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({
            packageName: "com.example.app",
            fingerprints: [MINE],
          }),
        }),
      );
    expect((await post()).status).toBe(201);
    const repeat = await post();
    expect(repeat.status).toBe(200);
    expect((await repeat.json()).created).toBe(0);

    const links = await (
      await import("@/app/api/well-known/assetlinks/route")
    ).GET();
    expect(statements(await links.text()).map(([p]) => p)).toContain(
      "com.example.app",
    );
    const config = await (await import("@/app/api/demo/server-config/route"))
      .GET()
      .json();
    expect(config.origins).toContain(androidOrigin(MINE));
  });
});
