# Changelog

All notable changes to PassKi. Versions before 1.0 are alphas: the API may still change, but every change is
listed here.

## 0.1.0-alpha02 (unreleased)

### Added
- **`passki-compose`** (new artifact `com.alirezaiyan.passki:passki-compose`), Compose Multiplatform helpers:
  - `rememberPasskeys()`
  - `rememberPasskeySignIn(passkeys, mode) { options }`, which returns a headless `PasskeySignInState` (`isRunning`,
    `lastError`, `signIn()`). Double taps join the running request, and `Cancelled` is never stored as an error.
  - `PasskeySignInButton`, a Material 3 button.
  - `PasskeyUsernameField`, a username field with passkey autofill. On iOS it hosts a UIKit `UITextField`, because
    Compose's username content type does not trigger passkey QuickType.
- **`SignInMode.Autofill`** (conditional mediation): passkeys as suggestions in the keyboard or QuickType bar of the
  focused username field. It needs Android 15 (API 35)+ or iOS 16+. `PasskeyCapabilities.autofill` reports support.
  - The call suspends until the user picks a passkey; cancelling the coroutine withdraws the offer.
  - Android restarts the autofill session, so the suggestion appears on the first focus.
  - iOS offers the passkeys again after a dismissal.
- **Signal API on iOS 26+** through an embedded Swift bridge to `ASCredentialUpdater`. Apps need no setup or linker
  flags. Below iOS 26 it returns `NotSupported`; a missing association returns `Misconfigured` with an AASA hint.
- **Typed PRF and largeBlob extensions:**
  - `PasskeyExtensions.withPrf` / `withLargeBlob` / `withLargeBlobRead` / `withLargeBlobWrite`, and `LargeBlobSupport`.
  - Outputs: `PasskeyAssertion.prf` / `largeBlob` / `largeBlobWritten`, `PasskeyRegistration.prfEnabled` / `prf` /
    `largeBlobSupported`, and `PrfResults`.
  - iOS maps them to AuthenticationServices (PRF on iOS 18+, largeBlob on iOS 17+); Android passes them to the
    credential provider.
- **passki-test:**
  - `FakePasskeys.autofillSupported`. Autofill models a keyboard suggestion: with a matching passkey it completes at
    once (as if the user picked it); with none it suspends until cancelled instead of returning `NoCredentials`.
  - Deterministic, test-only PRF and in-memory large blobs in `FakeAuthenticator`, switched with `prfSupported` /
    `largeBlobSupported`.
  - A recorded Android registration and assertion pair from the demo relying party.
- **Gradle plugin:**
  - `passkiSetup` generates the well-known files, writes the iOS entitlements and wires them through
    `Config.xcconfig` (never `project.pbxproj`), discovers the Team and bundle IDs, and writes `server-config.json`
    with verifier snippets.
  - `passkiCheck` runs the catalog checks (AND-01..05, IOS-01..06, SRV-01..03) and writes JSON and HTML reports.
    It exits non-zero on failure, so it works as a CI gate.
- **Check catalog:** stable IDs (`AND-*`, `IOS-*`, `SRV-*`, `CFG-01`, `DEV-*`) shared by `passkiCheck`, the
  `PasskeyDoctor` (`DoctorCheck.id`, new DEV-01/DEV-02 checks) and the dashboard, with a page per ID in `docs/checks/`.
- **Setup dashboard and demo relying party** at https://passki.alirezaiyan.com (source in `passki-web/`): a verifier,
  a generator, shareable reports, per-check docs and self-service iOS demo registration.
- **Sample app:** runs against the demo relying party with server verification. It has a Setup check screen, a PRF
  demo, and autorun flags (iOS `-passkiAutorun`, Android `--ez passkiAutorun true`). Its debug build is signed with
  a committed sample keystore, which is not for release.

### Changed
- `SignInMode` has a new entry, `Autofill`. Exhaustive `when`s need an `else` branch, as the KDoc already advised.
- The constructors of `PasskeyAssertion`, `PasskeyRegistration` and `PasskeyCapabilities` have new parameters. They
  are `@InternalPasskiApi`, so only code that opted in is affected. Use the same version of `passki`, `passki-test`
  and `passki-compose`.

## 0.1.0-alpha01

### Added
- **`passki` core:** `Passkeys()` with no platform handles.
  - Calls: `register`, `signIn` (`Modal`, `ImmediateOnly`), `signInOrPassword`, `upgrade` (conditional create),
    `signals` (WebAuthn Signal API) and `capabilities()`.
  - Typed `PasskeyError`s with fix hints and `PasskeyResult`.
  - Android 9+ uses Credential Manager and finds the foreground Activity through `androidx.startup`. iOS 16+ uses
    AuthenticationServices. JVM and browser targets compile and return `NotSupported`.
- **`passki-test`:** `FakePasskeys` and `FakeAuthenticator` produce real ES256 WebAuthn responses that webauthn4j
  accepts. `TestOptions` builds server-style options, and recorded real-device fixtures back a contract test.
- **Gradle plugin `com.alirezaiyan.passki`:** `passkiFingerprints`, `passkiWellKnown` (`assetlinks.json` and
  `apple-app-site-association`) and `passkiVerify`.
- **`PasskeyDoctor`:** on-device setup diagnostics for Android and iOS (platform support, initialisation, hosted
  well-known files, the origin your server must allow).
- Dokka API docs and ABI validation.
