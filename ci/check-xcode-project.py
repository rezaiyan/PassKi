#!/usr/bin/env python3
"""Fails when the sample's Xcode project hardcodes values that belong in the xcconfig files.

The sample's signing comes from iosApp/Configuration: Config.xcconfig (shared) and Local.xcconfig (each developer's
TEAM_ID, gitignored). Xcode rewrites project.pbxproj when its Signing & Capabilities editor is opened, putting the
resolved team ID back in. Committed, that makes every clone sign with one person's team again.

Usage: python3 ci/check-xcode-project.py [path/to/project.pbxproj]
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT = os.path.join(ROOT, "iosApp", "iosApp.xcodeproj", "project.pbxproj")
RESTORE = "Restore it with: git checkout -- iosApp/iosApp.xcodeproj/project.pbxproj (close Xcode first)."


def main(argv):
    path = argv[0] if argv else DEFAULT
    with open(path, encoding="utf-8") as f:
        text = f.read()
    problems = []
    teams = re.findall(r"^\s*DEVELOPMENT_TEAM = (.+);$", text, re.M)
    if not teams:
        problems.append("no DEVELOPMENT_TEAM setting found; expected DEVELOPMENT_TEAM = \"$(TEAM_ID)\" for each configuration")
    for value in teams:
        if value.strip() != '"$(TEAM_ID)"':
            problems.append(
                f"DEVELOPMENT_TEAM = {value.strip()}: the team must come from TEAM_ID in Local.xcconfig, "
                'so it must read DEVELOPMENT_TEAM = "$(TEAM_ID)". Xcode writes the resolved team when you pick one in '
                "Signing & Capabilities; set TEAM_ID in iosApp/Configuration/Local.xcconfig instead."
            )
    for key in ("PRODUCT_BUNDLE_IDENTIFIER", "CODE_SIGN_ENTITLEMENTS"):
        for value in re.findall(rf"^\s*{key} = (.+);$", text, re.M):
            problems.append(
                f"{key} = {value.strip()} in project.pbxproj overrides iosApp/Configuration/Config.xcconfig. "
                f"Remove it from the target's Build Settings; {key} belongs in the xcconfig files."
            )
    for p in problems:
        print("xcode project: " + p)
    if problems:
        print(RESTORE)
        return 1
    print(f"xcode project: OK ({len(teams)} configuration(s) take DEVELOPMENT_TEAM from TEAM_ID)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
