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

## The error tells you which key
On Android, an AND-03 error (`PasskeyError.Misconfigured` with `checkId == "AND-03"`) names the certificate the
running app is signed with and its origin, for example:

```text
… This app is signed with SHA-256 AB:CD:…:EF (origin android:apk-key-hash:q8…).
```

Copy that fingerprint into `sha256_cert_fingerprints`, and add the origin to your WebAuthn server's allowed origins.
A build from the Play Store is signed with the **Play App Signing** key, not your upload key, so its fingerprint is
only in this error, in `PasskeyDoctor` and in Play Console (Setup > App signing). `./gradlew passkiOrigin --sha256=…`
turns a fingerprint from Play Console into its origin.

## Still failing?
- Run `PasskeyDoctor.run("<rpId>")` in a debug build. If AND-01 and AND-02 pass and AND-03 passes too, the hosted file
  is right for this build.
- Compare the fingerprint in the error with the file. If it's listed and the Doctor passes, Google may still be
  serving an old copy: `passkiCheck` AND-04 shows what Google's Digital Asset Links API returns.
- **Emulators.** Some emulator images fail RP ID validation even when everything above passes; PassKi adds a note to
  the hint when it runs on an emulator. Try a physical device or another system image (see
  [Verified on](../verified-on.md) for the images we've tested).
