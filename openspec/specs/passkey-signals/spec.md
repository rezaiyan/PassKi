# passkey-signals Specification

## Purpose
Keeps the user's passkey provider in sync with server-side state through the WebAuthn Signal API, exposed as `Passkeys.signals`.

## Requirements

### Requirement: Signal calls
`PasskeySignals` SHALL provide `unknownCredential(rpId, credentialId)`, `acceptedCredentials(rpId, userId, credentialIds)` and `currentUserDetails(rpId, userId, name, displayName)`, each returning `PasskeyResult<Unit>`.

#### Scenario: Passkey deleted on the server
- **WHEN** the app calls `signals.unknownCredential(rpId, credentialId)` for a passkey the server no longer knows
- **THEN** the provider is told it may hide or delete that passkey

#### Scenario: Full list of valid passkeys
- **WHEN** the app calls `signals.acceptedCredentials(rpId, userId, ids)`
- **THEN** the provider is told that `ids` is the complete list for that user and may remove others

#### Scenario: User renamed
- **WHEN** the app calls `signals.currentUserDetails(rpId, userId, name, displayName)`
- **THEN** the provider is told the new name and display name for saved passkeys

### Requirement: Signals gated by capability
Signal calls SHALL require `PasskeyCapabilities.signals`; when the OS or the user's provider does not support them, the call MUST fail with `PasskeyError.NotSupported`.

#### Scenario: Provider without Signal API on Android
- **WHEN** the OS supports signals but the user's provider does not
- **THEN** the signal call returns `Failure(PasskeyError.NotSupported)`

#### Scenario: RP ID rejected on Android
- **WHEN** the provider rejects a signal for the RP ID with a security error
- **THEN** the call returns `Failure(PasskeyError.Misconfigured)` with an asset-links fix hint
