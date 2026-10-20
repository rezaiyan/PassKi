<!-- plan-type: feature -->
# PassKi: Passkey Library for Kotlin Multiplatform

## Goal
Ship a Kotlin Multiplatform library that lets an app register and sign in with passkeys (WebAuthn / FIDO2) from shared code. Each platform uses its native authenticator. The library works with any server: the app passes in the server's WebAuthn options JSON and sends the response JSON back to the server's verifier (SimpleWebAuthn, webauthn4j, java-webauthn-server, py_webauthn, ...). First targets are Android and iOS. Web and desktop come later, with a fallback for each limitation listed below.

## Background

### Market check (Oct 2026): two KMP libraries already exist
| Library | Version | Targets | Notes |
|---|---|---|---|
| [AndroidPoet/passkeys-kmp](https://klibs.io/project/AndroidPoet/passkeys-kmp) | 0.2.0 (Jun 2026) | Android 28+, iOS 16+, macOS 13+, JVM/Compose Desktop, Wasm, Windows 1903+, Linux (libfido2) | Has a Compose module, browser handoff and a Ktor RP server module. Wide but young. |
| [szijpeter/webauthn-kotlin-multiplatform](https://klibs.io/project/szijpeter/webauthn-kotlin-multiplatform) | 0.2.x | Android, iOS clients + JVM server | Follows the spec closely: typed models, strict validation, backend ceremony services |

**Implication:** a plain "wrapper around Credential Manager + AuthenticationServices" won't stand out. PassKi needs a clear reason to exist (see Open Questions #1). Possible angles:
- quality and testing depth
- a working answer for Compose/Wasm conditional UI
- desktop packaging tooling for macOS entitlements
- minimal, JSON-first design with no server opinion
- an internal need, e.g. Deblock-grade fintech requirements: attestation, PRF, audit-friendly errors

### Platform feasibility matrix

| Platform | Native API | Synced passkeys | Feasible? | Difficulty |
|---|---|---|---|---|
| **Android** | `androidx.credentials` 1.6.0 (stable Apr 2026), JSON in/out | ✅ Google PM + 3rd-party providers | ✅ Yes | Low |
| **iOS** | `AuthenticationServices` (`ASAuthorizationPlatformPublicKeyCredentialProvider`), reached from Kotlin/Native `platform.AuthenticationServices` | ✅ iCloud Keychain + 3rd-party (iOS 17+) | ✅ Yes | Medium |
| **Web (JS + Wasm)** | `navigator.credentials.create/get`, `PublicKeyCredential.parse*OptionsFromJSON` / `toJSON()` | ✅ (browser/OS) | ✅ Yes | Low–Medium (Compose canvas issues) |
| **Windows (JVM / mingwX64)** | `webauthn.dll` (`WebAuthNAuthenticatorMakeCredential/GetAssertion`), Win10 1903+ | ✅ Windows Hello; hybrid phone; 3rd-party plugin managers (GA Nov 2025) | ✅ Yes | Medium |
| **macOS (JVM Compose Desktop)** | Same AuthenticationServices, through a JNI/FFM bridge dylib | ✅ iCloud Keychain | ⚠️ Yes, but needs a signed `.app` with an Associated Domains entitlement and an embedded provisioning profile | **High** (packaging, not code) |
| **macOS (K/N native)** | AuthenticationServices directly | ✅ | ⚠️ Same entitlement problem. A K/N executable is not an app bundle | High |
| **Linux** | **No OS passkey API** | ❌ | ⚠️ Partial: libfido2 handles USB security keys only. Phone/hybrid needs a browser handoff | High / limited |

**Answer to "web and desktop too, or only iOS/Android?"**
- **Web**: yes, and it's easy. Browsers expose a stable API.
- **Windows**: yes. The OS API gives full passkey UX, including Windows Hello, phone-as-authenticator, and 1Password/Bitwarden plugins.
- **macOS desktop**: the API is possible, but it only works in a properly signed and provisioned `.app`. It fails under `./gradlew run`. The plan is to ship it together with packaging docs and Gradle tooling.
- **Linux**: there is no real platform support. Offer a **browser handoff** (open the RP's web page, run WebAuthn there, return via loopback or deep link) plus optional libfido2 for hardware keys. Browser handoff also works as a universal fallback on every desktop OS.

## Platform Deep Dive & Challenges

### Android
- **API:** `CredentialManager.createCredential(CreatePublicKeyCredentialRequest(json))` and `getCredential(GetCredentialRequest(listOf(GetPublicKeyCredentialOption(json))))`. Request and response are both WebAuthn JSON, so there's little to do here.
- **Needs an `Activity` context.** This conflicts with a context-free common API. Solution: a `PasskeyClient` factory that takes a platform handle (`ActivityProvider`), or a Compose helper `rememberPasskeyClient()`.
- **Server setup:** `/.well-known/assetlinks.json` with `delegate_permission/common.get_login_creds` plus the app signing cert SHA-256. Debug, release and Play App Signing certs differ, which is a common source of setup bugs. Ship a "doctor" checklist.
- **Origin:** clientDataJSON origin = `android:apk-key-hash:<b64url>`, not `https://...`. The server must allow-list it. Document this; it's the #1 integration failure.
- **Extras:**
  - `preferImmediatelyAvailableCredentials`
  - conditional create (`isConditional`, fixed in 1.5+)
  - Restore Credentials (`CreateRestoreCredentialRequest`)
  - Signal API (1.6)
  - PRF and largeBlob extensions pass through as JSON
- **Error mapping:** there are ~10 exception types (`CreateCredentialCancellationException`, `NoCredentialException`, `CreatePublicKeyCredentialDomException`, `...InterruptedException`, provider config errors, ...). Map them all to a common sealed `PasskeyError`.
- **minSdk:** passkeys need API 28+ with Play services. Lower APIs return "unsupported".

### iOS
- **API:** `ASAuthorizationController` + `ASAuthorizationControllerDelegate` + `ASAuthorizationControllerPresentationContextProviding`. All of these are ObjC protocols, so Kotlin/Native implements them as `NSObject()` subclasses.
- **No JSON API.** Apple uses typed objects with `NSData` fields (challenge, userID, credentialID, rawClientDataJSON, rawAttestationObject, authenticatorData, signature). **The library must parse server JSON → native params and build `RegistrationResponseJSON` / `AuthenticationResponseJSON` itself**: base64url, transports, authenticatorAttachment, clientExtensionResults. This is the core value-add and the biggest testing surface.
- **Presentation anchor:** needs a `UIWindow`. Resolve it from the key window scene by default and let the caller override.
- **Memory:** the K/N delegate must be strongly referenced until the callback runs, or the GC/ARC bridge drops it and the coroutine never resumes. Bridge with `suspendCancellableCoroutine` and call `controller.cancel()` on cancellation (iOS 16+).
- **Server setup:** Associated Domains `webcredentials:<rp>` entitlement plus the AASA file on the RP. Use `?mode=developer` for local testing.
- **Feature levels by OS version:**
  - iOS 16: passkeys, autofill-assisted requests (`performAutoFillAssistedRequests`)
  - iOS 17: security keys, largeBlob
  - iOS 18: PRF, conditional registration (`requestStyle = .conditional`)
  - iOS 26: account-creation / credential-update (signal) APIs
- **Swift-only API risk:** newer Apple APIs may be Swift-only and invisible to K/N cinterop. Mitigation: a tiny Swift shim compiled into the framework, or Kotlin Swift Export once it's stable.
- **Binary data:** `NSData` ↔ `ByteArray` copies via `usePinned` / `memcpy`. These are small payloads, so it's fine.

### Web (Kotlin/JS + Kotlin/Wasm)
- **API:**
  - `navigator.credentials.create({publicKey})` / `get(...)`
  - Prefer `PublicKeyCredential.parseCreationOptionsFromJSON` / `parseRequestOptionsFromJSON` and `credential.toJSON()` (Chrome 129+, Firefox 119+, Safari 18.4+)
  - Fall back to a manual base64url ↔ ArrayBuffer conversion
- **Wasm interop:** `Promise<JsAny?>` → `await()`. `ArrayBuffer` ↔ `ByteArray` costs a copy, which is acceptable.
- **Challenge 1: conditional mediation / autofill UI.** This needs a real DOM `<input autocomplete="username webauthn">`. **Compose for Web (Wasm) draws to a canvas, so there's no native input.** Options:
  - an HTML overlay input positioned over the Compose text field
  - skip conditional UI on Wasm and only offer the modal flow
  - Spike needed.
- **Challenge 2: user activation.** Safari requires `create()` to run inside a user gesture. If the coroutine dispatch path breaks transient activation (e.g. an extra hop), Safari rejects with `NotAllowedError`. Rule: the API must start the call synchronously from the click handler. Fetch server options *before* the click, or accept the risk and document it.
- **Origin:** the browser enforces RP ID ⊆ origin, so there's no extra setup. Feature detection: `PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()`, `isConditionalMediationAvailable()`, `getClientCapabilities()`.

### Windows (JVM desktop first, mingwX64 optional)
- **API:** `webauthn.dll`. JVM access goes through **Java FFM (Panama, JDK 22+)** or **JNA** (works on older JDKs). Version-gate struct layouts with `WebAuthNGetApiVersionNumber()`: options structs gained fields across versions (v1 → v7+).
- **The caller builds clientDataJSON and passes its hash.** The library must construct `{"type","challenge","origin","crossOrigin"}` itself and pick the origin. Use `https://<rpId>` to look like a browser, or a custom app origin that the server allow-lists.
- **Security note:** the Win32 API **does not check that the app is allowed to use the RP ID**. Any desktop app can request assertions for any RP; browsers rely on exactly this. There's no app↔domain binding like on Android/iOS. Document this; the server can't fully verify the calling app.
- **HWND:** the API needs a parent window handle. With Compose Desktop, get it from the AWT window via JNA `Native.getWindowPointer` or `WindowsWindow`.
- **UX gains:** Windows Hello, security keys, hybrid (scan a QR with a phone) on Win11, and 3rd-party passkey managers through the plugin API (GA Nov 2025).
- **Threading:** the calls block, so run them on `Dispatchers.IO` and support cancellation via `WebAuthNCancelCurrentOperation`.

### macOS (JVM Compose Desktop / K/N)
- **Same AuthenticationServices code as iOS** (shared through an `appleMain` source set for K/N). JVM needs a bridge: a small ObjC/Swift dylib inside the jar, loaded via JNI or FFM, with a C ABI (`passki_register(json, cb)`).
- **Blocker:** passkeys require the `com.apple.developer.associated-domains` entitlement. This is a *restricted* entitlement, so the app must be:
  - signed with a Developer ID (or App Store) cert
  - shipped with `embedded.provisionprofile` in `Contents/`
  - matched by an AASA file on the RP
- **What breaks:**
  - `./gradlew run`: no bundle or entitlements, so passkeys are unavailable
  - jpackage: it doesn't embed provisioning profiles by default
  - the bundled JRE: it must be signed too (hardened runtime)
  - notarization
- **Mitigation:**
  - ship a Gradle plugin or doc recipe: Compose `nativeDistributions.macOS { entitlementsFile, provisioningProfile, signing }`
  - in dev mode, auto-fall back to browser handoff
- **Browser-style API** (`ASAuthorizationWebBrowserPublicKeyCredentialManager`) is reserved for browsers and has its own entitlement. Not an option.

### Linux
- There is no OS passkey API and no platform authenticator. Freedesktop "credentials portal" efforts (e.g. linux-credentials / credentialsd) are experimental. Watch them, don't depend on them.
- **libfido2** (CTAP2 over USB/NFC) gives **security keys only**. It needs udev permissions and libfido2 installed. Phone/hybrid (caBLE: BLE advert + tunnel server) means a huge reimplementation, so it's out of scope.
- **Recommended:** browser handoff (see below). libfido2 can be an optional add-on module.

### Universal fallback: browser handoff (all desktops)
1. The app starts a loopback HTTP server (`127.0.0.1:<random>`) or registers a custom URL scheme.
2. It opens the system browser to `https://<rp>/passkey-handoff?session=...&redirect=...`.
3. The RP's web page runs WebAuthn natively, then posts the result to the RP backend or redirects to loopback with a one-time code.
4. The app exchanges the code for a session.

This needs RP cooperation (a hosted page). It's an OAuth-like pattern, similar to RFC 8252. The library provides the client half plus a reference page/JS snippet.

## Differentiation Strategy (decided: public OSS, must beat existing libs)

### Competitor gaps (from their READMEs, Oct 2026)
| Area | passkeys-kmp (AndroidPoet) | webauthn-kmp (szijpeter) | **PassKi target** |
|---|---|---|---|
| Install | core + compose + server artifacts | many modules (model, core, crypto-api, client-core, client-flow, defaults, platform bridges...), so a steep learning curve | **1 dependency** for 90% of apps |
| Result/error | `PasskeyResult.Failure(error.code, error.message)`, which is stringly-typed | typed, but layered | **Exhaustive sealed errors**, each with `fixHint` |
| Response | `result.value.rawJson` | typed models | Both: `.json` for the server plus typed accessors (`credentialId`, `prf`, `transports`) |
| Autofill / conditional UI | not advertised | not advertised | ✅ first-class on Android, iOS and Web (incl. Compose Wasm overlay) |
| Password + passkey in one sheet | ❌ | ❌ | ✅ (Android Credential Manager unified sheet; iOS combined requests) |
| Upgrade password users (conditional create) | ❌ | ❌ | ✅ |
| Signal API (delete / rename / sync) | ❌ | ❌ | ✅ |
| Android Restore Credentials | ❌ | ❌ | ✅ |
| Setup validation | docs | docs | ✅ **Gradle plugin**: generates + validates `assetlinks.json` / AASA, checks entitlements and the signing SHA |
| Runtime diagnostics | ❌ | ❌ | ✅ `Passkeys.doctor()`: live check of the RP's `.well-known` files vs the running app |
| Testing support | ❌ | ❌ | ✅ `passki-test`: fake authenticator that produces **cryptographically valid** responses, so app + server tests pass without devices |
| Desktop fallback | browser handoff | ❌ | ✅ handoff with a ready-made RP page + JS snippet |
| Compose UI | `rememberPasskeyClient()` | client-compose | ✅ plus a drop-in `PasskeySignInButton` and autofill-aware `PasskeyUsernameField` |
| Server | Ktor RP module | full server stack | ❌ **out of scope**: recommend mature verifiers and stay client-focused |

### Positioning
**"The Coil/Ktor of passkeys":** one dependency, one call, works the first time, and tells you exactly what's misconfigured when it doesn't.

PassKi wins on three things:
1. **DX:** simplest API and zero-config Compose.
2. **Setup tooling:** Gradle plugin + doctor. This is the #1 real-world pain.
3. **Advanced features nobody covers:** autofill, upgrade, signals, restore, PRF helpers, a valid fake authenticator.

It does *not* compete on server-side verification.

### Target API (DX north star)
```kotlin
// commonMain: no platform handles, no context juggling
val passkeys = Passkeys()                        // Compose: rememberPasskeys()

// Register
passkeys.register(serverOptionsJson)
    .onSuccess { api.finishRegistration(it.json) }
    .onFailure { err ->
        when (err) {
            PasskeyError.Cancelled -> Unit
            is PasskeyError.Misconfigured -> log(err.fixHint) // "assetlinks.json missing SHA-256 AB:CD..."
            else -> showError(err)
        }
    }

// Sign in: modal, autofill or unified with passwords
passkeys.signIn(serverOptionsJson)                               // modal
passkeys.signIn(serverOptionsJson, mode = SignInMode.Autofill)   // conditional mediation
passkeys.signIn(serverOptionsJson, alsoPasswords = true)         // unified sheet → PasskeyCredential | PasswordCredential

// Upgrade password user silently after password login
passkeys.upgrade(serverOptionsJson)

// Keep the provider in sync with the server
passkeys.signal.unknownCredential(rpId, credentialId)
passkeys.signal.acceptedCredentials(rpId, userId, ids)
passkeys.signal.userDetails(rpId, userId, name, displayName)

// Capabilities, so the UI can adapt before showing buttons
passkeys.capabilities()  // isSupported, autofill, prf, hybrid, signal, ...
```

**Compose helpers**
- `PasskeyUsernameField(...)`: a text field that triggers autofill on Android/iOS/Web
- `PasskeySignInButton(onOptions = { api.options() }, onResult = { ... })`: handles user activation, loading and errors

**API rules**
- `kotlin.Result`-like `PasskeyOutcome`; `suspend` everywhere; cancellation-safe
- No `expect` classes leak into the public API. Platform handles are resolved automatically: Android Activity via `ActivityLifecycleCallbacks` from an `androidx.startup` initializer; iOS key window; Desktop focused AWT window.
- An overload takes explicit handles for edge cases.
- Typed models are optional; JSON string in/out is always available.

## Proposed Architecture

```
passki-core            (commonMain)  WebAuthn L3 JSON models (kotlinx.serialization), base64url,
                                     PasskeyClient interface, PasskeyError sealed hierarchy,
                                     capability detection API
passki-android         androidx.credentials bridge
passki-apple           iOS + macOS K/N via AuthenticationServices (appleMain)
passki-web             js + wasmJs via navigator.credentials
passki-desktop-windows JVM, FFM/JNA → webauthn.dll
passki-desktop-macos   JVM, JNI/FFM → bundled dylib (AuthenticationServices)
passki-desktop-handoff JVM, loopback browser handoff (all desktop OS, incl. Linux)
passki-fido2           optional, JVM/Linux libfido2 for security keys
passki-compose         rememberPasskeyClient(), Activity/UIWindow/HWND wiring
passki-testing         fake PasskeyClient + recorded fixtures for app unit tests
```

**Common API sketch**
```kotlin
interface PasskeyClient {
    suspend fun capabilities(): PasskeyCapabilities
    suspend fun register(optionsJson: String): Result<String>          // RegistrationResponseJSON
    suspend fun authenticate(optionsJson: String,
                             mediation: Mediation = Mediation.Modal): Result<String> // AuthenticationResponseJSON
}
// typed overloads take/return PublicKeyCredentialCreationOptions etc.
sealed class PasskeyError : Exception() {
    object Cancelled; object NoCredentials; object NotSupported; object Timeout
    data class DomainNotAssociated(val hint: String)   // assetlinks / AASA / entitlement misconfig
    data class InvalidState(val msg: String)            // e.g. excludeCredentials hit
    data class Platform(val code: String, val msg: String)
}
```

**Design principles**
- JSON passthrough is the main path. That keeps it server-agnostic, and a server-side Ktor module stays optional and out of MVP.
- No crypto verification client-side, since the client is untrusted anyway.
- Every platform error maps to a stable error type, with a human-readable "setup doctor" hint.

## User Stories
- [ ] As a KMP app dev, I want to call `register(json)` / `authenticate(json)` from `commonMain`, so that I write passkey logic once.
- [ ] As a dev, I want clear errors when assetlinks/AASA/entitlements are wrong, so that I don't lose a day to generic "unknown error".
- [ ] As a dev, I want to plug any WebAuthn server, so that the library doesn't force a backend.
- [ ] As a Compose Multiplatform dev, I want `rememberPasskeyClient()`, so that platform handles (Activity, UIWindow, HWND) are wired automatically.
- [ ] As a desktop dev, I want an automatic browser-handoff fallback, so that Linux and unsigned dev builds still work.
- [ ] As a dev, I want a fake client + fixtures, so that I can unit-test my login flow without devices.

## Implementation Steps (Roadmap)

### Phase 0: Decide & spike (≈1 week)
- [x] Decision: public OSS that competes on DX + tooling + advanced features.
- [ ] Run both existing libs' samples. Write a DX benchmark (lines of code for register + sign-in, number of deps, time-to-first-passkey). Use it as the README comparison.
- [ ] Spike auto-resolving the Activity / UIWindow without passing handles.
- [ ] Spike a fake authenticator: ES256 key gen + a valid attestationObject (`fmt: none`) + assertion, verified by webauthn4j.
- [ ] Spike iOS from K/N: delegate retention, NSData ↔ ByteArray, building RegistrationResponseJSON.
- [ ] Spike Compose Wasm conditional UI (HTML overlay input) and Safari user-activation behaviour.
- [ ] Pick a test RP: local Ktor/Spring + webauthn4j, or SimpleWebAuthn example. Host assetlinks + AASA on a real HTTPS domain (tunnel or a VPS subdomain).

### Phase 1: MVP Android + iOS (≈3–4 weeks)
- [ ] Gradle KMP setup: android, iosArm64, iosSimulatorArm64, iosX64. Publishing to Maven Central (vanniktech plugin). Binary-compatibility validator. Dokka.
- [ ] `passki-core`: WebAuthn L3 JSON models, base64url, errors, capability API. Exhaustive serialization tests (round-trip, spec vectors).
- [ ] `passki-android`: Credential Manager bridge, error mapping, Activity provider.
- [ ] `passki-apple` (iOS): AuthenticationServices bridge, JSON ↔ native mapping, cancellation.
- [ ] Sample Compose Multiplatform app plus test RP. Manual E2E on real devices, Android emulator with Google account, and iOS simulator.
- [ ] Docs: assetlinks/AASA setup, the Android origin allow-list, troubleshooting matrix.
- [ ] **Differentiators in MVP**: autofill sign-in (Android + iOS), unified password+passkey, `upgrade()`, signal API, typed errors with `fixHint`.
- [ ] `passki-test` fake authenticator.
- [ ] `passki-gradle-plugin` v1: print/generate `assetlinks.json` from signing configs and AASA from team ID + bundle ID; fail the build on mismatch.
- [ ] `Passkeys.doctor()` runtime check.
- [ ] Release 0.1.0 with a README benchmark vs alternatives.

### Phase 2: Web (≈1–2 weeks)
- [ ] `passki-web` for js + wasmJs, with JSON-native browser APIs plus fallback conversion.
- [ ] Automated E2E with Chrome **virtual authenticator** (CDP `WebAuthn.addVirtualAuthenticator` via Playwright). This is the one platform with true CI E2E.
- [ ] Conditional UI on Wasm (if the spike succeeded) or document the limitation.

### Phase 3: Windows desktop (≈2 weeks)
- [ ] `passki-desktop-windows`: FFM (JDK 22+) with a JNA fallback. Versioned structs. clientDataJSON builder. HWND from the Compose window.
- [ ] Test Windows Hello, a security key and hybrid phone flow on Win11.

### Phase 4: macOS desktop (≈2–3 weeks)
- [ ] macOS K/N target in `passki-apple` (shared appleMain).
- [ ] JVM bridge dylib (universal arm64 + x64), packaged in the jar, loaded via JNI/FFM.
- [ ] Gradle recipe/plugin for entitlements, provisioning profile, signing and notarization with Compose `nativeDistributions`.
- [ ] Detect missing entitlement at runtime → `DomainNotAssociated` + auto handoff in dev.

### Phase 5: Handoff + Linux (≈2 weeks)
- [ ] `passki-desktop-handoff`: loopback server, one-time code, PKCE-like binding, reference RP web page + JS.
- [ ] Optional `passki-fido2` (libfido2 via FFM) for security keys on Linux.

### Phase 6: Advanced features (ongoing)
- [ ] Autofill/conditional mediation on Android, iOS and web.
- [ ] Conditional create (password → passkey upgrade).
- [ ] Signal APIs: unknown credential, accepted credentials, current user details.
- [ ] Extensions: PRF (E2E-encryption key derivation, relevant for fintech), largeBlob, credProps.
- [ ] Android Restore Credentials.
- [ ] Optional `passki-server-ktor`, only if users want it.

## Testing Strategy & Challenges
- **Real ceremonies can't run in CI on mobile/desktop.** Biometric/OS UI blocks automation. Strategy:
  - unit tests on all JSON/native mapping, with fixtures recorded from real devices
  - contract tests that run responses through a real server verifier (webauthn4j) to prove the output is valid
- **Web** gets full E2E via a CDP virtual authenticator.
- **Android emulator:** a Google account plus screen lock enables passkeys. Can be semi-automated with UiAutomator for the bottom sheet.
- **iOS simulator:** supports passkeys with Touch ID/Face ID enrolment simulated; needs an AASA-reachable domain.
- A device-lab manual checklist per release.

## Open Questions
1. ~~Build vs contribute?~~ **Decided:** public OSS; win on DX, tooling and advanced features (see Differentiation Strategy).
2. Which server verifier will the first consumer use? That affects origin/allow-list docs and fixtures.
3. Minimum OS versions: iOS 16 vs 17? Android minSdk 28? JDK 17 (JNA) vs 22 (FFM) for desktop?
4. Should the server-side module be in scope at all?
5. Is browser handoff acceptable to the target RP? It requires hosting a page.
6. Is the Compose Multiplatform helper required, or plain Kotlin only?
7. License and publishing coordinates (`com.deblock.passki`? personal?).

## Risks
- **Competition:** two KMP libs already exist. Without a clear angle, effort is duplicated. Mitigation: settle Open Q #1 in Phase 0.
- **iOS JSON mapping bugs** (base64url vs base64, missing `transports`, extension results) → server rejects. Mitigation: contract tests against a real verifier.
- **Configuration hell** (assetlinks fingerprints, AASA caching by Apple CDN, entitlement signing) causes most support issues. Mitigation: "doctor" diagnostics and a troubleshooting guide.
- **macOS JVM packaging** (signing nested JRE, provisioning profile, notarization) may eat more time than the code. Mitigation: timebox it and ship handoff first if needed.
- **Compose Wasm canvas** can block autofill/conditional UI. Mitigation: HTML overlay or document as unsupported.
- **Safari user-activation** rules can break `create()` after async hops. Mitigation: API design + docs.
- **Windows lacks app↔RP binding**, a weaker trust model. Mitigation: document it; the server can't attest the app.
- **Apple Swift-only APIs** may be unreachable from K/N. Mitigation: Swift shim.
- **API churn:** WebAuthn L3, Credential Manager and iOS yearly changes. Mitigation: a thin bridge layer and the binary-compatibility validator.

## Sources
- [AndroidPoet/passkeys-kmp (klibs)](https://klibs.io/project/AndroidPoet/passkeys-kmp)
- [szijpeter/webauthn-kotlin-multiplatform (klibs)](https://klibs.io/project/szijpeter/webauthn-kotlin-multiplatform)
- [androidx.credentials releases](https://developer.android.com/jetpack/androidx/releases/credentials)
- [Android: Create a passkey](https://developer.android.com/identity/passkeys/create-passkeys)
- [Apple: Supporting passkeys](https://developer.apple.com/documentation/authenticationservices/supporting-passkeys)
- [Apple forums: non-App Store Mac apps & passkeys](https://developer.apple.com/forums/thread/767028)
- [Microsoft: webauthn.h](https://learn.microsoft.com/en-us/windows/win32/api/webauthn/)
- [Microsoft: WebAuthn APIs on Windows](https://learn.microsoft.com/en-us/windows/security/identity-protection/hello-for-business/webauthn-apis)
- [Windows 11 3rd-party passkey managers](https://learn.microsoft.com/windows/apps/develop/security/third-party)
- [DSInternals webauthn-interop (Win32 wrapper reference)](https://github.com/MichaelGrafnetter/webauthn-interop)
