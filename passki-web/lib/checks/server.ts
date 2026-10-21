import { asObject, fetchProblem, parseJson, result, strings } from "./common";
import type { CheckResult, Fetched } from "./types";

const B64URL = /^[A-Za-z0-9_-]+={0,2}$/;

/** Decoded byte length of a base64url string, or null when it isn't base64url. */
export function base64UrlBytes(value: unknown): number | null {
  if (typeof value !== "string" || !B64URL.test(value)) return null;
  const unpadded = value.replace(/=+$/, "");
  if (unpadded.length % 4 === 1) return null;
  return Buffer.from(unpadded, "base64url").length;
}

/**
 * SRV-01 + SRV-03 over a server's registration-options response
 * (a `PublicKeyCredentialCreationOptionsJSON`, optionally wrapped in `{ publicKey: … }`).
 */
export function checkRegistrationOptions(
  rpId: string,
  fetched: Fetched,
): CheckResult[] {
  const problem = fetchProblem(fetched);
  const root = problem ? null : asObject(parseJson(fetched.body));
  if (problem || !root) {
    const msg = problem ?? `${fetched.url} did not return a JSON object`;
    return [
      result("SRV-01", "skip", "registration options unavailable"),
      result("SRV-03", "fail", msg),
    ];
  }
  const options = asObject(root.publicKey) ?? root;
  const out: CheckResult[] = [];
  const rp = asObject(options.rp);
  const serverRpId = typeof rp?.id === "string" ? rp.id : null;
  if (serverRpId === rpId)
    out.push(result("SRV-01", "pass", `rp.id is ${rpId}`));
  else
    out.push(
      result(
        "SRV-01",
        "fail",
        serverRpId
          ? `rp.id is ${serverRpId}, expected ${rpId}`
          : "rp.id is missing",
        { fixParams: { rpId } },
      ),
    );

  const problems: string[] = [];
  if (!serverRpId) problems.push("rp.id missing");
  const challenge = base64UrlBytes(options.challenge);
  if (challenge === null)
    problems.push("challenge is missing or not base64url");
  else if (challenge < 16)
    problems.push(`challenge is ${challenge} bytes (needs ≥ 16)`);
  const userId = base64UrlBytes(asObject(options.user)?.id);
  if (userId === null) problems.push("user.id is missing or not base64url");
  else if (userId < 1 || userId > 64)
    problems.push(`user.id is ${userId} bytes (needs 1-64)`);
  out.push(
    problems.length
      ? result("SRV-03", "fail", problems.join("; "))
      : result(
          "SRV-03",
          "pass",
          `challenge ${challenge} bytes, user.id ${userId} bytes`,
        ),
  );
  return out;
}

/** SRV-01 + SRV-02 over a `server-config.json` (`{ rpId, origins }`) as served by the developer's server. */
export function checkServerConfig(
  rpId: string,
  fetched: Fetched,
  expectedOrigins: string[],
): CheckResult[] {
  const problem = fetchProblem(fetched);
  const json = problem ? null : asObject(parseJson(fetched.body));
  if (problem || !json) {
    const msg = problem ?? `${fetched.url} did not return a JSON object`;
    return [
      result("SRV-01", "fail", msg, { fixParams: { rpId } }),
      result("SRV-02", "skip", "server config unavailable"),
    ];
  }
  const out: CheckResult[] = [];
  const serverRpId = typeof json.rpId === "string" ? json.rpId : null;
  out.push(
    serverRpId === rpId
      ? result("SRV-01", "pass", `rpId is ${rpId}`)
      : result(
          "SRV-01",
          "fail",
          serverRpId
            ? `rpId is ${serverRpId}, expected ${rpId}`
            : "rpId is missing",
          { fixParams: { rpId } },
        ),
  );
  const origins = strings(json.origins);
  const wanted = [
    `https://${rpId}`,
    ...expectedOrigins.filter((o) => o !== `https://${rpId}`),
  ];
  const missing = wanted.filter((o) => !origins.includes(o));
  out.push(
    missing.length
      ? result("SRV-02", "fail", `origins missing: ${missing.join(", ")}`, {
          fixParams: { rpId },
        })
      : result("SRV-02", "pass", `allows ${wanted.length} expected origin(s)`),
  );
  return out;
}
