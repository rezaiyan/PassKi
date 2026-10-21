import { NextResponse } from "next/server";
import { getDb, ipKey, MINUTE, rateLimit } from "@/lib/db";
import { DemoError } from "@/lib/demo/rp";
import { ReportError } from "@/lib/reports";
import { InputError } from "@/lib/validate";

export const NO_STORE = { "cache-control": "no-store" };

export function json(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return NextResponse.json(body, { status, headers: { ...NO_STORE, ...headers } });
}

/** Maps known input errors to 4xx; anything else is a 500 without internals. */
export function errorResponse(e: unknown) {
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

export async function readJson(req: Request, maxBytes = 64 * 1024): Promise<Record<string, unknown>> {
  const text = await req.text();
  if (Buffer.byteLength(text) > maxBytes) throw new InputError("request body too large");
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
