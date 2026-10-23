import {
  createHash,
  createHmac,
  randomBytes,
  timingSafeEqual,
} from "node:crypto";
import { check, docsPath, isCheckId } from "@/lib/catalog";
import { clock, DAY, purgeExpired, type Db } from "@/lib/db";
import { summarize } from "@/lib/checks/run";
import type {
  CheckResult,
  CheckStatus,
  VerifyReport,
} from "@/lib/checks/types";
import { validateDomain } from "@/lib/net/ssrf";

export const REPORT_TTL = 7 * DAY;
/** A stored report is at most this many bytes of JSON; real verifier reports are a few KB. */
export const MAX_REPORT_BYTES = 64 * 1024;
/** Shared reports kept at once (at most MAX_ACTIVE_REPORTS × MAX_REPORT_BYTES on disk). */
export const MAX_ACTIVE_REPORTS = Number(
  process.env.PASSKI_MAX_REPORTS || 2000,
);
const MAX_RESULTS = 100;
const STATUSES: CheckStatus[] = ["pass", "warn", "fail", "skip"];

export class ReportError extends Error {}

const text = (v: unknown, max: number): string | undefined =>
  typeof v === "string" ? v.slice(0, max) : undefined;

/**
 * Rebuilds a report from untrusted JSON, keeping only known fields: catalog IDs, status enum, capped strings.
 * Title, severity and docs come from the catalog, never from the client.
 */
export function sanitizeReport(raw: unknown): VerifyReport {
  const r = (raw ?? {}) as Record<string, unknown>;
  let rpId: string;
  try {
    rpId = validateDomain(String(r.rpId ?? ""));
  } catch {
    throw new ReportError("report.rpId is not a valid domain");
  }
  if (
    !Array.isArray(r.results) ||
    r.results.length === 0 ||
    r.results.length > MAX_RESULTS
  )
    throw new ReportError("report.results must be a non-empty list");
  const results: CheckResult[] = r.results.map((x) => {
    const e = (x ?? {}) as Record<string, unknown>;
    const id = String(e.id);
    if (!isCheckId(id)) throw new ReportError(`unknown check ID ${id}`);
    const status = e.status as CheckStatus;
    if (!STATUSES.includes(status))
      throw new ReportError(`invalid status for ${id}`);
    const c = check(id);
    const out: CheckResult = {
      id,
      title: c.title,
      severity: c.severity,
      status,
      message: text(e.message, 2000) ?? "",
      docs: docsPath(id),
    };
    const subject = text(e.subject, 200);
    const fix = text(e.fix, 2000);
    const hint = text(e.hint, 4000);
    if (subject) out.subject = subject;
    if (fix) out.fix = fix;
    if (hint) out.hint = hint;
    return out;
  });
  const checkedAt =
    typeof r.checkedAt === "string" && !Number.isNaN(Date.parse(r.checkedAt))
      ? new Date(r.checkedAt).toISOString()
      : new Date(clock.now()).toISOString();
  const input = { rpId, android: [], ios: [] } as VerifyReport["input"];
  const rin = (r.input ?? {}) as Record<string, unknown>;
  if (Array.isArray(rin.android))
    input.android = rin.android
      .slice(0, 10)
      .map((a: Record<string, unknown>) => ({
        packageName: text(a?.packageName, 255) ?? "",
        fingerprints: Array.isArray(a?.fingerprints)
          ? a.fingerprints.slice(0, 20).map((f: unknown) => text(f, 100) ?? "")
          : [],
      }));
  if (Array.isArray(rin.ios))
    input.ios = rin.ios.slice(0, 10).map((a: Record<string, unknown>) => ({
      teamId: text(a?.teamId, 10),
      bundleId: text(a?.bundleId, 155) ?? "",
    }));
  return { rpId, checkedAt, input, results, summary: summarize(results) };
}

let processKey: Buffer | undefined;

/** HMAC key for report signatures: from PASSKI_REPORT_SECRET, else the IP salt, else random per process. */
function reportKey(): Buffer {
  const secret = process.env.PASSKI_REPORT_SECRET || process.env.PASSKI_IP_SALT;
  if (secret)
    return createHash("sha256").update(`passki-report|${secret}`).digest();
  processKey ??= randomBytes(32);
  return processKey;
}

const mac = (report: VerifyReport) =>
  createHmac("sha256", reportKey())
    .update(JSON.stringify(report))
    .digest("base64url");

/**
 * Signs what /api/verify returns, so only reports this server produced can be shared under its domain.
 * The signature covers the sanitized form, which is what {@link saveReport} rebuilds and checks.
 */
export function signReport(raw: unknown): string {
  return mac(sanitizeReport(raw));
}

export function saveReport(
  db: Db,
  raw: unknown,
): { id: string; expiresAt: string } {
  const report = sanitizeReport(raw);
  const signature = (raw as { signature?: unknown } | null)?.signature;
  const expected = Buffer.from(mac(report));
  const given = Buffer.from(typeof signature === "string" ? signature : "");
  if (given.length !== expected.length || !timingSafeEqual(given, expected))
    throw new ReportError(
      "only reports from this site's verifier can be shared; run the check again",
    );
  const json = JSON.stringify(report);
  if (Buffer.byteLength(json) > MAX_REPORT_BYTES)
    throw new ReportError("report too large to share");
  purgeExpired(db, "reports");
  const { n } = db.prepare("SELECT COUNT(*) AS n FROM reports").get() as {
    n: number;
  };
  if (n >= MAX_ACTIVE_REPORTS)
    throw new ReportError("report storage is full right now; try again later");
  const id = randomBytes(12).toString("base64url");
  const now = clock.now();
  db.prepare(
    "INSERT INTO reports (id, json, created_at, expires_at) VALUES (?, ?, ?, ?)",
  ).run(id, json, now, now + REPORT_TTL);
  return { id, expiresAt: new Date(now + REPORT_TTL).toISOString() };
}

export function loadReport(
  db: Db,
  id: string,
): (VerifyReport & { expiresAt: string }) | null {
  if (!/^[A-Za-z0-9_-]{16}$/.test(id)) return null;
  purgeExpired(db, "reports");
  const row = db
    .prepare(
      "SELECT json, expires_at FROM reports WHERE id = ? AND expires_at > ?",
    )
    .get(id, clock.now()) as { json: string; expires_at: number } | undefined;
  return row
    ? {
        ...JSON.parse(row.json),
        expiresAt: new Date(row.expires_at).toISOString(),
      }
    : null;
}
