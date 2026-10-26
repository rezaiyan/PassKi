# Upgrading users to passkeys

`upgrade(options)` asks the user's password manager to create a passkey **without showing anything**, right after a
sign-in. WebAuthn calls this conditional create; Apple calls it automatic passkey upgrades. If the password manager
agrees, you get a `PasskeyRegistration` to send to your server like any other. If not, you get `NotSupported` and the
user sees nothing.

```kotlin
suspend fun afterPasswordSignIn(passkeys: Passkeys, api: MyApi) {
    if (!passkeys.capabilities().conditionalCreate) return
    passkeys.upgrade(api.registrationOptions())
        .onSuccess { api.finishRegistration(it.json) } // the device shows its own "passkey created" note
    // a failure is normal: never show an error for upgrade()
}
```

Call it immediately after the sign-in succeeds, with the same creation options you'd use for `register`
(`user.name` must be the account the password was saved for).

## When it creates a passkey

All three platforms decide the same way: **the user just signed in with a password that their password manager
filled in, for the same account.** The password manager then creates the passkey and tells the user afterwards.

| Platform | Needs | Source (checked 2026-10-26) |
|---|---|---|
| iOS 18+ | A password the Passwords app (iCloud Keychain) or a third-party credential manager just filled for the same account; the device has a passcode | Apple, [WWDC24 "Streamline sign-in with passkey upgrades and credential managers"](https://developer.apple.com/videos/play/wwdc2024/10125/) |
| Android | A credential provider that supports conditional create (Google Password Manager does through Play services), and a password it saved and filled for the account | androidx.credentials `CreatePublicKeyCredentialRequest(isConditional = true)`; Chrome's [Android announcement](https://developer.chrome.com/blog/automatic-passkey-creation-android) describes the same Google Password Manager rule |
| Web, Chrome 136+ desktop and Chrome 142+ Android | A password saved for the site in Google Password Manager and used recently to sign in | Chrome, [desktop](https://developer.chrome.com/blog/passkey-automatic-upgrades) and [Android](https://developer.chrome.com/blog/automatic-passkey-creation-android) announcements |

Users can turn automatic passkey creation off in Google Password Manager's settings, and every platform may add
conditions of its own, so treat `upgrade()` as a bonus, not a step users go through.

## After Google, Apple or a magic link

**`upgrade()` doesn't help after a sign-in without a saved password** (Sign in with Google, Sign in with Apple, a magic
link or a one-time code): the password manager filled nothing, so its main condition isn't met, and the call ends with
`NotSupported`. That follows from the rule all three sources state; none of them documents an exception for federated
sign-in, and we haven't seen one on a device.

For those users, offer a passkey in Settings or after sign-in, with a normal `register` call that shows the sheet:
[Passkeys as an extra sign-in](extra-sign-in.md).

## What PassKi reports

- **Success:** a `PasskeyRegistration`, which may come without user presence or user verification (the response's
  flags say which), since no sheet was shown. Your server must accept such registrations for upgrades: in webauthn4j, pass `false`
  for both in `RegistrationParameters` (PassKi's own tests check this against `FakePasskeys.upgrade`). With other
  libraries, check their option for user presence.
- **`NotSupported`:** the platform declined, which is the usual outcome. On Android, Play services answers with a
  status such as 17; on iOS the system declines silently.
- **`AlreadyRegistered`:** the account already has a passkey in this password manager (`excludeCredentials`).

`capabilities().conditionalCreate` says whether the platform can do it at all: iOS 18+, Android 9+ (the provider still
decides) and browsers that report it in `getClientCapabilities()`.
