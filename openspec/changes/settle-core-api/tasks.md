# Tasks

## 1. Modal gate

- [x] 1.1 Add the internal modal gate in commonMain (design decision 4) with commonTest cases for begin, release (idempotent), a second begin while held, and `awaitIdle`; verify `./gradlew :passki:jvmTest`
- [x] 1.2 Make iOS `AuthorizationArbiter` delegate its modal count to the gate and fail a second modal request with `Interrupted`; verify the existing `AuthorizationArbiterTest` still passes and a new test covers the second modal request (`./gradlew :passki:iosSimulatorArm64Test`)
- [x] 1.3 Add the Android test seam (design decision 6: SDK level and gate) and use the gate in `AndroidPasskeys` for `register`, modal and immediate `signIn`, `signInOrPassword` and `upgrade`; verify host tests for a second request failing with `Interrupted` (the first request completing and the gate's release are covered by `ModalGateTest`, which also runs as a host test) (`./gradlew :passki:testAndroidHostTest`)
- [x] 1.4 Use the gate in `WebPasskeys`; verify `WebPasskeysTest` covers the second request on `jsBrowserTest` and `wasmJsBrowserTest`
- [x] 1.5 `FakePasskeys` completes every call at once, so it never has a pending sheet for the rule to apply to; move the rule, with a held-sheet API, to `improve-test-fakes` (task 2a.1); verify `openspec validate improve-test-fakes --strict` passes
- [x] 1.6 Update the `Interrupted` KDoc and message for the concurrent case (both causes, never shown to users as a failure); verify `AndroidErrorMapperTest` and `AppleErrorMapperTest` still pass

## 2. Threading and cancellation

- [x] 2.1 Cancellation on Android: the gate helper's cancellation behaviour (ends with `CancellationException`, releases the gate, a late result goes nowhere) is tested in `ModalGateTest`, which runs on every target including Android host tests; Credential Manager's own request cancellation is its suspend API's contract (androidx.credentials 1.6.0 `CredentialManager.kt`: `invokeOnCancellation { canceller.cancel() }` on every suspend call); verify both
- [x] 2.2 iOS: `AuthorizationSession.perform` switches to `Dispatchers.Main`, refuses a second modal request before anything is shown, and on cancellation calls `controller.cancel()` and ends the session, which releases the gate; a failure in between also releases it. `perform` presents a real system sheet, so it can't run in simulator tests; verify the refusal and release paths in `AuthorizationArbiterTest` (`./gradlew :passki:iosSimulatorArm64Test`) and the rest in task 2.6
- [x] 2.3 Web tests: cancelling a pending call aborts the browser request (`BrowserAbortTest`, JS in headless Chrome; Wasm shares the glue) and releases the gate (`WebPasskeysTest`); raise Mocha's 2-second default in `passki/karma.config.d` so the first test of a fresh page doesn't time out on bundle loading; verify `jsBrowserTest` and `wasmJsBrowserTest` pass twice with `--rerun-tasks`
- [x] 2.4 Write the "Threading and cancellation" KDoc section on `Passkeys` and `PasskeySignals` (main-safe, caller context, cancellation vs `Cancelled`, one modal at a time); verify Dokka renders it
- [x] 2.5 Add the contract to the docs: one README line and a section in `docs/platforms.md` (moved into `docs/usage.md` by `add-developer-docs-pack` if that lands first), including the Android provider caveat; verify `python3 ci/check-docs-links.py` and `python3 ci/doc-snippets.py` pass

- [ ] 2.6 On the iOS simulator and the Android emulator, run the sample: start a sign-in and leave the screen while the sheet shows (the call ends without a result, and the next sign-in works), and tap Create twice quickly (the second tap gets `Interrupted`, which the sample doesn't show as an error); verify on both

## 3. Open and closed types

- [x] 3.1 Add "Open:" / "Closed:" paragraphs to the KDoc of every public enum and sealed type (design decision 7); verify Dokka shows them
- [x] 3.2 Add `ci/check-open-types.py` and run it in the CI `library` job; verify it passes, and fails naming the type when one label is removed

## 4. Record the decisions

- [x] 4.1 Update the roadmap's deferred list and the case study backlog rows for `UserHandle` / `CredentialId` (decided: stay `String`) and `SignalOutcome` (moved into `improve-signals-api`); verify `python3 ci/check-docs-links.py` passes
- [x] 4.2 Use named arguments in every signal example in README and docs; today no README or docs code example calls a signal (only the README call table names the parameters), and the signals section that `improve-signals-api` task 4.1 adds uses named arguments; verify `grep -rn 'signals\.' README.md docs` outside `docs/plans/` finds only the call table

## 5. Release notes

- [x] 5.1 Add the CHANGELOG entries (**BREAKING (behaviour)**: one modal request at a time; threading contract documented) and run `./gradlew checkLegacyAbi`; verify it passes, or the dump diff shows only internal changes
