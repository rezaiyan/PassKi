# Choosing your RP ID

Every passkey belongs to one RP ID, a domain such as `example.com`. It's the one passkey decision you can't undo:
**changing the RP ID later orphans every passkey already created**. Users would have to sign in another way and create
a new passkey. Take five minutes over it before the first release.

## The rules

- **It's a domain you control,** without scheme, port or path: `example.com` or `app.example.com`, never
  `https://example.com/`. PassKi rejects anything else with `InvalidOptions` ([CFG-01](../checks/CFG-01.md)).
- **The well-known files live on that exact host:** `https://<RP ID>/.well-known/assetlinks.json` (Android) and
  `https://<RP ID>/.well-known/apple-app-site-association` (iOS), with status 200 and no redirect
  ([AND-01](../checks/AND-01.md), [IOS-01](../checks/IOS-01.md)). A redirect from `example.com` to `www.example.com`
  breaks both.
- **A web app can sign in with it only from that domain or a subdomain.** With RP ID `example.com`, a web app on
  `app.example.com` works; one on `example-app.com` doesn't.
- **iOS needs `webcredentials:<RP ID>`** in the app's associated domains, and Android needs the app in the RP ID's
  `assetlinks.json`. `passkiSetup` writes both; [setup by hand](../setup.md#server-setup-by-hand) lists them.

## Apex domain or subdomain

| Choose | When |
|---|---|
| The product's own domain (`example.com`) | One product per domain. Its website, web app and mobile apps share passkeys |
| A product subdomain (`app.example.com`) | The apex belongs to a company with several products, or its well-known files are managed by another team |

Use **one RP ID per product, not per company.** Passkeys are shown and synced per RP ID, so two products on one RP ID
share a list of passkeys in every password manager: users see the other product's accounts in your sign-in sheet, and
the two products' well-known files have to be edited together forever.

`passkiSetup` warns when your RP ID is a bare domain whose live well-known files already list apps other than yours,
the sign that it's shared. The warning never fails the task; it is skipped offline and turned off with
`-Ppasski.setup.sharedRpIdCheck=false`.

## Web app and apps together

The RP ID is also the default for the web: a browser on `app.example.com` uses `app.example.com` unless the options
say `example.com`. So when the web app lives on a subdomain and the mobile apps use the apex, your server must send
`rpId: "example.com"` in the web options too, or web passkeys and app passkeys end up under two RP IDs.

The allowed origins on your server then are `https://example.com`, `https://app.example.com` (one per web host you sign
in from) and one `android:apk-key-hash:...` per Android signing key. See
[Verifying on the server](verify-on-server.md#what-your-server-must-check).

## Hosting the well-known files

- Serve them from the RP ID host itself, with status 200, no redirect and `Content-Type: application/json`, including
  the AASA file, which has no extension ([IOS-01](../checks/IOS-01.md)).
- A single-page app's catch-all route must not answer `/.well-known/*` with `index.html`. PassKi's checks recognise
  that page and say so.
- A web deploy that mirrors a build folder (and deletes extra files) wipes well-known files that aren't part of the
  build. Serve them from your server, or put them in the build.

Run `./gradlew passkiCheck` (or `PasskeyDoctor` on a device) after every hosting change; both check these rules and
name the failing check.

## Related

- [Setup](../setup.md): the Gradle plugin, `PasskeyDoctor` and setting up by hand.
- [Troubleshooting](troubleshooting.md): what to do when a check fails.
