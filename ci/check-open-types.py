#!/usr/bin/env python3
"""Fails unless every public enum and sealed type says in its KDoc whether it may gain entries.

Reads the types from the klib ABI dumps (`<module>/api/*.klib.api`), finds each declaration in the module's sources and
checks its KDoc for a paragraph starting with "Open:" (may gain entries; callers keep an `else` branch) or "Closed:"
(never gains entries). Types in `.internal` packages are skipped: they are behind @InternalPasskiApi.

Usage: python3 ci/check-open-types.py
"""
import glob
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ["passki", "passki-compose", "passki-test", "passki-demo"]

DECLARATION = re.compile(r"\b(?:sealed (?:class|interface)|enum class) (?:<[^>]*> )?([\w.]+)/([\w.]+)")
LABEL = re.compile(r"^\s*\*\s*(Open|Closed):")


def types_in_dump(dump: Path):
    for line in dump.read_text().splitlines():
        match = DECLARATION.search(line)
        if match and ".internal" not in match.group(1):
            yield match.group(1), match.group(2).split(".")[-1]


def kdoc_before(lines, index):
    """The KDoc lines directly above line [index], skipping annotations."""
    i = index - 1
    while i >= 0 and lines[i].strip().startswith("@"):
        i -= 1
    if i < 0 or not lines[i].strip().endswith("*/"):
        return []
    end = i
    while i >= 0 and "/**" not in lines[i]:
        i -= 1
    return lines[i:end + 1]


def main() -> int:
    problems = []
    checked = 0
    for module in MODULES:
        sources = [Path(p) for p in glob.glob(str(ROOT / module / "src" / "*Main" / "**" / "*.kt"), recursive=True)]
        for dump in sorted((ROOT / module / "api").glob("*.klib.api")):
            for package, name in sorted(set(types_in_dump(dump))):
                pattern = re.compile(rf"\b(?:sealed (?:class|interface)|enum class) {re.escape(name)}\b")
                found = False
                for source in sources:
                    lines = source.read_text().splitlines()
                    for index, line in enumerate(lines):
                        if pattern.search(line) and not line.lstrip().startswith(("*", "//")):
                            found = True
                            checked += 1
                            if not any(LABEL.match(doc) for doc in kdoc_before(lines, index)):
                                where = source.relative_to(ROOT)
                                problems.append(f"{package}.{name} ({where}:{index + 1}) has no 'Open:' or 'Closed:' KDoc paragraph")
                if not found:
                    problems.append(f"{package}.{name}: declaration not found in {module}/src")
    for problem in problems:
        print(problem)
    if problems:
        print(f"open types: {len(problems)} problem(s)")
        return 1
    print(f"open types: OK ({checked} types labelled)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
