# Spec Delta

## ADDED Requirements

### Requirement: Asset-links errors carry the app's signing evidence
On Android, every `Misconfigured` error with check ID `AND-03` SHALL include in its `fixHint` the SHA-256 fingerprint of each certificate the running app is currently signed with (`AA:BB:…` form; rotated-out keys excluded, as in `PasskeyDoctor`) and the matching `android:apk-key-hash:<base64url>` origin. When the signing certificates can't be read, the hint MUST say so and keep the generic fix.

#### Scenario: Sign-in rejected because the app isn't trusted
- **WHEN** Credential Manager rejects a sign-in with a DOM `SecurityError` for RP ID `example.com` and the app is signed with fingerprint `C9:64:…:03`
- **THEN** the result is `Misconfigured` with check ID `AND-03`
- **AND** its `fixHint` names `https://example.com/.well-known/assetlinks.json`, the fingerprint `C9:64:…:03` and the origin `android:apk-key-hash:<base64url of that fingerprint>`

#### Scenario: Signal rejected for the RP ID
- **WHEN** a signal call is rejected with a security error for the RP ID
- **THEN** the `Misconfigured` hint carries the same fingerprints and origins as a ceremony error

#### Scenario: Several signing certificates
- **WHEN** the app is signed by two signers
- **THEN** the hint lists both fingerprints and both origins

#### Scenario: Rotated signing key
- **WHEN** the app's signing key was rotated
- **THEN** the hint lists only the current key's fingerprint and origin

#### Scenario: Certificates unavailable
- **WHEN** the package manager can't return signing information
- **THEN** the hint says the fingerprint couldn't be read and points to `./gradlew passkiFingerprints` and `PasskeyDoctor`

### Requirement: Emulator note on asset-links errors
On Android, when the device is an emulator, the `fixHint` of an `AND-03` error SHALL end with a note that some emulator images fail RP ID validation even when `assetlinks.json` is correct, and that a passing `PasskeyDoctor` report plus a listed fingerprint means a real device or another image should be tried. On a physical device the note MUST NOT appear.

#### Scenario: AND-03 on an emulator
- **WHEN** an `AND-03` error occurs on an Android emulator
- **THEN** the hint ends with the emulator note

#### Scenario: AND-03 on a phone
- **WHEN** an `AND-03` error occurs on a physical device
- **THEN** the hint has no emulator note
