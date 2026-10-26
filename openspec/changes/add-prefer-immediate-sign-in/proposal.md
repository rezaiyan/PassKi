# Proposal

## Why

The README tells apps with an optional passkey button to pick `ImmediateOnly` when `capabilities().immediateSignIn` is
true and `Modal` otherwise, and alpha04 showed that this copied policy breaks when a platform reports a capability it
doesn't really have (Chromium; fixed on `main` by `4571df6`). Every app still carries the decision, and Lexicon still
carries a retry. The policy belongs in PassKi, in one place. See
`docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, section 4.1.

## What Changes

- New `SignInMode.PreferImmediate`: use device-only sign-in where the platform supports it, otherwise the standard
  sheet; PassKi does the capability check and falls back to the sheet if the immediate request is rejected as
  unsupported. `NoCredentials` is returned as is (no fallback), so first-time users never see a QR code on platforms
  with immediate sign-in.
- README: the optional-button section becomes one line with `PreferImmediate`, and states what happens on browsers
  without immediate mediation, so an app can hide the button there instead.
- `docs/platforms.md` lists where immediate sign-in is available.
- `FakePasskeys` implements the new mode using its existing `immediateSignIn` flag.
- Additive (`SignInMode` is documented as open). Not breaking.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `passkey-ceremonies`: new sign-in mode with a defined fallback.
- `test-fakes`: the fake models the new mode.

## Impact

- `passki` commonMain `SignInMode`; the mode resolution can live in shared code used by **Android**, **iOS** and
  **JS / Wasm**. On Android and iOS `immediateSignIn` is true wherever passkeys work, so the mode behaves like
  `ImmediateOnly`; the fallback matters on the **web**. **JVM** unsupported implementation: unchanged
  (`NotSupported`).
- `passki-compose`: `rememberPasskeySignIn(mode = …)` accepts it with no change.
- README, `docs/platforms.md`, CHANGELOG; ABI dumps gain one enum entry.
- Depends on `4571df6` (alpha05) for reliable web capability reporting.
