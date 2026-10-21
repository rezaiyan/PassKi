import { androidOrigin, normalizeFingerprint } from "@/lib/fingerprints";

/** Debug fingerprint of the PassKi sample (docs/setup/well-known/assetlinks.json). */
const DEFAULT_FINGERPRINT =
  "C9:64:0F:6A:68:90:AF:6C:26:49:DC:BB:AB:94:79:23:93:5C:FC:10:8B:DF:03:20:78:04:60:5C:5B:79:49:03";

function list(value: string | undefined): string[] {
  return (value ?? "")
    .split(/[\s,]+/)
    .map((v) => v.trim())
    .filter(Boolean);
}

export interface DemoConfig {
  rpId: string;
  rpName: string;
  androidPackage: string;
  androidFingerprints: string[];
  /** Base `TEAM.bundle` IDs always present in the demo AASA. */
  appleAppIds: string[];
  origins: string[];
}

/** Read per call so tests (and restarts with new env) see current values. */
export function demoConfig(env: Record<string, string | undefined> = process.env): DemoConfig {
  const rpId = (env.PASSKI_RP_ID || "passki.alirezaiyan.com").toLowerCase();
  const fps = list(env.PASSKI_ANDROID_FINGERPRINTS ?? DEFAULT_FINGERPRINT).map(
    (f) => {
      const n = normalizeFingerprint(f);
      if (!n)
        throw new Error(
          `PASSKI_ANDROID_FINGERPRINTS: not a SHA-256 fingerprint: ${f}`,
        );
      return n;
    },
  );
  const origins = [
    `https://${rpId}`,
    ...fps.map(androidOrigin),
    ...list(env.PASSKI_EXTRA_ORIGINS),
  ];
  return {
    rpId,
    rpName: env.PASSKI_RP_NAME || "PassKi demo",
    androidPackage: env.PASSKI_ANDROID_PACKAGE || "com.alirezaiyan.passki",
    androidFingerprints: fps,
    appleAppIds: list(
      env.PASSKI_APPLE_APP_IDS ?? "VFCFJC7Y5J.com.alirezaiyan.passki.PassKi",
    ),
    origins: [...new Set(origins)],
  };
}
