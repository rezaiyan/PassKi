# Verified on

What was tested on real platforms, and what wasn't. Both runs used the sample app against the demo server, which
checked every response. One registration and one sign-in response from each run are saved as test data
(fixtures) in `passki-test`, and the tests check their signatures against the saved public key.

## Android 16 emulator (API 36, Google Password Manager)

- Create a passkey.
- Sign in: the normal sheet (`PasskeySignInButton`), device-only sign-in, and passkey-or-password.
- Autofill: the passkey suggestion appears above Gboard. After a dismissal, tapping the field brings it back.
- PRF: 32 bytes, the same output for the same salt in two runs.
- Signals: after `unknownCredential`, a device-only sign-in returns `NoCredentials`. `currentUserDetails` renamed the
  passkey in Google Password Manager's suggestion.
- `upgrade()`: declined by the credential provider and reported as `NotSupported`, as expected.
- Setup check: `PasskeyDoctor` has 0 failures; SRV-01 and SRV-02 pass.

## iOS 26.3 simulator (Face ID)

- Create a passkey. The response was saved as an `ios` fixture on the demo server.
- Sign in: the normal sheet (`PasskeySignInButton`), device-only sign-in, and passkey-or-password.
- Autofill through `PasskeyUsernameField`: **Passwords** above the keyboard, then Face ID, then the passkey list, then
  signed in.
- PRF: 32 bytes, the same output for the same salt in two runs.
- Cancel: closing the sheet returns `Cancelled`.
- Signals: after `unknownCredential`, a device-only sign-in returns `NoCredentials` (the passkey is gone).
  `currentUserDetails` renamed the passkey in the system sheet.
- Setup check: `PasskeyDoctor` has 0 failures (IOS-03 passes; IOS-04 is skipped because the app can't read its own
  entitlements). SRV-01 and SRV-02 pass.
- The app builds, `passkiCheck` passes IOS-04, IOS-05 and IOS-06, and the autorun flag reports `upgrade()` as
  declined, as expected.

**Not tested on iOS:** `AlreadyRegistered`. The demo server creates a new user for every registration and sends no
`excludeCredentials`, so the sample can't trigger it. Unit tests cover it (error 1006). On an iOS 17.5 simulator,
signals return `NotSupported`, as designed.

Earlier registration responses from both platforms were also checked with webauthn4j; those fixtures are in
`passki-test` too.

---

[← README](../README.md)
