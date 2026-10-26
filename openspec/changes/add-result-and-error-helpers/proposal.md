# Proposal

## Why

About half of Lexicon's 90-line `PassKiAuthenticator` is code every app will write: a `when` to convert
`PasskeyResult` into the app's own result type, re-parsing the RP ID out of the options JSON because the results don't
carry it, and an error-to-UI mapping. The re-parse also became wrong in alpha04, where web options may omit the RP ID.
See `docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, sections 4.2, 4.4 and 4.6.

## What Changes

- `PasskeyRegistration.rpId` and `PasskeyAssertion.rpId`: the RP ID the ceremony ran for, from the options or, on the
  web, the one the browser used.
- `PasskeyResult` helpers: `fold`, `mapError`, `recover`, `isSuccess`, `isFailure`, `errorOrNull`, `toResult()`.
- `PasskeyError.isUserCancellation` (true for `Cancelled` and `Interrupted`).
- `PasskeyError.category`: a closed enum (`UserCancelled`, `NoCredential`, `AlreadyExists`, `Unsupported`, `Setup`,
  `Unexpected`) that apps can switch on without an `else`; new error subtypes always join an existing category.
- Every `PasskeyError.toString()` includes `docsUrl` when there is one.
- All additions; nothing removed or renamed. Not breaking (result constructors are `@InternalPasskiApi`).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `passkey-errors`: result conversion helpers, user-cancellation check, error categories, docs link in `toString()`.
- `passkey-ceremonies`: registration and assertion results carry the RP ID.

## Impact

- `passki` commonMain: `PasskeyResult.kt`, `PasskeyError.kt`, `Passkeys.kt` (result classes).
- Platform implementations fill `rpId`: **Android** and **iOS** from the parsed options; **JS / Wasm** from the options
  or the page's effective domain; **JVM** unsupported implementation never returns results (no change).
- `passki-test` `FakePasskeys` fills `rpId`. ABI dumps (`passki.api`, `passki.klib.api`) gain the new members.
- README usage snippets switch to `fold` / `category`; CHANGELOG.
