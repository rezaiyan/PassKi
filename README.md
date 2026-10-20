# PassKi

Passkeys for Kotlin Multiplatform. One dependency, one call, native authenticators, and errors that tell you how to fix them.

| Platform | Status |
|---|---|
| Android 9+ (Credential Manager) | register, sign in, passkey-or-password, upgrade, signals |
| iOS 16+ (AuthenticationServices) | register, sign in, passkey-or-password, upgrade (iOS 18+); signals return `NotSupported` for now |
| Web, Windows, macOS, Linux | roadmap; returns `PasskeyError.NotSupported` today |

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
```

Options are your server's WebAuthn JSON, used as-is (a `{"publicKey": ...}` envelope is fine). Verify the returned `json` on your server with java-webauthn-server, webauthn4j, SimpleWebAuthn or py_webauthn.

## Good to know

- **`upgrade()` is opportunistic.** It uses conditional create, so the platform may decline without any UI. Treat every `upgrade()` failure as silent: do not show an error and do not block your login flow.
- **Verifying upgrade responses.** A conditional-create response is created without a user prompt, so the user-presence (UP) flag may be 0. Servers verifying these responses must not require user presence.
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

## License

Apache-2.0 (c) Ali Rezaiyan
