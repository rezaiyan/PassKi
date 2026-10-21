# DEV-02: iOS device can use passkeys

Platform: device · Severity: warning

## What and why
Passkeys need a device passcode; the Simulator needs enrolled Face ID. With `?mode=developer` the device must also have Associated Domains Development enabled.

## Checked by
On-device `PasskeyDoctor`.

## Fix
Enrol Face ID/Touch ID (Simulator: Features > Face ID > Enrolled); for ?mode=developer enable Settings > Developer > Associated Domains Development.

Simulator: Features > Face ID > Enrolled. Device: Settings > Developer > Associated Domains Development. Tip: the hardware keyboard hides the QuickType passkey bar in the Simulator (I/O > Keyboard > Connect Hardware Keyboard off).
