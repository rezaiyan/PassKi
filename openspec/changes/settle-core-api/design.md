# Design

## Context

What the code does today (2026-10-26):

- **Android** (`AndroidPasskeys`) calls the suspend `CredentialManager.getCredential` / `createCredential` with the
  current activity, so cancelling the coroutine cancels the Credential Manager request. Whether the provider's sheet
  closes is up to the provider. The autofill path posts to the main looper and clears its pending request on
  cancellation. There is no rule for concurrent modal requests, and no test seam: the `CredentialManager` is created
  directly in the class.
- **iOS** (`AuthorizationSession.perform`) switches to `Dispatchers.Main` and calls `controller.cancel()` on
  cancellation. `AuthorizationArbiter` counts modal requests: a modal request cancels pending AutoFill requests, and
  AutoFill waits for `modalRequests == 0`. Two modal requests can run at once; AuthenticationServices then fails one of
  them in its own way.
- **Web** (`BrowserWebAuthn`) aborts the browser request through `passkiAbort(id)` on cancellation. There is no rule
  for concurrent calls; browsers reject a second `navigator.credentials` call while one is pending, which surfaces as
  a DOM error.
- Signals use `suspendCancellableCoroutine` on iOS and Credential Manager on Android.
- `PasskeyError.Interrupted` exists: "The request was interrupted by the system (e.g. app backgrounded). Safe to
  retry."
- The roadmap deferred `UserHandle` / `CredentialId` value classes and `SignalOutcome` to "before beta". Since
  PassKi isn't announced, "before launch" is the cheaper deadline.

## Goals / Non-Goals

**Goals:**
- One written contract for threads, cancellation and concurrency that holds on every platform and is tested.
- A final answer on the typed identifiers, so nothing about the core signatures is left open at launch.

**Non-Goals:**
- A queue that runs a second modal request after the first (see decision 3).
- Joining duplicate requests (that's `add-sign-in-controller`).
- Changing the signal return types here (`improve-signals-api` owns the signal requirements).

## Decisions

1. **Identifiers stay `String`.**
   - The JSON from the server carries them as strings, and the app passes them straight back. Wrapping them at every
     call adds friction on the first screen of the docs.
   - Value-class parameters get mangled JVM names, so Java callers (server tests using `passki-test`) can't call
     them.
   - They wouldn't stop the real mistake from the Lexicon case study: `UserHandle(accountId)` compiles as happily as
     `accountId`.
   - The mix-ups they would catch are already caught at run time. An RP ID must be a bare domain (CFG-01). User
     handles and credential IDs must be base64url, and the error names the parameter.
   - The docs use named arguments in every signal example.
   - Alternative: value classes with validating factories. Rejected for the interop cost and friction above;
     revisit only if adopters report swaps that validation can't catch.
2. **`SignalOutcome` in `improve-signals-api`.** Signals that the platform can't apply return
   `Success(SignalOutcome.Skipped(reason))`; `Failure` is kept for `Misconfigured`, `InvalidOptions` and `Unknown`.
   This keeps "never show `NotSupported` from a signal to the user" out of every app. That change's spec carries the
   requirement so the "Signal calls" requirement is modified in one place.
3. **Second modal request fails fast with `Interrupted`.** `Interrupted` stays a `data object` (so
   `PasskeyError.Interrupted ->` keeps working in `when`); its one message covers both causes: "The passkey request
   was interrupted, or another passkey request is in progress. Retry it."
   - Failing is predictable. Queueing would show a second sheet straight after the first, which after a double tap
     is worse than nothing.
   - `Interrupted` already means "nothing went wrong, retry later", and its KDoc now tells apps not to show it as an
     error.
   - Alternative: a new `Busy` error. Rejected to keep the error set small; with `add-sign-in-controller`, most apps
     never see this case.
4. **One internal modal gate in commonMain,** a non-suspending `tryBegin(): (() -> Unit)?` plus `awaitIdle()`. It is
   used by Android, web and `FakePasskeys`. iOS's `AuthorizationArbiter` delegates its modal count to it, so the
   AutoFill rules stay where they are. Each process has one gate, shared by all `Passkeys()` instances, as the
   arbiter is today.
5. **Main-safety by construction, verified by tests.**
   - iOS already switches to `Dispatchers.Main` and Android posts UI work to the main looper.
   - Web is single-threaded.
   - Tests on each platform call from `Dispatchers.Default` and assert the result arrives and the gate is released.
6. **Android test seam: SDK level and gate.** Host tests have no Robolectric and no real `android.os.Bundle`, so a
   Credential Manager request can't even be built there. `AndroidPasskeys` takes the SDK level and the gate as
   internal constructor parameters and checks the gate before it builds a request, so a host test holding the gate
   sees `Interrupted` from every modal call. Cancellation and release are tested once on the shared helper
   (`ModalGateTest`, which also runs as an Android host test). Credential Manager's own request cancellation is
   checked on the emulator. Alternative: add Robolectric for these tests, rejected as a new test dependency for little
   extra coverage.
7. **Open / closed labels checked by a script.** `ci/check-open-types.py` reads the enum and sealed types from the
   ABI dumps (`passki/api`, `passki-compose/api`, `passki-test/api`). It fails unless each one's KDoc contains
   "Open:" or "Closed:" as its own paragraph. Planned labels:
   - Open: `PasskeyError`, `SignInMode`, `DoctorStatus`, `LargeBlobSupport`, `SignInCredential`.
   - Closed: `PasskeyResult`.

## Risks / Trade-offs

- [Apps that relied on two modal calls overlapping break] → None are known, since PassKi is unannounced. The
  CHANGELOG marks it breaking.
- [Android providers keep their sheet after cancellation] → The spec only promises that PassKi asks for dismissal and
  drops late results. The platforms page documents this per provider where observed.
- [`Interrupted` now has two causes] → Its KDoc lists both; neither should be shown to users as a failure.
