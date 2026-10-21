# SRV-02: Server allows all origins

Platform: server · Severity: error (fails the check)

## What and why
Android apps report the origin `android:apk-key-hash:<base64url SHA-256 of the signing cert>`, not `https://<rpId>`. Servers that only allow the web origin reject every Android passkey.

## Checked by
`passkiCheck` (server config contract, see SRV-01).

## Fix
Allow https://<rpId> and every android:apk-key-hash:<hash> origin printed by ./gradlew passkiFingerprints.

Use the origins list from `build/passki/server-config.json`; copy-paste snippets for SimpleWebAuthn, webauthn4j, java-webauthn-server and py_webauthn are in `build/passki/server-snippets/`.
