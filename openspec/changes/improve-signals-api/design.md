# Design

## Context

- `PasskeySignals` (`Passkeys.kt`) has three functions; implementations: `AndroidPasskeys`, `IosSignals`,
  `WebPasskeys`, the JVM unsupported implementation and `passki-test` `FakePasskeys`.
- `internal/SignalRequests.canonical()` already rejects non-base64url input with `InvalidOptions("<field> must be
  base64url")`, using the field name `userId`. Many account ids (`"1234"`) are valid base64url, so validation can't
  catch the mistake; the wording and KDoc are what can.
- The spec "Binary-compatible public types" requires new `PasskeySignals` members to have default implementations.

## Goals / Non-Goals

**Goals:**
- Make the right value obvious from the parameter name, KDoc and error text.
- One call for "sync this account's passkeys" with best-effort semantics.

**Non-Goals:**
- `UserHandle` / `CredentialId` value classes (`settle-core-api` keeps `String`).
- Detecting account ids that happen to be valid base64url.

## Decisions

1. **Plain rename, no deprecation overload.** Kotlin parameter names aren't part of the JVM signature, so an overload
   with the old name can't coexist. Binary compatibility is kept (JVM signature unchanged); only Kotlin source using
   named arguments breaks. The klib ABI dump records parameter names and changes; that is accepted and noted in the
   CHANGELOG as **BREAKING (named arguments)**.
2. **`syncAccount` as a default interface method** in commonMain that calls `acceptedCredentials` then
   `currentUserDetails` and combines their `SignalOutcome`s (decision 4). Platforms don't override it unless a native
   batch API appears. Order: accepted credentials first, since removing stale passkeys matters more than labels.
3. **Error text.** `canonical()` takes the public parameter name (`userHandle`, `credentialIds`) and the fix hint for
   the user handle case names `user.id` and `PasskeyAssertion.userHandle`.

4. **`SignalOutcome` is a closed sealed interface** with `data object Applied` and `class Skipped(val reason:
   String)`, marked "Closed:" in its KDoc (see `settle-core-api` decision 7). Each platform maps its "not supported"
   cases (capability off, provider says unsupported, iOS before 26, JVM) to `Skipped` with a reason; everything else
   keeps its current error mapping. `syncAccount` returns `Applied` if any signal was applied, otherwise `Skipped` with
   the first reason.
5. **Order with `settle-core-api`.** Either can land first; both are in the same pre-launch alpha.

## Risks / Trade-offs

- [Named-argument callers break] → Alpha policy allows it; the compiler error names the parameter, and the CHANGELOG
  shows the one-word fix.
- [`syncAccount` hides a partial failure] → It returns the first hard error (`Misconfigured`, `InvalidOptions`,
  `Unknown`); only unsupported signals are skipped, and debug logging (`add-passkey-logging`) shows each step.
- [Apps can't tell "not supported" apart any more] → `Skipped.reason` says why, and `capabilities().signals` still
  reports support up front.

## Migration Plan

Rename `userId =` to `userHandle =` at call sites. Code that checked `NotSupported` from a signal can drop the check.
Apps calling both signals can switch to `syncAccount`.
