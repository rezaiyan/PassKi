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


def unwrap(markdown):
    """Joins hard-wrapped lines: GitHub keeps every line break in release notes, so wrapped CHANGELOG lines would
    break mid-sentence. Code blocks, headings, list items and blank lines stay as they are."""
    out, in_code = [], False
    for line in markdown.split("\n"):
        stripped = line.strip()
        if stripped.startswith("```"):
            in_code = not in_code
            out.append(line)
            continue
        joinable = (not in_code and stripped and out and out[-1].strip() and not out[-1].strip().startswith("```")
                    and not stripped.startswith(("- ", "#", "|", "```")) and not out[-1].startswith("#"))
        if joinable:
            out[-1] = out[-1].rstrip() + " " + stripped
        else:
            out.append(line)
    return "\n".join(out)


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
    print(unwrap(section(version)) + "\n\n" + install(version))


if __name__ == "__main__":
    main()
