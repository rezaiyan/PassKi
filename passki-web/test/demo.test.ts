import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  clock,
  DAY,
  MINUTE,
  openDb,
  purgeAll,
  rateLimit,
  type Db,
} from "@/lib/db";
import { demoConfig } from "@/lib/demo/config";
import {
  deleteIosApp,
  demoAasa,
  listIosApps,
  registerIosApp,
} from "@/lib/demo/iosApps";
import {
  authenticationOptions,
  captureFixture,
  DemoError,
  FIXTURES_PER_PLATFORM,
  listFixtures,
  registrationOptions,
  verifyAuthentication,
  verifyRegistration,
} from "@/lib/demo/rp";
import { androidOrigin } from "@/lib/fingerprints";
import { loadReport, saveReport } from "@/lib/reports";
import { FP } from "./helpers";
import { SoftAuthenticator } from "./softAuthenticator";

const cfg = demoConfig({
  PASSKI_RP_ID: "passki.alirezaiyan.com",
  PASSKI_ANDROID_FINGERPRINTS: FP,
});
let db: Db;
let t = Date.parse("2026-10-21T12:00:00Z");
const realNow = clock.now;

beforeEach(() => {
  t = Date.parse("2026-10-21T12:00:00Z");
  clock.now = () => t;
  db = openDb(":memory:");
});
afterEach(() => {
  clock.now = realNow;
  db.close();
});

describe("demo config", () => {
  it("should allow https://<rpId> and the android apk-key-hash origin", () => {
    expect(cfg.origins).toEqual([
      "https://passki.alirezaiyan.com",
      androidOrigin(FP),
    ]);
    expect(demoConfig({}).rpId).toBe("passki.alirezaiyan.com");
  });
});

describe("demo relying party", () => {
  for (const origin of ["https://passki.alirezaiyan.com", androidOrigin(FP)]) {
    it(`should register and log in end to end from ${origin.slice(0, 22)}`, async () => {
      const auth = new SoftAuthenticator(origin);
      const regOptions = await registrationOptions(
        db,
        { username: "alice" },
        cfg,
      );
      expect(regOptions.rp.id).toBe("passki.alirezaiyan.com");
      expect(
        Buffer.from(regOptions.challenge, "base64url").length,
      ).toBeGreaterThanOrEqual(16);
      const reg = await verifyRegistration(
        db,
        auth.register(regOptions) as never,
        cfg,
      );
      expect(reg).toMatchObject({
        verified: true,
        signCount: 0,
        userVerified: true,
        backupEligible: true,
        userHandle: regOptions.user.id,
        origin,
      });
      expect(reg.aaguid).toBeUndefined();

      const loginOptions = await authenticationOptions(
        db,
        { userHandle: reg.userHandle },
        cfg,
      );
      expect(loginOptions.allowCredentials?.map((c) => c.id)).toEqual([
        reg.credentialId,
      ]);
      const login = await verifyAuthentication(
        db,
        auth.assert(loginOptions) as never,
        cfg,
      );
      expect(login).toMatchObject({
        verified: true,
        signCount: 1,
        credentialId: reg.credentialId,
        userHandle: reg.userHandle,
      });

      const discoverable = await authenticationOptions(db, {}, cfg);
      expect(
        (
          await verifyAuthentication(
            db,
            auth.assert(discoverable) as never,
            cfg,
          )
        ).signCount,
      ).toBe(2);
    });
  }

  it("should reject a reused challenge (single use)", async () => {
    const auth = new SoftAuthenticator("https://passki.alirezaiyan.com");
    const regOptions = await registrationOptions(db, {}, cfg);
    const response = auth.register(regOptions);
    await verifyRegistration(db, response as never, cfg);
    await expect(
      verifyRegistration(db, response as never, cfg),
    ).rejects.toThrow(/already used/);
    const loginOptions = await authenticationOptions(db, {}, cfg);
    const assertion = auth.assert(loginOptions);
    await verifyAuthentication(db, assertion as never, cfg);
    await expect(
      verifyAuthentication(db, assertion as never, cfg),
    ).rejects.toThrow(/already used/);
  });

  it("should reject an expired challenge (5 min)", async () => {
    const auth = new SoftAuthenticator("https://passki.alirezaiyan.com");
    const regOptions = await registrationOptions(db, {}, cfg);
    t += 5 * MINUTE + 1;
    await expect(
      verifyRegistration(db, auth.register(regOptions) as never, cfg),
    ).rejects.toThrow(DemoError);
  });

  it("should reject an origin that isn't allowed", async () => {
    const auth = new SoftAuthenticator("https://evil.example.com");
    const regOptions = await registrationOptions(db, {}, cfg);
    await expect(
      verifyRegistration(db, auth.register(regOptions) as never, cfg),
    ).rejects.toThrow(/registration rejected/);
  });

  it("should purge demo users and credentials after 24 h", async () => {
    const auth = new SoftAuthenticator("https://passki.alirezaiyan.com");
    const reg = await verifyRegistration(
      db,
      auth.register(await registrationOptions(db, {}, cfg)) as never,
      cfg,
    );
    t += DAY + 1;
    await expect(
      authenticationOptions(db, { userHandle: reg.userHandle }, cfg),
    ).rejects.toThrow(/unknown or expired/);
    expect(
      (db.prepare("SELECT COUNT(*) n FROM credentials").get() as { n: number })
        .n,
    ).toBe(0);
    purgeAll(db);
    expect(
      (db.prepare("SELECT COUNT(*) n FROM demo_users").get() as { n: number })
        .n,
    ).toBe(0);
  });

  it("should keep only the last N fixtures per platform for 7 days", () => {
    for (let i = 0; i < FIXTURES_PER_PLATFORM + 5; i++)
      captureFixture(db, "android", { i });
    captureFixture(db, "ios", { i: 0 });
    expect(listFixtures(db, "android")).toHaveLength(FIXTURES_PER_PLATFORM);
    expect(listFixtures(db, "android")[0].i).toBe(FIXTURES_PER_PLATFORM + 4);
    expect(() => captureFixture(db, "windows", {})).toThrow(/platform/);
    t += 7 * DAY + 1;
    expect(listFixtures(db)).toHaveLength(0);
  });
});

describe("self-service iOS registration", () => {
  it("should add an entry to the demo AASA for 7 days and allow deleting it with the token", () => {
    const res = registerIosApp(
      db,
      { teamId: "ABCDE12345", bundleId: "com.example.app" },
      "1.2.3.4",
      cfg,
    );
    expect(res.entitlement).toBe(
      "webcredentials:passki.alirezaiyan.com?mode=developer",
    );
    expect(JSON.parse(demoAasa(db, cfg)).webcredentials.apps).toEqual([
      "VFCFJC7Y5J.com.alirezaiyan.passki.PassKi",
      "ABCDE12345.com.example.app",
    ]);
    expect(() =>
      deleteIosApp(db, { appId: res.appId, token: "wrong" }),
    ).toThrow(/wrong token/);
    deleteIosApp(db, { appId: res.appId, token: res.deleteToken });
    expect(listIosApps(db)).toEqual([]);
  });

  it("should expire entries after 7 days", () => {
    registerIosApp(
      db,
      { teamId: "ABCDE12345", bundleId: "com.example.app" },
      "1.2.3.4",
      cfg,
    );
    t += 7 * DAY + 1;
    expect(JSON.parse(demoAasa(db, cfg)).webcredentials.apps).toEqual([
      "VFCFJC7Y5J.com.alirezaiyan.passki.PassKi",
    ]);
  });

  it("should validate input and reject duplicates", () => {
    expect(() =>
      registerIosApp(
        db,
        { teamId: "abcde12345", bundleId: "com.example.app" },
        "ip",
        cfg,
      ),
    ).toThrow(/Team ID/);
    expect(() =>
      registerIosApp(
        db,
        { teamId: "ABCDE12345", bundleId: "not a bundle" },
        "ip",
        cfg,
      ),
    ).toThrow(/bundle ID/);
    registerIosApp(
      db,
      { teamId: "ABCDE12345", bundleId: "com.example.app" },
      "ip",
      cfg,
    );
    expect(() =>
      registerIosApp(
        db,
        { teamId: "ABCDE12345", bundleId: "com.example.app" },
        "ip2",
        cfg,
      ),
    ).toThrow(/already registered/);
  });

  it("should rate limit 5 registrations per IP per day", () => {
    for (let i = 0; i < 5; i++)
      registerIosApp(
        db,
        { teamId: "ABCDE12345", bundleId: `com.example.app${i}` },
        "9.9.9.9",
        cfg,
      );
    expect(() =>
      registerIosApp(
        db,
        { teamId: "ABCDE12345", bundleId: "com.example.app6" },
        "9.9.9.9",
        cfg,
      ),
    ).toThrow(/per day/);
    registerIosApp(
      db,
      { teamId: "ABCDE12345", bundleId: "com.example.other" },
      "8.8.8.8",
      cfg,
    );
    t += DAY + 1;
    registerIosApp(
      db,
      { teamId: "ABCDE12345", bundleId: "com.example.app6" },
      "9.9.9.9",
      cfg,
    );
  });

  it("should enforce the global cap of active entries", () => {
    const insert = db.prepare(
      "INSERT INTO ios_apps (app_id, team_id, bundle_id, token_hash, created_at, expires_at) VALUES (?, 'T', 'b', 'h', ?, ?)",
    );
    for (let i = 0; i < 500; i++)
      insert.run(`ABCDE12345.cap.app${i}`, t, t + DAY);
    expect(() =>
      registerIosApp(
        db,
        { teamId: "ABCDE12345", bundleId: "com.example.app" },
        "7.7.7.7",
        cfg,
      ),
    ).toThrow(/full/);
  });
});

describe("rate limiter and reports", () => {
  it("should slide the window", () => {
    expect([1, 2, 3].map(() => rateLimit(db, "b", "k", 2, MINUTE))).toEqual([
      true,
      true,
      false,
    ]);
    t += MINUTE + 1;
    expect(rateLimit(db, "b", "k", 2, MINUTE)).toBe(true);
  });

  it("should store sanitized reports for 7 days", () => {
    const { id } = saveReport(db, {
      rpId: "alirezaiyan.com",
      results: [
        {
          id: "AND-01",
          status: "pass",
          message: "ok",
          title: "<script>forged</script>",
          severity: "nope",
        },
      ],
    });
    const report = loadReport(db, id)!;
    expect(report.results[0]).toMatchObject({
      id: "AND-01",
      title: "assetlinks.json reachable",
      severity: "error",
      docs: "/checks/AND-01",
    });
    expect(report.summary.pass).toBe(1);
    expect(() =>
      saveReport(db, {
        rpId: "alirezaiyan.com",
        results: [{ id: "XXX-99", status: "pass" }],
      }),
    ).toThrow(/unknown check/);
    expect(() =>
      saveReport(db, {
        rpId: "localhost",
        results: [{ id: "AND-01", status: "pass" }],
      }),
    ).toThrow(/domain/);
    t += 7 * DAY + 1;
    expect(loadReport(db, id)).toBeNull();
  });
});
