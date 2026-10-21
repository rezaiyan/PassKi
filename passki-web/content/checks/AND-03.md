# AND-03: Signing certificate listed

Platform: android · Severity: error (fails the check)

## What and why
Each package's statement must list the SHA-256 of the certificate that signs the installed APK. Debug, release and Play App Signing keys are all different.

## Checked by
`passkiCheck`/`passkiVerify` (from your keystores) and `PasskeyDoctor` (from the running app's signature).

## Fix
Add the SHA-256 fingerprint <fingerprint> (run ./gradlew passkiFingerprints) to sha256_cert_fingerprints for <package>.

```bash
./gradlew passkiFingerprints   # packages, fingerprints and android:apk-key-hash origins
```
Re-run `passkiSetup` and upload the regenerated file after adding a keystore.
