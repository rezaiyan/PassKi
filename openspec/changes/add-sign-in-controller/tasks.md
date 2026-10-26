# Tasks

## 1. Core controller

- [ ] 1.1 Change `kotlinx-coroutines-core` to `api` in `passki/build.gradle.kts`; verify `./gradlew :passki:dependencies` shows it on the `api` configuration
- [ ] 1.2 Add `PasskeySignInController` with `signIn()`, `isRunning` and `lastError`; verify commonTest with `FakePasskeys` for double call, options failure, cancelled sheet and cancelled starter scope (`runTest`)
- [ ] 1.3 Run `./gradlew updateLegacyAbi` and review the new public surface

## 2. Compose

- [ ] 2.1 Make `PasskeySignInState` delegate to the controller; verify the existing `passki-compose` state tests pass unchanged
- [ ] 2.2 Add the stateless `PasskeySignInButton` overload and rebuild the stateful one on it; verify a Compose UI test for click, loading and custom content
- [ ] 2.3 Confirm the FIDO passkey icon licence, then add `PasskiIcons.Passkey`; verify a screenshot or UI test renders it, and record the licence decision in the KDoc

## 3. Docs

- [ ] 3.1 Add a ViewModel example to the README button section and mention the stateless button and icon; verify it compiles with `python3 ci/doc-snippets.py`
- [ ] 3.2 Add a CHANGELOG entry noting the `api` coroutines dependency; verify it lists every new public type
