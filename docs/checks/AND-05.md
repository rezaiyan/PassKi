# AND-05: Play App Signing key listed

Platform: android · Severity: warning

## What and why
With Play App Signing, Google re-signs your release with its own key, so the fingerprint users get is not the one in your keystore.

## Checked by
`passkiCheck` warns when a release (non-debuggable) variant has no local signing config and no `extraFingerprints`.

## Fix
For Play releases, copy the App signing key SHA-256 from Play Console > Test and release > App integrity, and add it via passki { android { extraFingerprints.add("...") } }.

```kotlin
passki {
    android { extraFingerprints.add("AA:BB:...") } // Play Console > Test and release > App integrity > App signing key
}
```
