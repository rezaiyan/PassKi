# Tasks

## 1. Signing evidence in AND-03 (Android)

- [x] 1.1 Extract certificate reading from `AndroidDoctorFacts` into an internal `AppSigning` helper returning fingerprints and `android:apk-key-hash:` origins, cached per process; verify with an Android host test that a known certificate yields the same origin as `passkiOrigin`
- [x] 1.2 Make `assetLinksHint` append fingerprints and origins (or the "couldn't read" fallback) and use it for ceremony and signal AND-03 errors; verify with `AndroidErrorMapper` tests for one certificate, two certificates and unreadable signing info
- [x] 1.3 Add emulator detection and the emulator note to the AND-03 hint; verify with host tests that fake `Build` values add or omit the note
- [x] 1.4 Switch `PasskeyDoctor`'s AND-03 check to `AppSigning`; verify existing doctor tests still pass

## 2. HTML at a well-known path (all consumers)

- [x] 2.1 Add the web-page message and fix to `docs/checks/catalog.json` for AND-01 and IOS-01, and describe the SPA catch-all case on `docs/checks/AND-01.md` and `IOS-01.md`; verify the catalog sync tests pass
- [x] 2.2 Detect HTML in `AssetLinksCheck` and `AasaCheck` (commonMain) before parsing; verify with commonTest cases for an HTML body, a `text/html` content type and malformed JSON
- [x] 2.3 Detect HTML in `passki-gradle-plugin` `WellKnownVerifier`; verify with plugin unit tests using the same HTML fixture
- [x] 2.4 Detect HTML in `passki-web/lib/checks/assetlinks.ts` and the AASA check; verify with `vitest` cases using the same fixture

## 3. Shared-apex warning (Gradle plugin)

- [x] 3.1 Add the shared-apex warning to `passkiSetup` (registrable-domain check, live file fetch with timeout, skipped offline); verify TestKit cases for an apex listing another package, a subdomain, and an unreachable host

## 4. Docs and release notes

- [x] 4.1 Add "Still failing?" to `docs/checks/AND-03.md` (evidence in the hint, Doctor, emulator images, Play App Signing); verify `python3 ci/check-docs-links.py` passes
- [x] 4.2 Record the failing AVD (device profile, system image, Play services version) in `docs/verified-on.md`; verify the entry names all three
- [x] 4.3 Add a CHANGELOG entry under the unreleased alpha; verify `./gradlew checkLegacyAbi` and `python3 ci/doc-snippets.py` pass

## 5. Integration check

- [ ] 5.1 Run the Android sample against a domain without the app's fingerprint and confirm logcat shows the fingerprint and origin in the AND-03 error
