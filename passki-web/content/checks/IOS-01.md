# IOS-01: apple-app-site-association reachable

Platform: ios · Severity: error (fails the check)

## What and why
iOS (through Apple's CDN) fetches `https://<rpId>/.well-known/apple-app-site-association`. It must be HTTPS, status 200, JSON, without redirects, below 128 KB, ideally `Content-Type: application/json`.

## Checked by
`passkiCheck`, `passkiVerify`, `PasskeyDoctor`. Runtime symptom: error 1004 "not associated".

## Fix
Serve https://<rpId>/.well-known/apple-app-site-association over HTTPS with status 200, JSON body and no redirects.

```bash
./gradlew passkiSetup   # writes build/passki/well-known/apple-app-site-association
```

## "Served a web page"
A single-page app (React, Vue, Next.js export, Compose for Web...) usually has a catch-all route that answers every
unknown path with `index.html` and status 200. The well-known file then looks reachable, but the platform gets HTML.
PassKi reports this as "<url> served a web page, not apple-app-site-association".

- Serve `/.well-known/apple-app-site-association` before the catch-all route, or from your server, generated from the same config as
  the origins it accepts so the two can't drift.
- Keep the file out of folders that a deploy mirrors or cleans (for example `rsync --delete` of the web build), or the
  next web deploy deletes it.

```bash
curl -s https://example.com/.well-known/apple-app-site-association | head -c 80   # must start with {, not <
```
