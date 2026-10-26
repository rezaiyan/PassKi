# Platforms

What each OS supports, and how Android and iOS behave differently. The short version is in the
[README](../README.md#platforms).

| Platform | What works |
|---|---|
| **Android 9+** (Credential Manager) | register, sign in, passkey-or-password, upgrade, signals, autofill (Android 15+), PRF and largeBlob (if the credential provider supports them) |
| **iOS 16+** (AuthenticationServices) | register, sign in, passkey-or-password, autofill, upgrade (iOS 18+), signals (iOS 26+), PRF (iOS 18+), largeBlob (iOS 17+) |
| **Browser** (js / wasmJs, `navigator.credentials`) | register, sign in, autofill, upgrade, signals, PRF and largeBlob, each where the browser has it. The page must be HTTPS or `http://localhost`. No passkey-or-password sheet |
| **JVM desktop** | Compiles. Every call returns `PasskeyError.NotSupported` for now. Native macOS, tvOS, watchOS, Linux and Windows targets are not published yet |

Call `passkeys.capabilities()` to find out what the current device supports, so your UI can adapt before it shows a
button.

## What each OS version supports

`capabilities()` returns a `PasskeyCapabilities` with one flag per feature:

| Flag | Feature | Android | iOS | Browser | Notes |
|---|---|---|---|---|---|
| `isSupported` | Passkeys at all | 9+ (API 28) | 16+ | WebAuthn on an HTTPS or `http://localhost` page | When false, every call fails with `NotSupported` |
| `immediateSignIn` | `SignInMode.ImmediateOnly` | 9+ | 16+ | Browsers with `mediation: "immediate"` | |
| `unifiedPasswordSignIn` | `signInOrPassword` | 9+ | 16+ | No | |
| `conditionalCreate` | `upgrade` | 9+ | 18+ | When `getClientCapabilities()` reports it | On Android, the credential provider can still decline |
| `signals` | `signals` | 9+ | 26+ | When `PublicKeyCredential` has the three signal methods | On Android, the credential provider can still decline |
| `autofill` | `SignInMode.Autofill` | 15+ (API 35) | 16+ | Conditional mediation | Needs a focused username field. In a browser, an HTML `<input autocomplete="username webauthn">`; `PasskeyUsernameField` can't offer it there |

Extensions (PRF and largeBlob) have no flag. As in browsers, a platform that can't handle an extension ignores it,
and its output is `null`.

| Extension | Android | iOS | Browser |
|---|---|---|---|
| PRF | Passed to the credential provider. Google Password Manager supports it. | Passed to the browser |
| largeBlob | Passed to the credential provider | 17+ | Passed to the browser |

## Threads, cancellation and one sheet at a time

These rules are the same on Android, iOS and the web:

- **Call from any dispatcher.** Every `Passkeys` and `signals` call is main-safe. PassKi moves its UI work to the main
  thread and never blocks the calling thread, and the result comes back in your coroutine.
- **Cancelling withdraws the request.** When the calling coroutine is cancelled (the screen closes, the ViewModel is
  cleared), the call ends with `CancellationException` and no result arrives later. On iOS and the web the sheet
  closes too. On Android PassKi cancels the Credential Manager request, but some credential providers keep their sheet
  open; a passkey picked there is discarded. A user closing the sheet is different: that returns
  `PasskeyError.Cancelled`.
- **One sheet at a time.** While `register`, `signIn` (except autofill), `signInOrPassword` or `upgrade` is pending,
  another of these calls fails at once with `PasskeyError.Interrupted`, and the pending call carries on. This is what a
  double tap on a button produces. Don't show `Interrupted` as an error; ignore it or retry later. Autofill sign-in
  waits until the pending call finishes, then starts.
- **Signals show no sheet,** so they don't take part in the one-sheet rule.

## Good to know

**`upgrade()` may silently do nothing.**
`upgrade()` adds a passkey without showing anything, right after a password sign-in. The platform decides whether it
happens. When it declines, you get `PasskeyError.NotSupported` on both platforms:

- Android: the credential provider declined.
- iOS: the system declined, usually because the person didn't just sign in with a password from AutoFill.

Treat every `upgrade()` failure as silent: don't show an error, and don't block your sign-in flow.

**Your server must accept upgrade responses without user presence.**
Because no prompt is shown, the response's user-presence (UP) flag can be 0. Don't require user presence when you
check these responses.

**iOS before 17.4 ignores `excludeCredentials`.**
So the same account can end up with two passkeys. Remove duplicates on your server. On Android and on iOS 17.4 and
newer, a duplicate always fails with `PasskeyError.AlreadyRegistered`.

**On Android, a duplicate registration still shows a sheet first.**
When `excludeCredentials` matches, Google Password Manager still shows its "create passkey" sheet. You get
`PasskeyError.AlreadyRegistered` only after the person taps Continue.

**On Android, `upgrade()` and `signals` depend on the credential provider.**
They can return `PasskeyError.NotSupported` even when `capabilities()` says they are available.

**iOS registration responses have no `publicKey` field.**
iOS leaves out the WebAuthn Level 3 `publicKey` and `publicKeyAlgorithm` fields. Read the public key from
`attestationObject` instead; every server library already does this.

**Signals on iOS need iOS 26, and no setup.**

- iOS provides signals through `ASCredentialUpdater`, a Swift-only API. PassKi includes a small Swift bridge in its
  library, so your app needs no Swift file and no linker flags. It works whether PassKi is linked as a static or a
  dynamic framework.
- Before iOS 26, signals return `PasskeyError.NotSupported`. The app still runs there, because the iOS 26
  functions are linked weakly.
- iOS stores only one name, so `currentUserDetails` uses `name` and ignores `displayName`.
- iOS refuses signals for a domain the app isn't linked to. PassKi reports that as `PasskeyError.Misconfigured`, with a
  hint about the `apple-app-site-association` file.

---

[← README](../README.md)
