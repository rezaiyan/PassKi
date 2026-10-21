# PassKi Plan 3: Compose helpers, Autofill sign-in, iOS Signals, PRF/largeBlob

> **For agentic workers:** Use claude-kit:implement to execute this plan task-by-task.

**Goal:** Close the remaining differentiators from the spec:
- **Compose Multiplatform helpers** that make passkeys a few lines in UI code
- **autofill sign-in**: passkeys offered in the keyboard / QuickType bar of a username field
- **the iOS Signal API**, which today returns NotSupported
- **typed PRF and largeBlob extensions**

These ship as alpha02.

**Architecture:**
- **`passki-compose`:** a new artifact, Compose Multiplatform, same targets as `passki`.
  - Headless state: `rememberPasskeySignIn`.
  - Material 3 conveniences: `PasskeySignInButton`, `PasskeyUsernameField`.
  - Depends on `passki` via `api`.
- **Autofill:** a new `SignInMode.Autofill` in core. `signIn(options, Autofill)` suspends until the user picks a passkey from the keyboard suggestions; cancelling the coroutine withdraws the offer.
  - Android: `View.setPendingCredentialRequest` (androidx.credentials 1.5+, Android 15 / API 35+), on the focused view.
  - iOS: `ASAuthorizationController.performAutoFillAssistedRequests()` (iOS 16+). It needs a focused text field with the username content type; Compose MP ≥1.9 maps `ContentType.Username` to `UITextContentTypeUsername`.
  - Capability flag: `PasskeyCapabilities.autofill`. Adding it is binary-safe because the constructor is internal.
- **iOS signals:** `ASCredentialUpdater` is a pure Swift class (iOS 26, async, mangled Swift symbols only), so Kotlin/Native can't call it directly.
  - **Spike A:** a Swift static library with `@_cdecl` C entry points, embedded in the klib through a cinterop `.def` (`staticLibraries`).
  - **Fallback B:** a public Kotlin interface `IosSignalBridge` that the app implements in five lines of Swift and registers at launch. The README gives the snippet.
- **PRF / largeBlob:**
  - **Android:** JSON already passes through; add typed builders and parsers in common code.
  - **iOS:** the ObjC headers exist (iOS 17: largeBlob; iOS 18: PRF; `NS_REFINED_FOR_SWIFT` only hides them from Swift), so map JSON ↔ `ASAuthorizationPublicKeyCredentialPRF*` / `LargeBlob*` directly in Kotlin/Native.

**Tech stack:**
- Compose Multiplatform 1.12.1 (catalog), material3 (catalog)
- androidx.credentials 1.6.0
- AuthenticationServices on iOS 16–26
- Swift 6 toolchain (Xcode 26.3) for the bridge spike

Run Gradle through `ctx_execute`; the Bash hook blocks `./gradlew`.

---

## Decisions (defaults)

| Decision | Value |
|---|---|
| New artifact | `com.alirezaiyan.passki:passki-compose` |
| Headless API | `rememberPasskeys()`, `rememberPasskeySignIn(passkeys, requestOptions: suspend () -> String, mode)` → state with `isRunning`, `lastError`, `signIn()` |
| UI API | `PasskeySignInButton(state, onSignedIn)`, `PasskeyUsernameField(value, onValueChange, passkeys, requestOptions, onSignedIn)` |
| Autofill errors | Never shown as UI errors: a dismissed suggestion just means no result. The coroutine keeps waiting until the field leaves composition. |
| Autofill below Android 15 | `NotSupported`. `capabilities().autofill = false`. |
| PRF API (common) | Extension helpers `PasskeyExtensions.withPrf(optionsJson, first, second?)` + `PasskeyAssertion.prf: PrfResults?` (+ registration `prfEnabled`) |
| largeBlob API | `withLargeBlob(optionsJson, support = Required/Preferred)`, `withLargeBlobRead`, `withLargeBlobWrite(bytes)`; outputs `largeBlob` on results |
| iOS signals | Spike A first. If any step needs app-side linker flags or breaks dynamic frameworks, ship B. |

## Waves (parallel where files don't overlap)

### Wave 1 (parallel)

**Task 1: iOS Swift bridge spike**
- **Files:** new `passki-ios-bridge/` with `PassKiSignals.swift`, a build script, and `passki/src/nativeInterop/cinterop/passkiSwift.def`; `passki/build.gradle.kts` (cinterop for ios targets only).
- [ ] Write a Swift file:
  - `@_cdecl("passki_report_unknown")`, `passki_report_all_accepted`, `passki_report_details`
  - each takes C strings or bytes plus a C callback `(int32 status, const char* message, void* ctx)`
  - internally calls `ASCredentialUpdater().reportUnknownPublicKeyCredential(relyingPartyIdentifier:credentialID:)`, `reportAllAcceptedPublicKeyCredentials(...)`, `reportPublicKeyCredentialUpdate(...)` inside a `Task`
  - guarded by `if #available(iOS 26, *)`; otherwise calls back with status `NOT_AVAILABLE`
  - Verify the exact Swift signatures against the SDK `.swiftinterface` / Xcode docs.
- [ ] Build `libPassKiSwift.a` for `iphoneos` (arm64) and `iphonesimulator` (arm64) with `xcrun swiftc -parse-as-library -emit-library -static -target arm64-apple-ios16.0[-simulator]`, wired as a Gradle `Exec` task feeding cinterop.
- [ ] Use `.def` `staticLibraries` / `libraryPaths` so the `.a` is embedded in the klib.
- [ ] Link test: the sample `iosApp` (static framework, SwiftUI app) must link with **no extra flags** in Xcode, and also the passki test binary (`iosSimulatorArm64Test`). If the K/N test binary needs `-L<toolchain>/usr/lib/swift/iphonesimulator` plus swift compatibility libs, record which `linkerOpts` it needs.
- [ ] **Decision gate.** Record the outcome in this plan:
  - If consumers need **zero** extra configuration, go with A.
  - Otherwise go with B, and delete the spike code.
- [ ] Commit (A or B): `feat(ios): Signal API via Swift bridge`

**Task 2: `passki-compose` module, headless state, sign-in button**
- **Files:** `passki-compose/build.gradle.kts`, `src/commonMain/.../compose/{RememberPasskeys,PasskeySignInState,PasskeySignInButton}.kt`, tests; `settings.gradle.kts`.
- [x] Module: same targets as `passki`, plugins `kotlinMultiplatform`, `androidMultiplatformLibrary`, `composeMultiplatform`, `composeCompiler`; explicit API; ABI validation; Dokka; publishing (artifactId `passki-compose`).
- [x] `rememberPasskeys(): Passkeys`
- [x] `PasskeySignInState`, a `@Stable` class:
  - members: `isRunning`, `lastError: PasskeyError?`, `suspend fun signIn(): PasskeyResult<PasskeyAssertion>`
  - guards against double taps
  - `Cancelled` is not stored as `lastError`
- [x] `rememberPasskeySignIn(passkeys, requestOptions, mode)`
- [x] `PasskeySignInButton(state, onSignedIn, modifier, onError, content = default "Sign in with a passkey")`: a Material3 Button, disabled while running, shows a small progress indicator.
- [x] **Tests**, commonTest with `FakePasskeys` from `passki-test` (test dependency), using `runComposeUiTest` where the compose test artifact supports all targets, otherwise state-only tests on jvm:
  - double tap → one call
  - `Cancelled` → no `lastError`
  - `Misconfigured` → `lastError` set
  - `isRunning` transitions
- [x] Commit: `feat(compose): passki-compose with sign-in state and button`

### Wave 2

**Task 3: `SignInMode.Autofill` core (Android + iOS)**
- **Files:** `Passkeys.kt` (enum + KDoc, `PasskeyCapabilities.autofill`), `android/AndroidPasskeys.kt`, `apple/IosPasskeys.kt`, `apple/AuthorizationSession.kt`, ABI dumps, tests.
- [ ] **Android:**
  - Requires API 35+, else NotSupported.
  - Take `activity.currentFocus` and set `PendingGetCredentialRequest(GetCredentialRequest(listOf(GetPublicKeyCredentialOption(json))), callback)` on it via the androidx `View.setPendingGetCredentialRequest` extension. Verify the exact extension name in the 1.6.0 jar.
  - Resume the coroutine on the callback.
  - On cancellation, clear it with `clearPendingGetCredentialRequest`.
  - No focused view → `Misconfigured("Autofill needs a focused text field", hint: use PasskeyUsernameField or focus your username field first)`.
  - Callback errors are not delivered (documented by Google), so the coroutine just keeps waiting.
- [ ] **iOS:**
  - `AuthorizationSession.performAutoFill(requests)` calls `performAutoFillAssistedRequests()`.
  - Cancellation calls `controller.cancel()`.
  - A 1001 after cancel is ignored.
  - Requires iOS 16+.
- [ ] **FakePasskeys:** Autofill behaves like Modal; flag `autofillSupported`.
- [ ] **Tests:**
  - mapper and pure parts
  - capability flags
  - FakePasskeys
- [ ] Commit: `feat(core): SignInMode.Autofill`

### Wave 3 (parallel)

**Task 4: `PasskeyUsernameField`**
- **Files:** `passki-compose/src/commonMain/.../PasskeyUsernameField.kt`
- [ ] A Material3 `OutlinedTextField` with `Modifier.semantics { contentType = ContentType.Username }` (verify the Compose 1.12 API name).
- [ ] While the field is focused and `capabilities().autofill`, run `signIn(requestOptions(), Autofill)` in a `LaunchedEffect` keyed on focus. Cancel when focus or composition ends.
- [ ] Deliver results via `onSignedIn`.
- [ ] Commit: `feat(compose): PasskeyUsernameField with passkey autofill`

**Task 5: PRF + largeBlob typed extensions**
- **Files:** `commonMain/.../PasskeyExtensions.kt` (public), `internal/ExtensionsJson.kt`, `apple/AppleRequests.kt` (map inputs/outputs), the result classes (new properties), tests, ABI dumps.
- [ ] **Common:**
  - builders that add `extensions.prf.eval{first,second}` (base64url) / `extensions.largeBlob` to options JSON
  - parsers from `clientExtensionResults`: `prf.enabled`, `prf.results.first/second`, `largeBlob.supported/blob/written`
- [ ] **iOS:**
  - registration: `prf` input → `ASAuthorizationPublicKeyCredentialPRFRegistrationInput` (checkForSupport / with input values), and `largeBlob` → `ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput(supportRequirement)`
  - assertion: PRF input values (`saltInput1/2`), largeBlob read/write
  - outputs → `clientExtensionResults` JSON in WebAuthn format
  - availability: iOS 18 (PRF), iOS 17 (largeBlob); otherwise the extension is omitted silently, matching browser behaviour
  - verify K/N names in the platform klib
- [ ] **Android:** JSON passthrough. The parser works on GPM output.
- [ ] **FakeAuthenticator:** deterministic PRF via HMAC-SHA256(credentialSecret, salt) so apps can test PRF-derived keys. Mark it test-only in KDoc.
- [ ] Commit: `feat(core): typed PRF and largeBlob extensions`

### Wave 4

**Task 6: Sample + emulator verification**
- [ ] Sample:
  - use `PasskeyUsernameField` + `PasskeySignInButton`
  - a "PRF" button showing the derived output (hex, first 8 bytes)
  - autorun steps for the non-UI parts
- [ ] **Android emulator (API 36):** focusing the username field shows the passkey suggestion in the keyboard; picking it signs in; PRF returns 32 bytes; the same salt gives the same output twice.
- [ ] **iOS simulator 26.3:**
  - focusing the field shows the QuickType passkey suggestion; picking it signs in
  - PRF 32 bytes, stable
  - Signals: `unknownCredential` then device-only sign-in → NoCredentials, if the provider honours it
- [ ] **Record results** in this plan; add the fixtures (an assertion pair if feasible).
- [ ] Commit: `test(sample): autofill, PRF, signals verified on emulators`

**Task 7: Docs + review**
- [ ] README: Compose section, autofill, PRF/largeBlob, the iOS signals status (A or B).
- [ ] Update the capability table.
- [ ] Dokka for `passki-compose`.
- [ ] Full gate:

  ```
  :passki:jvmTest :passki:testAndroidHostTest :passki:iosSimulatorArm64Test
  :passki-test:jvmTest :passki-compose:allTests(jvm/android/ios)
  :*:checkKotlinAbi :androidApp:assembleDebug
  ```

- [ ] Final review agent.
- [ ] Commit: `docs: Plan 3 README, compose docs`

---

## Risks

| Risk | Mitigation |
|---|---|
| **Autofill in Compose.** Android needs the pending request on the *focused View*; in Compose that's the single `AndroidComposeView` hosting all fields. | The spike in Task 3 verifies that the IME shows the suggestion when the Compose text field is focused. **Fallback:** document that autofill on Android needs `PasskeyUsernameField`, which hosts a tiny `AndroidView(EditText)` for the username. |
| **iOS autofill needs a real `UITextField` with `textContentType = .username`.** CMP ≥1.9 maps ContentType semantics, but QuickType passkey suggestions may need the field to be first responder at the moment `performAutoFillAssistedRequests` is called. | Verify on the simulator. **Fallback:** a UIKit interop text field (`UIKitView`) in `PasskeyUsernameField` on iOS. |
| **Swift bridge linking** (Spike A) may need consumer linker flags or break dynamic frameworks. | The decision gate picks B. |
| **PRF availability.** Android PRF depends on the provider (GPM supports it); iOS 18+ only; the simulator may not support PRF. | Report through the extension output (`prf.enabled == false`), never as an error. |
| **Binary compatibility.** The new enum entry `SignInMode.Autofill` and new result properties. | Enum KDoc already warns; constructors are internal; ABI dumps get updated. |
