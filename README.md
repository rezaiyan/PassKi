# PassKi

Passkeys for Kotlin Multiplatform. One dependency, one call, native authenticators, and errors that tell you how to fix them.

| Platform | Status |
|---|---|
| Android 9+ (Credential Manager) | register, sign in, passkey-or-password, upgrade, signals |
| iOS 16+ (AuthenticationServices) | register, sign in, passkey-or-password, upgrade (iOS 18+); signals return `NotSupported` for now |
| JVM desktop, browser (js / wasmJs) | compile and return `PasskeyError.NotSupported` today; native macOS/tvOS/watchOS/Linux/Windows targets are not published yet |

## Install

```kotlin
commonMain.dependencies { implementation("com.alirezaiyan.passki:passki:0.1.0-alpha01") }
commonTest.dependencies { implementation("com.alirezaiyan.passki:passki-test:0.1.0-alpha01") }
```

## Use

```kotlin
val passkeys = Passkeys() // no Activity, no UIWindow

passkeys.register(api.registrationOptions())
    .onSuccess { api.finishRegistration(it.json) }
    .onFailure { error ->
        when (error) {
            PasskeyError.Cancelled -> Unit
            is PasskeyError.Misconfigured -> log(error.fixHint) // tells you exactly what's wrong
            else -> showError(error.message)
        }
    }

passkeys.signIn(api.authenticationOptions()).onSuccess { api.finishSignIn(it.json) }
passkeys.signInOrPassword(options)   // one sheet: passkeys + saved passwords
passkeys.upgrade(options)            // silently add a passkey after password login
passkeys.signals.unknownCredential(rpId, credentialId) // keep password managers in sync

val caps = passkeys.capabilities()
if (caps.unifiedPasswordSignIn) { /* offer the combined passkey + password button */ }
```

PassKi initialises itself through `androidx.startup`. If your app removes that initializer, call `PasskiAndroid.init(application)` in `Application.onCreate()`.

Options are your server's WebAuthn JSON, used as-is (a `{"publicKey": ...}` envelope is fine). Verify the returned `json` on your server with java-webauthn-server, webauthn4j, SimpleWebAuthn or py_webauthn.

## Good to know

- **`upgrade()` is opportunistic.** It uses conditional create, so the platform may decline without any UI. A decline is reported as `PasskeyError.NotSupported` on both platforms (Android: the credential provider rejected conditional create; iOS: the system declined, typically because there was no recent password AutoFill sign-in for the account). Treat every `upgrade()` failure as silent: do not show an error and do not block your login flow.
- **Verifying upgrade responses.** A conditional-create response is created without a user prompt, so the user-presence (UP) flag may be 0. Servers verifying these responses must not require user presence.
- **`excludeCredentials` on iOS < 17.4 is ignored** by the system, so a duplicate passkey may be created; dedupe on your server. `PasskeyError.AlreadyRegistered` is guaranteed on Android and iOS 17.4+.
- **Duplicate registration on Android shows a sheet first.** When `excludeCredentials` matches, Google Password Manager still shows its create sheet; `PasskeyError.AlreadyRegistered` is returned only after the user taps Continue.
- **Android `upgrade()` and `signals` depend on the user's credential provider** and may return `PasskeyError.NotSupported` even when `capabilities()` reports them as available.
- **Signals on iOS.** `passkeys.signals` returns `PasskeyError.NotSupported` on iOS for now.
- **iOS registration responses** omit the WebAuthn Level 3 `publicKey` and `publicKeyAlgorithm` fields. Read the public key from `attestationObject`, which every server library already does.

## Test without devices

```kotlin
val passkeys = FakePasskeys(origin = "https://example.com") // real ES256 responses, accepted by webauthn4j
passkeys.failNextWith(PasskeyError.Cancelled)               // next call fails once
passkeys.conditionalCreate = false                          // simulate a device without conditional create
```

Other capability flags: `isSupported`, `immediateSignIn`, `unifiedPasswordSignIn`, `signalsSupported`. `TestOptions.creation(rpId)` and `TestOptions.request(rpId)` build server-style options JSON.

## Server setup

- **Android:** serve `https://<rpId>/.well-known/assetlinks.json` with `delegate_permission/common.get_login_creds` and your signing SHA-256. Allow the origin `android:apk-key-hash:<base64url-sha256>` in your verifier.
- **iOS:** add the Associated Domains `webcredentials:<rpId>` entitlement and serve `/.well-known/apple-app-site-association`.
- See `docs/setup/well-known/` for templates.

## Setup tooling

Most passkey failures are configuration: a missing fingerprint, a wrong Team ID, a redirecting `.well-known` file. PassKi ships a Gradle plugin that generates and checks those files, and a runtime doctor that checks the same things on a device.

### Gradle plugin

Apply it in your Android application module (`com.android.application`):

```kotlin
plugins {
    id("com.android.application")
    id("com.alirezaiyan.passki") version "0.1.0-alpha01"
}

passki {
    rpId = "example.com"
    android {
        // Fingerprints you can't derive from a local keystore, e.g. Play App Signing (colon-hex SHA-256).
        extraFingerprints.add("AB:CD:...")
    }
    apple {
        teamId = "ABCDE12345"
        bundleId = "com.example.app"
        entitlementsFile = rootProject.file("iosApp/iosApp/iosApp.entitlements") // optional
    }
}
```

The Android package and signing fingerprints are read from your Android build; `android { packageName = "..." }` overrides the base application ID. `assetlinks.json` gets one statement per package the build produces, so a build type with `applicationIdSuffix = ".debug"` gets its own `com.example.app.debug` statement with only the debug key. `extraFingerprints` go to the packages of non-debuggable variants.

| Task | What it does |
|---|---|
| `passkiFingerprints` | Prints package, variant, keystore and SHA-256 per variant, plus the `android:apk-key-hash:` origins for your server allow-list |
| `passkiWellKnown` | Writes `assetlinks.json` and `apple-app-site-association` to `build/passki/well-known/` |
| `passkiVerify` | Fetches the hosted files from `https://<rpId>` and checks them (and the iOS entitlements file) against the build. Needs network and is not part of `check` |

```
$ ./gradlew :androidApp:passkiFingerprints
passki signing fingerprints:
  package                | variant | keystore                           | SHA-256
  com.alirezaiyan.passki | debug   | /Users/ali/.android/debug.keystore | C9:64:0F:6A:...:79:49:03
  com.alirezaiyan.passki | release | -                                  | (no signing config)
Android WebAuthn origins (server allow-list):
  android:apk-key-hash:yWQPamiQr2wmSdy7q5R5I5Nc_BCL3wMgeARgXFt5SQM

$ ./gradlew :androidApp:passkiVerify
passki: well-known files OK for alirezaiyan.com
```

`passkiVerify` fails the build with a fix hint per problem. Keystore passwords are read from your signing configs but are not tracked task inputs (up-to-date checks use the keystore file, alias and type). They are stored in the configuration-cache entry under `.gradle/configuration-cache/`, which stays on your machine: don't share or upload that directory.

### PasskeyDoctor

`PasskeyDoctor` runs the same checks on the device: platform support, PassKi initialisation, the `INTERNET` permission, your hosted `assetlinks.json` (Android) or `apple-app-site-association` (iOS), and the origin your server must allow.

```kotlin
if (BuildConfig.DEBUG) {
    val report = PasskeyDoctor.run("example.com") // suspend; bare domain, never throws for setup problems
    println(report)
    report.failures.forEach { log(it.message, it.fixHint) } // passkeys won't work until fixed
    report.warnings.forEach { log(it.message, it.fixHint) } // likely trouble, not blocking
}
```

**Use it in debug builds only.** It makes network requests and never runs automatically. On platforms other than Android and iOS the report holds a single skipped check. Example output from the Android 16 emulator:

```
PassKi doctor for alirezaiyan.com: 0 failures, 0 warnings
[PASS] Platform support - Android 16 (API 36)
[PASS] PassKi initialised - package com.alirezaiyan.passki
[PASS] INTERNET permission - granted
[PASS] assetlinks.json - com.alirezaiyan.passki delegated with 1 fingerprint
[PASS] Android origin - allow this origin on your server: android:apk-key-hash:yWQPamiQr2wmSdy7q5R5I5Nc_BCL3wMgeARgXFt5SQM
```

A failing check prints a `fix:` line under it. The text is for humans; read `report.checks` (`name`, `status`, `message`, `fixHint`) instead of parsing it.

## Verified on

Android 16 emulator (Google Password Manager) and iOS 26.3 simulator. Real registration responses were verified by webauthn4j; the fixtures live in `passki-test`.

## API docs

Generate the API reference with `./gradlew dokkaGenerate` (aggregated into `build/dokka/html/`, or per module with `:passki:dokkaGenerate`).

## License

Apache-2.0 (c) Ali Rezaiyan
