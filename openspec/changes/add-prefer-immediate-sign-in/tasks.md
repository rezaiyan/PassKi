# Tasks

## 1. Mode and policy

- [ ] 1.1 Add `SignInMode.PreferImmediate` with KDoc describing the fallback and `NoCredentials` behaviour; verify `./gradlew updateLegacyAbi` shows only the new entry
- [ ] 1.2 Add the shared mode-resolution helper and route Android, iOS and web `signIn` through it; verify commonTest cases for immediate available, unavailable, `NotSupported` at request time and `NoCredentials`
- [ ] 1.3 Cover the web path in `WebPasskeysTest` (probe rejects immediate → one modal request); verify `./gradlew :passki:jsTest :passki:wasmJsTest`

## 2. Fake

- [ ] 2.1 Use the helper in `FakePasskeys` so `PreferImmediate` follows `immediateSignIn`; verify `FakePasskeysTest` covers both scenarios of the `test-fakes` delta

## 3. Docs

- [ ] 3.1 Replace the README optional-button snippet with `PreferImmediate` and describe browsers without immediate mediation; verify it compiles with `python3 ci/doc-snippets.py`
- [ ] 3.2 List immediate sign-in availability per platform and browser in `docs/platforms.md`; verify each row names its source (spec, browser release notes or a `verified-on` run)
- [ ] 3.3 Add a CHANGELOG entry; verify it shows the one-line migration
