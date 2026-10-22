import { NextResponse } from "next/server";
import { getDb, ipKey, MINUTE, rateLimit } from "@/lib/db";
import { DemoError } from "@/lib/demo/rp";
import { ReportError } from "@/lib/reports";
import { InputError } from "@/lib/validate";

export class PayloadTooLargeError extends InputError {
  readonly status = 413;
  constructor() {
    super("request body too large");
  }
}

export const NO_STORE = { "cache-control": "no-store" };

export function json(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return NextResponse.json(body, { status, headers: { ...NO_STORE, ...headers } });
}

/** Maps known input errors to 4xx; anything else is a 500 without internals. */
export function errorResponse(e: unknown) {
  if (e instanceof PayloadTooLargeError) return json({ error: e.message }, 413);
  if (e instanceof DemoError) return json({ error: e.message }, e.status);
  if (e instanceof InputError || e instanceof ReportError || e instanceof SyntaxError) return json({ error: e.message }, 400);
  console.error(e);
  return json({ error: "internal error" }, 500);
}

/**
 * Client IP. Behind Caddy (which sets X-Forwarded-For to the real client unless trusted_proxies is configured),
 * the first X-Forwarded-For entry is the client; locally it falls back to "local".
 */
export function clientIp(req: Request): string {
  const xff = req.headers.get("x-forwarded-for");
  return xff?.split(",")[0].trim() || req.headers.get("x-real-ip") || "local";
}

/** Reads the body as a stream with a hard cap: aborts as soon as `maxBytes` is exceeded (413). */
async function readCapped(req: Request, maxBytes: number): Promise<string> {
  const declared = Number(req.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > maxBytes) throw new PayloadTooLargeError();
  if (!req.body) return "";
  const reader = req.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > maxBytes) {
      await reader.cancel().catch(() => {});
      throw new PayloadTooLargeError();
    }
    chunks.push(value);
  }
  return Buffer.concat(chunks).toString("utf8");
}

export async function readJson(req: Request, maxBytes = 64 * 1024): Promise<Record<string, unknown>> {
  const text = await readCapped(req, maxBytes);
  if (!text.trim()) return {};
  const value = JSON.parse(text);
  if (value === null || typeof value !== "object" || Array.isArray(value)) throw new InputError("expected a JSON object");
  return value as Record<string, unknown>;
}

/** Per-IP limit for endpoints that cost something (outbound fetches, DB writes). Returns a 429 response or null. */
export function limited(req: Request, bucket: string, limit: number, windowMs = MINUTE) {
  if (rateLimit(getDb(), bucket, ipKey(clientIp(req)), limit, windowMs)) return null;
  return json({ error: `rate limit: at most ${limit} requests per ${Math.round(windowMs / MINUTE)} min` }, 429, { "retry-after": String(Math.ceil(windowMs / 1000)) });
}
