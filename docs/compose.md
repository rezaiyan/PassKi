# Compose

`passki-compose` turns passkey sign-in into a few lines of Compose Multiplatform code. It has the same targets as
`passki` and exposes it as an `api` dependency.

```kotlin
commonMain.dependencies { implementation("com.alirezaiyan.passki:passki-compose:0.1.0-alpha05") }
```

<table>
<tr>
<td>

```kotlin
@Composable
fun SignInScreen(api: MyApi) {
    val scope = rememberCoroutineScope()
    val passkeys = rememberPasskeys()
    var username by remember { mutableStateOf("") }

    // Offers passkeys as keyboard suggestions
    // while the field is focused.
    PasskeyUsernameField(
        value = username,
        onValueChange = { username = it },
        requestOptions = { api.authenticationOptions() },
        onSignedIn = { assertion ->
            scope.launch { api.finishSignIn(assertion.json) }
        },
        passkeys = passkeys,
    )

    // Material 3 button: shows progress while running,
    // double taps are safe.
    val signIn = rememberPasskeySignIn(
        requestOptions = { api.authenticationOptions() },
        passkeys = passkeys,
    )
    PasskeySignInButton(
        state = signIn,
        onSignedIn = { assertion ->
            scope.launch { api.finishSignIn(assertion.json) }
        },
        onError = { error -> showError(error.message) }, // never Cancelled
    )
}
```

</td>
<td width="260">
<img src="media/android-autofill.png" width="250" alt="PasskeyUsernameField on Android: saved passkeys appear as suggestion chips above the keyboard, under a Sign in with a passkey button">
<p align="center"><sub><code>PasskeyUsernameField</code> and <code>PasskeySignInButton</code> in the sample app on Android 16: saved passkeys show up as suggestions above the keyboard.</sub></p>
</td>
</tr>
</table>

Autofill with your own text field: [Autofill](autofill.md).

## Your own sign-in UI

`PasskeySignInButton` is built on a state object that you can use with your own UI. Create it with
`rememberPasskeySignIn(requestOptions, passkeys, mode)`:

- `isRunning` and `lastError` are Compose state, so your UI updates by itself.
- `signIn()` starts a sign-in and returns a `PasskeyResult<PasskeyAssertion>`.

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

`ImmediateOnly` here keeps a device with no passkey from showing the "another device" sheet or a QR code. Use it when
passkeys are optional in your app, and turn `PasskeyError.NoCredentials` into an in-app message. Why, and when to keep
`Modal`: [A "Sign in with a passkey" button](usage.md#a-sign-in-with-a-passkey-button).

- If `signIn()` is called while a sign-in is already running, it waits for that one instead of opening a second
  sheet. Double taps are safe.
- If `requestOptions` throws, you get `PasskeyError.Unknown`, with the exception as its `cause`.
- Every helper takes an optional `passkeys` parameter. Pass `FakePasskeys` in previews and tests.
- Parameters follow the Compose order: required ones first, then `modifier` (for UI), then optional ones, starting
  with `passkeys`.

## PasskeyUsernameField in detail

```
PasskeyUsernameField(value, onValueChange, requestOptions, onSignedIn, modifier, passkeys, label, onError, enabled)
```

While the field is focused, it offers the person's passkeys as suggestions in the keyboard (a `SignInMode.Autofill`
request). It stops offering them when it loses focus or leaves the screen, and it only offers them when
`capabilities().autofill` is true. Typing works as usual, so the same field also works for your password or
"continue with username" flow.

- **Android 15+:** a Material 3 `OutlinedTextField`, marked as a username field. The passkey shows up as a suggestion
  above the keyboard; tapping it opens the credential provider's sheet. It also works inside a `Dialog` or a
  `ModalBottomSheet`.
- **iOS 16+:** a UIKit `UITextField` with `textContentType = .username`, drawn with a Material-style border and label.
  A UIKit field is needed: a Compose text field with `ContentType.Username` doesn't make iOS offer passkeys (checked on
  iOS 26.3). iOS shows a **Passwords** item above the keyboard, not the account name. Tapping it, then Face ID, opens
  a list that includes your passkeys. In the simulator, turn off the hardware keyboard (I/O > Keyboard, or ⌘K) to see
  that bar.
- **JVM desktop and browser:** a plain text field. It makes no passkey request: Compose for Web draws on a canvas, so
  the browser has no `<input>` to show suggestions under. Use the sign-in button there.

`onError` reports two kinds of problems:

- Problems autofill can't recover from: `NotSupported`, `Misconfigured` or `InvalidOptions`. Each is reported once,
  and then the field stops offering passkeys.
- Unexpected errors, such as `requestOptions` throwing. The field tries again the next time it gets focus or is
  tapped.

Dismissing the suggestion is not an error and is never reported.

---

[← Docs](README.md)
