import { randomBytes, timingSafeEqual } from "node:crypto";
import {
  clock,
  DAY,
  ipKey,
  purgeExpired,
  rateLimit,
  sha256,
  type Db,
} from "@/lib/db";
import {
  androidOrigin,
  isPackageName,
  normalizeFingerprint,
} from "@/lib/fingerprints";
import { assetLinks, GET_LOGIN_CREDS } from "@/lib/generator";
import { demoConfig, type DemoConfig } from "./config";
import { DemoError } from "./rp";

export const ANDROID_APP_TTL = 7 * DAY;
export const ANDROID_PER_IP_PER_DAY = Number(
  process.env.PASSKI_ANDROID_PER_IP_PER_DAY || 5,
);
export const ANDROID_MAX_ACTIVE = Number(
  process.env.PASSKI_ANDROID_MAX_ACTIVE || 500,
);
/** Debug, release and Play App Signing keys, plus a couple of spares. */
export const ANDROID_FINGERPRINTS_PER_APP = 5;

export interface AndroidAppEntry {
  packageName: string;
  fingerprint: string;
  expiresAt: string;
}

export function listAndroidApps(db: Db): AndroidAppEntry[] {
  purgeExpired(db, "android_apps");
  const rows = db
    .prepare(
      "SELECT package_name, fingerprint, expires_at FROM android_apps WHERE expires_at > ? ORDER BY created_at, package_name, fingerprint",
    )
    .all(clock.now()) as {
    package_name: string;
    fingerprint: string;
    expires_at: number;
  }[];
  return rows.map((r) => ({
    packageName: r.package_name,
    fingerprint: r.fingerprint,
    expiresAt: new Date(r.expires_at).toISOString(),
  }));
}

/**
 * The demo package from env + non-expired self-service entries, one statement per package. Only
 * `get_login_creds`: anyone can list an app here (and the sample's debug key is public), so granting
 * `handle_all_urls` would let any app open links to the demo domain as a verified App Link.
 */
export function demoAssetLinks(db: Db, cfg: DemoConfig = demoConfig()): string {
  const byPackage = new Map<string, Set<string>>([
    [cfg.androidPackage, new Set(cfg.androidFingerprints)],
  ]);
  for (const e of listAndroidApps(db)) {
    if (!byPackage.has(e.packageName)) byPackage.set(e.packageName, new Set());
    byPackage.get(e.packageName)!.add(e.fingerprint);
  }
  return assetLinks(
    [...byPackage].map(([packageName, fps]) => ({
      packageName,
      fingerprints: [...fps],
    })),
    [GET_LOGIN_CREDS],
  );
}

/** [cfg] with the origins of every registered Android signing key added, so their responses verify. */
export function demoConfigFor(
  db: Db,
  cfg: DemoConfig = demoConfig(),
): DemoConfig {
  const registered = listAndroidApps(db).map((e) =>
    androidOrigin(e.fingerprint),
  );
  return { ...cfg, origins: [...new Set([...cfg.origins, ...registered])] };
}

/**
 * POST body {packageName, fingerprints[]}: lists the package with those signing keys in the demo assetlinks.json
 * and accepts their `android:apk-key-hash:` origins for 7 days. Pairs that are already listed are reported, not
 * renewed, and cost no rate limit, so re-running `passkiSetup` is safe.
 */
export interface AndroidRegistration {
  packageName: string;
  fingerprints: string[];
  /** The `android:apk-key-hash:` origins the demo RP now accepts for these keys. */
  origins: string[];
  /** How many package/fingerprint pairs were newly listed (0: all were already listed). */
  created: number;
  expiresAt: string;
  /** Only when something new was listed: deletes those entries (DELETE /api/demo/android-apps). */
  deleteToken?: string;
  note?: string;
}

export function registerAndroidApp(
  db: Db,
  body: { packageName?: unknown; fingerprints?: unknown },
  ip: string,
  cfg: DemoConfig = demoConfig(),
): AndroidRegistration {
  const packageName =
    typeof body.packageName === "string" ? body.packageName.trim() : "";
  if (!isPackageName(packageName))
    throw new DemoError("package name must look like com.example.app");
  if (!Array.isArray(body.fingerprints) || body.fingerprints.length === 0)
    throw new DemoError(
      "fingerprints must be a non-empty list of SHA-256 signing fingerprints",
    );
  if (body.fingerprints.length > ANDROID_FINGERPRINTS_PER_APP)
    throw new DemoError(
      `at most ${ANDROID_FINGERPRINTS_PER_APP} fingerprints per package`,
    );
  const fingerprints = [
    ...new Set(
      body.fingerprints.map((f) => {
        const fp = typeof f === "string" ? normalizeFingerprint(f) : null;
        if (!fp) throw new DemoError(`not a SHA-256 fingerprint: ${String(f)}`);
        return fp;
      }),
    ),
  ];
  if (
    packageName === cfg.androidPackage &&
    fingerprints.every((fp) => cfg.androidFingerprints.includes(fp))
  )
    throw new DemoError(
      `${packageName} with this key is already permanently listed`,
      409,
    );

  purgeExpired(db, "android_apps");
  const existing = new Map(
    (
      db
        .prepare(
          "SELECT fingerprint, expires_at FROM android_apps WHERE package_name = ?",
        )
        .all(packageName) as { fingerprint: string; expires_at: number }[]
    ).map((r) => [r.fingerprint, r.expires_at]),
  );
  const fresh = fingerprints.filter(
    (fp) =>
      !existing.has(fp) &&
      !(
        packageName === cfg.androidPackage &&
        cfg.androidFingerprints.includes(fp)
      ),
  );
  const result = {
    packageName,
    fingerprints,
    origins: fingerprints.map(androidOrigin),
    created: fresh.length,
  };
  if (fresh.length === 0) {
    const expiresAt = Math.min(
      ...fingerprints.map((fp) => existing.get(fp) ?? Infinity),
    );
    return { ...result, expiresAt: new Date(expiresAt).toISOString() };
  }

  const { n } = db.prepare("SELECT COUNT(*) AS n FROM android_apps").get() as {
    n: number;
  };
  if (n + fresh.length > ANDROID_MAX_ACTIVE)
    throw new DemoError("the demo is full right now; try again later", 503);
  if (
    !rateLimit(db, "android-register", ipKey(ip), ANDROID_PER_IP_PER_DAY, DAY)
  )
    throw new DemoError(
      `limit is ${ANDROID_PER_IP_PER_DAY} registrations per day`,
      429,
    );
  const token = randomBytes(24).toString("base64url");
  const now = clock.now();
  const insert = db.prepare(
    "INSERT INTO android_apps (package_name, fingerprint, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
  );
  db.transaction(() => {
    for (const fp of fresh)
      insert.run(packageName, fp, sha256(token), now, now + ANDROID_APP_TTL);
  })();
  return {
    ...result,
    expiresAt: new Date(now + ANDROID_APP_TTL).toISOString(),
    deleteToken: token,
    note: "Google caches asset links: a new app can take a few minutes before passkeys work. passkiCheck AND-04 shows when Google lists it.",
  };
}

/** DELETE body {packageName, token}: removes the entries created with that token. */
export function deleteAndroidApp(
  db: Db,
  body: { packageName?: unknown; token?: unknown },
): void {
  const packageName =
    typeof body.packageName === "string" ? body.packageName : "";
  const token = typeof body.token === "string" ? body.token : "";
  const hashes = (
    db
      .prepare(
        "SELECT DISTINCT token_hash FROM android_apps WHERE package_name = ?",
      )
      .all(packageName) as { token_hash: string }[]
  ).map((r) => r.token_hash);
  const a = Buffer.from(sha256(token));
  const match = hashes.find(
    (h) => a.length === h.length && timingSafeEqual(a, Buffer.from(h)),
  );
  if (!match) throw new DemoError("unknown package or wrong token", 403);
  db.prepare(
    "DELETE FROM android_apps WHERE package_name = ? AND token_hash = ?",
  ).run(packageName, match);
}
