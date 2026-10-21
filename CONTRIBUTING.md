# Contributing to PassKi

## Requirements

- **JDK 17+** and the Android SDK (`local.properties` with `sdk.dir`, or `ANDROID_HOME`).
- **macOS with Xcode** for the iOS targets. Xcode 26 or newer is needed: the iOS Signal API bridge calls
  `ASCredentialUpdater`, which is only in the iOS 26 SDK.
- **Node.js 22** (as in `passki-web/Dockerfile`) for `passki-web/` (the setup dashboard and demo relying party).

On Linux or Windows, Kotlin skips the iOS targets. You can still build and test the JVM, Android and web parts.

## The Swift bridge (iOS signals)

`ASCredentialUpdater` is a Swift-only API, so Kotlin/Native can't call it directly. The bridge lives in
`passki-ios-bridge/`:

- `PassKiSignals.swift` exposes `@_cdecl` C entry points (`passki_report_unknown`, `passki_report_all_accepted`,
  `passki_report_details`). They copy their arguments, call the updater in a `Task` and report back through a C
  callback exactly once.
- `passki_swift.h` declares those functions for cinterop.
- `build.sh` compiles `libPassKiSwift.a` with `xcrun --sdk <iphoneos|iphonesimulator> swiftc`. The flag
  `-runtime-compatibility-version none` keeps toolchain-only libraries out, so the archive links against the SDK's
  Swift runtime only.

Gradle runs it for you: `buildPassKiSwiftIosArm64` / `buildPassKiSwiftIosSimulatorArm64` (in `passki/build.gradle.kts`)
feed the `passkiSwift` cinterop (`passki/src/nativeInterop/cinterop/passkiSwift.def`), which embeds the archive in the
klib. Consumers need no Swift code or linker flags; keep it that way.

- **Changing the bridge:**
  - Update the Swift file and the header together.
  - Run `:passki:iosSimulatorArm64Test`. It links the real archive and calls the updater on the simulator.
  - Check that the sample `iosApp` still builds in Xcode without new settings.
- **`xcrun: error: unable to find utility "swiftc"`:** select a full Xcode, not just the Command Line Tools:
  `sudo xcode-select -s /Applications/Xcode.app`.
- **Gradle's up-to-date check covers the files in `passki-ios-bridge/`, but not the Xcode version.** After switching
  Xcode, run `./gradlew clean` or delete `passki/build/passkiSwift/`.

## Checks before a pull request

Gradle runs with the configuration cache on (`gradle.properties`). Keep new tasks and plugin code compatible with it.

```sh
./gradlew :passki:jvmTest :passki:testAndroidHostTest :passki:iosSimulatorArm64Test \
  :passki-test:jvmTest \
  :passki-compose:jvmTest :passki-compose:testAndroidHostTest :passki-compose:iosSimulatorArm64Test \
  :passki:checkKotlinAbi :passki-test:checkKotlinAbi :passki-compose:checkKotlinAbi \
  :shared:jvmTest :androidApp:assembleDebug dokkaGenerate
./gradlew -p passki-gradle-plugin test
(cd passki-web && npm ci && npm test)
```

- **Public API changes:** run `./gradlew updateKotlinAbi` and commit the updated files in `*/api/`.
  - New public declarations need KDoc: Dokka should report no undocumented declarations.
  - Constructors of result types are `@InternalPasskiApi`, so fields can be added later.
  - New enum entries and sealed subtypes are allowed in minor releases.
- **Platform behaviour** (sheets, autofill, signals, PRF) can only be checked on a device or emulator. Record what you
  verified in the pull request: OS version and credential provider. The sample's autorun flag (see the README) runs
  the UI-free steps unattended.

## Secrets

Don't commit keystores, `.env` files or credentials. The only exception is `androidApp/sample-debug.keystore`, a
public debug key for the sample app (see `androidApp/README.md`).
