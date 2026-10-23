#!/usr/bin/env python3
"""Totals the JUnit XML results under the given directories, prints a table (and appends it to the GitHub job summary),
and fails on test failures, or on skipped tests with --no-skips.

The Gradle plugin's functional tests skip themselves without an Android SDK, so CI runs them with --no-skips: a green
step must mean they ran.

Usage: python3 ci/test-summary.py [--no-skips] <dir> [<dir> ...]
"""
import glob
import os
import re
import sys


def main(argv):
    no_skips = "--no-skips" in argv
    dirs = [a for a in argv if a != "--no-skips"]
    rows = []
    for d in dirs:
        for path in sorted(glob.glob(os.path.join(d, "**", "build", "test-results", "**", "*.xml"), recursive=True)):
            with open(path, encoding="utf-8") as f:
                head = f.read(4096)
            m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', head)
            if m:
                rel = os.path.relpath(path, d)
                module = os.path.join(d, re.match(r"(.*?)/?build/test-results/", rel).group(1)).rstrip("/.") or "."
                rows.append((os.path.normpath(module), *map(int, m.groups())))
    totals = {}
    for module, tests, skipped, failures, errors in rows:
        t = totals.setdefault(module, [0, 0, 0, 0])
        for i, v in enumerate((tests, skipped, failures, errors)):
            t[i] += v
    lines = ["| Module | Tests | Skipped | Failed |", "|---|---|---|---|"]
    for module, (tests, skipped, failures, errors) in sorted(totals.items()):
        lines.append(f"| {module} | {tests} | {skipped} | {failures + errors} |")
    report = "\n".join(lines)
    print(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(report + "\n\n")
    all_tests = sum(t[0] for t in totals.values())
    if all_tests == 0:
        print("no test results found")
        return 1
    if any(t[2] + t[3] for t in totals.values()):
        return 1
    if no_skips and any(t[1] for t in totals.values()):
        print("skipped tests are not allowed here (--no-skips)")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
