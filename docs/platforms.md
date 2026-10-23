# Platforms and platform behaviour

What each OS supports, and how the platforms differ. The short version is in the [README](../README.md#platforms).

| Platform | What works |
|---|---|
| **Android 9+** (Credential Manager) | register, sign in, passkey-or-password, upgrade, signals, autofill (Android 15+), PRF / largeBlob (depends on the credential provider) |
| **iOS 16+** (AuthenticationServices) | register, sign in, passkey-or-password, autofill, upgrade (iOS 18+), signals (iOS 26+), PRF (iOS 18+), largeBlob (iOS 17+) |
| **JVM desktop, browser** (js / wasmJs) | Compiles, and every call returns `PasskeyError.NotSupported` for now. Native macOS/tvOS/watchOS/Linux/Windows targets are not published yet |

`passkeys.capabilities()` reports what the current device supports, so your UI can adapt before it shows a button.

## Capability flags and extensions per OS version

| `PasskeyCapabilities` | Android | iOS | Notes |
|---|---|---|---|
| `isSupported` | 9+ (API 28) | 16+ | When false, every call fails with `NotSupported` |
| `immediateSignIn` (`SignInMode.ImmediateOnly`) | 9+ | 16+ | |
| `unifiedPasswordSignIn` (`signInOrPassword`) | 9+ | 16+ | |
| `conditionalCreate` (`upgrade`) | 9+ | 18+ | Android: the credential provider may still decline |
| `signals` | 9+ | 26+ | Android: the credential provider may still decline |
| `autofill` (`SignInMode.Autofill`) | 15+ (API 35) | 16+ | Needs a focused username field |

Extensions have no flag. As in browsers, an extension the platform can't handle is ignored and its output is `null`.

| Extension | Android | iOS |
|---|---|---|
| PRF | passed to the credential provider (Google Password Manager supports it) | 18+ |
| largeBlob | passed to the credential provider | 17+ |

## Good to know

- **`upgrade()` is opportunistic.** It uses conditional create, so the platform may decline without any UI. A decline
  is reported as `PasskeyError.NotSupported` on both platforms (Android: the credential provider rejected conditional
  create; iOS: the system declined, typically because there was no recent password AutoFill sign-in for the
  account). Treat every `upgrade()` failure as silent: do not show an error and do not block your login flow.
- **Verifying upgrade responses.** A conditional-create response is created without a user prompt, so the
  user-presence (UP) flag may be 0. Servers verifying these responses must not require user presence.
- **`excludeCredentials` on iOS < 17.4 is ignored** by the system, so a duplicate passkey may be created; dedupe on
  your server. `PasskeyError.AlreadyRegistered` is guaranteed on Android and iOS 17.4+.
- **Duplicate registration on Android shows a sheet first.** When `excludeCredentials` matches, Google Password
  Manager still shows its create sheet; `PasskeyError.AlreadyRegistered` is returned only after the user taps
  Continue.
- **Android `upgrade()` and `signals` depend on the user's credential provider** and may return
  `PasskeyError.NotSupported` even when `capabilities()` reports them as available.
- **iOS registration responses** omit the WebAuthn Level 3 `publicKey` and `publicKeyAlgorithm` fields. Read the
  public key from `attestationObject`, which every server library already does.
- **Signals on iOS** work on iOS 26+ through `ASCredentialUpdater`. That API is Swift-only, so PassKi ships a small
  Swift bridge compiled into its klib. Your app needs **no setup**: no Swift file, no linker flags, and PassKi links
  as a static or a dynamic framework. Older iOS versions return `PasskeyError.NotSupported`; the iOS 26 symbols are
  weak-linked, so the app still runs there. iOS takes only one name, so `currentUserDetails` reports `name` and
  ignores `displayName`. The updater refuses signals for a domain the app is not associated with; PassKi reports
  that as `PasskeyError.Misconfigured` with an AASA hint.

---

[← README](../README.md)
