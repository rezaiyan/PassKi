# Compose

`passki-compose` in detail. The basic example is in the [README](../README.md#compose).

## Headless state

For custom UI, use the headless state from `rememberPasskeySignIn(requestOptions, passkeys, mode)`. `isRunning` and
`lastError` are Compose state, and `signIn()` returns a `PasskeyResult<PasskeyAssertion>`:

```kotlin
val state = rememberPasskeySignIn(
    requestOptions = { api.authenticationOptions() },
    mode = SignInMode.ImmediateOnly,
)
Button(enabled = !state.isRunning, onClick = {
    scope.launch { state.signIn().onSuccess { api.finishSignIn(it.json) } }
}) { Text("Sign in") }
state.lastError?.let { Text(it.message) } // Cancelled is never stored
```

- A `signIn()` call made while another one is running joins it instead of opening a second sheet.
- If `requestOptions` throws, the exception comes back as `PasskeyError.Unknown` with the exception as its `cause`.
- Every helper takes an optional `passkeys` parameter, so you can pass `FakePasskeys` in previews and tests.
- Parameters follow the Compose convention: required ones first, then `modifier` (for UI), then optional ones with
  defaults, `passkeys` first.

## PasskeyUsernameField in detail

`PasskeyUsernameField(value, onValueChange, requestOptions, onSignedIn, modifier, passkeys, label, onError, enabled)`
keeps a `SignInMode.Autofill` request open while it is focused and `capabilities().autofill` is true. It withdraws the
offer when it loses focus or leaves composition. Typing works as usual, so the same field also serves your password
or "continue with username" path.

- **Android 15+:** a Material 3 `OutlinedTextField` marked as a username field. The passkey shows up as a suggestion
  chip in the keyboard; tapping it opens the credential provider's sheet. It also works inside a `Dialog` or
  `ModalBottomSheet`.
- **iOS 16+:** the field hosts a UIKit `UITextField` with `textContentType = .username`, drawn with a Material-like
  border and label. A Compose text field with `ContentType.Username` does not make iOS offer passkeys (checked on iOS
  26.3), so the UIKit field is required. iOS shows a **"Passwords"** item in the QuickType bar rather than the account
  name. Tapping it (after Face ID) opens a list that includes your passkeys. In the simulator, turn off the hardware
  keyboard (I/O > Keyboard, or ⌘K) to see the QuickType bar.
- **JVM desktop and browser:** a plain text field. No request is made.

`onError` only reports problems autofill can't recover from: `NotSupported`, `Misconfigured` or `InvalidOptions`.
These are reported once, and then the field stops offering passkeys. It also reports unexpected errors such as
`requestOptions` throwing; the field then tries again on the next focus or tap. Dismissing the suggestion is never
reported.

---

[← README](../README.md)
