# Recommended server API

PassKi needs four calls to your server, and Settings needs two more. Nothing forces these exact paths, but agreeing on
the shapes up front saves a round of rework, and the [extra sign-in recipe](extra-sign-in.md) uses them.

| Call | Auth | Request | Response |
|---|---|---|---|
| `POST /passkeys/registration-options` | Signed in | none | `200`: creation options JSON |
| `POST /passkeys` | Signed in | `PasskeyRegistration.json` as the body | `201`: the saved passkey (`credentialId`, `label`) |
| `POST /passkeys/sign-in-options` | Public, rate-limited | none, or `{"username": "..."}` | `200`: request options JSON |
| `POST /passkeys/sign-in` | Public, rate-limited | `PasskeyAssertion.json` as the body | `200`: your session; `404` for an unknown passkey |
| `GET /passkeys` | Signed in | none | `200`: the list, with what signals need (below) |
| `DELETE /passkeys/{credentialId}` | Signed in | none | `204` |

## Options

Send the options JSON as your server library produces it. PassKi accepts both the bare options and the
`{"publicKey": {...}}` wrapper that java-webauthn-server's `toCredentialsCreateJson()` returns.

- **Always set the RP ID** (`rp.id` in creation options, `rpId` in request options). Apps have no web origin to
  derive it from, so PassKi fails with `InvalidOptions` ([CFG-01](../checks/CFG-01.md)) without it.
- **Creation options:** `user.id` is the account's user handle ([rules](user-names.md)); `excludeCredentials` lists the
  account's existing passkeys; `authenticatorSelection.residentKey` is `required`, so the passkey works without a
  username.
- **Request options:** leave `allowCredentials` empty so any passkey for your RP ID can be picked; that's what makes
  "Sign in with a passkey" work without a username.

## Responses

Post PassKi's `json` **as the request body**, unchanged. It is the WebAuthn response JSON server libraries parse
directly. Wrapping it as a string (`{"responseJson": "{...}"}`) works too but means JSON inside a string; if you need
other fields, embed it as an object instead: `{"response": {...}, "label": "..."}`.

Your server finds the challenge for the response either from the session that requested the options, or by value
from the response's `clientDataJSON`. Either way, check that it's unused, unexpired and was issued for this ceremony
and (for registration) this account, then delete it.

## Errors

| Status | When | What the app does |
|---|---|---|
| `400` | The response is malformed or doesn't verify | Show a generic failure. Never answer `500`: some libraries throw `RuntimeException`s on malformed input ([webauthn4j](verify-on-server.md#webauthn4j)) |
| `401` | Not signed in (registration, list, delete) | Your usual session handling |
| `404` | Sign-in with a passkey the server doesn't have (deleted, or from another environment) | Call `signals.unknownCredential(assertion.rpId, assertion.credentialId)` so the device stops offering it |
| `429` | Rate limit on the public endpoints | Show "Try again in a moment" |

## The list

`GET /passkeys` returns the passkeys and what the app needs to keep the password manager in step:

```json
{
  "rpId": "example.com",
  "userHandle": "q8Xr…",
  "accountName": "alice@example.com",
  "accountDisplayName": "Alice",
  "passkeys": [
    { "credentialId": "duiH5hP-…", "label": "iPhone", "createdAt": "2026-10-24T10:12:00Z", "lastUsedAt": "2026-10-25T08:01:00Z" }
  ]
}
```

The app passes `rpId`, `userHandle`, every `credentialId` and the two account fields to `signals.syncAccount`
([example](extra-sign-in.md#keep-the-password-manager-in-step)).

## Also on your server

- **The well-known files**, `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association`, if your
  server serves the RP ID host. Generate them from the same configuration as the allowed origins, so the two can't
  disagree. See [Choosing your RP ID](choosing-rp-id.md#hosting-the-well-known-files).
- **Optionally `GET /passkeys/server-config`** returning `{"rpId": "...", "origins": ["..."]}`. Point
  `passki { server { configUrl = ... } }` at it, and `passkiCheck` compares your server's origins with your app's
  signing keys ([SRV-01](../checks/SRV-01.md) to [SRV-03](../checks/SRV-03.md)).

## How the demo server differs

`passki-demo`'s `PasskiDemoServer` talks to the hosted demo relying party, which has no accounts or sessions:

| This page | Demo server |
|---|---|
| `POST /passkeys/registration-options` (signed in) | `POST /api/demo/register/options` with an optional `{"username": "..."}`: it creates a throwaway user |
| `POST /passkeys` with the response as the body | `POST /api/demo/register/verify` with `{"response": {...}}`, returning what the server checked |
| `POST /passkeys/sign-in-options` | `POST /api/demo/login/options` with an optional `{"userHandle": "..."}` |
| `POST /passkeys/sign-in` | `POST /api/demo/login/verify` with `{"response": {...}}` |
| `GET /passkeys`, `DELETE /passkeys/{id}` | Not offered: demo users are deleted after 24 hours |
| `GET /passkeys/server-config` | `GET /api/demo/server-config` |
