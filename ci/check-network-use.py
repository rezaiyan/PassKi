#!/usr/bin/env python3
"""Keeps the security model's network claim true: PassKi's library code makes no network requests except
PasskeyDoctor fetching your well-known files.

Fails when a network API appears in passki/src/*Main outside the doctor's two fetchers. passki-demo (the demo server
client) and the Gradle plugin are separate artifacts and not checked. See docs/security-model.md.

Usage: python3 ci/check-network-use.py   (from the repository root)
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = os.path.join(ROOT, "passki", "src")

# The only files allowed to open connections: PasskeyDoctor's fetchers for the well-known files.
ALLOWED = {
    os.path.join("androidMain", "kotlin", "com", "alirezaiyan", "passki", "android", "UrlConnectionGetter.kt"),
    os.path.join("iosMain", "kotlin", "com", "alirezaiyan", "passki", "apple", "NSUrlSessionGetter.kt"),
}

NETWORK_APIS = [
    r"\bHttpURLConnection\b",
    r"\bHttpsURLConnection\b",
    r"\bURLConnection\b",
    r"\.openConnection\(",
    r"\bjava\.net\.(URL|Socket|http)\b",
    r"\bjava\.net\.http\b",
    r"\bokhttp3\b",
    r"\bio\.ktor\.client\b",
    r"\bNSURLSession\b",
    r"\bNSURLConnection\b",
    r"\bfetch\(",
    r"\bXMLHttpRequest\b",
    r"\bWebSocket\b",
    r"\bsendBeacon\b",
]


def main():
    problems = []
    checked = 0
    for source_set in sorted(os.listdir(SOURCES)):
        if not source_set.endswith("Main"):
            continue  # tests may use anything
        for dirpath, _, names in os.walk(os.path.join(SOURCES, source_set)):
            for name in names:
                if not name.endswith(".kt"):
                    continue
                path = os.path.join(dirpath, name)
                relative = os.path.relpath(path, SOURCES)
                checked += 1
                if relative in ALLOWED:
                    continue
                with open(path, encoding="utf-8") as f:
                    for number, line in enumerate(f, 1):
                        code = line.split("//", 1)[0]
                        for pattern in NETWORK_APIS:
                            if re.search(pattern, code):
                                problems.append(f"passki/src/{relative}:{number}: {line.strip()}")
                                break
    for problem in problems:
        print(problem)
    if problems:
        print("network use outside PasskeyDoctor's fetchers: update docs/security-model.md, or move the call")
    print(f"network use: {'FAILED' if problems else 'OK'} ({checked} files, {len(ALLOWED)} allowed fetchers)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
