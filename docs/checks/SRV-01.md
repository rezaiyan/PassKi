# SRV-01: Server RP ID matches

Platform: server · Severity: error (fails the check)

## What and why
Your WebAuthn server must use the same RP ID as the app; assertions for another RP ID are rejected.

## Checked by
`passkiCheck` with `passki { server { configUrl = "https://example.com/passki/server-config" } }`.

## Fix
Configure your WebAuthn server's RP ID as <rpId>.

Server config contract (any JSON endpoint, GET, no auth):
```json
{"rpId": "example.com", "origins": ["https://example.com", "android:apk-key-hash:..."]}
```
`passkiSetup` writes the expected values to `build/passki/server-config.json`.
