# Tasks

## 1. Result helpers

- [x] 1.1 Add `fold`, `mapError`, `recover`, `isSuccess`, `isFailure`, `errorOrNull` and `toResult` to `PasskeyResult.kt` with KDoc; verify with commonTest cases for each on `Success` and `Failure` (run `./gradlew :passki:allTests`)

## 2. Error helpers

- [x] 2.1 Add the `PasskeyErrorCategory` enum, `PasskeyError.category` and `isUserCancellation`; verify a commonTest pins the mapping for every subtype
- [x] 2.1a Switch `PasskiLog.levelOf` (from `add-passkey-logging`, which matches error types for now) to `category`; verify `PassKiLogTest` still passes unchanged
- [x] 2.2 Include `docsUrl` in `toString()` for errors that have one; verify string tests for `Misconfigured` with and without a check ID and for `Cancelled`

## 3. RP ID on results

- [x] 3.1 Add `rpId` (last constructor parameter) to `PasskeyRegistration` and `PasskeyAssertion`; verify `./gradlew checkLegacyAbi` fails, then run `updateLegacyAbi` and review the diff shows only additions
  - The diff also changes the `@InternalPasskiApi` constructors and `parseRegistration` / `parseAssertion`, which now take the RP ID; neither is public API.
- [x] 3.2 Fill `rpId` on Android and iOS from the parsed options; verify Android host tests and iOS tests assert it for register and sign-in
  - Android host tests can't reach Credential Manager and iOS tests can't complete an authorization, so neither can return a success to assert on. Both pass the options' RP ID to `parseRegistration` / `parseAssertion`, which `WebAuthnResponsesTest` covers on every target. Not yet checked on a device.
- [x] 3.3 Fill `rpId` on JS / Wasm from the options or the page host; verify `WebPasskeysTest` covers both cases
- [x] 3.4 Fill `rpId` in `passki-test` `FakePasskeys`; verify `FakePasskeysTest` asserts it

## 4. Docs

- [x] 4.1 Update README usage snippets to `fold` / `category` and mention `assertion.rpId` for signals; verify `python3 ci/doc-snippets.py` passes
- [x] 4.2 State in the `Passkeys.capabilities()` KDoc that it is cheap on Android and iOS and cached per page on the web, so it can be called before every sign-in; verify Dokka output shows the note
  - The web isn't cached per page: each call asks the browser (`getClientCapabilities`, no UI or network); only the immediate-mediation probe runs once per page. The KDoc says that.
- [x] 4.3 Add a CHANGELOG entry; verify it lists every new member
