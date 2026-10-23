# gradle-setup Specification

## Purpose
The `com.alirezaiyan.passki` Gradle plugin turns one `passki { }` block into everything the app–domain trust needs (well-known files, iOS entitlements and their Xcode wiring, server config), in one idempotent `passkiSetup` run, or registers the app with the demo server.

## Requirements

### Requirement: passki DSL
The plugin SHALL register a `passki { }` extension with `rpId`, `useDemoServer()`, `android { packageName; extraFingerprints }`, `apple { teamId; bundleId; entitlementsFile; xcodeProject; scheme; xcconfig; developerMode }` and `server { configUrl }`. `apple.developerMode` SHALL default to true.

#### Scenario: Minimal configuration
- **WHEN** a module sets only `passki { rpId = "example.com" }`
- **THEN** the package and signing fingerprints come from the Android build, and Team ID and bundle ID come from `xcodebuild -showBuildSettings`

### Requirement: RP ID validated with a suggestion
The tasks MUST reject an `rpId` that is not a bare lowercase domain (no scheme, port, path, IPv4 address, uppercase or trailing dot), and when a corrected value can be derived the error SHALL name it.

#### Scenario: URL instead of domain
- **WHEN** `rpId = "https://Example.com/"`
- **THEN** the task fails with `passki { rpId = "https://Example.com/" }: the RP ID is a bare domain: use "example.com"`

#### Scenario: No RP ID
- **WHEN** neither `rpId` nor `useDemoServer()` is set
- **THEN** the task fails saying it needs `passki { rpId = "example.com" }` or `passki { useDemoServer() }`

### Requirement: Front-door tasks always present
`passkiSetup` and `passkiCheck` SHALL be registered in every module that applies the plugin, so they can be run as `./gradlew passkiSetup` from the project root. `passkiFingerprints`, `passkiWellKnown` and `passkiVerify` SHALL be registered only when `com.android.application` is applied.

#### Scenario: Plugin on an Android library module
- **WHEN** the plugin is applied to a `com.android.library` or KMP library module
- **THEN** the tasks run in iOS-only mode and say the module is an Android library, not the Android app, and to apply the plugin in the app module (e.g. `:androidApp` or `:composeApp`) instead

#### Scenario: iOS-only build
- **WHEN** the module has no Android app
- **THEN** Android is skipped with that reason logged, and iOS setup still runs

#### Scenario: Nothing to set up
- **WHEN** there is no Android app and no Apple app ID can be found
- **THEN** `passkiSetup` fails saying it has nothing to set up and to set `passki { apple { teamId; bundleId } }`

### Requirement: One-shot idempotent setup
`passkiSetup` SHALL write and wire everything passkeys need in one run, and running it twice MUST change nothing the second time.

#### Scenario: Own domain
- **WHEN** `passkiSetup` runs with `rpId = "example.com"`
- **THEN** it writes `assetlinks.json` and `apple-app-site-association` to `build/passki/well-known`, `build/passki/server-config.json`, and verifier snippets to `build/passki/server-snippets/`
- **AND** the next steps say to upload the well-known files to `https://example.com/.well-known/` (HTTPS, 200, `application/json`, no redirects)

#### Scenario: iOS entitlements
- **WHEN** an Apple app is configured or discovered
- **THEN** `webcredentials:<rpId>` (plus `webcredentials:<rpId>?mode=developer` when `developerMode`) is merged into the entitlements file, and `CODE_SIGN_ENTITLEMENTS` is set in the base `.xcconfig`

#### Scenario: Second run
- **WHEN** `passkiSetup` runs again with unchanged configuration
- **THEN** every file is reported unchanged

#### Scenario: Summary
- **WHEN** `passkiSetup` finishes
- **THEN** it prints every file created, changed or unchanged (with a diff for the user's own files), every request sent, and the remaining manual steps, ending with "Run ./gradlew passkiCheck"

### Requirement: Never edits project.pbxproj
`passkiSetup` MUST NOT modify `project.pbxproj`. Without a base `.xcconfig` it SHALL print the Xcode steps to set Code Signing Entitlements; when `project.pbxproj` sets a target-level `CODE_SIGN_ENTITLEMENTS` that overrides the xcconfig, it SHALL warn and tell the user to remove it.

#### Scenario: No xcconfig
- **WHEN** no base `.xcconfig` is found
- **THEN** the entitlements file is still written, and the next steps tell the user how to set Code Signing Entitlements in Xcode

### Requirement: Preview mode
`passkiSetup --preview` SHALL print the same summary and MUST NOT write any file or send any request.

#### Scenario: Preview in demo mode
- **WHEN** `passkiSetup --preview` runs with `useDemoServer()`
- **THEN** each registration request is printed with "not sent (preview)"

### Requirement: Demo server mode
With `useDemoServer()`, the RP ID SHALL be `passki.alirezaiyan.com`, `server.configUrl` its server config, and `passkiSetup` SHALL register each Android package (with its signing fingerprints) and each Apple app ID with the demo server for 7 days, printing exactly what it sends (public data only) and the server's answer. Any failed registration MUST fail the task after the summary. The Gradle property `passki.demo.url` SHALL override the demo base URL.

#### Scenario: Registration rejected
- **WHEN** the demo server rejects one registration
- **THEN** the summary is printed and the task fails naming the registration that failed

### Requirement: Fingerprints and advanced tasks
`passkiFingerprints` SHALL print Android packages, signing SHA-256 fingerprints and `android:apk-key-hash:` origins; `passkiWellKnown` SHALL write only the well-known files; `extraFingerprints` (for example Play App Signing) SHALL be normalised to colon-hex SHA-256 and added to the packages of non-debuggable variants, or to all packages when every variant is debuggable.

#### Scenario: Invalid extra fingerprint
- **WHEN** `extraFingerprints` contains a value that is not a SHA-256 fingerprint
- **THEN** the task fails with an error starting `passki.android.extraFingerprints:`
