#!/usr/bin/env python3
"""Prints the GitHub Release notes for one version: its CHANGELOG section, then how to install it.

Usage: python3 ci/release-notes.py 0.1.0-alpha05 > notes.md   (from the repository root)
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = "https://github.com/rezaiyan/PassKi"


def section(version):
    with open(os.path.join(ROOT, "CHANGELOG.md"), encoding="utf-8") as f:
        text = f.read()
    match = re.search(rf"^## {re.escape(version)} \(.*?\)\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    if not match:
        sys.exit(f"CHANGELOG.md has no section for {version}")
    # Relative links in the CHANGELOG point into the repository; on the release page they need the tag's tree.
    body = re.sub(r"\]\((?!https?:|#)([^)]+)\)", rf"]({REPO}/blob/v{version}/\1)", match.group(1).strip())
    return body


def install(version):
    bom = version >= "0.1.0-alpha05"  # passki-bom exists from alpha05 on
    lines = ["## Install", "", "```kotlin", "commonMain.dependencies {"]
    if bom:
        lines += [f'    implementation(project.dependencies.platform("com.alirezaiyan.passki:passki-bom:{version}"))',
                  '    implementation("com.alirezaiyan.passki:passki")']
    else:
        lines += [f'    implementation("com.alirezaiyan.passki:passki:{version}")']
    lines += ["}", "```", "",
              f'Gradle plugin: `id("com.alirezaiyan.passki") version "{version}"`. All artifacts are on '
              f"[Maven Central](https://central.sonatype.com/namespace/com.alirezaiyan.passki).", ""]
    if bom:
        lines += [f"- API reference: https://rezaiyan.github.io/PassKi/{version}/",
                  f"- Docs for this version: {REPO}/tree/v{version}/docs"]
    else:
        lines += [f"- Docs for this version: {REPO}/tree/v{version}#readme"]
    return "\n".join(lines)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    version = sys.argv[1].removeprefix("v")
    print(section(version) + "\n\n" + install(version))


if __name__ == "__main__":
    main()
