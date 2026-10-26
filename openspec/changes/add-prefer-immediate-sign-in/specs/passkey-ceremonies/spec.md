# Spec Delta

## ADDED Requirements

### Requirement: Prefer device passkeys
`signIn(options, SignInMode.PreferImmediate)` SHALL behave as `ImmediateOnly` when `capabilities().immediateSignIn` is true and as `Modal` otherwise. If the immediate request fails with `NotSupported`, it MUST retry once as `Modal`. A `NoCredentials` result MUST be returned without falling back.

#### Scenario: Platform with immediate sign-in and a local passkey
- **WHEN** an Android or iOS app calls `signIn(options, PreferImmediate)` and a passkey for the RP ID is on the device
- **THEN** the device-only sheet is shown and the assertion is returned

#### Scenario: No passkey on the device
- **WHEN** `PreferImmediate` runs where immediate sign-in is supported and no local passkey exists
- **THEN** the result is `Failure(NoCredentials)` and no hybrid / QR sheet is shown

#### Scenario: Browser without immediate mediation
- **WHEN** a browser reports `immediateSignIn = false`
- **THEN** `PreferImmediate` shows the standard browser sheet

#### Scenario: Browser rejects immediate mediation at request time
- **WHEN** the immediate request fails with `NotSupported`
- **THEN** PassKi retries once with the standard sheet and returns that result
