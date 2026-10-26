import { check, docsPath, fixText, type FixParams } from "@/lib/catalog";
import type { CheckResult, CheckStatus, Fetched } from "./types";

export const MAX_WELL_KNOWN_BYTES = 1024 * 1024;
export const APPLE_MAX_AASA_BYTES = 128 * 1024;

export interface ResultOptions {
  subject?: string;
  fixParams?: FixParams;
  hint?: string;
}

/**
 * Builds a result for catalog check [id]. A failing check with catalog severity `warning` is reported as
 * `warn`; the catalog fix (placeholders filled) is attached to every non-passing, non-skipped result.
 */
export function result(
  id: string,
  status: CheckStatus,
  message: string,
  options: ResultOptions = {},
): CheckResult {
  const entry = check(id);
  const effective: CheckStatus =
    status === "fail" && entry.severity === "warning" ? "warn" : status;
  const out: CheckResult = {
    id,
    title: entry.title,
    severity: entry.severity,
    status: effective,
    message,
    docs: docsPath(id),
  };
  if (options.subject) out.subject = options.subject;
  if (effective === "fail" || effective === "warn") {
    out.fix = fixText(id, options.fixParams);
    if (options.hint) out.hint = options.hint;
  }
  return out;
}

/** Same semantics as the Kotlin doctor's `HttpResult.fetchProblem()`. */
export function fetchProblem(f: Fetched): string | null {
  if (f.error) return `could not fetch ${f.url}: ${f.error}`;
  if (f.redirectedTo || (f.status >= 300 && f.status <= 399)) {
    return `${f.url} redirects to ${f.redirectedTo || "another location"} (redirects are not followed)`;
  }
  if (f.status !== 200) return `${f.url} returned HTTP ${f.status}`;
  if (f.truncated)
    return `${f.url} is larger than 1 MiB; only the first 1 MiB is read`;
  if (!f.body || !f.body.trim()) return `${f.url} is empty`;
  return null;
}

/**
 * A web page instead of the well-known file (typically a single-page app's catch-all route): the body starts with
 * `<`, or it is served as text/html and isn't JSON. Same rule as the Kotlin doctor's `HttpResult.isWebPage`.
 */
export function isWebPage(f: Fetched): boolean {
  const text = (f.body ?? "").replace(/^[\uFEFF\s]+/, "");
  if (text.startsWith("<")) return true;
  return (
    (f.contentType ?? "").toLowerCase().includes("text/html") &&
    parseJson(text) === undefined
  );
}

export function parseJson(body: string): unknown {
  try {
    return JSON.parse(body);
  } catch {
    return undefined;
  }
}

export function isJsonContentType(contentType: string | null): boolean {
  return (
    contentType != null &&
    contentType.split(";")[0].trim().toLowerCase() === "application/json"
  );
}

export function strings(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((v): v is string => typeof v === "string")
    : [];
}

export function asObject(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

export function plural(count: number, noun: string): string {
  return count === 1 ? `1 ${noun}` : `${count} ${noun}s`;
}

export function byteLength(f: Fetched): number {
  return f.bytes ?? Buffer.byteLength(f.body ?? "", "utf8");
}
