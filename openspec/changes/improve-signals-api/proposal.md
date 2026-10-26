# Proposal

## Why

`acceptedCredentials` and `currentUserDetails` call their parameter `userId`, but it must be the WebAuthn user handle
(`user.id` from the creation options), not the app's account id. Passing an account id often passes base64url
validation (`"1234"` decodes) and then silently does nothing, or hands an internal id to the credential provider.
Lexicon also calls both signals back to back every time Settings loads, ignoring `NotSupported` each time. See
`docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, sections 4.3 and 4.7.

## What Changes

- **BREAKING (named arguments only):** rename the `userId` parameter of `PasskeySignals.acceptedCredentials` and
  `currentUserDetails` to `userHandle`. Positional callers and implementers are unaffected at the binary level; Kotlin
  callers using `userId =` must rename. A deprecated overload isn't possible (same JVM signature).
- KDoc and the `InvalidOptions` message for a bad user handle say what it is: "the base64url `user.id` from the
  creation options; never your account id or email".
- New `PasskeySignals.syncAccount(rpId, userHandle, credentialIds, name, displayName)`: one best-effort call that tells
  the provider the account's current passkeys and label, running what the platform supports. Added with a default
  implementation, so existing implementers stay compatible.
- **BREAKING (return type):** every signal returns `PasskeyResult<SignalOutcome>` instead of `PasskeyResult<Unit>`.
  `SignalOutcome` is `Applied` or `Skipped(reason)`. "The OS or provider can't apply this signal" becomes
  `Success(Skipped(reason))` instead of `Failure(NotSupported)`, so `onFailure { log(it) }` only sees problems worth
  logging. `syncAccount` returns the same type. Decided in `settle-core-api` (design decision 2); free to change
  because PassKi isn't announced yet.
- Docs: a skipped signal is normal and never reaches the user.
- The main spec's "Unsupported platforms fail predictably" still says browsers are unsupported, which hasn't been
  true since web support shipped; the modified requirement covers the JVM target only.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `passkey-signals`: parameter renamed to `userHandle`; `SignalOutcome` return type; unsupported signals are
  skipped, not failed; clearer error for a bad handle; new `syncAccount`.
- `passkey-ceremonies`: "Unsupported platforms fail predictably" says signals are skipped on JVM, and drops the stale
  browser wording.

## Impact

- `passki` commonMain `Passkeys.kt` (`PasskeySignals`), `internal/SignalRequests.kt`; implementations on **Android**
  (`AndroidPasskeys`), **iOS** (`IosSignals`), **JS / Wasm** (`WebPasskeys`), **JVM** (unsupported, inherits default).
- `passki-test` `FakePasskeys` and its tests.
- ABI dumps change: signal return types, the new `SignalOutcome` type and `syncAccount`; parameter name metadata
  (klib). CHANGELOG entries marked breaking for the return type and for named arguments; README signals section.
- Not in this change: `UserHandle` / `CredentialId` value classes; `settle-core-api` decided to keep `String`.
