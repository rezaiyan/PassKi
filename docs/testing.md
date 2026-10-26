# Testing

`passki-test` gives you `FakePasskeys`: a drop-in `Passkeys` that runs anywhere Kotlin runs and returns real signed
WebAuthn responses, the kind server libraries such as webauthn4j and java-webauthn-server verify. Use it to test your
app's passkey code without a device, and your server without a phone.

```kotlin
commonTest.dependencies { implementation("com.alirezaiyan.passki:passki-test:0.1.0-alpha04") }
```

## Test without devices

```kotlin
val passkeys = FakePasskeys(origin = "https://example.com") // real ES256 responses, accepted by webauthn4j
passkeys.failNextWith(PasskeyError.Cancelled)               // next call fails once
passkeys.conditionalCreate = false                          // simulate a device without conditional create
```

- Other capability flags: `isSupported`, `immediateSignIn`, `unifiedPasswordSignIn`, `signalsSupported`,
  `autofillSupported` (when on, `SignInMode.Autofill` completes at once, as if the user picked the suggestion).
- `passkeys.authenticator.prfSupported` / `largeBlobSupported` switch the extensions off.
- `TestOptions.creation(rpId)` and `TestOptions.request(rpId)` build server-style options JSON.
- `passkeys.receivedSignals` lists the signals your code sent; `savedPasswords` feeds `signInOrPassword`.
- `FakePasskeys` logs like the real implementations do (through `PassKi.logHandler`), so a test can assert what your
  users would see in logcat. Restore `PassKi.logLevel` and `logHandler` in `@AfterTest` if you change them: they are
  process-wide.

## Test your server

`FakePasskeys` also runs on a JVM server, so your server tests can do a real register and sign-in round trip against
your own endpoints and verifier:

```kotlin
dependencies {
    testImplementation("com.alirezaiyan.passki:passki-test:0.1.0-alpha04")
    // Up to 0.1.0-alpha03 only; later versions bring coroutines with them.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
```

```kotlin
import kotlin.test.Test

@Test
fun registerThenSignIn(): Unit = runBlocking {
    val device = FakePasskeys(origin = "https://example.com") // must be one of your server's allowed origins
    val created = device.register(server.registrationOptions(user)).getOrThrow()
    server.finishRegistration(user, created.json)
    val assertion = device.signIn(server.authenticationOptions()).getOrThrow()
    server.finishSignIn(assertion.json)
}
```

Complete examples with webauthn4j and java-webauthn-server, compiled and run in PassKi's CI:
[Verifying on the server](guides/verify-on-server.md).
