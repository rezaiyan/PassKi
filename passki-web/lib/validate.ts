import {
  isPackageName,
  normalizeFingerprint,
  parseFingerprints,
} from "@/lib/fingerprints";
import { validateDomain } from "@/lib/net/ssrf";
import type { AndroidExpectation, VerifyInput } from "@/lib/checks/types";

export class InputError extends Error {}

export const TEAM_ID = /^[A-Z0-9]{10}$/;
export const BUNDLE_ID = /^[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/;

export function isTeamId(value: string): boolean {
  return TEAM_ID.test(value);
}

export function isBundleId(value: string): boolean {
  return value.length <= 155 && BUNDLE_ID.test(value);
}

export function rpIdOrThrow(value: unknown): string {
  if (typeof value !== "string") throw new InputError("rpId is required");
  try {
    return validateDomain(value);
  } catch (e) {
    throw new InputError(`rpId: ${(e as Error).message}`);
  }
}

const MAX_PACKAGES = 10;
const MAX_FINGERPRINTS = 20;

function str(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

/**
 * Android expectations from either `android: [{packageName, fingerprints: [...]}]` or
 * `packageName` + free-text `fingerprints` (passkiFingerprints output); unassigned fingerprints go to `packageName`.
 */
export function androidFrom(
  body: Record<string, unknown>,
  { requireFingerprints = false } = {},
): AndroidExpectation[] {
  const map = new Map<string, string[]>();
  const add = (pkg: string, fps: string[]) => {
    if (!isPackageName(pkg))
      throw new InputError(`not a valid Android package name: ${pkg}`);
    const list = map.get(pkg) ?? [];
    for (const fp of fps) {
      const n = normalizeFingerprint(fp);
      if (!n) throw new InputError(`not a SHA-256 fingerprint: ${fp}`);
      if (!list.includes(n)) list.push(n);
    }
    map.set(pkg, list);
  };
  if (Array.isArray(body.android)) {
    for (const entry of body.android) {
      const e = (entry ?? {}) as Record<string, unknown>;
      const pkg = str(e.packageName);
      if (!pkg) continue;
      const fps = Array.isArray(e.fingerprints)
        ? e.fingerprints.map(str).filter(Boolean)
        : [];
      add(pkg, fps);
    }
  }
  const pkg = str(body.packageName);
  const text = str(body.fingerprints);
  if (pkg || text) {
    const parsed = parseFingerprints(text);
    for (const [p, fps] of Object.entries(parsed.byPackage)) add(p, fps);
    if (parsed.unassigned.length) {
      if (!pkg)
        throw new InputError(
          "fingerprints without a package: enter the package name",
        );
      add(pkg, parsed.unassigned);
    } else if (pkg && !map.has(pkg)) {
      add(pkg, []);
    }
  }
  if (map.size > MAX_PACKAGES)
    throw new InputError(`at most ${MAX_PACKAGES} packages`);
  const out = [...map.entries()].map(([packageName, fingerprints]) => ({
    packageName,
    fingerprints,
  }));
  for (const e of out) {
    if (e.fingerprints.length > MAX_FINGERPRINTS)
      throw new InputError(
        `at most ${MAX_FINGERPRINTS} fingerprints per package`,
      );
    if (requireFingerprints && e.fingerprints.length === 0)
      throw new InputError(`no fingerprints for ${e.packageName}`);
  }
  return out;
}

/** iOS expectations from `ios: [{teamId, bundleId}]` or `teamId` + `bundleId` (comma/newline separated bundle IDs allowed). */
export function iosFrom(
  body: Record<string, unknown>,
  { requireTeam = false } = {},
): VerifyInput["ios"] {
  const out: VerifyInput["ios"] = [];
  const add = (teamIdRaw: string, bundleId: string) => {
    const teamId = teamIdRaw.toUpperCase();
    if (teamId && !isTeamId(teamId))
      throw new InputError(
        `Team ID must be 10 uppercase letters/digits: ${teamIdRaw}`,
      );
    if (requireTeam && !teamId) throw new InputError("Team ID is required");
    if (!isBundleId(bundleId))
      throw new InputError(`not a valid bundle ID: ${bundleId}`);
    if (
      !out.some(
        (o) => o.teamId === (teamId || undefined) && o.bundleId === bundleId,
      )
    )
      out.push({ teamId: teamId || undefined, bundleId });
  };
  if (Array.isArray(body.ios)) {
    for (const entry of body.ios) {
      const e = (entry ?? {}) as Record<string, unknown>;
      if (str(e.bundleId)) add(str(e.teamId), str(e.bundleId));
    }
  }
  const bundles = str(body.bundleId)
    .split(/[\s,]+/)
    .filter(Boolean);
  for (const b of bundles) {
    // Accept a full app ID (TEAMID.bundle) in the bundle field.
    const m = b.match(/^([A-Z0-9]{10})\.(.+)$/);
    if (m && !str(body.teamId)) add(m[1], m[2]);
    else add(str(body.teamId), b);
  }
  if (out.length > MAX_PACKAGES)
    throw new InputError(`at most ${MAX_PACKAGES} iOS apps`);
  return out;
}

export function verifyInputFrom(body: unknown): VerifyInput {
  const b = (body ?? {}) as Record<string, unknown>;
  return {
    rpId: rpIdOrThrow(b.rpId),
    android: androidFrom(b),
    ios: iosFrom(b),
  };
}

export interface GeneratorRequest {
  rpId: string;
  android: AndroidExpectation[];
  appleAppIds: string[];
  developerMode: boolean;
  entitlementsPath?: string;
}

export function generatorInputFrom(body: unknown): GeneratorRequest {
  const b = (body ?? {}) as Record<string, unknown>;
  const rpId = rpIdOrThrow(b.rpId);
  const android = androidFrom(b, { requireFingerprints: true });
  const appleAppIds = iosFrom(b, { requireTeam: true }).map((a) => `${a.teamId}.${a.bundleId}`);
  if (android.length === 0 && appleAppIds.length === 0) throw new InputError("enter an Android package with fingerprints and/or an iOS Team ID + bundle ID");
  const path = str(b.entitlementsPath);
  if (path && !/^[\w./ -]{1,200}\.entitlements$/.test(path)) throw new InputError("entitlements path must end in .entitlements");
  return { rpId, android, appleAppIds, developerMode: b.developerMode === true, entitlementsPath: path || undefined };
}
