<h1 align="center">PassKi</h1>

<p align="center">
  <b>Passkeys for Kotlin Multiplatform.</b><br>
  One dependency, one call, native authenticators, and errors that tell you how to fix them.
</p>

<p align="center">
  <img alt="Kotlin Multiplatform" src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Android 9+" src="https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white">
  <img alt="iOS 16+" src="https://img.shields.io/badge/iOS-16%2B-000000?logo=apple&logoColor=white">
  <img alt="Web" src="https://img.shields.io/badge/Web-js%20%7C%20wasm-F7DF1E">
  <a href="https://central.sonatype.com/artifact/com.alirezaiyan.passki/passki"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/com.alirezaiyan.passki/passki?label=Maven%20Central&color=orange"></a>
  <img alt="License" src="https://img.shields.io/badge/license-Apache--2.0-blue">
</p>

<table align="center">
  <tr>
    <th>Android 16 · Google Password Manager</th>
    <th>iOS 26.3 · Face ID</th>
  </tr>
  <tr>
    <td><img src="docs/media/android-register-sign-in.gif" width="300" alt="Android: Create an account opens the Google Password Manager sheet, then the app shows Verified by the server; after signing out, Sign in with a passkey signs back in"></td>
    <td><img src="docs/media/ios-register-sign-in.gif" width="300" alt="iOS: Create an account opens the Add a passkey sheet, Face ID, then Verified by the server; after signing out, Sign in with a passkey signs back in"></td>
  </tr>
</table>

<p align="center"><sub>The <a href="docs/try-it.md">sample app</a>: create a passkey, the server verifies it, sign
out, sign back in.</sub></p>

Your server creates WebAuthn options and checks the response. PassKi shows the native passkey sheet in between, from
`commonMain`, on Android, iOS and the web:

```kotlin
val passkeys = Passkeys() // no Activity, no UIWindow

passkeys.register(api.registrationOptions())         // options JSON from your server
    .onSuccess { api.finishRegistration(it.json) }  // your server checks the response

passkeys.signIn(api.authenticationOptions())
    .onSuccess { api.finishSignIn(it.json) }
```

- **Errors you can act on.** A setup problem comes back as `Misconfigured` with a fix and a check ID such as `AND-03`
  that links to its docs page, and PassKi logs it, so a release build still says why.
- **Setup that checks itself.** A Gradle plugin writes your well-known files and iOS entitlements and checks your live
  domain; `PasskeyDoctor` runs the same checks on a device. Both are optional.
- **Tests without a device.** `FakePasskeys` produces real signed responses that server libraries such as webauthn4j
  and java-webauthn-server verify, so you can test your app and your server on the JVM.

## Install

```kotlin
commonMain.dependencies {
    implementation(project.dependencies.platform("com.alirezaiyan.passki:passki-bom:0.1.0-alpha04"))
    implementation("com.alirezaiyan.passki:passki") // the BOM keeps every PassKi library on one version
}
commonTest.dependencies { implementation("com.alirezaiyan.passki:passki-test") }
```

Android needs Kotlin 2.1+ and runs passkeys on Android 9+; iOS 16+ and the web need Kotlin 2.2+.
[Requirements](docs/usage.md#install). Compose Multiplatform UI: [`passki-compose`](docs/compose.md).

## Quickstart

1. **Your server** gets four endpoints, for creation options, registration, request options and sign-in, verified with
   any WebAuthn library ([shapes](docs/guides/server-api.md), [verifying](docs/guides/verify-on-server.md)). Its RP ID
   is your domain, such as `example.com`: [choose it carefully](docs/guides/choosing-rp-id.md), it can't change.
2. **Your app** calls `register` and `signIn` as above, and handles errors by category:

   ```kotlin
   passkeys.signIn(api.authenticationOptions())
       .onSuccess { api.finishSignIn(it.json) }
       .onFailure { if (!it.isUserCancellation) showError(it.message) } // what to show: docs/guides/what-to-show-users.md
   ```

3. **Your domain** lists your app, so the OS lets it use passkeys for that domain. Pick one:

   | With the Gradle plugin | By hand |
   |---|---|
   | `passki { rpId = "example.com" }` in your Android app module, then `./gradlew passkiSetup`: it writes both well-known files and the iOS entitlement, and prints your server's allowed origins. Upload the files, then `./gradlew passkiCheck` checks everything (also in CI) | Serve `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association`, add `webcredentials:example.com` to the iOS entitlements, and allow one `android:apk-key-hash:` origin per signing key on your server. [Checklist](docs/setup.md#server-setup-by-hand) |

4. **Run it** on a device and create a passkey. In a debug build, `PasskeyDoctor.run("example.com")` prints every
   failing check with its fix.

Step by step, per platform: [Quickstart](docs/quickstart.md). Just want to see it work first?
[Try it](docs/try-it.md) against the hosted demo server, with nothing to set up.

## Next

| Page | What's in it |
|---|---|
| [Quickstart](docs/quickstart.md) | Your app and your server to a first passkey, on Android, iOS and the web |
| [Guides](docs/README.md#guides) | Passkeys as an extra sign-in, what to show users, RP ID, server API and verification, upgrades |
| [Concepts](docs/concepts.md) | How it works, and the words these docs use |
| [Usage](docs/usage.md) | Every call, results and errors, signals |
| [Platforms](docs/platforms.md) | What each OS version supports, and how the platforms differ |
| [Troubleshooting](docs/guides/troubleshooting.md) | Symptom first: AND-03, Play builds, IOS-02, the web |
| [Security model](docs/security-model.md) | What PassKi does and doesn't do, and what your server must check |
| [All docs](docs/README.md) | Compose, autofill, PRF and largeBlob, testing, logging, setup tooling, the check catalog |

API reference: [rezaiyan.github.io/PassKi](https://rezaiyan.github.io/PassKi/) (one folder per version). Changes in
each release: [CHANGELOG](CHANGELOG.md). What alpha, beta and 1.0 promise: [Versioning](docs/versioning.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Building the iOS targets needs Xcode.

## License

Apache-2.0 © Ali Rezaiyan
