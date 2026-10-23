# Autofill sign-in

`SignInMode.Autofill` (WebAuthn "conditional mediation") offers passkeys in the keyboard or QuickType bar of the
focused username field. It needs Android 15 (API 35)+ or iOS 16+ (`capabilities().autofill`).
`PasskeyUsernameField` does all of this for you. To use it with your own field:

```kotlin
// Your username field must be focused (content type username) when you call this.
val job = scope.launch {
    passkeys.signIn(api.authenticationOptions(), SignInMode.Autofill)
        .onSuccess { api.finishSignIn(it.json) }
}
// When the field loses focus or leaves the screen:
job.cancel() // withdraws the offer
```

```mermaid
flowchart LR
    F["Field focused<br/>signIn(..., Autofill)"] --> O(["Passkey offered<br/>in the keyboard"])
    O -- "user picks it" --> S["Signed in"]
    O -- "user dismisses it" --> D["Dismissed"]
    D -- "Android: call again on tap<br/>iOS: re-offered after ~500 ms" --> O
    O -- "focus lost: job.cancel()" --> X["Offer withdrawn"]
```

- **The call suspends until the user picks a passkey.** Dismissing the suggestion or the sheet does not complete it.
  Cancel the coroutine to withdraw the offer.
- **Re-arming after a dismissal:** on Android the chip does not come back while the field stays focused, so cancel and
  call again, for example when the field is tapped (`PasskeyUsernameField` re-arms on every tap). On iOS, PassKi
  offers the passkeys again by itself, about 500 ms after a dismissal.
- **Stale chips (Android):** a chip that has been visible for more than about a minute can fail in Google Password
  Manager with "Flow has timed out". The next call offers a fresh one.
- **Errors:** below Android 15, `NotSupported`. On Android with no focused view, `Misconfigured` ("Autofill needs a
  focused text field"). On iOS, a missing association gives `Misconfigured`, as with the other calls.

---

[← README](../README.md)
