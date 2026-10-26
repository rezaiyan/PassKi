import {
  APPLE_MAX_AASA_BYTES,
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
import type { CheckResult, Fetched, VerifyInput } from "./types";
import { webPageText } from "@/lib/catalog";

export function webcredentialsApps(json: unknown): string[] {
  return strings(asObject(asObject(json)?.webcredentials)?.apps);
}

type IosExpectation = VerifyInput["ios"][number];

function label(exp: IosExpectation): string {
  return exp.teamId ? `${exp.teamId}.${exp.bundleId}` : exp.bundleId;
}

/** The app ID in [apps] matching [exp]: exact `TEAM.bundle`, or any team when no Team ID is given (doctor semantics). */
function findApp(apps: string[], exp: IosExpectation): string | undefined {
  if (exp.teamId)
    return apps.find((a) => a === `${exp.teamId}.${exp.bundleId}`);
  return apps.find(
    (a) => a.includes(".") && a.slice(a.indexOf(".") + 1) === exp.bundleId,
  );
}

/**
 * IOS-01..03 over the direct AASA (`https://<rpId>/.well-known/apple-app-site-association`) and Apple's CDN copy
 * (`https://app-site-association.cdn-apple.com/a/v1/<rpId>`). Only the webcredentials entry is compared on the CDN.
 */
export function checkAasa(
  rpId: string,
  direct: Fetched,
  cdn: Fetched | null,
  expected: IosExpectation[],
): CheckResult[] {
  const out: CheckResult[] = [];
  const params = { rpId };
  const problem = fetchProblem(direct);
  if (!problem && isWebPage(direct)) {
    const text = webPageText("IOS-01", direct.url, "apple-app-site-association");
    out.push(result("IOS-01", "fail", text.message, { fixParams: params, hint: text.fix }));
    const subjects = expected.length ? expected.map(label) : [undefined];
    for (const subject of subjects)
      out.push(
        result("IOS-02", "skip", "apple-app-site-association is a web page", { subject }),
        result("IOS-03", "skip", "apple-app-site-association is a web page", { subject }),
      );
    return out;
  }
  const json = problem ? undefined : parseJson(direct.body);
  if (problem || !asObject(json)) {
    out.push(
      result(
        "IOS-01",
        "fail",
        problem ?? `${direct.url} is not a JSON object`,
        {
          fixParams: params,
          hint: problem
            ? undefined
            : "Fix the JSON syntax; ./gradlew passkiWellKnown or the generator writes a valid file.",
        },
      ),
    );
    const reason = problem
      ? "apple-app-site-association is not reachable"
      : "apple-app-site-association is not valid";
    const subjects = expected.length ? expected.map(label) : [undefined];
    for (const subject of subjects)
      out.push(
        result("IOS-02", "skip", reason, { subject }),
        result("IOS-03", "skip", reason, { subject }),
      );
    return out;
  }
  const size = byteLength(direct);
  if (size > APPLE_MAX_AASA_BYTES) {
    out.push(
      result(
        "IOS-01",
        "warn",
        `the file is ${Math.round(size / 1024)} KB; Apple ignores AASA files over 128 KB`,
        {
          fixParams: params,
          hint: `Shrink ${direct.url} below 128 KB (e.g. trim applinks paths).`,
        },
      ),
    );
  } else if (!isJsonContentType(direct.contentType)) {
    out.push(
      result(
        "IOS-01",
        "warn",
        `served with Content-Type ${direct.contentType ?? "(none)"}, expected application/json`,
        {
          fixParams: params,
        },
      ),
    );
  } else {
    out.push(result("IOS-01", "pass", `${size} bytes, application/json`));
  }

  const apps = webcredentialsApps(json);
  const cdnProblem = cdn ? fetchProblem(cdn) : "not fetched";
  const cdnJson = cdn && !cdnProblem ? parseJson(cdn.body) : undefined;
  const cdnApps = asObject(cdnJson) ? webcredentialsApps(cdnJson) : null;
  const cdnUnavailable =
    cdnProblem ??
    (cdnApps === null ? `${cdn!.url} is not a JSON object` : null);
  const cdnParams = { rpId };

  if (expected.length === 0) {
    out.push(
      apps.length
        ? result(
            "IOS-02",
            "pass",
            `webcredentials.apps lists ${apps.join(", ")}`,
          )
        : result("IOS-02", "fail", "webcredentials.apps lists no apps", {
            fixParams: { teamId: "<teamId>", bundleId: "<bundleId>" },
          }),
    );
    if (cdnUnavailable) {
      out.push(
        result(
          "IOS-03",
          "fail",
          `Apple's CDN copy is unavailable: ${cdnUnavailable}`,
          { fixParams: cdnParams },
        ),
      );
    } else {
      const missing = apps.filter((a) => !cdnApps!.includes(a));
      const extra = cdnApps!.filter((a) => !apps.includes(a));
      out.push(
        missing.length || extra.length
          ? result(
              "IOS-03",
              "fail",
              `Apple's CDN webcredentials differ (CDN lagging): ${[
                missing.length ? `missing ${missing.join(", ")}` : "",
                extra.length ? `stale ${extra.join(", ")}` : "",
              ]
                .filter(Boolean)
                .join("; ")}`,
              { fixParams: cdnParams },
            )
          : result(
              "IOS-03",
              "pass",
              `Apple's CDN lists the same ${plural(apps.length, "app")}`,
            ),
      );
    }
    return out;
  }

  for (const exp of expected) {
    const subject = label(exp);
    const match = findApp(apps, exp);
    if (!match) {
      const teams = [...new Set(apps.map((a) => a.split(".")[0]))];
      const found = apps.length
        ? `found ${apps.join(", ")}`
        : "it lists no apps";
      out.push(
        result(
          "IOS-02",
          "fail",
          `webcredentials.apps has no entry for ${subject}; ${found}`,
          {
            subject,
            fixParams: {
              teamId: exp.teamId ?? (teams.length === 1 ? teams[0] : undefined),
              bundleId: exp.bundleId,
            },
          },
        ),
      );
      out.push(
        result("IOS-03", "skip", "the app is not listed in the direct file", {
          subject,
        }),
      );
      continue;
    }
    out.push(
      result(
        "IOS-02",
        "pass",
        `lists ${match} (team ID ${match.split(".")[0]})`,
        { subject },
      ),
    );
    if (cdnUnavailable) {
      out.push(
        result(
          "IOS-03",
          "fail",
          `Apple's CDN copy is unavailable: ${cdnUnavailable}`,
          { subject, fixParams: cdnParams },
        ),
      );
    } else if (!cdnApps!.includes(match)) {
      out.push(
        result(
          "IOS-03",
          "fail",
          `Apple's CDN copy at ${cdn!.url} doesn't list ${match} yet (CDN lagging)`,
          { subject, fixParams: cdnParams },
        ),
      );
    } else {
      out.push(
        result("IOS-03", "pass", `Apple's CDN copy lists ${match}`, {
          subject,
        }),
      );
    }
  }
  return out;
}
