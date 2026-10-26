# Changelog

All notable changes to PassKi. Versions before 1.0 are alphas: the API may still change, but every change is
listed here.

## 0.1.0-alpha05 (unreleased)

### Changed
- **BREAKING: signals return `PasskeyResult<SignalOutcome>`.** `SignalOutcome` is `Applied` or `Skipped(reason)`.
  When the OS or credential provider can't apply a signal, you now get `Success(SignalOutcome.Skipped(reason))`
  instead of `Failure(PasskeyError.NotSupported)`, so `onFailure` only sees problems worth logging
  (`Misconfigured`, `InvalidOptions`, `Unknown`). On JVM desktop every signal is skipped.
  **Migrating from alpha04:** drop any `NotSupported` special case for signals; code that only used
  `onSuccess` / `onFailure` keeps compiling.
  ```kotlin
  // before
  passkeys.signals.unknownCredential(rpId, id).onFailure { if (it !is PasskeyError.NotSupported) log(it) }
  // after
  passkeys.signals.unknownCredential(rpId, id).onFailure { log(it) }
  ```
- **BREAKING (named arguments): `userId` is now `userHandle`** in `PasskeySignals.acceptedCredentials` and
  `currentUserDetails`, and in passki-test's `FakeSignal` and `FakeAuthenticator.retainOnly`. It is the WebAuthn user
  handle (`user.id` of the creation options, also `PasskeyAssertion.userHandle`), never your account id. A value that
  isn't base64url fails with `InvalidOptions` naming `userHandle`, with a fix hint that says where the handle comes
  from. **Migrating from alpha04:** rename `userId =` to `userHandle =`; positional calls are unaffected.
- **BREAKING (behaviour): one passkey sheet at a time.** While `register`, `signIn` (modal or `ImmediateOnly`),
  `signInOrPassword` or `upgrade` is pending, another of these calls now fails at once with
  `PasskeyError.Interrupted` instead of competing with it (before, what happened was up to each platform). The
  pending call carries on. Autofill sign-in waits for
  it to finish, as it already did on iOS. `Interrupted`'s message now covers both causes.
  **Migrating from alpha04:** nothing to change if you already treat `Interrupted` as "retry later"; don't show it as
  an error after a double tap.
- **Threads and cancellation are documented.** Every call is main-safe, and cancelling the calling coroutine
  withdraws the request (`CancellationException`, no late result); see the `Passkeys` KDoc and
  `docs/platforms.md#threads-cancellation-and-one-sheet-at-a-time`. Behaviour was already like this; it is now part of
  the contract and tested on Android, iOS and the web.
- **Enums and sealed types say whether they can grow.** Their KDoc starts a paragraph with "Open:" (keep an `else`
  branch) or "Closed:" (`PasskeyResult`), and CI checks every public one.
- **AND-03 errors name the key to add** (Android). Every `Misconfigured` error with check ID `AND-03` (from a sign-in,
  a registration or a signal) now ends with the SHA-256 fingerprint of each certificate the running app is signed
  with and its `android:apk-key-hash:` origin, so the fix can be copied from logcat or a crash report, including the
  Play App Signing key of a Play Store build. On an emulator the hint adds that some emulator images fail RP ID
  validation even when `assetlinks.json` is right. `PasskeyDoctor` reads the fingerprints the same way.
- **A web page at a well-known path gets its own message.** When `assetlinks.json` or
  `apple-app-site-association` comes back as HTML (usually a single-page app's catch-all route), `PasskeyDoctor`,
  `passkiCheck` / `passkiVerify` and the web verifier report AND-01 / IOS-01 as "served a web page" with a hosting
  fix, instead of "not valid JSON". The text lives in `docs/checks/catalog.json` (`html`).

- **Errors with a docs page print it:** `Misconfigured` and `InvalidOptions` with a check ID now include
  `docsUrl=...` in `toString()`, after the check ID. Errors without one print as before.

### Added
- **Results carry the RP ID: `PasskeyRegistration.rpId` and `PasskeyAssertion.rpId`.** The RP ID the ceremony ran
  for: the options' RP ID, or on the web the page's host when the options name none. Pass it to signals, such as
  `signals.unknownCredential(assertion.rpId, assertion.credentialId)`, instead of parsing the options again.
  passki-test's `FakePasskeys` fills it the same way (the origin's host when the options name none).
- **`PasskeyResult` helpers named as in `kotlin.Result`:** `fold(onSuccess, onFailure)`, `mapError`, `recover`,
  `isSuccess`, `isFailure`, `errorOrNull()` and `toResult()` (a `kotlin.Result` whose failure is a `PasskeyException`
  with the original error).
- **`PasskeyError.category` and `PasskeyErrorCategory`:** `UserCancelled` (`Cancelled`, `Interrupted`),
  `NoCredential`, `AlreadyExists`, `Unsupported`, `Setup` (`Misconfigured`, `InvalidOptions`) and `Unexpected`
  (`Unknown`). The set is closed: a `when (error.category)` needs no `else` and keeps compiling when a release adds an
  error type, which always joins an existing category.
- **`PasskeyError.isUserCancellation`:** true for `Cancelled` and `Interrupted`, when no error should be shown.
- **`capabilities()` KDoc says it is cheap,** so it can be called before every sign-in.
- **`passki-bom`:** keeps `passki`, `passki-compose`, `passki-test` and `passki-demo` on one version; declare them
  without a version. In a KMP source set:
  `implementation(project.dependencies.platform("com.alirezaiyan.passki:passki-bom:<version>"))`.
- **Works in minified Android release builds without app-side rules.** `passki` now ships a consumer keep rule that
  keeps Credential Manager exception names, so the debug log and `PasskeyError.Unknown.platformCode` still read
  `CreateCredentialCancellationException` in an R8 build, and the log names PassKi's own errors (`Cancelled`) without
  relying on class names R8 renames. CI builds a minified consumer app against every change.
- **Release and project policies:** [versioning and deprecation](docs/versioning.md) (what alpha, beta and 1.0
  promise), `SECURITY.md` (private reporting through GitHub security advisories), `CODE_OF_CONDUCT.md`, issue and pull
  request templates, Renovate for dependency updates (holding the Kotlin 2.2 floor), and KDoc `@sample`s for
  `Passkeys`, `PasskeyResult` and `FakePasskeys`. Releases now wait until every artifact resolves from Maven Central
  and publish the API reference to GitHub Pages, one folder per version.
- **Docs: a short README and pages that start from your situation.** The README is now one screen with a quickstart;
  everything it held moved to `docs/` without loss (`docs/README.md` lists every page). New: a
  [quickstart](docs/quickstart.md) for your own server, guides on passkeys as an extra sign-in, what to show users,
  choosing your RP ID, user names, a recommended server API, verifying on the server (webauthn4j and
  java-webauthn-server, compiled and run in CI), upgrading users and troubleshooting, plus a
  [security model](docs/security-model.md) and `llms.txt` on passki.alirezaiyan.com. CI keeps the README under 150
  lines, the error table complete and the "no network calls" claim true (`ci/check-network-use.py`).
- **Logging: `PassKi.logLevel` and `PassKi.logHandler`.** By default (`PasskiLogLevel.Error`) PassKi logs every
  failure with a fix hint (`Misconfigured`, `InvalidOptions`) and every `Unknown`, once per failed call, with the
  check ID, fix and docs link: logcat tag `PassKi` on Android, `NSLog` on iOS, the browser console on the web,
  standard error on the JVM. The user closing the sheet, `NoCredentials` and `AlreadyRegistered` are not logged at
  this level. `Warn`, `Info` and `Debug` add more, up to each call's start and how each platform error was mapped.
  A handler (`PasskiLogHandler`, a `fun interface`) routes entries to Timber, Kermit or a crash reporter instead.
  Logs never contain challenges, user handles, options or response JSON, passwords, PRF outputs or large-blob data;
  credential IDs are shortened. `PasskiLogRedactionTest` checks a full debug trace for these. passki-test's
  `FakePasskeys` logs the same way. **If your app logs PassKi errors itself**, remove that code or set
  `PassKi.logLevel = PasskiLogLevel.None` to avoid duplicate lines.
- **`PasskeySignals.syncAccount(rpId, userHandle, credentialIds, name, displayName)`.** One call that tells the
  credential provider an account's complete list of passkeys and its current label, for example when the passkey
  settings screen opens. It returns `Applied` if the provider took either signal and stops at the first failure.
  It has a default implementation, so your own `PasskeySignals` implementations keep compiling.
- **`passkiSetup` warns about an RP ID shared with other apps.** When the RP ID is a bare domain (`example.com`) and
  its live well-known files already list apps other than yours, the task warns that the RP ID can't change later
  without orphaning passkeys and suggests a product subdomain. Best effort: skipped offline, never fails the task;
  turn it off with `-Ppasski.setup.sharedRpIdCheck=false`.

### Fixed
- **Immediate sign-in in Chromium browsers that only claim it** (js and wasmJs). Some Chromium builds report
  `immediateGet` from `getClientCapabilities()` yet reject `mediation: "immediate"` as an unknown enum value, so
  `SignInMode.ImmediateOnly` failed with `InvalidOptions`. `capabilities().immediateSignIn` now asks the browser
  itself: once per page, a `navigator.credentials.get` with `mediation: "immediate"` and an already-aborted signal,
  which the browser rejects before any UI or authenticator (a `TypeError` means it doesn't know the value). If a sign-in
  still meets that `TypeError`, `ImmediateOnly` fails with `NotSupported`, so callers can fall back to `Modal`, and
  `immediateSignIn` is false from then on. Other `TypeError`s are still `InvalidOptions`.
- **Error messages no longer repeat the JSON they failed on.** `InvalidOptions` for malformed options JSON, and
  `Unknown` for a malformed platform response, included kotlinx.serialization's "JSON input: …" excerpt, which could
  hold the challenge or user handle. They now keep only the reason.

## 0.1.0-alpha04 (2026-10-25)

### Added
- **Passkeys in the browser** (js and wasmJs). `Passkeys()` uses `navigator.credentials`: register, sign in,
  autofill (conditional mediation), upgrade (conditional create), signals, PRF and largeBlob, each where the browser
  supports it, as `capabilities()` reports. The browser checks the RP ID against the page, so options may leave it out.
  Browser errors map to the usual `PasskeyError`s, such as `NotAllowedError` → `Cancelled` and `InvalidStateError` →
  `AlreadyRegistered`. `signInOrPassword` is `NotSupported`: browsers have no combined sheet.
- **`passkiOrigin` task:** `./gradlew passkiOrigin --sha256=<fingerprint> [--package=<applicationId>]` prints the
  `android:apk-key-hash:` origin and the `assetlinks.json` statement for a raw SHA-256, such as the Play App Signing
  key, which is in no local keystore. Works in any project, with or without an Android app.
- **Docs:** README sections on building a "Sign in with a passkey" button (what `Modal` shows on a device with no
  passkey, and when to use `ImmediateOnly`), testing your server with `FakePasskeys`, and "D. Ship it without the
  plugin". `docs/setup.md` covers by-hand setup in full: every signing key, the origin formula, the App Store
  provisioning profile step and Apple's CDN cache.

### Changed
- **`passki-test` exposes `kotlinx-coroutines-core` as `api`.** `FakePasskeys` is all `suspend`; JVM server tests can
  call `runBlocking` without adding coroutines themselves.
- **webauthn4j server snippet** uses `ServerProperty.builder()` instead of the constructor deprecated in
  webauthn4j 0.30.0, names the webauthn4j version it targets, and adds `parseRegistration` / `parseAuthentication`
  helpers that catch `RuntimeException`. webauthn4j throws `NullPointerException` (not `DataConversionException`) on
  malformed JSON such as `{"id":"x"}`, which unguarded servers answer with a 500.

## 0.1.0-alpha03 (2026-10-24)

### Fixed
- The JVM artifacts (`passki-jvm`, `passki-compose-jvm`, `passki-test-jvm`, `passki-demo-jvm`) are Java 11 bytecode,
  like the Android ones. In 0.1.0-alpha02 they were Java 21 (class file 65), so desktop apps and JVM tests on JDK 11-20
  failed with `UnsupportedClassVersionError`. CI and the release workflow now fail if a published library class is
  newer than Java 11.

### Changed
- Releases upload only what Maven Central reads: each artifact, its `.asc` signature and md5/sha1 checksums (656 files
  instead of 1,640). No checksums of signatures and no sha256/sha512 any more (`com.vanniktech.maven.publish` 0.37.0).

## 0.1.0-alpha02 (2026-10-24)

### Added
- **On Maven Central:** `passki`, `passki-compose`, `passki-test`, `passki-demo` and the Gradle plugin
  (`com.alirezaiyan.passki`) resolve from `mavenCentral()`; no `mavenLocal()` build needed. Releases are signed and
  published by `.github/workflows/release.yml` from a `v<version>` tag.
- **Errors name their setup check:** `PasskeyError.checkId` and `docsUrl`. `Misconfigured` errors carry the catalog ID
  that covers them (`AND-03` asset links, `DEV-01` credential provider, `AND-06` initialisation, `IOS-02` association),
  and `InvalidOptions` for a missing or malformed RP ID carries `CFG-01`. The same ID `PasskeyDoctor`, `passkiCheck`
  and the web verifier report. `PasskeyException`'s message adds the docs link; `FakePasskeys` reports `CFG-01` too.
- **`passki-demo`** (new artifact `com.alirezaiyan.passki:passki-demo`): `PasskiDemoServer`, a client for the hosted
  demo relying party, so you can try passkeys in your own app before you have a domain or a server. It returns the
  server's full verdict (`DemoVerdict`) for every response. Ktor lives in this artifact only, never in `passki`.
- **Gradle plugin, try mode:** `passki { useDemoServer() }` sets the RP ID to `passki.alirezaiyan.com`, and
  `passkiSetup` then registers the app with the demo server for 7 days (Android package and signing fingerprints,
  iOS `TEAM.bundle`), printing every request it sends and the server's answer.
- **Gradle plugin, transparency:** `passkiSetup` ends with a summary of every file it created, changed or left
  unchanged, with a diff for your own files (entitlements, `xcconfig`). `passkiSetup --preview` prints the same summary
  and writes or sends nothing.
- **Gradle plugin, iOS-only mode:** `passkiSetup` and `passkiCheck` are registered without `com.android.application`.
  Android is skipped with the reason, and a plugin applied to the wrong module (such as `shared`) names the module to
  apply it to instead of silently registering no tasks.
- **Demo relying party:** self-service Android registration (`/api/demo/android-apps`): a package and its signing
  keys are listed in the demo `assetlinks.json` and accepted as origins for 7 days. Re-registering is free.
- **`passki-compose`** (new artifact `com.alirezaiyan.passki:passki-compose`), Compose Multiplatform helpers:
  - `rememberPasskeys()`
  - `rememberPasskeySignIn(requestOptions, passkeys = rememberPasskeys(), mode = Modal)`, which returns a headless
    `PasskeySignInState` (`isRunning`, `lastError`, `signIn()`). Double taps join the running request, and
    `Cancelled` is never stored as an error.
  - Parameters follow the Compose convention in every helper: required ones first, then `modifier`, then optional
    ones with defaults, `passkeys` first among them.
  - `PasskeySignInButton`, a Material 3 button.
  - `PasskeyUsernameField`, a username field with passkey autofill. On iOS it hosts a UIKit `UITextField`, because
    Compose's username content type does not trigger passkey QuickType. On Android it anchors the request to its own
    view, so it also works inside a `Dialog` or `ModalBottomSheet` (separate windows).
- **`SignInMode.Autofill`** (conditional mediation): passkeys as suggestions in the keyboard or QuickType bar of the
  focused username field. It needs Android 15 (API 35)+ or iOS 16+. `PasskeyCapabilities.autofill` reports support.
  - The call suspends until the user picks a passkey; cancelling the coroutine withdraws the offer.
  - Android restarts the autofill session, so the suggestion appears on the first focus. It attaches the request to
    the focused field of whichever app window has focus (activity, dialog or bottom sheet), and waits for that window
    to have focus first, so a field behind a dialog can't cancel a passkey being picked on top.
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
  demo, an "Autofill in dialog" demo, and autorun flags (iOS `-passkiAutorun`, Android `--ez passkiAutorun true`).
  Its debug build is signed with a committed sample keystore, which is not for release.

### Changed
- Sample: signing out clears the username field. With text left in it, Gboard showed the text as a word suggestion
  instead of the passkeys. Found in an end-to-end run on the Android 16 emulator (`docs/verified-on.md`).
- Docs rewritten in plain language: a "Words used here" table, one name per concept ("demo server"), numbered steps
  with one idea per sentence. Path B now shows the `plugins { }` line and creates `passkeys` before using it.
  `docs/setup.md` describes demo registration through `passkiSetup` (Android and iOS) and lists every demo endpoint.
- CI checks the docs: `ci/doc-snippets.py` compiles every Kotlin example in the README and `docs/` against the
  published artifacts on Kotlin 2.2.21, configures every `passki { }` block, and checks every dependency coordinate.
- CI (GitHub Actions): library, plugin and web tests; a clone-and-run job for the sample against the demo server; iOS
  tests, API dumps, the Xcode sample build, and consumer apps on Kotlin 2.2.21 and on AGP's built-in Kotlin with
  `compileSdk` 35 (`ci/consumers/`). Docs links are checked too.
- The Gradle plugin's build resolves its JDK 17 toolchain itself (Foojay), so a fresh clone on a machine without JDK 17
  configures.
- Wider compatibility for apps: the libraries publish Kotlin 2.2 metadata and depend on `kotlin-stdlib` 2.2.21 (JVM and
  Android), so Android apps on Kotlin 2.1+ can use them; before, they needed Kotlin 2.4. Their AARs require
  `compileSdk` 35 (the `androidx.credentials` floor) instead of PassKi's own 37. `passki-demo` uses Ktor's Android engine
  instead of OkHttp, whose 5.x AAR also required `compileSdk` 37.
- PassKi is built with Kotlin 2.2.21 instead of 2.4.20, so iOS, JS and Wasm apps on Kotlin 2.2+ can use it (klibs can
  only be read by the compiler that built them or a newer one). Dependencies moved to releases built with Kotlin 2.2:
  kotlinx-serialization 1.9.0, Ktor 3.3.3 (`passki-demo`), cryptography 0.5.0 (`passki-test`), Compose Multiplatform
  1.10.3 (`passki-compose`). The iOS library now carries `-rpath /usr/lib/swift`, so plain Kotlin/Native executables
  (such as iOS test binaries) find the Swift runtime the Signal API bridge needs.
- README: starts with three paths (run the sample, try it in your app, ship it). Reference sections moved verbatim to
  `docs/` (platforms, compose, autofill, extensions, setup, verified-on); Install states the Kotlin and compileSdk
  requirements.
- Sample, iOS: the only per-developer value is `TEAM_ID`, in the gitignored `iosApp/Configuration/Local.xcconfig`
  (copy `Local.xcconfig.example`). The Xcode project reads `DEVELOPMENT_TEAM` from it, and the bundle ID gets the Team
  ID as a suffix. The sample's plugin block uses `useDemoServer()`, so `passkiSetup` registers your iOS app with the demo
  server.
- Sample: runs the Setup check at launch and shows a banner with the failing check IDs; removed the KMP template
  leftovers (`Greeting`, placeholder tests).
- `PasskeyError.Misconfigured` and `InvalidOptions` include `checkId` in `equals`, `hashCode` and `toString` (omitted
  from `toString` when null). The existing constructors are kept; new ones take the ID.
- Gradle plugin: `passki { rpId }` must be a bare domain. `https://example.com/` or `Example.com` fail when a task runs,
  with the corrected value in the message.
- Gradle plugin: `passkiSetup` and `passkiCheck` are the two front-door tasks (group `passki`, with
  `passkiFingerprints`). `passkiWellKnown` and `passkiVerify` moved to the group `passki advanced`; both still work, and
  `passkiVerify` points to `passkiCheck`.
- The sample talks to the demo server through `passki-demo` instead of its own copy of the client.
- `SignInMode` has a new entry, `Autofill`. Exhaustive `when`s need an `else` branch, as the KDoc already advised.
- The constructors of `PasskeyAssertion`, `PasskeyRegistration` and `PasskeyCapabilities` have new parameters. They
  are `@InternalPasskiApi`, so only code that opted in is affected. Use the same version of `passki`, `passki-test`
  and `passki-compose`.
- New binary API on Android: `Passkeys.autofillSignIn(optionsJson, anchor: View)` (`@InternalPasskiApi`), which
  `passki-compose` uses to anchor autofill to its own view.
- Android now requires the RP ID (`rp.id` / `rpId`) in options, as iOS already did, and both platforms require it to
  be a bare domain. A missing or malformed RP ID fails early with `InvalidOptions` instead of an opaque provider
  error. New `@InternalPasskiApi` binary API: `WebAuthnOptions.requireRpId`.

### Fixed
- iOS: passkey autofill no longer competes with a modal request. Starting `register`, `signIn` or
  `signInOrPassword` cancels a pending autofill request, and autofill waits until the modal request ends before
  offering passkeys again. Previously the autofill retry could restart while a sign-in sheet was open.
- JSON `null` in extension inputs and outputs (for example `"prf":{"results":{"first":"…","second":null}}`) is read
  as absent. Previously a `null` `second` dropped the whole PRF result, and a `null` input was `InvalidOptions`.
- An empty `userHandle` from the provider is reported as `PasskeyAssertion.userHandle == null` on Android, as on iOS.
- Documented that iOS below 17.4 ignores `excludeCredentials`, so `register` there never returns `AlreadyRegistered`.

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
