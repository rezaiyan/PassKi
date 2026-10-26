import { webPageText } from "@/lib/catalog";
import { normalizeFingerprint } from "@/lib/fingerprints";
import {
  asObject,
  byteLength,
  fetchProblem,
  isJsonContentType,
  isWebPage,
  parseJson,
  plural,
  result,
  strings,
} from "./common";
import type { AndroidExpectation, CheckResult, Fetched } from "./types";

export const LOGIN_CREDS = "delegate_permission/common.get_login_creds";

export interface Statement {
  relations: string[];
  namespace: string | null;
  packageName: string | null;
  fingerprints: string[];
  include: string | null;
}

/** Null when [body] isn't a JSON array. Malformed entries inside the array are skipped. */
export function parseStatements(body: string): Statement[] | null {
  const root = parseJson(body);
  if (!Array.isArray(root)) return null;
  return root.flatMap((element) => {
    const obj = asObject(element);
    if (!obj) return [];
    const target = asObject(obj.target);
    return [
      {
        relations: strings(obj.relation),
        namespace:
          typeof target?.namespace === "string" ? target.namespace : null,
        packageName:
          typeof target?.package_name === "string" ? target.package_name : null,
        fingerprints: strings(target?.sha256_cert_fingerprints),
        include: typeof obj.include === "string" ? obj.include : null,
      },
    ];
  });
}

/** Canonical form for comparison; unparseable values are compared upper-cased (case-insensitive). */
function canonical(fp: string): string {
  return normalizeFingerprint(fp) ?? fp.trim().toUpperCase();
}

function statementSnippet(packageName: string, fingerprints: string[]): string {
  return JSON.stringify(
    {
      relation: [LOGIN_CREDS],
      target: {
        namespace: "android_app",
        package_name: packageName,
        sha256_cert_fingerprints: fingerprints,
      },
    },
    null,
    2,
  );
}

/**
 * AND-01..03 over a fetched `https://<rpId>/.well-known/assetlinks.json`.
 * Without [expected] packages, AND-02 reports which packages are delegated and AND-03 is skipped.
 */
export function checkAssetLinks(
  rpId: string,
  fetched: Fetched,
  expected: AndroidExpectation[],
): CheckResult[] {
  const out: CheckResult[] = [];
  const params = { rpId };
  const problem = fetchProblem(fetched);
  if (problem) {
    out.push(result("AND-01", "fail", problem, { fixParams: params }));
    return out.concat(
      skipDependents("assetlinks.json is not reachable", expected),
    );
  }
  if (isWebPage(fetched)) {
    const text = webPageText("AND-01", fetched.url, "assetlinks.json");
    out.push(
      result("AND-01", "fail", text.message, { fixParams: params, hint: text.fix }),
    );
    return out.concat(skipDependents("assetlinks.json is a web page", expected));
  }
  const statements = parseStatements(fetched.body);
  if (!statements) {
    out.push(
      result(
        "AND-01",
        "fail",
        `${fetched.url} is not a JSON array of statements`,
        {
          fixParams: params,
          hint: "Fix the JSON syntax; ./gradlew passkiWellKnown or the generator writes a valid file.",
        },
      ),
    );
    return out.concat(skipDependents("assetlinks.json is not valid", expected));
  }
  if (!isJsonContentType(fetched.contentType)) {
    out.push(
      result(
        "AND-01",
        "warn",
        `served with Content-Type ${fetched.contentType ?? "(none)"}, expected application/json`,
        {
          fixParams: params,
        },
      ),
    );
  } else {
    out.push(
      result(
        "AND-01",
        "pass",
        `${plural(statements.length, "statement")}, ${byteLength(fetched)} bytes, application/json`,
      ),
    );
  }

  // {"include": "<url>"} entries pull statements from another file; they are not followed, so a problem an
  // include might resolve is a warning, not a failure (same as the Kotlin doctor).
  const includes = statements
    .map((s) => s.include)
    .filter((i): i is string => i !== null);
  const problemStatus = includes.length ? "warn" : "fail";
  const includeNote = includes.length
    ? `; include statements are not followed (${includes.join(", ")})`
    : "";
  const login = statements.filter((s) => s.relations.includes(LOGIN_CREDS));

  if (expected.length === 0) {
    const pkgs = [
      ...new Set(
        login
          .filter((s) => s.namespace === "android_app" && s.packageName)
          .map((s) => s.packageName!),
      ),
    ];
    out.push(
      pkgs.length
        ? result(
            "AND-02",
            "pass",
            `${LOGIN_CREDS} granted to ${pkgs.join(", ")}`,
          )
        : result(
            "AND-02",
            problemStatus,
            `no statement grants ${LOGIN_CREDS} to an android_app${includeNote}`,
            {
              fixParams: { package: "<package>" },
            },
          ),
    );
    out.push(
      result(
        "AND-03",
        "skip",
        "no package/fingerprints given to check against",
      ),
    );
    return out;
  }

  for (const exp of expected) {
    const fixParams = {
      rpId,
      package: exp.packageName,
      fingerprint: exp.fingerprints[0],
    };
    const subject = exp.packageName;
    const hint = `Add this statement to ${fetched.url}:\n${statementSnippet(exp.packageName, exp.fingerprints)}`;
    if (login.length === 0) {
      out.push(
        result(
          "AND-02",
          problemStatus,
          `no statement grants ${LOGIN_CREDS}${includeNote}`,
          { subject, fixParams, hint },
        ),
      );
      out.push(
        result(
          "AND-03",
          "skip",
          "no login-credential statement for this package",
          { subject },
        ),
      );
      continue;
    }
    const ours = login.filter(
      (s) => s.namespace === "android_app" && s.packageName === exp.packageName,
    );
    if (ours.length === 0) {
      out.push(
        result(
          "AND-02",
          problemStatus,
          `no ${LOGIN_CREDS} statement for package ${exp.packageName}${includeNote}`,
          {
            subject,
            fixParams,
            hint,
          },
        ),
      );
      out.push(
        result(
          "AND-03",
          "skip",
          "no login-credential statement for this package",
          { subject },
        ),
      );
      continue;
    }
    out.push(
      result("AND-02", "pass", `${exp.packageName} has ${LOGIN_CREDS}`, {
        subject,
      }),
    );
    if (exp.fingerprints.length === 0) {
      out.push(
        result("AND-03", "skip", "no fingerprints given for this package", {
          subject,
        }),
      );
      continue;
    }
    const listed = new Set(ours.flatMap((s) => s.fingerprints.map(canonical)));
    const missing = exp.fingerprints.filter((fp) => !listed.has(canonical(fp)));
    if (missing.length) {
      out.push(
        result(
          "AND-03",
          problemStatus,
          `${plural(missing.length, "fingerprint")} ${missing.join(", ")} missing for ${exp.packageName}${includeNote}`,
          {
            subject,
            fixParams: { ...fixParams, fingerprint: missing[0] },
            hint: `Add this statement to ${fetched.url}:\n${statementSnippet(exp.packageName, missing)}`,
          },
        ),
      );
    } else {
      out.push(
        result(
          "AND-03",
          "pass",
          `${plural(exp.fingerprints.length, "fingerprint")} listed for ${exp.packageName}`,
          { subject },
        ),
      );
    }
  }
  return out;
}

function skipDependents(
  reason: string,
  expected: AndroidExpectation[],
): CheckResult[] {
  const subjects = expected.length
    ? expected.map((e) => e.packageName)
    : [undefined];
  return subjects.flatMap((subject) => [
    result("AND-02", "skip", reason, { subject }),
    result("AND-03", "skip", reason, { subject }),
  ]);
}

/**
 * AND-04 over the Google Digital Asset Links API response
 * (`statements:list?source.web.site=https://<rpId>&relation=…get_login_creds`).
 */
export function checkDigitalAssetLinks(
  rpId: string,
  fetched: Fetched,
  expected: AndroidExpectation[],
): CheckResult[] {
  const problem = fetchProblem(fetched);
  const json = problem ? null : asObject(parseJson(fetched.body));
  if (problem || !json) {
    return [
      result(
        "AND-04",
        "fail",
        `Google Digital Asset Links API: ${problem ?? "response is not a JSON object"}`,
        { fixParams: { rpId } },
      ),
    ];
  }
  const seen = new Map<string, Set<string>>();
  for (const s of Array.isArray(json.statements) ? json.statements : []) {
    const app = asObject(asObject(asObject(s)?.target)?.androidApp);
    const pkg = typeof app?.packageName === "string" ? app.packageName : null;
    const fp = asObject(app?.certificate)?.sha256Fingerprint;
    if (!pkg) continue;
    if (!seen.has(pkg)) seen.set(pkg, new Set());
    if (typeof fp === "string") seen.get(pkg)!.add(canonical(fp));
  }
  if (expected.length === 0) {
    return [
      seen.size
        ? result(
            "AND-04",
            "pass",
            `Google sees ${LOGIN_CREDS} for ${[...seen.keys()].join(", ")}`,
          )
        : result(
            "AND-04",
            "fail",
            `Google sees no ${LOGIN_CREDS} statement for https://${rpId}`,
            { fixParams: { rpId } },
          ),
    ];
  }
  return expected.map((exp) => {
    const subject = exp.packageName;
    const fps = seen.get(exp.packageName);
    if (!fps)
      return result(
        "AND-04",
        "fail",
        `Google sees no statement for ${exp.packageName}`,
        { subject, fixParams: { rpId } },
      );
    const missing = exp.fingerprints.filter((fp) => !fps.has(canonical(fp)));
    return missing.length
      ? result(
          "AND-04",
          "fail",
          `Google doesn't list ${missing.join(", ")} for ${exp.packageName} yet`,
          { subject, fixParams: { rpId } },
        )
      : result(
          "AND-04",
          "pass",
          `Google agrees for ${exp.packageName} (${plural(fps.size, "fingerprint")})`,
          { subject },
        );
  });
}
