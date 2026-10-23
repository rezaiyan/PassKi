import type { Severity } from "@/lib/catalog";

/** One HTTP response as seen by the checks. Redirects are never followed; they are reported. */
export interface Fetched {
  url: string;
  status: number;
  contentType: string | null;
  body: string;
  /** Location header of a 3xx response. */
  redirectedTo?: string | null;
  /** Network/validation error; status is 0 then. */
  error?: string | null;
  /** The body was cut at the size cap. */
  truncated?: boolean;
  /** Body size in bytes as read (≤ cap). */
  bytes?: number;
}

export type CheckStatus = "pass" | "warn" | "fail" | "skip";

export interface CheckResult {
  id: string;
  title: string;
  severity: Severity;
  status: CheckStatus;
  /** What the result is about (a package, an app ID), when one check ID runs several times. */
  subject?: string;
  message: string;
  /** Catalog fix with placeholders filled, plus a concrete hint when we have one. */
  fix?: string;
  hint?: string;
  docs: string;
}

export interface AndroidExpectation {
  packageName: string;
  /** Canonical uppercase colon-hex SHA-256 fingerprints. */
  fingerprints: string[];
}

export interface VerifyInput {
  rpId: string;
  android: AndroidExpectation[];
  /** `TEAMID.bundle.id` entries; `teamId` may be omitted when only a bundle ID is known. */
  ios: { teamId?: string; bundleId: string }[];
}

export interface VerifyReport {
  rpId: string;
  checkedAt: string;
  input: VerifyInput;
  results: CheckResult[];
  summary: { pass: number; warn: number; fail: number; skip: number };
  /** Set by /api/verify; /api/reports only stores reports that carry a valid one. */
  signature?: string;
}
