# Spec Delta

## Purpose

Defines what every PassKi release gives adopters: artifacts that resolve from Maven Central once the release reports
success, a BOM that keeps versions aligned, a hosted API reference for that version, and Android libraries that work
in minified release builds.

## ADDED Requirements

### Requirement: A successful release is resolvable
A tagged release run SHALL report success only after the POM of every published artifact (`passki`, `passki-compose`, `passki-test`, `passki-demo`, `passki-bom`, the Gradle plugin and its plugin marker) at the tagged version resolves from `https://repo1.maven.org/maven2/`; otherwise, after a time limit of at least 60 minutes, the run MUST fail and name the missing artifacts.

#### Scenario: Central syncs in time
- **WHEN** a tag `v0.1.0-alpha05` is pushed and all POMs appear on `repo1.maven.org` within the limit
- **THEN** the release run succeeds and its summary lists each resolved coordinate

#### Scenario: Deployment uploaded but not released
- **WHEN** an artifact is uploaded but never becomes available on `repo1.maven.org`
- **THEN** the run fails and names that artifact and its expected URL

### Requirement: Bill of materials
Every release SHALL publish `com.alirezaiyan.passki:passki-bom` at the release version, constraining `passki`, `passki-compose`, `passki-test` and `passki-demo` to that same version.

#### Scenario: Consumer uses the BOM
- **WHEN** an app declares `implementation(platform("com.alirezaiyan.passki:passki-bom:0.1.0-alpha05"))` and `implementation("com.alirezaiyan.passki:passki")` without a version
- **THEN** Gradle resolves `passki` 0.1.0-alpha05

### Requirement: Hosted API reference per version
Every release SHALL publish the Dokka HTML reference for all public modules at a versioned public URL and update a `latest` URL to it; earlier versions MUST stay available.

#### Scenario: Reading the reference for an old version
- **WHEN** 0.1.0-alpha06 has been released
- **THEN** the 0.1.0-alpha05 reference is still reachable at its versioned URL and `latest` shows 0.1.0-alpha06

### Requirement: Works in minified Android release builds
PassKi's Android libraries SHALL work in an app release build with R8 enabled and no PassKi-specific keep rules in the app; any rules PassKi needs MUST ship in the libraries' consumer ProGuard files. CI MUST build a minified release variant of the Android consumer app against every change.

#### Scenario: Minified consumer build in CI
- **WHEN** CI builds `ci/consumers/android` with `isMinifyEnabled = true` for the release variant
- **THEN** the build succeeds without the app declaring keep rules for PassKi, kotlinx-serialization or Credential Manager

#### Scenario: Minified sample on a device
- **WHEN** the Android sample is built as a minified release and run against the demo server before a release
- **THEN** creating a passkey and signing in succeed, and a setup error still carries its fix hint and check ID
