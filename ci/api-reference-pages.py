#!/usr/bin/env python3
"""Writes a release's Dokka HTML into a GitHub Pages checkout: <version>/ (kept for good) and latest/ (replaced).

Earlier versions' folders are never touched. The root index.html redirects to latest/.

Usage: python3 ci/api-reference-pages.py --version 0.1.0-alpha05 --html build/dokka/html --pages <gh-pages checkout>
"""
import argparse
import os
import re
import shutil
import sys

INDEX = """<!doctype html>
<meta charset="utf-8">
<title>PassKi API reference</title>
<meta http-equiv="refresh" content="0; url=latest/">
<link rel="canonical" href="latest/">
<p><a href="latest/">PassKi API reference (latest)</a> · {versions}</p>
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--html", required=True, help="Dokka output, e.g. build/dokka/html")
    parser.add_argument("--pages", required=True, help="checkout of the gh-pages branch")
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+\.\d+(-[\w.]+)?", args.version):
        sys.exit(f"not a release version: {args.version}")
    if not os.path.isfile(os.path.join(args.html, "index.html")):
        sys.exit(f"no Dokka output in {args.html} (run ./gradlew dokkaGenerate)")

    for folder in (args.version, "latest"):
        target = os.path.join(args.pages, folder)
        shutil.rmtree(target, ignore_errors=True)
        shutil.copytree(args.html, target)

    versions = sorted(
        (name for name in os.listdir(args.pages) if re.fullmatch(r"\d+\.\d+\.\d+(-[\w.]+)?", name)),
        key=lambda v: [int(part) if part.isdigit() else part for part in re.split(r"(\d+)", v)],
        reverse=True,
    )
    links = " · ".join(f'<a href="{v}/">{v}</a>' for v in versions)
    with open(os.path.join(args.pages, "index.html"), "w", encoding="utf-8") as f:
        f.write(INDEX.format(versions=links))
    # Dokka's file names start with underscores in places; tell Pages not to run Jekyll over them.
    open(os.path.join(args.pages, ".nojekyll"), "w").close()
    print(f"wrote {args.version}/ and latest/ ({len(versions)} version(s) in {args.pages})")


if __name__ == "__main__":
    main()
