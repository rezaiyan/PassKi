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
import { isBundleId, isTeamId } from "@/lib/validate";
import { demoConfig, type DemoConfig } from "./config";
import { DemoError } from "./rp";

export const IOS_APP_TTL = 7 * DAY;
export const IOS_PER_IP_PER_DAY = Number(
  process.env.PASSKI_IOS_PER_IP_PER_DAY || 5,
);
export const IOS_MAX_ACTIVE = Number(process.env.PASSKI_IOS_MAX_ACTIVE || 500);

export interface IosAppEntry {
  appId: string;
  expiresAt: string;
}

export function listIosApps(db: Db): IosAppEntry[] {
  purgeExpired(db, "ios_apps");
  const rows = db
    .prepare(
      "SELECT app_id, expires_at FROM ios_apps WHERE expires_at > ? ORDER BY created_at",
    )
    .all(clock.now()) as {
    app_id: string;
    expires_at: number;
  }[];
  return rows.map((r) => ({
    appId: r.app_id,
    expiresAt: new Date(r.expires_at).toISOString(),
  }));
}

/** Base app IDs from env + non-expired self-service entries. */
export function demoAasa(db: Db, cfg: DemoConfig = demoConfig()): string {
  const apps = [
    ...new Set([...cfg.appleAppIds, ...listIosApps(db).map((e) => e.appId)]),
  ];
  return JSON.stringify({ webcredentials: { apps } }, null, 2) + "\n";
}

export function registerIosApp(
  db: Db,
  body: { teamId?: unknown; bundleId?: unknown },
  ip: string,
  cfg: DemoConfig = demoConfig(),
) {
  const teamId = typeof body.teamId === "string" ? body.teamId.trim() : "";
  const bundleId =
    typeof body.bundleId === "string" ? body.bundleId.trim() : "";
  if (!isTeamId(teamId))
    throw new DemoError(
      "Team ID must be exactly 10 uppercase letters or digits",
    );
  if (!isBundleId(bundleId))
    throw new DemoError("bundle ID must be reverse-DNS, e.g. com.example.app");
  const appId = `${teamId}.${bundleId}`;
  if (cfg.appleAppIds.includes(appId))
    throw new DemoError(`${appId} is already permanently listed`, 409);
  purgeExpired(db, "ios_apps");
  const existing = db
    .prepare("SELECT expires_at FROM ios_apps WHERE app_id = ?")
    .get(appId) as { expires_at: number } | undefined;
  if (existing)
    throw new DemoError(
      `${appId} is already registered until ${new Date(existing.expires_at).toISOString()}`,
      409,
    );
  const { n } = db.prepare("SELECT COUNT(*) AS n FROM ios_apps").get() as {
    n: number;
  };
  if (n >= IOS_MAX_ACTIVE)
    throw new DemoError("the demo is full right now; try again later", 503);
  if (!rateLimit(db, "ios-register", ipKey(ip), IOS_PER_IP_PER_DAY, DAY))
    throw new DemoError(
      `limit is ${IOS_PER_IP_PER_DAY} registrations per day`,
      429,
    );
  const token = randomBytes(24).toString("base64url");
  const now = clock.now();
  db.prepare(
    "INSERT INTO ios_apps (app_id, team_id, bundle_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
  ).run(appId, teamId, bundleId, sha256(token), now, now + IOS_APP_TTL);
  return {
    appId,
    expiresAt: new Date(now + IOS_APP_TTL).toISOString(),
    deleteToken: token,
    entitlement: `webcredentials:${cfg.rpId}?mode=developer`,
    note: "Use ?mode=developer in your entitlements and enable Settings > Developer > Associated Domains Development; the device then fetches the AASA directly instead of Apple's CDN.",
  };
}

export function deleteIosApp(
  db: Db,
  body: { appId?: unknown; token?: unknown },
): void {
  const appId = typeof body.appId === "string" ? body.appId : "";
  const token = typeof body.token === "string" ? body.token : "";
  const row = db
    .prepare("SELECT token_hash FROM ios_apps WHERE app_id = ?")
    .get(appId) as { token_hash: string } | undefined;
  const a = Buffer.from(sha256(token));
  if (
    !row ||
    a.length !== row.token_hash.length ||
    !timingSafeEqual(a, Buffer.from(row.token_hash))
  )
    throw new DemoError("unknown app ID or wrong token", 403);
  db.prepare("DELETE FROM ios_apps WHERE app_id = ?").run(appId);
}
