const HEX64 = /^[0-9A-F]{64}$/;

/** Accepts `ab:cd…` or `abcd…` (any case); returns canonical uppercase colon-hex, or null. */
export function normalizeFingerprint(value: string): string | null {
  const hex = value.trim().replace(/:/g, "").toUpperCase();
  if (!HEX64.test(hex)) return null;
  return hex.match(/../g)!.join(":");
}

export function requireFingerprint(value: string): string {
  const fp = normalizeFingerprint(value);
  if (!fp) throw new Error(`Not a SHA-256 fingerprint: ${value}`);
  return fp;
}

/** WebAuthn origin Android reports for an app signed by [fingerprint]: `android:apk-key-hash:<base64url>`. */
export function androidOrigin(fingerprint: string): string {
  const bytes = Buffer.from(
    requireFingerprint(fingerprint).replace(/:/g, ""),
    "hex",
  );
  return "android:apk-key-hash:" + bytes.toString("base64url");
}

const PACKAGE = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/;

export function isPackageName(value: string): boolean {
  return value.length <= 255 && PACKAGE.test(value);
}

const FP_IN_TEXT = /(?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}|\b[0-9A-Fa-f]{64}\b/g;
const PACKAGE_IN_TEXT = /\b[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+\b/;

export interface ParsedFingerprints {
  /** Fingerprints per package, in input order, de-duplicated. */
  byPackage: Record<string, string[]>;
  /** Fingerprints found on lines without a package name. */
  unassigned: string[];
}

/**
 * Parses `./gradlew passkiFingerprints` output (`  pkg | variant | keystore | SHA-256` rows) and looser
 * forms (`pkg AA:BB…`, bare fingerprints one per line). Lines with origins or headers are ignored.
 */
export function parseFingerprints(text: string): ParsedFingerprints {
  const byPackage: Record<string, string[]> = {};
  const unassigned: string[] = [];
  const add = (list: string[], fp: string) => {
    if (!list.includes(fp)) list.push(fp);
  };
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.includes("apk-key-hash")) continue;
    const fps = (line.match(FP_IN_TEXT) ?? [])
      .map((m) => normalizeFingerprint(m))
      .filter((f): f is string => f !== null);
    if (fps.length === 0) continue;
    let pkg: string | undefined;
    if (line.includes("|")) {
      const first = line.split("|")[0].trim();
      if (isPackageName(first)) pkg = first;
    } else {
      const withoutFps = line.replace(FP_IN_TEXT, " ");
      const m = withoutFps.match(PACKAGE_IN_TEXT);
      if (m && isPackageName(m[0])) pkg = m[0];
    }
    const list = pkg ? (byPackage[pkg] ??= []) : unassigned;
    fps.forEach((fp) => add(list, fp));
  }
  return { byPackage, unassigned };
}
