#!/usr/bin/env python3
"""Lists every artifact a PassKi release publishes, and checks that a release resolves from Maven Central.

The list comes from the build files, so a new module can't be forgotten: each `coordinates(artifactId = "...")` in a
`*/build.gradle.kts` (libraries, the BOM, the Gradle plugin) plus the marker of each Gradle plugin `id`.

Usage (from the repository root):
  python3 ci/published-artifacts.py                       # print group:artifact:VERSION_NAME, one per line
  python3 ci/published-artifacts.py --verify [VERSION]    # wait until every POM resolves from repo1.maven.org
      [--timeout-minutes 75] [--interval-seconds 120] [--repository https://repo1.maven.org/maven2]

--verify exits 1 and names each missing artifact and its URL when the time limit runs out. On GitHub Actions it also
writes the result to the step summary.
"""
import argparse
import glob
import os
import re
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def properties():
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        return dict(re.findall(r"^([\w.]+)=(.*)$", f.read(), re.M))


def artifacts():
    """(group, artifactId) for every published artifact, sorted."""
    group = properties()["GROUP"]
    found = set()
    for path in glob.glob(os.path.join(ROOT, "*", "build.gradle.kts")):
        with open(path, encoding="utf-8") as f:
            text = f.read()
        found.update((group, a) for a in re.findall(r'coordinates\(artifactId = "([^"]+)"\)', text))
        # Gradle plugin markers: <plugin id>:<plugin id>.gradle.plugin, in the plugin id's own group.
        for block in re.findall(r"gradlePlugin \{(.*?)\n\}", text, re.S):
            for plugin_id in re.findall(r'\bid = "([^"]+)"', block):
                found.add((plugin_id, plugin_id + ".gradle.plugin"))
    return sorted(found)


def pom_url(repository, group, artifact, version):
    return f"{repository}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"


def resolves(url):
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "passki-release-check"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status == 200
    except OSError:  # URLError, timeouts and dropped connections: not there yet, ask again next round
        return False


def summary(lines):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")


def verify(version, repository, timeout_minutes, interval_seconds):
    pending = {(g, a): pom_url(repository, g, a, version) for g, a in artifacts()}
    resolved = []
    deadline = time.monotonic() + timeout_minutes * 60
    while True:
        for key, url in list(pending.items()):
            if resolves(url):
                resolved.append(f"{key[0]}:{key[1]}:{version}")
                del pending[key]
        print(f"{len(resolved)} resolved, {len(pending)} pending", flush=True)
        if not pending or time.monotonic() + interval_seconds > deadline:
            break
        time.sleep(interval_seconds)
    lines = [f"### Maven Central, {version}", ""] + [f"- ✓ `{c}`" for c in sorted(resolved)]
    if pending:
        lines += [f"- ✗ `{g}:{a}:{version}` not at {url}" for (g, a), url in sorted(pending.items())]
        lines += ["", f"Not all artifacts resolved within {timeout_minutes} minutes. Central may still publish them: "
                  "check https://central.sonatype.com/publishing/deployments, then re-run only this job."]
    summary(lines)
    for line in lines:
        print(line)
    return 1 if pending else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--verify", nargs="?", const="", metavar="VERSION")
    parser.add_argument("--timeout-minutes", type=float, default=75)
    parser.add_argument("--interval-seconds", type=float, default=120)
    parser.add_argument("--repository", default="https://repo1.maven.org/maven2")
    args = parser.parse_args()
    version = args.verify or properties()["VERSION_NAME"]
    if args.verify is None:
        for group, artifact in artifacts():
            print(f"{group}:{artifact}:{version}")
        return 0
    return verify(version, args.repository.rstrip("/"), args.timeout_minutes, args.interval_seconds)


if __name__ == "__main__":
    sys.exit(main())
