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
