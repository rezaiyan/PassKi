# Proposal

## Why

`passki-compose` already solves double taps and cancellation filtering (`rememberPasskeySignIn`), but only as Compose
state, and its `PasskeySignInButton` owns the whole flow and the Material look. Lexicon, an MVVM app with its own design
system, used neither: it re-implemented the guard in two ViewModels and drew its own button. See
`docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, sections 4.8 and 4.9.

## What Changes

- New `PasskeySignInController` in `passki` core: one sign-in at a time, a second call joins the running one,
  `isRunning` and `lastError` as `StateFlow`, cancellation never stored as an error. Usable from any ViewModel or
  presenter.
- `rememberPasskeySignIn` / `PasskeySignInState` keep their API and behaviour but delegate to the controller.
- New stateless `PasskeySignInButton(onClick, modifier, isLoading, enabled, content)` overload that any design system
  can style; the stateful button is built on it.
- New public `PasskiIcons.Passkey` `ImageVector` (the FIDO Alliance passkey symbol) for custom buttons.
- `kotlinx-coroutines-core` becomes an `api` dependency of `passki` (needed for `StateFlow` in the public API).
- Additive; not breaking.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `passkey-ceremonies`: single-flight sign-in controller in core.
- `compose-ui`: stateless sign-in button and passkey icon.

## Impact

- `passki` commonMain (new controller; all targets: **Android**, **iOS**, **JVM**, **JS**, **Wasm** — pure common
  code); Gradle dependency scope for coroutines.
- `passki-compose`: `PasskeySignInState` internals, new button overload, icon.
- ABI dumps for `passki` and `passki-compose`; README "Sign in with a passkey" button section gets a ViewModel example;
  CHANGELOG.
- Licence check for the FIDO passkey icon before shipping it.
