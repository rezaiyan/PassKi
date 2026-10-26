# Design

## Context

- AND-03 hints come from `assetLinksHint(context)` in `passki/src/androidMain/.../AndroidErrorMapper.kt`, used for DOM
  `SecurityError`, RP ID validation failures and signal rejections. It knows the RP ID and package, not the signing
  certificate.
- `AndroidDoctorFacts` already reads signing certificates with `GET_SIGNING_CERTIFICATES` (API 28+) for
  `PasskeyDoctor`'s AND-03 check, and `PasskiInitializer` gives PassKi an application context.
- Hosted-file checks exist three times: `internal/doctor/AssetLinksCheck.kt` / `AasaCheck.kt` (commonMain),
  `passki-gradle-plugin/.../WellKnownVerifier.kt` and `passki-web/lib/checks/assetlinks.ts` (TypeScript). Catalog fix
  texts live in `docs/checks/catalog.json`.
- `docs/verified-on.md` records an API 36 emulator passing, so the Lexicon failure is image-specific.

## Goals / Non-Goals

**Goals:**
- An AND-03 error alone is enough to fix a wrong or missing fingerprint.
- HTML at a well-known path gets a named failure everywhere.

**Non-Goals:**
- Finding the root cause of the failing emulator image (tracked as a docs task: record image and Play services version).
- New check IDs. HTML responses stay under AND-01 / IOS-01; the catalog stays stable.
- Calling the Digital Asset Links API from the device.

## Decisions

1. **Reuse the doctor's certificate reader.** Move the fingerprint and origin computation out of `AndroidDoctorFacts`
   into a small internal `AppSigning` helper used by both the doctor and the error mapper. The origin uses the same
   base64url (no padding) encoding as `passkiOrigin`. Alternative considered: compute in the mapper only, which would
   duplicate the API-level handling.
2. **Read lazily, cache per process.** Signing info is read only when an AND-03 error is built, then cached; the
   happy path pays nothing. Failures to read return an empty list and the hint says so.
3. **Emulator detection by `Build` properties.** Use the usual signals (`Build.FINGERPRINT` starting with `generic` or
   containing `emulator`, `Build.HARDWARE` `ranchu` / `goldfish`, `Build.PRODUCT` containing `sdk`). A false negative
   only drops the note; a false positive only adds a line, so a simple heuristic is enough. Alternative: no detection,
   always mention emulators, rejected because it would push every real misconfiguration towards "try a phone".
4. **HTML detection rule shared by text, not code.** Each consumer applies the same rule (content type `text/html`, or
   first non-whitespace byte `<`) before JSON parsing, and takes the fix text from the catalog. A shared test fixture
   (an HTML body) is added to each consumer's tests so they can't drift.
5. **Shared-apex warning from live files, best effort.** The plugin has no public suffix list, so "bare domain" is a
   heuristic: two labels, or three under a country second level (`example.co.uk`); a miss only skips the warning.
   The fetch reuses the plugin's `Http` client with a 5-second timeout, honours `passki.check.baseUrl` (for tests),
   is skipped with `--offline`, and can be turned off with `passki.setup.sharedRpIdCheck=false`; TestKit tests turn
   it off by default so they stay offline. Only a warning: a shared apex is sometimes intended (one account system
   for several apps).
6. **Hint length.** The evidence goes after the existing hint sentence; with two certificates the hint stays under
   about 400 characters. `toString()` already includes the hint.

## Risks / Trade-offs

- [Hint text becomes long in logs] → One line per certificate, no line breaks; fingerprints are public data (they are
  in `assetlinks.json`), so no privacy issue.
- [Fingerprint of the debug key misleads on a release build] → The hint shows whatever key signed the running app,
  which is exactly what must be listed; the docs page explains debug vs upload vs Play App Signing keys.
- [Three implementations of the HTML rule] → Shared fixture and catalog-sync tests in each consumer.

## Migration Plan

Ships in the next alpha. Only message text changes; apps matching on `message` strings (not supported) might notice.
