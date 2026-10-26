# Tasks

## 1. Plugin guarantee

- [ ] 1.1 Add a TestKit test that applies the plugin with only `rpId` to an Android app fixture and asserts `build --dry-run` schedules no `passki*` task and the merged manifest is unchanged; verify it runs in `./gradlew -p passki-gradle-plugin test`
- [ ] 1.2 Make derivable `passkiCheck` inputs optional with skip reasons naming the setting; verify TestKit cases for an app with only `rpId`, with and without an Xcode project

## 2. Debug panel

- [ ] 2.1 Record the last non-cancellation failure in an internal holder at the shared failure path (reuse the logging hook if `add-passkey-logging` has landed); verify commonTest that `Cancelled` doesn't replace a stored `Misconfigured`
- [ ] 2.2 Add `PasskeyDebugPanel` to `passki-compose` (Doctor checks, capabilities, last error, re-run); verify a Compose UI test with a fake Doctor report and an unsupported-platform case
- [ ] 2.3 Add the panel to the sample app's Setup tab; verify it renders on Android and in the web sample
- [ ] 2.4 Run `./gradlew updateLegacyAbi` for `passki-compose` and review the new API

## 3. Docs

- [ ] 3.1 Add path D steps 6 and 7 (Doctor in debug builds, `passkiCheck` in CI with only `rpId`) to the README and `docs/setup.md`; verify they compile with `python3 ci/doc-snippets.py`
- [ ] 3.2 Add a CHANGELOG entry; verify it states that applying the plugin never changes the build
