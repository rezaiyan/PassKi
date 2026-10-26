# Quickstart

From a Kotlin Multiplatform app with its own server to a first passkey that your server verified. You need:

- A KMP app with an Android, iOS or web target (the one from the [KMP wizard](https://kmp.jetbrains.com) is fine).
- A server you can add endpoints to, with a WebAuthn library such as webauthn4j, java-webauthn-server, SimpleWebAuthn
  or py_webauthn.
- A domain on HTTPS that will be your RP ID, such as `example.com`. It can't change later:
  [choose it first](guides/choosing-rp-id.md).
- A device that can hold a passkey: Android 9+ with a Google account and a screen lock, iOS 16+ with Face ID or a
  passcode, or a current Chrome, Safari or Firefox.

Only want to see PassKi work? [Try it](try-it.md) against the demo server instead; it needs none of this.

## 1. Add PassKi

```kotlin
commonMain.dependencies {
    implementation("com.alirezaiyan.passki:passki:0.1.0-alpha04")
}
commonTest.dependencies { implementation("com.alirezaiyan.passki:passki-test:0.1.0-alpha04") }
```

Requirements and compatibility: [Usage](usage.md#install).

## 2. Add the server endpoints

Your server needs four endpoints: creation options, finish registration, request options and finish sign-in
([shapes](guides/server-api.md)). Configure your WebAuthn library with:

- **RP ID:** your domain, `example.com`.
- **Allowed origins:** `https://example.com` (and every web host you sign in from), plus one
  `android:apk-key-hash:<base64url SHA-256>` per Android signing key. Step 4 prints them.

[Verifying on the server](guides/verify-on-server.md) has complete webauthn4j and java-webauthn-server code, with tests
that use `FakePasskeys` as the device. Write that test now: it proves your server works before any device is involved.

## 3. Call PassKi from your app

In `commonMain`, with your own HTTP client behind `MyApi`:

```kotlin
suspend fun createPasskey(api: MyApi) {
    Passkeys().register(api.registrationOptions())
        .onSuccess { api.finishRegistration(it.json) }
        .onFailure { if (!it.isUserCancellation) showError(it.message) }
}

suspend fun signInWithPasskey(api: MyApi) {
    Passkeys().signIn(api.authenticationOptions())
        .onSuccess { api.finishSignIn(it.json) }
        .onFailure { if (!it.isUserCancellation) showError(it.message) }
}
```

`Passkeys()` needs no `Activity` or `UIWindow`. Pass the options JSON from your server unchanged, and post the returned
`json` back unchanged. Setup problems are logged with their fix (logcat tag `PassKi`); see [Logging](logging.md).

## 4. Link your app and your domain

The OS creates a passkey for `example.com` only if `example.com` says your app may use it. Pick one way; both end in
the same files.

**With the Gradle plugin** (also works for apps that host the files themselves):

```kotlin
plugins {
    id("com.alirezaiyan.passki") version "0.1.0-alpha04" // in your Android app module
}

passki {
    rpId = "example.com"
}
```

1. Run `./gradlew passkiSetup`. It writes `assetlinks.json` and `apple-app-site-association` to
   `build/passki/well-known/`, adds `webcredentials:example.com` to your iOS app's entitlements, and prints your RP ID
   and allowed origins for step 2. Add `--preview` to see every change first.
2. Upload the two files to `https://example.com/.well-known/` (status 200, no redirect, `application/json`).
3. Run `./gradlew passkiCheck`. It checks the live files, your Xcode project and, if you point it at one, your
   server's configuration, and fails the build when something is wrong, so it can gate CI.

**By hand:** follow [Server setup by hand](setup.md#server-setup-by-hand). It lists each file, the signing keys to
include (debug, upload and **Play App Signing**), the iOS entitlement and the provisioning profile step.

Either way, add this to a debug build to check the setup from the device:

```kotlin
if (BuildConfig.DEBUG) println(PasskeyDoctor.run("example.com")) // every failing check prints its fix
```

## 5. Run it

### Android

Run the app on a device or a Google Play emulator image signed in to a Google account, tap your "create passkey"
button, and confirm with the screen lock. Your server should accept the response. If it fails with `Misconfigured`
(`AND-03`), the error names the signing key `assetlinks.json` is missing; Google can take a few minutes to see a new
file ([AND-04](checks/AND-04.md)).

### iOS

Set your Team ID in Xcode or your xcconfig, run on an iPhone or a simulator with **Features → Face ID → Enrolled**, and
create a passkey. If it fails with `Misconfigured` (`IOS-02`), the app isn't linked to the domain yet: check the AASA
file and the entitlement, and remember that Apple's CDN caches the file. While developing,
`webcredentials:example.com?mode=developer` on a device in Developer Mode skips the CDN.

### Web

Serve the web app from `example.com` or a subdomain over HTTPS (or from `http://localhost` while developing, with
your server's RP ID set to `localhost`). On the web the well-known files aren't needed: the browser checks that the
page's host matches the RP ID. Make sure the options' RP ID is the domain shared with your apps, not the subdomain
([why](guides/choosing-rp-id.md#web-app-and-apps-together)).

## Next

- [Passkeys as an extra sign-in](guides/extra-sign-in.md): the sign-in button, Settings, deleting and relabelling.
- [What to show users](guides/what-to-show-users.md): one row per error.
- [Troubleshooting](guides/troubleshooting.md): symptom first.
- [Testing](testing.md): `FakePasskeys` in your app's and your server's tests.
