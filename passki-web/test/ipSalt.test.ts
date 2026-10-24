import { afterEach, describe, expect, it, vi } from "vitest";
import { ipKey, ipSalt } from "../lib/db";

describe("IP salt", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("should refuse to run in production without PASSKI_IP_SALT", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("PASSKI_IP_SALT", "");
    expect(() => ipSalt()).toThrow(/PASSKI_IP_SALT/);
    expect(() => ipKey("203.0.113.7")).toThrow(/PASSKI_IP_SALT/);
  });

  it("should refuse the .env.example placeholder in production", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("PASSKI_IP_SALT", "change-me");
    expect(() => ipSalt()).toThrow(/PASSKI_IP_SALT/);
  });

  it("should use the configured salt in production", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("PASSKI_IP_SALT", "4f0c1e9a7b2d");
    expect(ipSalt()).toBe("4f0c1e9a7b2d");
    expect(ipKey("203.0.113.7")).not.toBe(ipKey("203.0.113.8"));
  });

  it("should fall back to a dev salt outside production", () => {
    vi.stubEnv("NODE_ENV", "development");
    vi.stubEnv("PASSKI_IP_SALT", "");
    expect(ipSalt()).toBe("passki");
  });
});
