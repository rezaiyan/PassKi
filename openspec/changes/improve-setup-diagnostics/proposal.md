# Proposal

## Why

The most expensive bug in the Lexicon integration was an AND-03 (`Misconfigured`, "RP ID cannot be validated") whose
hint pointed at `assetlinks.json` while the file was correct; it cost hours on one emulator image. The hint names the
file to fix but not the evidence (which certificate the running app is signed with), so a developer can't tell a wrong
fingerprint from a platform problem. Hosted-file checks also give a generic "not JSON" message when an SPA catch-all
serves `index.html`, a common hosting mistake. See `docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, sections
6.1 and 6.4.

## What Changes

- Android AND-03 errors (`Misconfigured` from a DOM `SecurityError` or an RP ID validation failure, in ceremonies and
  signals) include the running app's signing SHA-256 fingerprint(s) and the matching `android:apk-key-hash:` origin(s),
  so the fix can be copied from logcat or a crash report.
- On an emulator, the AND-03 hint adds one line saying some emulator images fail RP ID validation even with a correct
  setup, and what to try (`PasskeyDoctor`, a real device).
- `PasskeyDoctor`, `passkiCheck` and the passki-web verifier report an HTML page served at a well-known path as its own
  failure (AND-01 / IOS-01) with a hosting-specific fix, instead of "not a JSON array".
- `passkiSetup` warns when the RP ID is a bare registrable domain whose live well-known files already list other apps,
  because changing the RP ID later orphans every passkey (case study section 6.3).
- Docs: `docs/checks/AND-03.md` gains a "Still failing?" section; `docs/verified-on.md` records the failing AVD image
  and Play services version; AND-01 / IOS-01 pages describe the SPA catch-all case.
- No public API is added or removed; error message and hint text change (not part of the API contract).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `passkey-errors`: Android asset-links errors carry the running app's fingerprints, origins and an emulator note.
- `setup-check-catalog`: hosted-file checks recognise an HTML page served at a well-known path, in every consumer.
- `gradle-setup`: `passkiSetup` warns about an RP ID shared with other apps.

## Impact

- **Android:** `AndroidErrorMapper` / `assetLinksHint` gain signing facts (already read by `AndroidDoctorFacts`) and an
  emulator check. **iOS, JVM, JS, Wasm:** no runtime change, except the shared doctor checks (commonMain) used by
  `PasskeyDoctor` on Android and iOS.
- `passki-gradle-plugin` (`WellKnownVerifier`) and `passki-web` verifier: HTML detection.
- `docs/checks/AND-01.md`, `AND-03.md`, `IOS-01.md`, `docs/verified-on.md`, CHANGELOG.
- Not breaking.
