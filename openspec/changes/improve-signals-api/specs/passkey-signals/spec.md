# Spec Delta

## MODIFIED Requirements

### Requirement: Signal calls
`PasskeySignals` SHALL provide `unknownCredential(rpId, credentialId)`, `acceptedCredentials(rpId, userHandle, credentialIds)` and `currentUserDetails(rpId, userHandle, name, displayName)`, each returning `PasskeyResult<SignalOutcome>`, where `SignalOutcome` is `Applied` (the provider accepted the signal) or `Skipped(reason)` (the OS or provider can't apply it). `userHandle` is the base64url WebAuthn user handle (`user.id` of the creation options), never the app's account id.

#### Scenario: Passkey deleted on the server
- **WHEN** the app calls `signals.unknownCredential(rpId, credentialId)` for a passkey the server no longer knows
- **THEN** the provider is told it may hide or delete that passkey
- **AND** the call returns `Success(SignalOutcome.Applied)`

#### Scenario: Full list of valid passkeys
- **WHEN** the app calls `signals.acceptedCredentials(rpId, userHandle, ids)`
- **THEN** the provider is told that `ids` is the complete list for that user and may remove others

#### Scenario: User renamed
- **WHEN** the app calls `signals.currentUserDetails(rpId, userHandle, name, displayName)`
- **THEN** the provider is told the new name and display name for saved passkeys

### Requirement: Signals gated by capability
Signal calls SHALL require `PasskeyCapabilities.signals`; when the OS or the user's provider does not support them, the call MUST return `Success(SignalOutcome.Skipped(reason))` with a reason naming what's missing, never `Failure(PasskeyError.NotSupported)`. Failures are reserved for problems the app can fix or should log (`Misconfigured`, `InvalidOptions`, `Unknown`).

#### Scenario: Provider without Signal API on Android
- **WHEN** the OS supports signals but the user's provider does not
- **THEN** the signal call returns `Success(SignalOutcome.Skipped(reason))` and the reason names the provider limitation

#### Scenario: RP ID rejected on Android
- **WHEN** the provider rejects a signal for the RP ID with a security error
- **THEN** the call returns `Failure(PasskeyError.Misconfigured)` with an asset-links fix hint

#### Scenario: App logs only failures
- **WHEN** an app writes `signals.unknownCredential(rpId, id).onFailure { log(it) }` on a device without signal support
- **THEN** nothing is logged

## ADDED Requirements

### Requirement: Clear error for a bad user handle
When `userHandle` or a credential ID is not valid base64url, the signal call SHALL fail with `InvalidOptions` whose message names the parameter and whose fix hint says the user handle is the `user.id` from the creation options (also returned as `PasskeyAssertion.userHandle`), not an account id or email.

#### Scenario: Email passed as user handle
- **WHEN** the app calls `acceptedCredentials(rpId, "pat@example.com", ids)`
- **THEN** the result is `Failure(InvalidOptions)` naming `userHandle`
- **AND** the fix hint explains where the user handle comes from

### Requirement: Account sync in one call
`PasskeySignals.syncAccount(rpId, userHandle, credentialIds, name, displayName)` SHALL send the accepted-credentials and current-user-details signals for the account, skipping either one the provider can't apply. It MUST return `Success(Applied)` when at least one signal was applied and every other one was skipped or applied, `Success(Skipped(reason))` when none could be applied, and fail only with the first `Misconfigured`, `InvalidOptions` or `Unknown` error.

#### Scenario: Settings opens
- **WHEN** the app calls `syncAccount` with the account's credential IDs and label on a device that supports both signals
- **THEN** the provider receives both signals and the call returns `Success(SignalOutcome.Applied)`

#### Scenario: Provider supports neither signal
- **WHEN** `PasskeyCapabilities.signals` is false
- **THEN** `syncAccount` returns `Success(SignalOutcome.Skipped(reason))` without calling the platform

#### Scenario: One signal not supported by the provider
- **WHEN** the provider applies accepted credentials but rejects user details as not supported
- **THEN** `syncAccount` returns `Success(SignalOutcome.Applied)`

#### Scenario: Existing implementers
- **WHEN** an adopter's own `PasskeySignals` implementation was written before `syncAccount` existed
- **THEN** it still compiles and `syncAccount` calls its two signal functions
