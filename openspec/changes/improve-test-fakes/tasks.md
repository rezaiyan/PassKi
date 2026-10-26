# Tasks

## 1. Shared DOM-error mapping

- [ ] 1.1 Extract the DOM-error table from `WebPasskeys.kt` into a commonMain internal function with a platform-hint parameter; verify a table-driven commonTest covers every name × (modal, immediate, conditional create) and `WebPasskeysTest` passes unchanged
- [ ] 1.2 Use it for the shared names in `AndroidErrorMapper`, keeping provider-specific cases; verify Android host mapper tests pass unchanged

## 2. Fake injection

- [ ] 2.1 Replace the fake's failure queue with `Ready` / `Platform` entries and add `failNextWithPlatformError`; verify `FakePasskeysTest` covers the four scenarios in the `test-fakes` delta and that `failNextWith` ordering is unchanged

## 2a. Held sheet

- [ ] 2a.1 Add `holdNextSheet()` / `FakeSheet` (design decision 4) and run the fake's modal ceremonies through its own `ModalGate`; verify `FakePasskeysTest` covers a double `register` while held (`Interrupted`), `finish()`, `dismiss()` (`Cancelled`) and autofill waiting

- [ ] 2a.2 Rename the remaining `userId` names in passki-test to `userHandle` (`TestOptions.creation(userId)`, `FakeCredential.userId`), matching `PasskeySignals` and `FakeSignal` since `improve-signals-api`; verify `grep -rn 'userId' passki-test/src` finds only the WebAuthn JSON key and `./gradlew :passki-test:allTests` passes

## 3. Blocking JVM view

- [ ] 3.1 Add a `jvmMain` source set to `passki-test` with `BlockingFakePasskeys` and `FakePasskeys.blocking()`; verify a `jvmTest` registers and signs in through it and a suspending call sees the same passkey
- [ ] 3.2 Add a Java JUnit test in `passki-test/src/jvmTest/java` that verifies a blocking registration; verify it runs in `./gradlew :passki-test:jvmTest`
- [ ] 3.3 Run `./gradlew updateLegacyAbi` for `passki` and `passki-test`; review that only the new API appears

## 4. Docs

- [ ] 4.1 Add the Java example and `failNextWithPlatformError` to the README testing section; verify it compiles with `python3 ci/doc-snippets.py`
- [ ] 4.2 Add a CHANGELOG entry; verify it names both additions
