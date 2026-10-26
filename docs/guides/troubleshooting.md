# Troubleshooting

Start from what you see. Each entry says what to check first; most link to the check page with the full fix. Two tools
find most problems on their own: `./gradlew passkiCheck` (your domain, Xcode project and server, also in CI) and
`PasskeyDoctor` (the same checks from a running app, in debug builds). See [Setup](../setup.md).

## Android

### AND-03, but `assetlinks.json` looks right

1. **Compare fingerprints.** The AND-03 error names the certificate the running app is signed with, for example
   `This app is signed with SHA-256 AB:CD:…`. That exact fingerprint must be in `sha256_cert_fingerprints` for the
   package ([AND-03](../checks/AND-03.md)). Debug, release and Play App Signing keys are all different.
2. **Wait for Google's cache.** Google caches which apps a domain trusts. A change can take a few minutes to arrive;
   `passkiCheck` reports [AND-04](../checks/AND-04.md) once Google's Digital Asset Links API agrees.
3. **Check the statement itself:** the relation must be `delegate_permission/common.get_login_creds`
   ([AND-02](../checks/AND-02.md)), and the file must load with status 200, no redirect and
   `Content-Type: application/json` ([AND-01](../checks/AND-01.md)).
4. **Still failing on an emulator?** If `PasskeyDoctor` passes AND-01 to AND-04 and the fingerprint from the error is
   in the file, try a physical device or another system image. One emulator image failed every passkey creation with
   AND-03 although the setup was right; it's recorded in [Verified on](../verified-on.md).

### Works in debug, fails from the Play Store

Play re-signs your app with the **Play App Signing** key, so a Play build has a fingerprint that is in no local
keystore. Copy it from the AND-03 error or from Play Console (Setup > App signing), add it to `assetlinks.json`
([AND-05](../checks/AND-05.md)) and add its origin to your server:

```bash
./gradlew passkiOrigin --sha256=AB:CD:...   # prints the android:apk-key-hash: origin and the assetlinks statement
```

Your server must accept that origin too ([SRV-02](../checks/SRV-02.md)), or the passkey is created on the device and
then rejected by the server.

### "No credential provider" or DEV-01

The device has no password manager that can hold passkeys: on an emulator, use a Google Play system image, sign in to
a Google account and set a screen lock ([DEV-01](../checks/DEV-01.md)).

### AND-06: PassKi isn't started

PassKi starts itself with `androidx.startup`. If your app removed the startup provider, call
`PasskiAndroid.init(application)` in `Application.onCreate()` ([AND-06](../checks/AND-06.md)). The same error appears
when no activity is in the foreground, for example a call from a background service.

## iOS

### IOS-02: the app isn't linked to the domain

1. **The AASA file** must list `<TeamID>.<bundleId>` under `webcredentials.apps` ([IOS-02](../checks/IOS-02.md)), and
   load from `https://<RP ID>/.well-known/apple-app-site-association` with status 200, no redirect and
   `Content-Type: application/json` ([IOS-01](../checks/IOS-01.md)). Static file servers often send the file, which
   has no extension, as `application/octet-stream`.
2. **The entitlement:** `webcredentials:<RP ID>` in the app's associated domains
   ([IOS-04](../checks/IOS-04.md) to [IOS-06](../checks/IOS-06.md)).
3. **Apple's CDN** serves a cached copy of the AASA file to devices; a change can take a while to arrive
   ([IOS-03](../checks/IOS-03.md)).

### Works from Xcode, fails in TestFlight or the App Store

After adding Associated Domains, **regenerate the App Store provisioning profile**. Debug builds work without it;
release signing (often in CI) fails or produces an app without the entitlement ([IOS-06](../checks/IOS-06.md)).

## Web

### "The passkey didn't work" in the browser

- **The page's host must be the RP ID or a subdomain of it.** Otherwise the browser rejects the request and PassKi
  reports `Misconfigured`. See [Choosing your RP ID](choosing-rp-id.md#web-app-and-apps-together).
- **The page must be HTTPS or `http://localhost`.** Elsewhere `capabilities().isSupported` is false and every call
  fails with `NotSupported` ([DEV-03](../checks/DEV-03.md)).
- **`ImmediateOnly` in a browser without immediate mediation** fails with `NotSupported`; check
  `capabilities().immediateSignIn` first and fall back to `Modal`.

## Every platform

### The well-known files disappeared after a deploy

A deploy that mirrors a build folder (deleting files that aren't in the build) wipes well-known files kept next to it.
Serve them from your server, or make them part of the build. Put `passkiCheck` in CI after the deploy, so this fails
the pipeline instead of your users' sign-ins.

### `/.well-known/...` returns your web app's HTML page

A single-page app's catch-all route answers every unknown path with `index.html` and status 200. PassKi's checks
recognise that page and say so ([AND-01](../checks/AND-01.md), [IOS-01](../checks/IOS-01.md)); exclude `/.well-known/`
from the catch-all.

### The server rejects every response

Compare your server's RP ID and allowed origins with what `passkiSetup` prints ([SRV-01](../checks/SRV-01.md),
[SRV-02](../checks/SRV-02.md)). Android responses come from `android:apk-key-hash:<base64url SHA-256>`, one per signing
key; iOS responses from `https://<RP ID>`. See [Verifying on the server](verify-on-server.md).

### `InvalidOptions` with CFG-01

The options have no RP ID, or it isn't a bare domain (`example.com`, without `https://`, port or path). Apps have no
web origin to derive one from ([CFG-01](../checks/CFG-01.md)).

### First-time users see a QR code or "Sign in another way"

That's `SignInMode.Modal` on a device with no passkey yet. For an optional "Sign in with a passkey" button, use
`ImmediateOnly` and handle `NoCredentials` ([recipe](extra-sign-in.md#the-sign-in-button)).

### A second passkey shows up as a second account

Each account needs one user handle for all its passkeys ([Choosing user name and display name](user-names.md)).

### A double tap shows an error

The second call fails with `Interrupted` while the first sheet is showing. It's a user cancellation
(`isUserCancellation`): show nothing ([What to show users](what-to-show-users.md)).

## Still stuck?

Turn on `PassKi.logLevel = PasskiLogLevel.Debug` and reproduce: the log shows each call's mode and RP ID and which
platform error each failure came from ([Logging](../logging.md)). Then open an issue with that log; it contains no
secrets.
