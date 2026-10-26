# Tasks

## 1. Rename and error text

- [x] 1.1 Rename `userId` to `userHandle` in `PasskeySignals` and every implementation (Android, iOS, web, JVM unsupported, `FakePasskeys`), with KDoc saying what a user handle is; verify `./gradlew :passki:allTests :passki-test:allTests` passes
- [x] 1.2 Pass the public parameter name into `SignalRequests.canonical()` and add the user-handle fix hint; verify a commonTest asserts the message names `userHandle` and the hint mentions `user.id`

## 2. SignalOutcome

- [x] 2.1 Add the closed `SignalOutcome` type (design decision 4) and change every signal to return `PasskeyResult<SignalOutcome>`, mapping unsupported cases to `Skipped(reason)` on Android, iOS, web, JVM and in `FakePasskeys`; verify platform tests assert `Skipped` for the unsupported cases and `Misconfigured` still fails, across `./gradlew :passki:allTests :passki-test:allTests`

## 3. syncAccount

- [x] 3.1 Add `syncAccount` with a default implementation in `PasskeySignals`; verify commonTest with a hand-written `PasskeySignals` (no override) covering both applied (`Applied`), one skipped (`Applied`), capability off (`Skipped`) and a `Misconfigured` failure
- [x] 3.2 Make `FakePasskeys` record `syncAccount` calls through its existing signal log; verify `FakePasskeysTest`

## 4. Docs

- [x] 4.1 Update the README signals section: `userHandle` with named arguments, `syncAccount`, `SignalOutcome` ("a skipped signal is normal; only log failures"); verify it compiles with `python3 ci/doc-snippets.py`
- [x] 4.2 Add CHANGELOG entries marked **BREAKING** for the return type and **BREAKING (named arguments)** for the rename, each with a "Migrating from" block; verify they show the before/after call

## 5. ABI

- [x] 5.1 Run `./gradlew updateLegacyAbi` and check the dump diff shows only the return types, `SignalOutcome`, `syncAccount` and parameter metadata; verify `checkLegacyAbi` passes after the dump
