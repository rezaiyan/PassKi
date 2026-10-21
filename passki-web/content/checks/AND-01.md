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
