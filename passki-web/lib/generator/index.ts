import { zipSync, strToU8 } from "fflate";
import { androidOrigin } from "@/lib/fingerprints";
import type { AndroidExpectation } from "@/lib/checks/types";

/** Same relations, key order and formatting as the Gradle plugin's `WellKnownFiles`. */
const RELATIONS = [
  "delegate_permission/common.handle_all_urls",
  "delegate_permission/common.get_login_creds",
];

const render = (value: unknown) => JSON.stringify(value, null, 2) + "\n";
const sorted = (values: string[]) => [...new Set(values)].sort();

export interface GeneratorInput {
  rpId: string;
  android: AndroidExpectation[];
  /** `TEAMID.bundle.id` entries. */
  appleAppIds: string[];
  /** Entitlements path used in the xcconfig line. */
  entitlementsPath?: string;
  /** Add `?mode=developer` to the entitlement (bypasses Apple's CDN while testing). */
  developerMode?: boolean;
}

export interface GeneratedFile {
  path: string;
  content: string;
  description: string;
}

/** One statement per package (sorted), fingerprints de-duplicated and sorted. */
export function assetLinks(android: AndroidExpectation[]): string {
  const statements = [...android]
    .sort((a, b) =>
      a.packageName < b.packageName
        ? -1
        : a.packageName > b.packageName
          ? 1
          : 0,
    )
    .map((a) => ({
      relation: RELATIONS,
      target: {
        namespace: "android_app",
        package_name: a.packageName,
        sha256_cert_fingerprints: sorted(a.fingerprints),
      },
    }));
  return render(statements);
}

export function aasa(appIds: string[]): string {
  return render({ webcredentials: { apps: sorted(appIds) } });
}

export function associatedDomain(rpId: string, developerMode = false): string {
  return `webcredentials:${rpId}${developerMode ? "?mode=developer" : ""}`;
}

export function entitlementsPlist(rpId: string, developerMode = false): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
\t<key>com.apple.developer.associated-domains</key>
\t<array>
\t\t<string>${associatedDomain(rpId, developerMode)}</string>
\t</array>
</dict>
</plist>
`;
}

export const DEFAULT_ENTITLEMENTS_PATH = "iosApp/iosApp.entitlements";

export function xcconfigLine(path = DEFAULT_ENTITLEMENTS_PATH): string {
  return `CODE_SIGN_ENTITLEMENTS = ${path}`;
}

/** Every origin a WebAuthn server must accept: `https://<rpId>` + one `android:apk-key-hash:` per fingerprint. */
export function origins(rpId: string, android: AndroidExpectation[]): string[] {
  const android_ = sorted(
    android.flatMap((a) => a.fingerprints).map(androidOrigin),
  );
  return [`https://${rpId}`, ...android_];
}

export function serverConfig(
  rpId: string,
  android: AndroidExpectation[],
): string {
  return render({ rpId, origins: origins(rpId, android) });
}

const list = (values: string[], indent: string, quote = '"') =>
  values.map((v) => `${indent}${quote}${v}${quote},`).join("\n");

export function snippets(
  rpId: string,
  android: AndroidExpectation[],
): Record<string, string> {
  const o = origins(rpId, android);
  return {
    "simplewebauthn.ts": `// @simplewebauthn/server
import { verifyRegistrationResponse, verifyAuthenticationResponse } from "@simplewebauthn/server";

export const rpID = "${rpId}";
export const expectedOrigin = [
${list(o, "  ")}
];

// verifyRegistrationResponse({ response, expectedChallenge, expectedOrigin, expectedRPID: rpID })
// verifyAuthenticationResponse({ response, expectedChallenge, expectedOrigin, expectedRPID: rpID, credential })
`,
    "webauthn4j.kt": `// com.webauthn4j:webauthn4j-core
import com.webauthn4j.data.client.Origin
import com.webauthn4j.server.ServerProperty

val origins: Set<Origin> = setOf(
${o.map((v) => `    Origin.create("${v}"),`).join("\n")}
)

fun serverProperty(challenge: com.webauthn4j.data.client.challenge.Challenge) =
    ServerProperty(origins, "${rpId}", challenge, null)
`,
    "java-webauthn-server.java": `// com.yubico:webauthn-server-core
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import java.util.Set;

RelyingParty rp = RelyingParty.builder()
    .identity(RelyingPartyIdentity.builder().id("${rpId}").name("${rpId}").build())
    .credentialRepository(credentialRepository)
    .origins(Set.of(
${o.map((v) => `        "${v}"`).join(",\n")}
    ))
    .build();
`,
    "py_webauthn.py": `# webauthn (py_webauthn)
from webauthn import verify_registration_response, verify_authentication_response

RP_ID = "${rpId}"
EXPECTED_ORIGINS = [
${list(o, "    ")}
]

# verify_registration_response(credential=..., expected_challenge=..., expected_rp_id=RP_ID, expected_origin=EXPECTED_ORIGINS)
`,
  };
}

export function generate(input: GeneratorInput): GeneratedFile[] {
  const { rpId, android, appleAppIds } = input;
  const files: GeneratedFile[] = [];
  if (android.length) {
    files.push({
      path: "well-known/assetlinks.json",
      content: assetLinks(android),
      description: `Upload to https://${rpId}/.well-known/assetlinks.json`,
    });
  }
  if (appleAppIds.length) {
    files.push(
      {
        path: "well-known/apple-app-site-association",
        content: aasa(appleAppIds),
        description: `Upload to https://${rpId}/.well-known/apple-app-site-association (no extension, application/json)`,
      },
      {
        path: "ios/iosApp.entitlements",
        content: entitlementsPlist(rpId, input.developerMode),
        description: "Associated Domains entitlement for the iOS app",
      },
      {
        path: "ios/Config.xcconfig.line",
        content: xcconfigLine(input.entitlementsPath) + "\n",
        description:
          "Add to Configuration/Config.xcconfig (no project.pbxproj edits)",
      },
    );
  }
  files.push({
    path: "server/server-config.json",
    content: serverConfig(rpId, android),
    description: "RP ID and the origins allow-list for your WebAuthn server",
  });
  for (const [name, content] of Object.entries(snippets(rpId, android))) {
    files.push({
      path: `server/snippets/${name}`,
      content,
      description: `Verifier config: ${name.split(".")[0]}`,
    });
  }
  files.push({
    path: "README.txt",
    content: readme(input, files),
    description: "Next steps",
  });
  return files;
}

function readme(input: GeneratorInput, files: GeneratedFile[]): string {
  const steps = [
    files.some((f) => f.path.startsWith("well-known/")) &&
      `Upload the files in well-known/ to https://${input.rpId}/.well-known/ (HTTP 200, no redirects, Content-Type: application/json).`,
    input.appleAppIds.length &&
      `Copy ios/iosApp.entitlements into your iOS app and add the line in ios/Config.xcconfig.line to Configuration/Config.xcconfig.`,
    `Configure your WebAuthn server with server/server-config.json (rpId + origins); see server/snippets/.`,
    `Verify: https://passki.alirezaiyan.com/verify?rpId=${input.rpId} or ./gradlew passkiCheck.`,
  ].filter(Boolean) as string[];
  return `PassKi setup files for ${input.rpId}\n\n${steps.map((s, i) => `${i + 1}. ${s}`).join("\n")}\n`;
}

export function zip(
  files: GeneratedFile[],
  folder = "passki-setup",
): Uint8Array {
  const entries: Record<string, Uint8Array> = {};
  for (const f of files) entries[`${folder}/${f.path}`] = strToU8(f.content);
  return zipSync(entries, {
    level: 6,
    mtime: new Date("2026-01-01T00:00:00Z"),
  });
}
