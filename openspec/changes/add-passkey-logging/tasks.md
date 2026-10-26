# Tasks

## 1. Logging core

- [x] 1.1 Add `PassKi`, `PasskiLogLevel` and `PasskiLogHandler` in commonMain with atomic storage; verify commonTest that changing level and handler takes effect for the next call
- [x] 1.2 Add default handlers for Android, iOS, JS / Wasm and JVM; verify each platform's test source set captures a line from the default handler (Android with Robolectric `ShadowLog`)
  - Done without Robolectric (the module has none): `AndroidLogHandler` takes `Log.println` as a parameter, and a host test checks priorities and the standard-error fallback used when `android.util.Log` is a stub (an app's local unit tests). JVM captures `System.err`; JS replaces `console.error` in headless Chrome; Wasm runs the same `webMain` handler; iOS can't read `NSLog` back, so its test only checks the call, with `%` in the message.
- [x] 1.3 Add redaction helpers (`shortId`, JSON length only); verify unit tests for short and empty IDs

## 2. Log events

- [x] 2.1 Log setup failures and `Unknown` at `Error` from `passkeyCatching`, skipping user-cancellation, no-credential and already-exists categories; verify commonTest with `FakePasskeys.failNextWith` for `Misconfigured`, `Cancelled` and `NoCredentials`
  - Outcome logging sits in `LoggingPasskeys` (what `Passkeys()` returns) and FakePasskeys, through `PasskiLog`; `passkeyCatching` adds only the debug mapping line. `PasskeyError.category` doesn't exist yet, so `PasskiLog.levelOf` matches the error types; `add-result-and-error-helpers` switches it to `category`.
- [x] 2.2 Add debug start/outcome tracing for register, signIn, signInOrPassword, upgrade and signals, including platform error type before mapping; verify Android host tests and `WebPasskeysTest` capture the mapped line
  - Android host tests can't reach Credential Manager, so the Android mapped line is covered by `PassKiLogTest` (commonTest, also run on the Android host) and `AndroidLoggingTest` traces a call through the real `AndroidPasskeys`.
- [x] 2.3 Add a redaction test that runs a full `FakePasskeys` register + sign-in at `Debug` and asserts the output contains no challenge, user handle or JSON; verify it runs in `./gradlew check`

## 3. Docs

- [x] 3.1 Add a README "Logging" section (levels, Timber / Kermit / Crashlytics handler examples, what's never logged); verify it compiles with `python3 ci/doc-snippets.py`
- [x] 3.2 Add the logging rule to `CONTRIBUTING.md` and a CHANGELOG entry; verify both mention the redaction test
- [x] 3.3 Run `./gradlew updateLegacyAbi` and review that only the new `PassKi` types were added
