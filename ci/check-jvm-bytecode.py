#!/usr/bin/env python3
"""Fails when a published PassKi class is newer bytecode than apps can load.

The libraries' JVM and Android classes must be Java 11 (class file 55); the Gradle plugin's must be Java 17 (61), the
floor Gradle 9 itself needs. Without an explicit jvmTarget, Kotlin follows the JDK running the build, which is how
0.1.0-alpha02 shipped Java 21 JVM jars.

Reads the artifacts of the current VERSION_NAME from a Maven repository, after publishToMavenLocal.

Usage: python3 ci/check-jvm-bytecode.py [maven-repo]   (from the repository root; default ~/.m2/repository)
"""
import io
import os
import re
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIBRARIES = ["passki", "passki-compose", "passki-test", "passki-demo"]
JAVA_11, JAVA_17 = 55, 61


def version_name():
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        return re.search(r"^VERSION_NAME=(.+)$", f.read(), re.M).group(1).strip()


def class_majors(jar_bytes):
    """Class file major versions in a jar, ignoring multi-release entries under META-INF/versions."""
    majors = set()
    with zipfile.ZipFile(io.BytesIO(jar_bytes)) as jar:
        for name in jar.namelist():
            if name.endswith(".class") and not name.startswith("META-INF/"):
                majors.add(struct.unpack(">H", jar.read(name)[6:8])[0])
    return majors


def jars(repo, version):
    """(artifact, jar bytes, allowed major) for every published JVM-bytecode artifact of this version."""
    base = os.path.join(repo, "com", "alirezaiyan", "passki")
    for library in LIBRARIES:
        for artifact, extension in [(f"{library}-jvm", "jar"), (f"{library}-android", "aar")]:
            path = os.path.join(base, artifact, version, f"{artifact}-{version}.{extension}")
            if not os.path.isfile(path):
                continue  # not published for this library; checked == 0 still catches a missing publish
            with open(path, "rb") as f:
                data = f.read()
            if extension == "aar":
                with zipfile.ZipFile(io.BytesIO(data)) as aar:
                    data = aar.read("classes.jar")
            yield artifact, data, JAVA_11
    plugin = os.path.join(base, "passki-gradle-plugin", version, f"passki-gradle-plugin-{version}.jar")
    if os.path.isfile(plugin):
        with open(plugin, "rb") as f:
            yield "passki-gradle-plugin", f.read(), JAVA_17


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/.m2/repository")
    version = version_name()
    checked, problems = 0, []
    for artifact, data, allowed in jars(repo, version):
        checked += 1
        majors = class_majors(data)
        if not majors:
            problems.append(f"{artifact}: no classes")
        elif max(majors) > allowed:
            problems.append(f"{artifact}: class file {max(majors)} (Java {max(majors) - 44}), "
                            f"allowed {allowed} (Java {allowed - 44})")
    if checked == 0:
        problems.append(f"no PassKi {version} artifacts in {repo}; run publishToMavenLocal first")
    for problem in problems:
        print(f"::error::{problem}")
    print(f"checked {checked} artifacts of {version}: {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
