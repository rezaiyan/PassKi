# gradle-check Specification

## Purpose
`passkiCheck` verifies the whole passkey setup from the build machine (hosted files, Apple and Google caches, entitlements, the signed app, the WebAuthn server) and reports each result by its catalog ID.

## Requirements

### Requirement: End-to-end checklist
`passkiCheck` SHALL run every check that can run from the build machine (`AND-01`..`AND-05`, `IOS-01`..`IOS-06`, `SRV-01`..`SRV-03`), print a checklist with concrete fix texts, and write `build/reports/passki/check.json` and `index.html`.

#### Scenario: All good
- **WHEN** the hosted files, entitlements and server all match the configuration
- **THEN** every check passes and the reports are written

### Requirement: Failure policy
`passkiCheck` MUST fail when any check fails, and SHALL also fail on warnings when the Gradle property `passki.check.warningsAsErrors=true` is set.

#### Scenario: Warning only
- **WHEN** only `IOS-03` (Apple CDN lag) warns
- **THEN** the task succeeds unless `-Ppasski.check.warningsAsErrors=true` is given

### Requirement: Network checks are opt-in
`passkiCheck` and `passkiVerify` MUST NOT be wired into Gradle's `check` lifecycle and MUST NOT be cached, because they depend on remote state.

#### Scenario: Running ./gradlew check
- **WHEN** a developer runs `./gradlew check`
- **THEN** no PassKi network check runs

### Requirement: iOS-only mode
Without an Android app module, `passkiCheck` SHALL report `AND-01`..`AND-05` as skipped with the iOS-only reason, and still run the iOS and server checks.

#### Scenario: Plugin on a library module
- **WHEN** `passkiCheck` runs in a module that is an Android library
- **THEN** Android checks are skipped and the reason names where to apply the plugin

### Requirement: Overridable endpoints
The hosted-file base URL (`passki.check.baseUrl`, falling back to `passki.verify.baseUrl`), the Google Digital Asset Links API (`passki.check.googleDalUrl`) and the Apple AASA CDN (`passki.check.appleCdnUrl`) SHALL be overridable through Gradle properties.

#### Scenario: Functional test against a local server
- **WHEN** `-Ppasski.check.baseUrl=http://localhost:8080` is set
- **THEN** the well-known files are fetched from that base instead of `https://<rpId>`

### Requirement: Hosted-file check only
`passkiVerify` SHALL run only the hosted-file checks of `passkiCheck`, with no report, and is kept as an advanced task.

#### Scenario: Quick hosted-file check
- **WHEN** a developer runs `passkiVerify`
- **THEN** only `assetlinks.json`, `apple-app-site-association` and entitlements are checked
