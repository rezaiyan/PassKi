# SRV-03: Server options are valid WebAuthn

Platform: server · Severity: error (fails the check)

## What and why
Registration options must be valid WebAuthn: `rp.id`, a base64url challenge of at least 16 random bytes and a `user.id` of 1-64 bytes.

## Checked by
`passkiCheck` when `server.configUrl` returns registration options (`{rp, challenge, user}` or `{publicKey: {...}}`) instead of the config contract.

## Fix
Registration options must include rp.id, a base64url challenge of at least 16 bytes and a user.id of 1-64 bytes.

Let your WebAuthn library generate the options; never reuse challenges or use an email as `user.id`.
