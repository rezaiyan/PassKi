#!/bin/sh
# Builds libPassKiSwift.a (Spike A). Usage: build.sh <iphoneos|iphonesimulator> <swift target triple> <out dir>
set -eu
here=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$3"
xcrun --sdk "$1" swiftc -parse-as-library -emit-library -static -module-name PassKiSwift \
  -swift-version 5 -O -runtime-compatibility-version none -target "$2" \
  "$here/PassKiSignals.swift" -o "$3/libPassKiSwift.a"
