# AND-01: assetlinks.json reachable

Platform: android · Severity: error (fails the check)

## What and why
Android fetches `https://<rpId>/.well-known/assetlinks.json` to decide whether your app may use passkeys for the domain. It must be HTTPS, status 200, JSON, and served **without redirects** (Android does not follow them).

## Checked by
`passkiCheck`, `passkiVerify` and the on-device `PasskeyDoctor`. Typical runtime symptom: `RP ID cannot be validated`.

## Fix
Serve https://<rpId>/.well-known/assetlinks.json over HTTPS with status 200, Content-Type application/json and no redirects.

```bash
./gradlew passkiSetup          # writes build/passki/well-known/assetlinks.json
curl -sI https://example.com/.well-known/assetlinks.json   # expect 200 + application/json, no Location
```

## "Served a web page"
A single-page app (React, Vue, Next.js export, Compose for Web...) usually has a catch-all route that answers every
unknown path with `index.html` and status 200. The well-known file then looks reachable, but the platform gets HTML.
PassKi reports this as "<url> served a web page, not assetlinks.json".

- Serve `/.well-known/assetlinks.json` before the catch-all route, or from your server, generated from the same config as
  the origins it accepts so the two can't drift.
- Keep the file out of folders that a deploy mirrors or cleans (for example `rsync --delete` of the web build), or the
  next web deploy deletes it.

```bash
curl -s https://example.com/.well-known/assetlinks.json | head -c 80   # must start with { or [, not <
```
