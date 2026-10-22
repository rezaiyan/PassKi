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
| Headless API | `rememberPasskeys()`, `rememberPasskeySignIn(requestOptions: suspend () -> String, passkeys, mode)` → state with `isRunning`, `lastError`, `signIn()` |
| UI API | `PasskeySignInButton(state, onSignedIn)`, `PasskeyUsernameField(value, onValueChange, requestOptions, onSignedIn, modifier, passkeys, …)` |
| Autofill errors | Never shown as UI errors: a dismissed suggestion just means no result. The coroutine keeps waiting until the field leaves composition. |
| Autofill below Android 15 | `NotSupported`. `capabilities().autofill = false`. |
| PRF API (common) | Extension helpers `PasskeyExtensions.withPrf(optionsJson, first, second?)` + `PasskeyAssertion.prf: PrfResults?` (+ registration `prfEnabled`) |
| largeBlob API | `withLargeBlob(optionsJson, support = Required/Preferred)`, `withLargeBlobRead`, `withLargeBlobWrite(bytes)`; outputs `largeBlob` on results |
| iOS signals | Spike A first. If any step needs app-side linker flags or breaks dynamic frameworks, ship B. |

## Waves (parallel where files don't overlap)

### Wave 1 (parallel)

**Task 1: iOS Swift bridge spike**
- **Files:** new `passki-ios-bridge/` with `PassKiSignals.swift`, a build script, and `passki/src/nativeInterop/cinterop/passkiSwift.def`; `passki/build.gradle.kts` (cinterop for ios targets only).
- [x] Write a Swift file:
  - `@_cdecl("passki_report_unknown")`, `passki_report_all_accepted`, `passki_report_details`
  - each takes C strings or bytes plus a C callback `(int32 status, const char* message, void* ctx)`
  - internally calls `ASCredentialUpdater().reportUnknownPublicKeyCredential(relyingPartyIdentifier:credentialID:)`, `reportAllAcceptedPublicKeyCredentials(...)`, `reportPublicKeyCredentialUpdate(...)` inside a `Task`
  - guarded by `if #available(iOS 26, *)`; otherwise calls back with status `NOT_AVAILABLE`
  - Verify the exact Swift signatures against the SDK `.swiftinterface` / Xcode docs.
- [x] Build `libPassKiSwift.a` for `iphoneos` (arm64) and `iphonesimulator` (arm64) with `xcrun swiftc -parse-as-library -emit-library -static -target arm64-apple-ios16.0[-simulator]`, wired as a Gradle `Exec` task feeding cinterop.
- [x] Use `.def` `staticLibraries` / `libraryPaths` so the `.a` is embedded in the klib.
- [x] Link test: the sample `iosApp` (static framework, SwiftUI app) must link with **no extra flags** in Xcode, and also the passki test binary (`iosSimulatorArm64Test`). If the K/N test binary needs `-L<toolchain>/usr/lib/swift/iphonesimulator` plus swift compatibility libs, record which `linkerOpts` it needs.
- [x] **Decision gate.** Record the outcome in this plan:
  - If consumers need **zero** extra configuration, go with A.
  - Otherwise go with B, and delete the spike code.
- [x] Commit (A or B): `feat(ios): Signal API via Swift bridge`
- **Outcome (2026-10-21): A ships.** Evidence:
  - Swift signatures (demangled from the SDK `.tbd`, iOS SDK 26.2): `reportUnknownPublicKeyCredential(relyingPartyIdentifier: String, credentialID: Data)`, `reportAllAcceptedPublicKeyCredentials(relyingPartyIdentifier: String, userHandle: Data, acceptedCredentialIDs: [Data])`, `reportPublicKeyCredentialUpdate(relyingPartyIdentifier: String, userHandle: Data, newName: String)`, all `async throws`.
  - `passki-ios-bridge/build.sh` compiles with `-swift-version 5 -runtime-compatibility-version none`; the latter drops the toolchain-only `swiftCompatibilityPacks` autolink, so only SDK `/usr/lib/swift` dylibs are referenced. The `.a` is embedded in the cinterop klib (`.../included/libPassKiSwift.a`).
  - `iosSimulatorArm64Test` links with **no `linkerOpts`**. On the iOS 26.3 simulator the real updater runs (`Failure(Unknown(... AuthorizationError 1004 ...))`, as expected without associated domains); the same `test.kexe` on an iOS 17.5 simulator returns `NotSupported(Requires iOS 26.)`. The iOS 26 symbols are weak, so it back-deploys without crashing.
  - A temporary dynamic framework (`isStatic = false`) linked for `iosSimulatorArm64` and `iosArm64`.
  - The sample `iosApp` (static `Shared`, Xcode project unchanged) builds with xcodebuild. `-passkiAutorun` logs `PASSKI_DEMO Signal unknown ✓` and `signals=true`.
  - The only build change is `kotlin.mpp.enableCInteropCommonization=true` in `gradle.properties`, so the shared `iosMain` sees the cinterop. It is library-internal; consumers need nothing.
  - On iOS, `currentUserDetails` passes `name` as `newName` and ignores `displayName`.

**Task 2: `passki-compose` module, headless state, sign-in button**
- **Files:** `passki-compose/build.gradle.kts`, `src/commonMain/.../compose/{RememberPasskeys,PasskeySignInState,PasskeySignInButton}.kt`, tests; `settings.gradle.kts`.
- [x] Module: same targets as `passki`, plugins `kotlinMultiplatform`, `androidMultiplatformLibrary`, `composeMultiplatform`, `composeCompiler`; explicit API; ABI validation; Dokka; publishing (artifactId `passki-compose`).
- [x] `rememberPasskeys(): Passkeys`
- [x] `PasskeySignInState`, a `@Stable` class:
  - members: `isRunning`, `lastError: PasskeyError?`, `suspend fun signIn(): PasskeyResult<PasskeyAssertion>`
  - guards against double taps
  - `Cancelled` is not stored as `lastError`
- [x] `rememberPasskeySignIn(requestOptions, passkeys, mode)` (order changed in the final-review fixes)
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
- [x] **Android:**
  - Requires API 35+, else NotSupported.
  - Take `activity.currentFocus` and set `PendingGetCredentialRequest(GetCredentialRequest(listOf(GetPublicKeyCredentialOption(json))), callback)` on it via the androidx `View.setPendingGetCredentialRequest` extension. Verify the exact extension name in the 1.6.0 jar.
  - Resume the coroutine on the callback.
  - On cancellation, clear it with `clearPendingGetCredentialRequest`.
  - No focused view → `Misconfigured("Autofill needs a focused text field", hint: use PasskeyUsernameField or focus your username field first)`.
  - Callback errors are not delivered (documented by Google), so the coroutine just keeps waiting.
- [x] **iOS:**
  - `AuthorizationSession.performAutoFill(requests)` calls `performAutoFillAssistedRequests()`.
  - Cancellation calls `controller.cancel()`.
  - A 1001 after cancel is ignored.
  - Requires iOS 16+.
- [x] **FakePasskeys:** Autofill behaves like Modal; flag `autofillSupported`.
- [x] **Tests:**
  - mapper and pure parts
  - capability flags
  - FakePasskeys
- [x] Commit: `feat(core): SignInMode.Autofill`
- **Outcome (2026-10-21).** Commits `4a1586f` (signals 1004), `3e1970e` (core), `6f09038` (Android session restart), sample hook.
  - **API names (androidx.credentials 1.6.0, javap):** `PendingGetCredentialRequest(request: GetCredentialRequest, callback: (GetCredentialResponse) -> Unit)` and the extension property `var View.pendingGetCredentialRequest: PendingGetCredentialRequest?` (`CredentialManagerViewHandler.kt`); assigning `null` clears it (→ `View.clearPendingCredentialRequest()`). On API 35+ it calls `View.setPendingCredentialRequest(request, OutcomeReceiver)`; errors are not delivered.
  - **Android implementation:** on the main thread, anchor = `ActivityTracker.currentActivity?.currentFocus` (none → `Misconfigured("Autofill needs a focused text field", …)`), attach the request, then **restart the autofill session** with `AutofillManager.cancel()` + `notifyViewEntered(view)`. The request is cleared (main thread) on success and on cancellation.
  - **Emulator (API 36, GPM, passkey for alirezaiyan.com):**
    - Compose `OutlinedTextField` with `Modifier.semantics { contentType = ContentType.Username }` (`androidx.compose.ui.autofill.ContentType`, `androidx.compose.ui.semantics.contentType`): **works.** Focusing the field shows a "PassKi Demo" passkey chip in the Gboard suggestion strip (inline suggestion). Tapping it opens the GPM sheet; after PIN, `PASSKI_DEMO Sign in (autofill) ✓ PasskeyAssertion(credentialId=P-Y0NwBAncnNqezQv1FMbg, …)`. Repeated twice from a fresh launch. The request sits on the `AndroidComposeView` (the activity's focused view) and GPM still offers it, so the Task 4 `AndroidView(EditText)` fallback is **not needed**.
    - A native `EditText` (`AUTOFILL_HINT_USERNAME`) behaves the same (checked with a temporary spike, removed).
    - Without the session restart, the chip only appears after the field is re-focused (the fill request runs at focus, before the request is attached), and the IME just shows the generic "Passwords" chip ("No saved passwords for PassKi"). `AutofillManager.requestAutofill(view)` is **not** enough: the chip shows, but the picked credential is dropped (`AutofillSession: setAuthenticationResultLocked: no responses`). `cancel()` + `notifyViewEntered()` fixed it.
    - **Dismissal:** backing out of the GPM PIN prompt delivers nothing; the coroutine keeps waiting. The chip does **not** come back while the field stays focused (the session is used up); it comes back after a new `signIn(…, Autofill)` call. **Task 4:** `PasskeyUsernameField` should re-arm (cancel + call again) on each tap/IME show, not only on focus changes.
    - A suggestion chip that stays visible for more than about a minute can be stale: tapping it fails in GPM with `Flow has timed out` (nothing is delivered).
  - **iOS simulator 26.3 (window visible):** the sample builds and installs. Focusing the Compose username field starts `performAutoFillAssistedRequests()`, which fails fast with `Misconfigured: App VFCFJC7Y5J.com.alirezaiyan.passki.PassKi is not associated with alirezaiyan.com.` Modal `Sign in` fails the same way right now, so the simulator has no working association (AASA / developer mode). **The QuickType suggestion is not verified yet;** that moves to Task 6. A 1004 ends autofill (only a 1001 dismissal re-arms, after 500 ms).
  - **iOS follow-up (coordinator, rebuilt with `CODE_SIGN_ENTITLEMENTS=iosApp/iosApp.entitlements`):** the 1004 above was a build issue. The committed `project.pbxproj` has no `CODE_SIGN_ENTITLEMENTS`, so the agent's build lacked Associated Domains. With the entitlement, autofill starts and waits ("Autofill: waiting for a passkey suggestion"). The software keyboard (⌘K; the hardware keyboard was connected) shows ordinary word predictions ("I / The / I'm"), **not** the passkey QuickType bar, even after unfocusing and focusing again. **Conclusion:** Compose MP 1.12 `ContentType.Username` does not make iOS treat the field as a username field for passkey AutoFill. **Task 4 must use the planned fallback on iOS:** a UIKit `UITextField` with `textContentType = UITextContentTypeUsername`, hosted via `UIKitView`.
  - **Signals follow-up:** a bridge ERROR with `AuthorizationError 1004` or "not associated with domain" → `Misconfigured` with the concrete AASA hint when the message names the app/domain, otherwise the generic `AASA_HINT`. The real `iosSimulatorArm64Test` bridge run now reports `Misconfigured` instead of `Unknown`.

### Wave 3 (parallel)

**Task 4: `PasskeyUsernameField`**
- **Files:** `passki-compose/src/commonMain/.../PasskeyUsernameField.kt`, `internal/AutofillController.kt`, `PlatformUsernameField.{android,jvm,web,ios}.kt`, `AutofillControllerTest.kt`, ABI dumps; sample `App.kt`, `shared/build.gradle.kts`.
- [x] A Material3 `OutlinedTextField` with `Modifier.semantics { contentType = ContentType.Username }` (`androidx.compose.ui.autofill.ContentType`, `androidx.compose.ui.semantics.contentType`) on Android/jvm/web. iOS hosts a UIKit `UITextField` instead (see below).
- [x] While the field is focused and `capabilities().autofill`, run `signIn(requestOptions(), Autofill)`. Cancel when focus or composition ends.
- [x] Deliver results via `onSignedIn`.
- [x] Commit: `feat(compose): PasskeyUsernameField with passkey autofill`
- **Outcome (2026-10-21).**
  - **API:** `PasskeyUsernameField(value, onValueChange, requestOptions, onSignedIn, modifier, passkeys = rememberPasskeys(), label = "Username", onError = {}, enabled = true)`. Internal `expect fun PlatformUsernameField(…, onFocusChanged, onTap)`.
  - **Logic** is the pure `internal class AutofillController` (commonMain), unit-tested on jvm/android/iOS with a CompletableDeferred-gated fake and `testTimeSource` (12 tests):
    - focus arms, blur/dispose cancels
    - a tap on the focused field re-arms (Android: the chip is used up after a dismissal); taps within 500 ms of the last arm are ignored, so the focusing tap doesn't restart
    - Cancelled/NoCredentials/Interrupted → next call, starts ≥ 500 ms apart (no restart storm)
    - NotSupported/Misconfigured/InvalidOptions → `onError` once, stop for good
    - other errors (e.g. `requestOptions` throws → Unknown) → `onError`, wait for next focus/tap
    - success → `onSignedIn`, idle until next focus/tap
  - **Android taps:** `PressInteraction.Release` from the field's `MutableInteractionSource`.
  - **iOS field:** `UIKitView` (`androidx.compose.ui.viewinterop.UIKitView(factory, modifier, update, onRelease, onReset, properties)`, CMP 1.12.1) hosting a `UITextField`:
    - `textContentType = UITextContentTypeUsername`, autocorrection/autocapitalization/spellcheck off, email keyboard
    - `UIKitInteropProperties(interactionMode = NonCooperative, isNativeAccessibilityEnabled = true)`
    - text via target-action `editingChanged:` (`@ObjCAction`), focus via `UITextFieldDelegateProtocol` begin/end editing
    - Material-like border/label drawn in Compose; the field needs an opaque `surface` background (the interop hole otherwise shows white)
    - taps not reported (core re-offers after a 1001 by itself)
  - **Android emulator (API 36, GPM):**
    - tapping the field shows the "PassKi Demo" passkey chip in Gboard (`scratchpad/a1-focus.png`); tap chip → PIN → `PASSKI_DEMO Sign in (autofill) ✓`
    - dismissal (back on the PIN prompt) → no chip (`a3-dismissed.png`); tapping the still-focused field → `Autofill: waiting…` again and the chip returns (`a4-rearmed.png`); picking it → `Sign in (autofill) ✓`
    - `PasskeySignInButton` "Sign in" → `PASSKI_DEMO Sign in ✓`
  - **iOS simulator 26.3 (entitlements override):**
    - focusing the UIKit field: the QuickType bar shows **"🔑 Passwords"**, not an inline "demo@passki.dev" item (`i9-focused.png`); with the Compose field it showed only word predictions
    - tapping "Passwords" → Face ID → a "Sign In" list with **Passkeys: alirezaiyan.com / demo@passki.dev** (`i10-list.png`); picking it → `PASSKI_DEMO Sign in (autofill) ✓ PasskeyAssertion(credentialId=5GHAW5v6NuAdmHQgL3flacl8XV4, …)`, delivered through the autofill request
    - blur + refocus → re-armed → success again; closing the list (X) → 1001, the core keeps waiting; opening it again and picking → success
    - starting the request on appear instead of on focus showed the same "Passwords" bar (no inline item) and failed once at launch with `Unknown: Unable to verify webcredentials association … try again in a few seconds`, so focus-start stays
    - `PasskeySignInButton` → `Sign in ✓`
  - Screenshots: `/private/tmp/claude-501/-Users-ali-projects-deblock-PassKi/485ddd86-db4f-406f-a03a-cc5a90be42f4/scratchpad/`.

**Task 5: PRF + largeBlob typed extensions**
- **Files:** `commonMain/.../PasskeyExtensions.kt` (public), `internal/ExtensionsJson.kt`, `apple/AppleRequests.kt` (map inputs/outputs), the result classes (new properties), tests, ABI dumps.
- [x] **Common:**
  - builders that add `extensions.prf.eval{first,second}` (base64url) / `extensions.largeBlob` to options JSON
  - parsers from `clientExtensionResults`: `prf.enabled`, `prf.results.first/second`, `largeBlob.supported/blob/written`
- [x] **iOS:**
  - registration: `prf` input → `ASAuthorizationPublicKeyCredentialPRFRegistrationInput` (checkForSupport / with input values), and `largeBlob` → `ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput(supportRequirement)`
  - assertion: PRF input values (`saltInput1/2`), largeBlob read/write
  - outputs → `clientExtensionResults` JSON in WebAuthn format
  - availability: iOS 18 (PRF), iOS 17 (largeBlob); otherwise the extension is omitted silently, matching browser behaviour
  - verify K/N names in the platform klib
- [x] **Android:** JSON passthrough. The parser works on GPM output.
- [x] **FakeAuthenticator:** deterministic PRF via HMAC-SHA256(credentialSecret, salt) so apps can test PRF-derived keys. Mark it test-only in KDoc.
- [x] Commit: `feat(core): typed PRF and largeBlob extensions`

### Wave 4

**Task 6: Sample + emulator verification**
- [x] Sample (now against the live demo RP, setup-experience Phase 2):
  - use `PasskeyUsernameField` + `PasskeySignInButton`; every response verified by the server, verdict in the log
  - a "PRF" button showing the derived output (hex, first 8 bytes) and whether it matches the previous run
  - autorun steps for the non-UI parts (iOS `-passkiAutorun`, Android `--ez passkiAutorun true`)
- [x] **Android emulator (API 36):** focusing the username field shows the passkey suggestion in the keyboard; picking it signs in (`Sign in (autofill) ✓ verified by server`); PRF returns 32 bytes; the same salt gives the same output twice (`60d4951e6da94aeb` both runs). Signals: `unknownCredential` then device-only sign-in → `NoCredentials` (GPM honours it).
- [ ] **iOS simulator 26.3:** (2026-10-22, Mac screen locked, so no taps)
  - focusing the field shows the QuickType passkey suggestion; picking it signs in: **not verified**
  - PRF 32 bytes, stable: **not verified**
  - Signals: `unknownCredential` → `Signal unknown ✓` (real ASCredentialUpdater call on iOS 26.3); the device-only follow-up has no credential to compare yet
  - verified: build, `passkiCheck` IOS-04/05/06, doctor, SRV-01/02, the passkey sheet opens for passki.alirezaiyan.com (association OK)
- [x] **Record results** in this plan; add the fixtures (an assertion pair if feasible). `android-20261022-registration.json` + `android-20261022-assertion.json` (same credential); `RecordedFixturesTest` verifies 3 registrations + 1 assertion against the paired public key.
- [ ] Commit: `test(sample): autofill, PRF, signals verified on emulators` (Android done; iOS rerun pending)

**Task 7: Docs + review**
- [x] README: Compose section, autofill, PRF/largeBlob, the iOS signals status (A or B).
- [x] Update the capability table.
- [x] Dokka for `passki-compose`.
- [x] Full gate:

  ```
  :passki:jvmTest :passki:testAndroidHostTest :passki:iosSimulatorArm64Test
  :passki-test:jvmTest :passki-compose:allTests(jvm/android/ios)
  :*:checkKotlinAbi :androidApp:assembleDebug
  ```

- [x] Final review agent.
- [x] Commit: `docs: Plan 3 README, compose docs`
- **Outcome (2026-10-22).**
  - README: Compose, autofill, PRF/largeBlob, iOS signals (A, no app setup), the demo RP + dashboard, the sample
    app, capability tables and "Verified on". iOS server-verified register/sign-in/autofill/PRF are marked pending
    (Task 6).
  - `passki-compose/Module.md` is complete; Dokka has `reportUndocumented` on for `passki-compose` (0 warnings).
  - Added CHANGELOG.md and CONTRIBUTING.md (Swift bridge needs Xcode `swiftc`); versions are now 0.1.0-alpha02.
  - Gate: the full Gradle list plus `dokkaGenerate --configuration-cache --rerun-tasks` passed (passki 775,
    passki-test 115, passki-compose 65, shared 14 tests, 0 failures; ABI checks and assembleDebug green). The plugin
    `test` passed (114) and `passki-web` `npm test` passed (124).

**Final-review fixes (2026-10-22)**
- [x] **Android autofill in dialogs / bottom sheets.** A Compose `Dialog` or `ModalBottomSheet` is its own window, so
  `activity.currentFocus` missed the field (the activity window has no focused view, or a stale one behind the
  dialog).
  - Core: `chooseAutofillTarget` picks, in order, an explicit anchor (its focused descendant, else itself), the
    focused view of the app window that has window focus (`WindowInspector.getGlobalWindowViews()`, API 29+), then
    `activity.currentFocus`. New `@InternalPasskiApi suspend fun Passkeys.autofillSignIn(optionsJson, anchor: View)`;
    the public `signIn(options, Autofill)` uses the window search.
  - Core: the request is attached only once the view's window has window focus. Restarting the autofill session
    (`AutofillManager.cancel()`) cancels every window's session of the activity, so a field behind the dialog that
    re-armed during the PIN prompt dropped the picked passkey (`onAuthenticationResult(): sessionId=… not active`).
  - Compose: `PasskeyUsernameField` on Android anchors to `LocalView.current` (the dialog's view inside a Dialog);
    `AutofillController` takes the platform call (`AutofillSignIn`).
  - Sample: "Autofill in dialog" button opens a `Dialog` with a `PasskeyUsernameField`.
  - **Emulator (API 36, GPM, Gboard), `$TMPDIR/passki-e2e/dialog-flow.sh`:** open dialog → tap field → "PassKi demo
    user" passkey chip in Gboard → tap → PIN → `Sign in (autofill in dialog) ✓ verified by server ✓ … userVerified
    true` (credential `kmXRGM-H6QrlW6oFp4hJ9A`). Repeated 3× with the final build. After the dialog closes, the main
    field's deferred offer attaches: its chip shows and picking it gives `Sign in (autofill) ✓ verified by server`.
    - Red, same flow: old target logic (activity `currentFocus` only) → `Sign in (autofill in dialog) ✗
      [Misconfigured] Autofill needs a focused text field`. Without the window-focus wait: the main field re-armed
      during the PIN entry (key events leave touch mode, Compose focuses the first field) and the result was lost.
    - Public path (no anchor, window search only): also `✓ verified by server` in the dialog.
    - `ModalBottomSheet` is not run on the device; it is a separate window like `Dialog` and takes the same path.
- [x] **FakePasskeys Autofill parity.** No matching passkey → suspends until cancelled (outside the lock); a matching
  passkey → completes at once (the "picked" suggestion); `failNextWith` still ends it. Tests: cancellation, other
  operations not blocked, `AutofillController` with the plain fake makes one call in 60 s (no spin).
- [x] **Parameter order.** `rememberPasskeySignIn(requestOptions, passkeys = rememberPasskeys(), mode = Modal)`, the
  same convention as `PasskeyUsernameField` (required, `modifier`, optional with `passkeys` first).
- [x] **Swift bridge staleness.** `swiftc --version`, SDK version and SDK build are inputs of
  `buildPassKiSwift*` (lazy `providers.exec`, config-cache safe). `DEVELOPER_DIR=/Library/Developer/CommandLineTools`
  made the task out of date: "Value of input property 'swiftcVersion' has changed".

---

## Risks

| Risk | Mitigation |
|---|---|
| **Autofill in Compose.** Android needs the pending request on the *focused View*; in Compose that's the single `AndroidComposeView` hosting all fields. | The spike in Task 3 verifies that the IME shows the suggestion when the Compose text field is focused. **Fallback:** document that autofill on Android needs `PasskeyUsernameField`, which hosts a tiny `AndroidView(EditText)` for the username. |
| **iOS autofill needs a real `UITextField` with `textContentType = .username`.** CMP ≥1.9 maps ContentType semantics, but QuickType passkey suggestions may need the field to be first responder at the moment `performAutoFillAssistedRequests` is called. | Verify on the simulator. **Fallback:** a UIKit interop text field (`UIKitView`) in `PasskeyUsernameField` on iOS. |
| **Swift bridge linking** (Spike A) may need consumer linker flags or break dynamic frameworks. | The decision gate picks B. |
| **PRF availability.** Android PRF depends on the provider (GPM supports it); iOS 18+ only; the simulator may not support PRF. | Report through the extension output (`prf.enabled == false`), never as an error. |
| **Binary compatibility.** The new enum entry `SignInMode.Autofill` and new result properties. | Enum KDoc already warns; constructors are internal; ABI dumps get updated. |
