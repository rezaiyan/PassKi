# Module passki-compose

Compose Multiplatform helpers for PassKi: passkey sign-in in a few lines of UI code. Same targets as `passki`
(Android, iOS, JVM desktop, js, wasmJs), which it exposes as an `api` dependency.

- [com.alirezaiyan.passki.compose.rememberPasskeys] remembers the platform `Passkeys`.
- [com.alirezaiyan.passki.compose.rememberPasskeySignIn] returns a headless
  [com.alirezaiyan.passki.compose.PasskeySignInState] (`isRunning`, `lastError`, `signIn()`) for custom UI.
  Double taps join the running request; a dismissed sheet is never stored as `lastError`.
- [com.alirezaiyan.passki.compose.PasskeySignInButton] is a ready-made Material 3 button on top of it.
- [com.alirezaiyan.passki.compose.PasskeyUsernameField] is a username field that offers the user's passkeys as
  keyboard suggestions (`SignInMode.Autofill`) while it is focused: Android 15+ (suggestion chip in the keyboard)
  and iOS 16+ (the "Passwords" item in the QuickType bar opens the passkey list). On iOS the field hosts a UIKit
  `UITextField` with the username content type, because Compose's `ContentType.Username` does not make iOS offer
  passkeys. On other platforms it is a plain text field.

Every helper takes an optional `passkeys` parameter: pass passki-test's `FakePasskeys` in previews and tests.

# Package com.alirezaiyan.passki.compose

Composables and state holders for passkey sign-in and passkey autofill.
