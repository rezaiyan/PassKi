# PassKi docs

## Start here

| Page | What's in it |
|---|---|
| [Quickstart](quickstart.md) | Your app and your server to a first verified passkey, on Android, iOS and the web |
| [Try it](try-it.md) | The sample app, or your app against the hosted demo server, with nothing to set up |
| [Concepts](concepts.md) | How a passkey ceremony works, and the words these docs use |

## Guides

| Page | What's in it |
|---|---|
| [Passkeys as an extra sign-in](guides/extra-sign-in.md) | Accounts from Google, Apple or a password; a passkey added in Settings; the sign-in button; list, delete and relabel |
| [What to show users](guides/what-to-show-users.md) | One row per error: what to show, what to log, retry or not |
| [Choosing your RP ID](guides/choosing-rp-id.md) | The decision you can't undo: apex or subdomain, web and apps together, hosting the files |
| [Choosing user name and display name](guides/user-names.md) | User handles, account labels, Hide My Email |
| [Recommended server API](guides/server-api.md) | Endpoints, request and response shapes, status codes |
| [Verifying on the server](guides/verify-on-server.md) | webauthn4j and java-webauthn-server, each with a `FakePasskeys` test |
| [Upgrading users](guides/upgrading-users.md) | When `upgrade()` creates a passkey without a sheet, per platform |
| [Troubleshooting](guides/troubleshooting.md) | Symptom first: AND-03, Play builds, IOS-02, the web |

## Reference

| Page | What's in it |
|---|---|
| [Usage](usage.md) | Install, every call, results and errors, signals |
| [Platforms](platforms.md) | What each OS version supports, threads and cancellation, platform differences |
| [Compose](compose.md) | `PasskeySignInButton`, `PasskeyUsernameField` and sign-in state for your own UI |
| [Autofill](autofill.md) | Offering passkeys in the keyboard with your own text field |
| [PRF and largeBlob](extensions.md) | Encryption keys and data stored with a passkey |
| [Testing](testing.md) | `FakePasskeys` in your app's and your server's tests |
| [Logging](logging.md) | Log levels, routing to Timber, Kermit or Crashlytics, what's never logged |
| [Setup tooling](setup.md) | The Gradle plugin, `PasskeyDoctor`, the web dashboard, setting up by hand |
| [Check catalog](checks/README.md) | One page per check ID (`AND-01` … `SRV-03`) |
| [Security model](security-model.md) | What PassKi does and doesn't do, and what your server must check |
| [Verified on](verified-on.md) | What was tested on which devices, and what wasn't |

The API reference is generated with `./gradlew dokkaGenerate` (into `build/dokka/html/`).
