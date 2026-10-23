# Verified on

Both runs used the sample app against the demo relying party, and the server verified every response. A
registration and assertion pair from each run is a fixture in `passki-test`, verified against its public key.

## Android 16 emulator (API 36, Google Password Manager)

- register
- sign in (modal, `PasskeySignInButton`), device-only sign-in and passkey-or-password
- autofill: the passkey chip in Gboard, re-armed by a tap after a dismissal
- PRF: 32 bytes, the same output for the same salt on two runs
- signals: after `unknownCredential`, device-only sign-in returns `NoCredentials`; `currentUserDetails` renamed the
  passkey in the GPM chip
- upgrade: declined by the provider and reported as `NotSupported`, as expected
- Setup check: doctor 0 failures, SRV-01/02 pass

## iOS 26.3 simulator (Face ID)

- register (the response was shared as an `ios` fixture on the demo RP)
- sign in (modal, `PasskeySignInButton`), device-only sign-in and passkey-or-password
- autofill through `PasskeyUsernameField`: "Passwords" in the QuickType bar, then Face ID, then the passkey list,
  then signed in
- PRF: 32 bytes, the same output for the same salt on two runs
- cancel: closing the sheet returns `Cancelled`
- signals: after `unknownCredential`, device-only sign-in returns `NoCredentials` (the passkey is gone);
  `currentUserDetails` renamed the passkey in the system sheet
- Setup check: doctor 0 failures (IOS-03 AASA pass, IOS-04 skipped at runtime), SRV-01/02 pass
- build, plus `passkiCheck` IOS-04/05/06; `-passkiAutorun` declines upgrade as expected

**Not verified on iOS:** `AlreadyRegistered`. The demo relying party creates a new user for every registration and
sends no `excludeCredentials`, so the sample cannot trigger it; it is covered by unit tests only (error 1006). On an
iOS 17.5 simulator, signals return `NotSupported` as designed.

Earlier real registration responses from both platforms were verified with webauthn4j; the fixtures live in
`passki-test`.

---

[← README](../README.md)
