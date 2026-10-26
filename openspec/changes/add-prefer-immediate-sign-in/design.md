# Design

## Context

- `SignInMode` has `Modal`, `ImmediateOnly`, `Autofill` and is documented as open ("keep an `else` branch").
- Android (`AndroidPasskeys`) and iOS (`IosPasskeys`) report `immediateSignIn = supported`; the web
  (`WebPasskeys`, since `4571df6`) probes `mediation: "immediate"` once per page and maps a leftover `TypeError` to
  `NotSupported`, remembering it.
- `FakePasskeys` already has an `immediateSignIn` flag; `ImmediateOnly` fails with `NotSupported` when it's false.

## Goals / Non-Goals

**Goals:**
- One mode that encodes the README's optional-button policy, including the web fallback.

**Non-Goals:**
- Hiding the button automatically on browsers without immediate mediation (an app decision; documented).
- Changing `ImmediateOnly` or `Modal`.

## Decisions

1. **Resolve the mode in shared code.** A commonMain helper runs `capabilities()`, then calls the platform
   implementation with `ImmediateOnly` or `Modal`, and retries with `Modal` on `NotSupported`. Platforms keep handling
   only concrete modes, and `FakePasskeys` reuses the same helper so tests exercise the real policy. Alternative: each
   platform handles `PreferImmediate`, rejected as three copies of one rule.
2. **No fallback on `NoCredentials`.** That fallback is exactly what shows a QR code to first-time users; the README
   already tells apps to show a "no passkey on this device yet" notice instead.
3. **Name:** `PreferImmediate`, matching the existing `ImmediateOnly` and WebAuthn's `immediate` mediation term.
   `DeviceFirst` was considered but introduces a new word.
4. **Autofill unaffected.** `PreferImmediate` never turns into `Autofill`.

## Risks / Trade-offs

- [A second sheet after a rejected immediate request] → The rejection happens before any UI on the web (probe), so the
  user sees one sheet; covered by `WebPasskeysTest`.
- [Apps already using an exhaustive `when` on `SignInMode`] → It's documented as open; CHANGELOG mentions the new
  entry.

## Migration Plan

Apps replace the capability check and retry with `signIn(options, SignInMode.PreferImmediate)`.
