# CFG-01: RP ID is a bare domain

Platform: config · Severity: error (fails the check)

## What and why
The RP ID passed to PassKi must be a bare domain such as `example.com`: no scheme, port, path or query.

## Checked by
On-device `PasskeyDoctor` (fails before any network request).

## Fix
Pass the relying party ID as a bare domain such as example.com: no scheme, port, path or query (the same value as the WebAuthn rp.id).

Pass the same value you use as the WebAuthn `rp.id`.
