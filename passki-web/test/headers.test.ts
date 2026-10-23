import { describe, expect, it } from "vitest";
import nextConfig from "../next.config";

describe("security headers", () => {
  it("should send HSTS, a CSP and a Permissions-Policy on every page", async () => {
    const rules = await nextConfig.headers!();
    const all = rules.find((r) => r.source === "/:path*")!;
    const header = (key: string) =>
      all.headers.find((h) => h.key === key)?.value;

    expect(header("Strict-Transport-Security")).toBe(
      "max-age=31536000; includeSubDomains",
    );
    expect(header("Content-Security-Policy")).toContain("default-src 'self'");
    expect(header("Content-Security-Policy")).toContain(
      "frame-ancestors 'none'",
    );
    expect(header("Content-Security-Policy")).toContain("object-src 'none'");
    expect(header("Permissions-Policy")).toContain("camera=()");
  });
});
