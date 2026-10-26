# Verified on

What was tested on real platforms, and what wasn't. Both runs used the sample app against the demo server, which
checked every response. One registration and one sign-in response from each run are saved as test data
(fixtures) in `passki-test`, and the tests check their signatures against the saved public key.

## Android 16 emulator (API 36, Google Password Manager), 2026-10-23

Run on `main` after the Kotlin 2.2 build and the `passki-demo` client, driven step by step on the emulator. Every
result came from the demo server.

| Flow | Result |
|---|---|
| Launch | Setup check passed, no banner |
| Create an account (sheet, screen lock PIN) | `Register ✓`, user verified, `prfEnabled=true` |
| PRF, twice | 32 bytes, the same first 8 bytes both times |
| Sign out, then sign in with the sheet | `Sign in ✓` |
| Autofill: tap the passkey suggestion above the keyboard | `Sign in (autofill) ✓` |
| Close the sheet without choosing | No error, nothing logged, the button works again |
| Device only, then passkey or password | `Sign in (device only) ✓`, `Sign in or password (passkey) ✓` |
| Rename the user, then forget the passkey | `Signal user details ✓`, `Signal unknown ✓`; the forgotten passkey also left the keyboard suggestions |
| Device only, after forgetting | `✗ [NoCredentials]`, as expected |
| `upgrade()` | `✗ [NotSupported]` (status 17), as expected: Google Password Manager declined |
| Autofill inside a `Dialog` | `Sign in (autofill in dialog) ✓` |

This run found one sample problem, now fixed: after sign-out the username stayed in the field, and Gboard then showed
it as a word suggestion instead of the passkeys. Sign-out now clears the field.

## Android 16 emulator (API 36, Google Password Manager), earlier run

- Create a passkey.
- Sign in: the normal sheet (`PasskeySignInButton`), device-only sign-in, and passkey-or-password.
- Autofill: the passkey suggestion appears above Gboard. After a dismissal, tapping the field brings it back.
- PRF: 32 bytes, the same output for the same salt in two runs.
- Signals: after `unknownCredential`, a device-only sign-in returns `NoCredentials`. `currentUserDetails` renamed the
  passkey in Google Password Manager's suggestion.
- `upgrade()`: declined by the credential provider and reported as `NotSupported`, as expected.
- Setup check: `PasskeyDoctor` has 0 failures; SRV-01 and SRV-02 pass.

## Known failure: Pixel 9 Pro Fold emulator, 2026-10-24/25

On this emulator every passkey creation failed with Play services error 50152 "RP ID cannot be validated"
(`Misconfigured`, AND-03), for the sample app on the demo domain and for another app on its own domain. Both work on a
physical phone, and `PasskeyDoctor` passed AND-01 and AND-03 on the emulator. The cause isn't known.

| | |
|---|---|
| Device profile | `pixel_9_pro_fold` |
| System image | `system-images/android-36/google_apis_playstore/arm64-v8a`, revision 7 (build `BE2A.250530.026.D1`) |
| Play services | 25.08.34 during the failing runs; it updated itself to 26.41.31 when the emulator booted on 2026-10-26 |
| Device state | Google account signed in, screen lock set |

Re-running it needs a Google account signed in and a screen lock again (on 2026-10-26 neither was set, so the
emulator only offered "Create passkey on another device"). If it still fails with Play services 26.41.31, try a
physical device or another system image. Since then, AND-03 errors name the app's signing fingerprint and add an
emulator note, so this case can be told apart from a missing fingerprint.

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
