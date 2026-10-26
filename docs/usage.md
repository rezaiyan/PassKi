# Usage

Every PassKi call, with error handling. For a first passkey from scratch, start with the [quickstart](quickstart.md).

## Install

PassKi is on Maven Central:

```kotlin
commonMain.dependencies {
    implementation("com.alirezaiyan.passki:passki:0.1.0-alpha04")
    implementation("com.alirezaiyan.passki:passki-compose:0.1.0-alpha04") // optional: Compose Multiplatform UI
    implementation("com.alirezaiyan.passki:passki-demo:0.1.0-alpha04")    // optional: demo server client
}
commonTest.dependencies { implementation("com.alirezaiyan.passki:passki-test:0.1.0-alpha04") }
```

The Gradle plugin is on Maven Central too, not the Gradle Plugin Portal, so `pluginManagement { repositories }` in
`settings.gradle.kts` needs `mavenCentral()`. The Kotlin Multiplatform template already has it.

| Your app | Needs |
|---|---|
| Android | Kotlin 2.1 or newer, `compileSdk` 35 or newer, `minSdk` 24 or newer. Passkeys work on Android 9 (API 28) and newer. |
| iOS, JS and Wasm | Kotlin 2.2 or newer |

PassKi is built with Kotlin 2.2 and depends on `kotlin-stdlib` 2.2, so it doesn't push your app to a newer Kotlin. For
the same reason, its dependencies stay at versions built with Kotlin 2.2: kotlinx-serialization 1.9, Ktor 3.3 (in
`passki-demo`) and Compose Multiplatform 1.10 (in `passki-compose`).

CI builds two apps against every change, using PassKi built from that change through Maven Local: a
Kotlin 2.2.21 app (iOS simulator and JVM tests), and an AGP 9.1.1 Android app on its built-in Kotlin 2.2.10 with
`compileSdk` 35. If an upgrade would lock out apps on those toolchains, the build fails. Also tested with Kotlin 2.4.20.
See [`ci/consumers`](../ci/consumers/README.md).

On Android, PassKi starts itself through `androidx.startup`. If your app turns that off, call
`PasskiAndroid.init(application)` in `Application.onCreate()`.

Your app and your domain must also trust each other through the well-known files and associated domains. The Gradle
plugin creates and checks them for you: see [Setup](setup.md).

## Your server's four calls

Your server creates the options and checks the responses. In your app, that is four calls to your server, for example
(shapes: [Recommended server API](guides/server-api.md)):

```kotlin
interface MyApi {
    suspend fun registrationOptions(): String          // POST /passkeys/registration-options → creation options JSON
    suspend fun finishRegistration(responseJson: String)
    suspend fun authenticationOptions(): String        // POST /passkeys/sign-in-options → request options JSON
    suspend fun finishSignIn(responseJson: String)
}
```

With those, creating a passkey and signing in looks like this:

```kotlin
val passkeys = Passkeys() // no Activity, no UIWindow

passkeys.register(api.registrationOptions())
    .onSuccess { api.finishRegistration(it.json) }
    .onFailure { error ->
        when (error.category) {
            PasskeyErrorCategory.UserCancelled -> Unit // the user closed the sheet: show nothing
            PasskeyErrorCategory.Setup -> log(error)  // what's wrong, its check ID and docs page (PassKi logs it too)
            else -> showError(error.message)
        }
    }

passkeys.signIn(api.authenticationOptions()).onSuccess { api.finishSignIn(it.json) }
```

Pass your server's options JSON to PassKi as it is. A `{"publicKey": ...}` wrapper is fine. Check the returned `json`
on your server: [Verifying on the server](guides/verify-on-server.md).

## Results and errors

`error.category` is one of six values (`UserCancelled`, `NoCredential`, `AlreadyExists`, `Unsupported`, `Setup`,
`Unexpected`) and the set never grows, so a `when` over it needs no `else`. What to show for each error:
[What to show users](guides/what-to-show-users.md). To turn a result into your own type, use `fold`:

```kotlin
sealed interface SignInState {
    data object Idle : SignInState
    data object SignedIn : SignInState
    data class Failed(val message: String) : SignInState
}

suspend fun signInState(): SignInState = passkeys.signIn(api.authenticationOptions()).fold(
    onSuccess = { api.finishSignIn(it.json); SignInState.SignedIn },
    onFailure = { if (it.isUserCancellation) SignInState.Idle else SignInState.Failed(it.message) },
)
```

`PasskeyResult` also has `map`, `mapError`, `recover`, `getOrNull`, `errorOrNull`, `isSuccess` and `toResult()` (a
`kotlin.Result`), named as in `kotlin.Result`.

When setup is wrong, the error names the check that failed. The same check ID appears in `PasskeyDoctor` and
`passkiCheck`, so all three point to the same docs page:

| Error | Check ID |
|---|---|
| Android: the app isn't listed in your domain's `assetlinks.json` | `AND-03` (`PasskeyDoctor` and `passkiCheck` also report `AND-01` or `AND-02` when the file itself is the problem) |
| Android: no credential provider can create or return the passkey | `DEV-01` |
| Android: PassKi isn't started, or no screen is in the foreground | `AND-06` |
| iOS: the app isn't linked to the domain (well-known file or entitlement) | `IOS-02` (`passkiCheck` also checks the entitlement: `IOS-04` to `IOS-06`) |
| The options have no RP ID, or the RP ID isn't a plain domain (`InvalidOptions`) | `CFG-01` |

`error.checkId` is the check ID, and `error.docsUrl` links to its page. `getOrThrow()` throws an exception whose message
includes the fix and the link. Errors that aren't caused by setup, such as autofill without a focused text field, have
no check ID. Symptom-first help: [Troubleshooting](guides/troubleshooting.md).

## Every call

| Call | What it does |
|---|---|
| `register(options)` | Creates a passkey |
| `signIn(options, mode)` | Signs in. `mode` is `Modal` (the default), `ImmediateOnly` (only passkeys already on this device; fails fast with `NoCredentials` if there are none; [which one to use](#a-sign-in-with-a-passkey-button)) or `Autofill` |
| `signInOrPassword(options)` | Shows one sheet with both passkeys and saved passwords |
| `upgrade(options)` | Adds a passkey without any UI after a password sign-in, if the platform allows it ([details](guides/upgrading-users.md)) |
| `signals.syncAccount(...)`, `signals.unknownCredential(rpId, credentialId)` | Keep the credential provider in step with your server: an account's passkeys and label, or one passkey your server deleted. A device that can't apply a signal returns `SignalOutcome.Skipped`, never an error. Registrations and assertions carry the `rpId` to pass |
| `capabilities()` | Reports what this device supports. Cheap enough to call before every sign-in |

```kotlin
val caps = passkeys.capabilities()
if (caps.unifiedPasswordSignIn) { /* offer the combined passkey + password button */ }
```

Calls are main-safe, and only one sheet runs at a time: a second `register` or `signIn` while a sheet is showing (a
double tap) fails with `Interrupted`. Cancelling the calling coroutine withdraws the request.
[Details](platforms.md#threads-cancellation-and-one-sheet-at-a-time).

## Signals

When your passkey settings screen loads an account's passkeys from your server, pass them on in one call:

```kotlin
passkeys.signals.syncAccount(
    rpId = "example.com",
    userHandle = "AQID", // the base64url user.id from the creation options, never your account id or email
    credentialIds = listOf("BAUG"), // every passkey your server still has for this account
    name = "alice@example.com",
    displayName = "Alice",
).onFailure { log(it) } // only real problems arrive here; an unsupported device is Success(Skipped)
```

When your server rejects a sign-in because it no longer has that passkey, tell the provider so it stops offering it:

```kotlin
passkeys.signIn(api.authenticationOptions()).onSuccess { assertion ->
    try {
        api.finishSignIn(assertion.json)
    } catch (e: UnknownPasskeyException) { // however your API client reports "no such passkey" (e.g. HTTP 404)
        passkeys.signals.unknownCredential(assertion.rpId, assertion.credentialId)
    }
}
```

## A "Sign in with a passkey" button

With the default `SignInMode.Modal`, a device with no passkey for your domain doesn't fail. It offers a passkey from
somewhere else: Android opens a "Sign in another way" sheet, and iOS shows a QR code to scan with another device. That
is correct when passkeys are how users sign in. When passkeys are an extra option users add later, first-time users
read it as a bug.

For an optional passkey button, ask only for passkeys already on this device and handle `NoCredentials` in your app:

```kotlin
val mode = if (passkeys.capabilities().immediateSignIn) SignInMode.ImmediateOnly else SignInMode.Modal
passkeys.signIn(api.authenticationOptions(), mode)
    .onSuccess { api.finishSignIn(it.json) }
    .onFailure { error ->
        when (error.category) {
            PasskeyErrorCategory.NoCredential -> showMessage("No passkey on this device yet. Sign in another way, then add one in Settings.")
            PasskeyErrorCategory.UserCancelled -> Unit
            else -> showError(error.message)
        }
    }
```

`ImmediateOnly` returns `NoCredentials` quickly, without showing a sheet. Keep `Modal` when users may have their
passkey on another device, such as a phone they use to sign in on a tablet. The full flow, with Settings and signals:
[Passkeys as an extra sign-in](guides/extra-sign-in.md).

## PRF and largeBlob

`PasskeyExtensions` adds two WebAuthn extensions to your server's options: PRF, which derives an encryption key from a
passkey, and largeBlob, which stores a small amount of data with a passkey: [PRF and largeBlob](extensions.md).
