# What to show users

Every failed PassKi call returns a `PasskeyError`. Most of them are not the user's problem and need no message at
all. This table says what to show, what to log and whether to offer a retry, for each one.

| Error | Category | What happened | Show | Log | Retry |
|---|---|---|---|---|---|
| `Cancelled` | `UserCancelled` | The user closed the sheet | Nothing | No | They can tap again |
| `Interrupted` | `UserCancelled` | The system interrupted the request (app went to the background), or another PassKi sheet was still open (double tap) | Nothing | No (PassKi logs it at `Warn`) | Yes, silently or on the next tap |
| `NoCredentials` | `NoCredential` | No passkey for this account on this device | A neutral notice and the other sign-in options: "No passkey on this device yet. Sign in another way." | No | No; offer another sign-in method |
| `AlreadyRegistered` | `AlreadyExists` | This device already has a passkey for this account (`excludeCredentials` matched) | "This device already has a passkey for your account." | No | No |
| `NotSupported` | `Unsupported` | The device, OS, browser or credential provider can't do this | Hide the passkey action; after `upgrade()` or a signal, show nothing | Optional | No |
| `InvalidOptions` | `Setup` | Your server's options JSON is malformed, or its RP ID is missing or not a bare domain | A generic failure: "Passkeys aren't working right now." | Yes: message, `checkId`, `fixHint` (PassKi logs it by default) | No; it needs a fix in your server |
| `Misconfigured` | `Setup` | Your app, domain or well-known files are set up wrong (AND-03, IOS-02, ...) | "Passkeys aren't working right now." Never "your device can't", because it's your setup | Yes: message, `checkId`, `fixHint`, `docsUrl` (PassKi logs it by default) | No; it needs a fix in your setup |
| `Unknown` | `Unexpected` | Anything PassKi couldn't classify | A generic failure with a retry | Yes, with `platformCode` and `cause` (PassKi logs it by default) | Yes, once |

Two shortcuts cover most screens:

- `error.isUserCancellation` is true for `Cancelled` and `Interrupted`: show nothing.
- `error.category` has six values and the set never grows, so a `when` over it needs no `else` and keeps compiling
  when a release adds an error type:

```kotlin
fun messageFor(error: PasskeyError): String? = when (error.category) {
    PasskeyErrorCategory.UserCancelled -> null
    PasskeyErrorCategory.NoCredential -> "No passkey on this device yet. Sign in another way."
    PasskeyErrorCategory.AlreadyExists -> "This device already has a passkey for your account."
    PasskeyErrorCategory.Unsupported -> null // hide the passkey button instead
    PasskeyErrorCategory.Setup -> "Passkeys aren't working right now."
    PasskeyErrorCategory.Unexpected -> "Something went wrong. Try again."
}
```

## By call

The same error can mean different things depending on the call:

| Call | What's normal |
|---|---|
| `register` | `Cancelled`; `AlreadyRegistered` when the user adds a second passkey on the same device |
| `signIn` with `ImmediateOnly` | `NoCredentials` quickly, without a sheet, on a device with no passkey: that's the point of the mode |
| `signIn` with `Autofill` | Nothing for a long time: the request waits until the user picks a suggestion |
| `upgrade` | `NotSupported` whenever the platform declines; never show anything for a failed `upgrade()` ([details](upgrading-users.md)) |
| `signals.*` | `Success(SignalOutcome.Skipped)` on devices that can't apply them; failures are only `InvalidOptions` (a user handle or credential ID that isn't base64url) and `Unknown` |

## Logging

You don't need your own log line for setup errors: at the default `PasskiLogLevel.Error`, PassKi logs every
`Misconfigured`, `InvalidOptions` and `Unknown` with its check ID, fix and docs link. Route PassKi's log to your crash
reporter with `PassKi.logHandler` so release builds report them too: see [Logging](../logging.md).

`"$error"` (its `toString()`) includes the check ID and docs link, and never contains secrets.
