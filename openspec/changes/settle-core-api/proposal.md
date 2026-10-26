# Proposal

## Why

PassKi isn't announced yet, so its core API can still change for free. After launch, every change costs adopters a
migration. Three parts of the core contract are either undecided or unwritten. The roadmap deferred the decision on
typed identifiers to "before beta". Nothing says whether `Passkeys` calls are main-safe, or what cancelling one does.
What happens when a second sheet is requested while one is showing is defined only for iOS autofill. See
`docs/plans/2026-10-26-passki-launch-readiness.md`, section 2, and the roadmap's deferred list.

## What Changes

- **Decision: user handles and credential IDs stay `String`.** No `UserHandle` / `CredentialId` value classes. The
  reasons are in the design. Mix-ups are caught by validation and named parameters instead: `improve-signals-api`
  improves the error text, and the docs use named arguments in every signal example.
- **Decision: signals return `PasskeyResult<SignalOutcome>`.** "The platform can't apply this signal" becomes
  `Success(SignalOutcome.Skipped(reason))` instead of `Failure(NotSupported)`, so `onFailure` only sees real problems.
  This is specified and built in `improve-signals-api`, which already owns the signal requirements; this change only
  records the decision.
- **Threading and cancellation contract.**
  - Every `Passkeys` and `PasskeySignals` call is main-safe and can be called from any dispatcher.
  - Results resume in the caller's coroutine context.
  - Cancelling the calling coroutine withdraws the platform request: the sheet is dismissed where the platform
    allows it, the call ends with `CancellationException`, and no late result arrives.
  - The contract is written in KDoc and the docs, and backed by tests on Android, iOS and web.
- **BREAKING (behaviour): one modal request at a time.** A `register`, `signIn` (modal or immediate),
  `signInOrPassword` or `upgrade` call made while another PassKi modal request is showing fails at once with
  `PasskeyError.Interrupted`, whose message says another request is in progress. The first request is unaffected.
  Autofill keeps its current rule and waits for modal requests to finish.
- **Open and closed types are labelled.** The KDoc of every public enum and sealed type says whether it may gain
  entries (open: add an `else` branch) or never will (closed). A CI check enforces it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `passkey-ceremonies`:
  - new threading and cancellation contract;
  - new one-modal-request rule;
  - the binary-compatibility requirement now says every enum and sealed type is labelled open or closed.

## Impact

- `passki` commonMain: `Passkeys.kt` KDoc, `PasskeyError.kt` (the `Interrupted` message for concurrent requests), and
  a small internal modal gate shared by the platform implementations.
- **Android:** `AndroidPasskeys` (modal gate; cancellation of Credential Manager requests).
- **iOS:** `AuthorizationSession` (its arbiter becomes or uses the shared gate).
- **JS / Wasm:** `WebPasskeys` and `BrowserWebAuthn` (modal gate; abort on cancellation is already in place).
- **JVM:** unsupported implementation, no change.
- `passki-test` `FakePasskeys` follows the same one-modal rule.
- New `ci/check-open-types.py`.
- ABI: no signature change from this change (the signal return types change in `improve-signals-api`). The CHANGELOG
  marks the concurrent-request behaviour as breaking.
