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
- **Gradle's up-to-date check covers the files in `passki-ios-bridge/` and the toolchain:** the `swiftc --version`
  output and the iOS SDK version and build are task inputs, so switching or upgrading Xcode rebuilds the archive by
  itself. No `clean` needed.

## Checks before a pull request

Gradle runs with the configuration cache on (`gradle.properties`). Keep new tasks and plugin code compatible with it.

```sh
./gradlew :passki:jvmTest :passki:testAndroidHostTest :passki:iosSimulatorArm64Test \
  :passki-test:jvmTest \
  :passki-compose:jvmTest :passki-compose:testAndroidHostTest :passki-compose:iosSimulatorArm64Test \
  :passki-demo:jvmTest :passki-demo:iosSimulatorArm64Test \
  :passki:checkLegacyAbi :passki-test:checkLegacyAbi :passki-compose:checkLegacyAbi :passki-demo:checkLegacyAbi \
  :shared:jvmTest :androidApp:assembleDebug dokkaGenerate
./gradlew -p passki-gradle-plugin test
(cd passki-web && npm ci && npm test)
python3 ci/check-docs-links.py   # links, README under 150 lines, error table, llms.txt
python3 ci/check-xcode-project.py
python3 ci/check-open-types.py
python3 ci/check-network-use.py  # no network calls outside PasskeyDoctor (docs/security-model.md)
```

`ci/check-xcode-project.py` fails when `iosApp/iosApp.xcodeproj/project.pbxproj` hardcodes a team, bundle ID or
entitlements file. Xcode writes the team into that file when you pick one in **Signing & Capabilities**; set
`TEAM_ID` in `iosApp/Configuration/Local.xcconfig` instead, and don't commit Xcode's change.

CI (`.github/workflows/ci.yml`) runs the same checks, plus two that protect the onboarding paths:

| Job | Runs on | What it checks |
|---|---|---|
| Library and plugin | Ubuntu, every push to `main` and pull request | JVM/Android/Wasm tests, the Gradle plugin's unit and TestKit tests, Dokka, the Android sample build, docs links |
| passki-web | Ubuntu | tests, typecheck, production build |
| Sample against the demo server | Ubuntu | README path A: a fresh clone builds the sample, then `passkiSetup --preview` and `passkiCheck` against passki.alirezaiyan.com |
| iOS, API dumps and consumer apps | macOS 26 (`main`, pull requests and manual runs only: macOS minutes cost 10x on a private repository) | iOS simulator tests, ABI dumps, the Xcode build of the iOS sample, and the [consumer apps](ci/consumers/README.md): an app on Kotlin 2.2.21 and an Android app on AGP's built-in Kotlin with `compileSdk` 35, both using PassKi from Maven Local |

The macOS job also runs `ci/doc-snippets.py`: every Kotlin example in the README and `docs/` must compile against the
published artifacts, every ```` ```kotlin server ```` example must compile and its tests pass against webauthn4j and
java-webauthn-server, every `passki { }` block must configure, and every `com.alirezaiyan.passki` coordinate must name a
published artifact and the current version. When you change an example, run it locally after publishing to Maven Local.

The plugin's TestKit tests must run, not skip: a skipped functional test hides a broken plugin.

The consumer apps fail when a Kotlin or dependency upgrade would lock out apps on older toolchains. Run them locally
with the commands in [`ci/consumers/README.md`](ci/consumers/README.md).

- **Kotlin version:** PassKi is built with Kotlin 2.2 so apps on Kotlin 2.2+ can read its iOS/JS/Wasm libraries.
  Before upgrading Kotlin or a dependency, check which Kotlin built the new release (its klib `manifest` has
  `compiler_version`): a release built with a newer Kotlin raises the minimum for every app using PassKi.
- **Public API changes:** run `./gradlew updateLegacyAbi` and commit the updated files in `*/api/`.
  - New public declarations need KDoc: Dokka should report no undocumented declarations.
  - Constructors of result types are `@InternalPasskiApi`, so fields can be added later.
  - New enum entries and sealed subtypes are allowed in minor releases only for types labelled "Open:";
    `ci/check-open-types.py` checks every enum and sealed type has an "Open:" or "Closed:" label.
  - Follow [the versioning policy](docs/versioning.md): every breaking change gets a CHANGELOG entry marked
    **BREAKING** with a "Migrating from" block (before and after code); from the first beta, deprecate with
    `@Deprecated(replaceWith = ReplaceWith(...))` first whenever the signature allows it.
  - The main entry points (`Passkeys`, `PasskeyResult`, `FakePasskeys`) link a compiled sample with KDoc `@sample`.
    Samples live in `src/commonTest/kotlin/.../samples/`, so they compile with the tests; add or update one when an
    entry point's usage changes.
- **Logging:** every public operation logs through `PasskiLog` (`internal/PasskiLog.kt`), never `println` or a
  platform logger directly. A log line must not contain a challenge, user handle, options or response JSON, a
  password, PRF output or large-blob data: show credential IDs with `PasskiLog.shortId` and JSON with
  `PasskiLog.lengthOf`. `PasskiLogRedactionTest` (passki-test) checks a full debug trace; extend it when you add a log
  line that prints new data.
- **Platform behaviour** (sheets, autofill, signals, PRF) can only be checked on a device or emulator. Record what you
  verified in the pull request: OS version and credential provider. The sample's autorun flag
  ([docs/try-it.md](docs/try-it.md#whats-in-the-sample)) runs the UI-free steps unattended.

## Before a release

CI can't tap through a passkey sheet or Face ID, or catch what R8 only breaks at run time. Before tagging:

1. Run the iOS steps in [`ci/manual-checks-ios.md`](ci/manual-checks-ios.md).
2. Run the minified Android consumer once on a device or emulator, and read its log:

   ```sh
   ./gradlew -p ci/consumers/android installRelease
   adb shell am start -n com.example.consumer/.MainActivity
   adb logcat -s PassKi System.out
   ```

   The `PasskeyDoctor` report must print, the `register` call must reach the sheet, and failures must be logged with
   readable names (`Cancelled`, `CreateCredentialCancellationException`), not R8's short ones. If something breaks,
   add a rule to `passki/consumer-rules.pro` with a comment saying what needs it.
3. Bump `VERSION_NAME`, give the CHANGELOG section its date, tag `v<version>` and push the tag. The release workflow
   publishes, then waits until `python3 ci/published-artifacts.py --verify` finds every artifact on Maven Central,
   then publishes the API reference to GitHub Pages.

## Secrets

Don't commit keystores, `.env` files or credentials. The only exception is `androidApp/sample-debug.keystore`, a
public debug key for the sample app (see `androidApp/README.md`).
