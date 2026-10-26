#!/usr/bin/env python3
"""Fails when a relative link or #anchor in the README, CONTRIBUTING or docs/ points nowhere.

Also fails when:
- README.md is longer than README_LIMIT lines (details belong on docs pages; raise the limit deliberately),
- docs/guides/what-to-show-users.md has no table row for a PasskeyError subtype,
- a page in docs/guides/ names a check ID that isn't in docs/checks/catalog.json,
- a link in passki-web/public/llms.txt doesn't resolve to a file in this repository,
- the install snippet on passki-web's landing page names another version than VERSION_NAME.

Usage: python3 ci/check-docs-links.py   (from the repository root)
"""
import json
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


README_LIMIT = 150
REPO_URL = "https://github.com/rezaiyan/PassKi/blob/main/"
ERRORS_PAGE = os.path.join("docs", "guides", "what-to-show-users.md")
LLMS_TXT = os.path.join("passki-web", "public", "llms.txt")


def read(name):
    with open(os.path.join(ROOT, name), encoding="utf-8") as f:
        return f.read()


def readme_length():
    lines = read("README.md").count("\n")
    return [f"README.md: {lines} lines, more than {README_LIMIT}"] if lines > README_LIMIT else []


def error_table():
    """Every PasskeyError subtype needs a row in the "What to show users" table."""
    source = read(os.path.join("passki", "src", "commonMain", "kotlin", "com", "alirezaiyan", "passki", "PasskeyError.kt"))
    subtypes = re.findall(r"public (?:data object|class) (\w+)\b[^{]*?: PasskeyError\(\)", source, re.S)
    rows = set(re.findall(r"^\| `(\w+)` \|", read(ERRORS_PAGE), re.M))
    return [f"{ERRORS_PAGE}: no row for PasskeyError.{name}" for name in subtypes if name not in rows]


def guide_check_ids():
    catalog = {check["id"] for check in json.loads(read(os.path.join("docs", "checks", "catalog.json")))["checks"]}
    problems = []
    guides = os.path.join(ROOT, "docs", "guides")
    for name in sorted(os.listdir(guides)):
        for check_id in sorted(set(re.findall(r"\b(?:AND|IOS|CFG|DEV|SRV)-\d{2}\b", read(os.path.join("docs", "guides", name))))):
            if check_id not in catalog:
                problems.append(f"docs/guides/{name}: {check_id} is not in docs/checks/catalog.json")
    return problems


def llms_txt():
    problems = []
    for link in re.findall(r"\]\(([^)\s]+)\)", read(LLMS_TXT)):
        if not link.startswith(REPO_URL):
            problems.append(f"{LLMS_TXT}: {link} is not a {REPO_URL} link")
            continue
        target = link[len(REPO_URL):].partition("#")[0]
        if not os.path.exists(os.path.join(ROOT, target)):
            problems.append(f"{LLMS_TXT}: {link} (no such file)")
    return problems


def web_version():
    version = re.search(r"^VERSION_NAME=(.+)$", read("gradle.properties"), re.M).group(1).strip()
    page = os.path.join("passki-web", "app", "page.tsx")
    shown = re.search(r'const VERSION = "([^"]+)"', read(page)).group(1)
    return [] if shown == version else [f"{page}: VERSION is {shown}, but VERSION_NAME is {version}"]


def main():
    broken = readme_length() + error_table() + guide_check_ids() + llms_txt() + web_version()
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
