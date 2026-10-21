# DEV-01: Android device can store passkeys

Platform: device · Severity: warning

## What and why
Credential Manager providers (Google Password Manager) refuse to create passkeys without a secure screen lock; on emulators this looks like a hang.

## Checked by
On-device `PasskeyDoctor`.

## Fix
Set a screen lock and sign in to a Google account (or enable another credential provider) in Settings.

Settings > Security > Screen lock (PIN, pattern or password), and sign in to a Google account.
