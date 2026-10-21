# DEV-03: Platform supports passkeys

Platform: device · Severity: error (fails the check)

## What and why
Passkeys need Android 9 (API 28)+ or iOS 16+.

## Checked by
On-device `PasskeyDoctor`.

## Fix
Passkeys need Android 9+ or iOS 16+.

Hide passkey UI when `Passkeys.capabilities().isSupported` is false.
