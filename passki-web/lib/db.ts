import Database from "better-sqlite3";
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { createHash } from "node:crypto";

export type Db = Database.Database;

/** Overridable clock (ms since epoch) so TTL behaviour is testable. */
export const clock = { now: () => Date.now() };

export const MINUTE = 60_000;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

const TTL_TABLES = [
  "challenges",
  "demo_users",
  "credentials",
  "reports",
  "ios_apps",
  "fixtures",
] as const;
type TtlTable = (typeof TTL_TABLES)[number];

const SCHEMA = `
CREATE TABLE IF NOT EXISTS challenges (
  challenge TEXT PRIMARY KEY,
  kind TEXT NOT NULL CHECK (kind IN ('reg', 'auth')),
  user_id TEXT,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS demo_users (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS credentials (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  public_key BLOB NOT NULL,
  counter INTEGER NOT NULL,
  transports TEXT,
  aaguid TEXT,
  backup_eligible INTEGER NOT NULL,
  backed_up INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS credentials_user ON credentials(user_id);
CREATE TABLE IF NOT EXISTS reports (
  id TEXT PRIMARY KEY,
  json TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS ios_apps (
  app_id TEXT PRIMARY KEY,
  team_id TEXT NOT NULL,
  bundle_id TEXT NOT NULL,
  token_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS fixtures (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  platform TEXT NOT NULL,
  json TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS fixtures_platform ON fixtures(platform, id);
CREATE TABLE IF NOT EXISTS rate_events (
  bucket TEXT NOT NULL,
  key TEXT NOT NULL,
  at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS rate_events_key ON rate_events(bucket, key, at);
${TTL_TABLES.map((t) => `CREATE INDEX IF NOT EXISTS ${t}_expiry ON ${t}(expires_at);`).join("\n")}
`;

export function openDb(path: string): Db {
  if (path !== ":memory:")
    mkdirSync(dirname(resolve(path)), { recursive: true });
  const db = new Database(path);
  db.pragma("journal_mode = WAL");
  db.pragma("busy_timeout = 5000");
  db.exec(SCHEMA);
  purgeAll(db);
  return db;
}

/** Deletes expired rows from [table]; called lazily on every access. */
export function purgeExpired(db: Db, table: TtlTable): number {
  return db
    .prepare(`DELETE FROM ${table} WHERE expires_at <= ?`)
    .run(clock.now()).changes;
}

/** Startup cleanup: every TTL table plus rate events older than two days. */
export function purgeAll(db: Db): number {
  let n = 0;
  for (const t of TTL_TABLES) n += purgeExpired(db, t);
  n += db
    .prepare("DELETE FROM rate_events WHERE at <= ?")
    .run(clock.now() - 2 * DAY).changes;
  return n;
}

const globalForDb = globalThis as unknown as { passkiDb?: Db };

export function dbPath(): string {
  return process.env.PASSKI_DB_PATH || "./data/passki.db";
}

/** Process-wide connection (survives Next.js dev hot reloads). */
export function getDb(): Db {
  globalForDb.passkiDb ??= openDb(dbPath());
  return globalForDb.passkiDb;
}

/** Tests swap in an in-memory database. */
export function setDb(db: Db | undefined): void {
  globalForDb.passkiDb = db;
}

/**
 * Sliding-window limiter: records an event and returns true when fewer than [limit] events for
 * ([bucket], [key]) happened in the last [windowMs]; returns false (and records nothing) otherwise.
 */
export function rateLimit(
  db: Db,
  bucket: string,
  key: string,
  limit: number,
  windowMs: number,
): boolean {
  const now = clock.now();
  db.prepare("DELETE FROM rate_events WHERE at <= ?").run(now - 2 * DAY);
  const { n } = db
    .prepare(
      "SELECT COUNT(*) AS n FROM rate_events WHERE bucket = ? AND key = ? AND at > ?",
    )
    .get(bucket, key, now - windowMs) as { n: number };
  if (n >= limit) return false;
  db.prepare("INSERT INTO rate_events (bucket, key, at) VALUES (?, ?, ?)").run(
    bucket,
    key,
    now,
  );
  return true;
}

/** Client IPs are stored only as salted hashes. */
export function ipKey(ip: string): string {
  return createHash("sha256")
    .update((process.env.PASSKI_IP_SALT || "passki") + "|" + ip)
    .digest("base64url")
    .slice(0, 22);
}

export function sha256(value: string): string {
  return createHash("sha256").update(value).digest("base64url");
}
