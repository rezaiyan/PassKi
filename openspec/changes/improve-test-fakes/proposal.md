# Proposal

## Why

`FakePasskeys` is PassKi's strongest DX feature (real ES256 responses that server libraries accept), and two gaps showed
up when Lexicon used it on both sides. `failNextWith(PasskeyError)` skips PassKi's error mapping, so apps can't test
how a real platform failure (a DOM `NotAllowedError` during immediate sign-in, a `SecurityError`) reaches their code.
And Lexicon's server tests wrap every fake call in `runBlocking`, because the fake is `suspend`-only, which is clumsy in
JVM and Java test code where most server tests live. See `docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`,
section 4.10.

## What Changes

- `FakePasskeys.failNextWithPlatformError(name, message)`: queue a WebAuthn DOM error by name (`NotAllowedError`,
  `InvalidStateError`, `SecurityError`, `NotSupportedError`, `AbortError`, `TypeError`, …) that goes through PassKi's
  real DOM-error mapping for the operation and mode, instead of a ready-made `PasskeyError`.
- A shared, platform-neutral DOM-error mapping in `passki` used by the web implementation, the Android DOM error path
  and the fake, so they can't disagree.
- JVM: `FakePasskeys.blocking()` returns a view with the same operations as plain (non-`suspend`) functions, callable
  from Kotlin and Java tests.
- `FakePasskeys.holdNextSheet()`: keeps the next ceremony pending, as if its sheet were showing, until the test calls
  `finish()` (the ceremony completes) or `dismiss()` (it fails with `Cancelled`). While it is pending, another modal
  ceremony on the same fake fails with `Interrupted`, matching the one-modal-request rule from `settle-core-api`, so
  apps can test their double-tap handling.
- README "Testing your server" shows a Java/JUnit example with the blocking view.
- Additive; not breaking.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `test-fakes`: platform error injection through the real mapping; blocking JVM view; a held sheet that follows the
  one-modal-request rule.

## Impact

- `passki` commonMain: shared DOM-error mapping (internal, `@InternalPasskiApi` where `passki-test` needs it); the
  **JS / Wasm** mapper in `WebPasskeys.kt` and the **Android** DOM error branch in `AndroidErrorMapper` call it with
  their own platform hints. **iOS** maps `ASAuthorizationError` codes, not DOM names: unchanged.
- `passki-test`: new commonMain API, new `jvmMain` source set for the blocking view.
- ABI dumps for `passki-test` (common, JVM, Android); README; CHANGELOG.
