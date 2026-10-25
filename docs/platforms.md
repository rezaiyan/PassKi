# Platforms

What each OS supports, and how Android, iOS and browsers behave differently. The short version is in the
[README](../README.md#platforms).

| Platform | What works |
|---|---|
| **Android 9+** (Credential Manager) | register, sign in, passkey-or-password, upgrade, signals, autofill (Android 15+), PRF and largeBlob (if the credential provider supports them) |
| **iOS 16+** (AuthenticationServices) | register, sign in, passkey-or-password, autofill, upgrade (iOS 18+), signals (iOS 26+), PRF (iOS 18+), largeBlob (iOS 17+) |
| **Browser** (js / wasmJs, WebAuthn) | register, sign in, upgrade, signals, autofill, PRF and largeBlob, each where the browser supports it; immediate sign-in where the browser accepts `mediation: "immediate"`. No passkey-or-password |
| **JVM desktop** | Compiles. Every call returns `PasskeyError.NotSupported` for now. Native macOS, tvOS, watchOS, Linux and Windows targets are not published yet |

Call `passkeys.capabilities()` to find out what the current device supports, so your UI can adapt before it shows a
button.

## What each OS version supports

`capabilities()` returns a `PasskeyCapabilities` with one flag per feature:

| Flag | Feature | Android | iOS | Browser | Notes |
|---|---|---|---|---|---|
| `isSupported` | Passkeys at all | 9+ (API 28) | 16+ | Any browser with WebAuthn, on HTTPS or localhost | When false, every call fails with `NotSupported` |
| `immediateSignIn` | `SignInMode.ImmediateOnly` | 9+ | 16+ | Browsers that accept `mediation: "immediate"` | |
| `unifiedPasswordSignIn` | `signInOrPassword` | 9+ | 16+ | Never | |
| `conditionalCreate` | `upgrade` | 9+ | 18+ | Browsers whose `getClientCapabilities()` reports `conditionalCreate` | On Android and in browsers, the credential provider can still decline |
| `signals` | `signals` | 9+ | 26+ | Browsers with the Signal API (`PublicKeyCredential.signal*`) | On Android, the credential provider can still decline |
| `autofill` | `SignInMode.Autofill` | 15+ (API 35) | 16+ | Browsers with conditional mediation | Needs a focused username field; in a browser, an HTML `<input autocomplete="username webauthn">` |

Extensions (PRF and largeBlob) have no flag. As in browsers, a platform that can't handle an extension ignores it,
and its output is `null`.

| Extension | Android | iOS | Browser |
|---|---|---|---|
| PRF | Passed to the credential provider. Google Password Manager supports it. | 18+ | Passed to the browser |
| largeBlob | Passed to the credential provider | 17+ | Passed to the browser |

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

**In a browser, PassKi calls WebAuthn (`navigator.credentials`).**

- The page must be served over HTTPS, or from `localhost` while developing. Otherwise `isSupported` is false.
- The browser checks the RP ID against the page's origin, so the options' RP ID is optional there: it defaults to the
  page's domain. If you set it, it must be that domain or a registrable suffix of it (a page on
  `login.example.com` can use `example.com`). A mismatch fails with `PasskeyError.Misconfigured`. To use one RP ID
  across several domains, list them in `https://<rpId>/.well-known/webauthn` (related origins).
- Your server must accept the page's origin (such as `https://example.com`) next to your apps' origins.
- Browsers report a dismissed sheet, a timeout and "no passkey for this site" the same way, so a modal `signIn()` with
  no matching passkey fails with `PasskeyError.Cancelled`, not `NoCredentials`.
- `SignInMode.Autofill` offers passkeys in an HTML `<input autocomplete="username webauthn">`, so it needs a DOM
  input. Compose for Web draws on a canvas and has none: use `Modal` there. A modal `signIn()` call takes over from
  a pending autofill request, which starts again when the modal call ends.
- `upgrade()` gives up after the options' `timeout`, or 10 seconds without one, because a browser that won't create a
  passkey may never answer.
- `signInOrPassword()` returns `NotSupported`: browsers have no sheet that returns both kinds of credential to the
  page. The browser's password manager fills your password form.
- Registration responses include the WebAuthn Level 3 `publicKey`, `publicKeyAlgorithm` and `authenticatorData`
  fields when the browser provides them.
- `PasskeyDoctor` checks app setup (asset links, AASA), so in a browser it returns a skipped report.

---

[← README](../README.md)
