# Security model

What PassKi does with passkeys, and what stays your server's job. Each claim links to the code, test or CI check that
keeps it true.

## What PassKi does

PassKi passes your server's WebAuthn options to the platform's passkey API (Credential Manager on Android,
AuthenticationServices on iOS, `navigator.credentials` in the browser) and returns the platform's response JSON
unchanged, so your server can verify exactly what the authenticator signed.

| Claim | Backed by |
|---|---|
| **PassKi never verifies responses.** It checks that options and responses are well-formed JSON with base64url fields, and nothing more; the signature, challenge, origin and RP ID are your server's to check | [`WebAuthnResponses.kt`](../passki/src/commonMain/kotlin/com/alirezaiyan/passki/internal/WebAuthnResponses.kt): parsing only, no crypto |
| **PassKi never stores credentials.** Passkeys stay in the platform's credential provider (Google Password Manager, iCloud Keychain, a third-party manager). PassKi writes no files, preferences or keychain items; in memory it keeps only its settings (`PassKi.logLevel`), the request in progress and, on the web, whether the browser rejected immediate mediation | `passki/src` uses no storage API (no `SharedPreferences`, `NSUserDefaults` or `localStorage`) |
| **No network calls, except `PasskeyDoctor`.** The library talks only to the platform API. `PasskeyDoctor` fetches your own well-known files when you call it, and never runs by itself | [`ci/check-network-use.py`](../ci/check-network-use.py) fails CI if a network API appears anywhere else in `passki/src` |
| **No telemetry.** Nothing is collected or sent, to PassKi's author or anyone else | Same check: there is no network code to send it with |
| **Secrets stay out of `toString()`.** Passwords print as `***`, PRF outputs and JSON as their sizes | `PasskeyResultTest` ([`passwordToStringHidesThePassword`](../passki/src/commonTest/kotlin/com/alirezaiyan/passki/PasskeyResultTest.kt), `registrationToStringOmitsJsonBody`), `PasskeyExtensionsTest` (PRF) |
| **Secrets stay out of logs.** No challenges, user handles, options or response JSON, passwords, PRF outputs or large-blob data; credential IDs shortened to 8 characters | [`PasskiLogRedactionTest`](../passki-test/src/commonTest/kotlin/com/alirezaiyan/passki/test/FakePasskeysLoggingTest.kt) checks a full debug trace |
| **Error messages don't repeat options or responses.** Parse errors keep only the reason, not the JSON they failed on | `PassKiLogTest.invalidJsonOptionsDoNotRepeatTheOptionsInTheError` ([`PassKiLogTest.kt`](../passki/src/commonTest/kotlin/com/alirezaiyan/passki/PassKiLogTest.kt)) |

### Other PassKi artifacts

- **`passki-demo`** talks to the hosted demo server (`passki.alirezaiyan.com`), which is its purpose. Use it for trying
  PassKi, never in production: the demo server deletes its users after 24 hours.
- **The Gradle plugin's** `passkiCheck` and `passkiVerify` fetch your well-known files and, if you configure one, your
  server-config URL. `passkiSetup` fetches your RP ID's live well-known files for the shared-RP-ID warning (skipped
  with `--offline`), and with `useDemoServer()` registers your app's public identifiers (package names, signing-key
  fingerprints, Apple app IDs) with the demo server, printing what it sends first. Nothing runs at configuration
  time.
- **`passki-test`** makes no network calls; `FakePasskeys` signs responses locally with keys it generates.

## What your server must do

PassKi's responses are untrusted input until your server verifies them with a WebAuthn library:

- **Verify every response:** signature, a challenge your server issued for this ceremony and used once, the RP ID and
  an allowed origin (`https://<RP ID>` and one `android:apk-key-hash:` per Android signing key).
- **Bind registration challenges to the account** they were issued for, so one account's response can't add a passkey
  to another.
- **Require user verification for sign-in,** except for `upgrade()` registrations, which come without it by design.
- **Keep user handles random** and separate from account IDs ([rules](guides/user-names.md)).
- **Treat malformed input as a 400,** never a 500: some libraries throw unchecked exceptions on bad JSON.
- **Rate-limit the public endpoints** (sign-in options and sign-in).

Complete, tested examples: [Verifying on the server](guides/verify-on-server.md). Endpoint shapes and status codes:
[Recommended server API](guides/server-api.md).

## Reporting a vulnerability

Please don't open a public issue. Report it privately through GitHub's security advisories for this repository
(**Security → Report a vulnerability**).
