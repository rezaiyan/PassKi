#!/usr/bin/env python3
"""Fails when a relative link or #anchor in the README, CONTRIBUTING or docs/ points nowhere.

Usage: python3 ci/check-docs-links.py   (from the repository root)
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = ["README.md", "CONTRIBUTING.md", "CHANGELOG.md"] + [
    os.path.join(dirpath, name)[len(ROOT) + 1:]
    for dirpath, _, names in os.walk(os.path.join(ROOT, "docs"))
    if "/plans" not in dirpath
    for name in names
    if name.endswith(".md")
]


def anchors(path):
    """GitHub-style heading anchors of a Markdown file."""
    with open(path, encoding="utf-8") as f:
        text = re.sub(r"```.*?```", "", f.read(), flags=re.S)
    found = set()
    for heading in re.findall(r"^#{1,6} (.+)$", text, re.M):
        slug = re.sub(r"[`*]", "", heading).strip().lower()
        slug = re.sub(r"[^\w\- ]", "", slug).replace(" ", "-")
        found.add(slug)
    return found


def main():
    broken = []
    for name in FILES:
        path = os.path.join(ROOT, name)
        with open(path, encoding="utf-8") as f:
            text = re.sub(r"```.*?```", "", f.read(), flags=re.S)
        for match in re.finditer(r"\]\(([^)\s]+)\)|src=\"([^\"]+)\"", text):
            link = match.group(1) or match.group(2)
            if re.match(r"^[a-z]+:", link):
                continue  # https:, mailto:, ...
            target, _, fragment = link.partition("#")
            resolved = os.path.normpath(os.path.join(os.path.dirname(path), target)) if target else path
            if not os.path.exists(resolved):
                broken.append(f"{name}: {link} (no such file)")
            elif fragment and resolved.endswith(".md") and fragment not in anchors(resolved):
                broken.append(f"{name}: {link} (no such heading)")
    for line in broken:
        print(line)
    print(f"checked {len(FILES)} files: {len(broken)} broken link(s)")
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
