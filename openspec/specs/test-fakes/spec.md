# test-fakes Specification

## Purpose
`passki-test` lets adopters unit-test passkey flows without a device: `FakePasskeys` is a drop-in `Passkeys` backed by an in-memory authenticator whose responses pass real WebAuthn server verifiers.

## Requirements

### Requirement: Cryptographically valid responses
`FakeAuthenticator` SHALL produce WebAuthn registration and assertion responses signed with ES256 and attestation `none`, with `clientDataJSON` carrying the configured `origin`, so real server libraries accept them.

#### Scenario: Server verification in a test
- **WHEN** a test registers with `FakePasskeys("https://example.com")` and verifies the returned `json` with a real WebAuthn server library
- **THEN** verification succeeds

#### Scenario: Sign count
- **WHEN** a stored passkey signs in
- **THEN** its `signCount` advances as on a real authenticator

### Requirement: RP ID checked like a platform
The fake authenticator SHALL check the options' RP ID against `origin`: for an `http(s)` origin the host MUST equal the RP ID or be a subdomain of it, otherwise `Misconfigured`; for other origins (for example `android:apk-key-hash:...`) the options MUST name the RP ID, otherwise `InvalidOptions`.

#### Scenario: Origin outside the RP ID
- **WHEN** `FakePasskeys("https://other.com")` registers with `rp.id` `example.com`
- **THEN** the result is `Failure(PasskeyError.Misconfigured)`

### Requirement: Capability flags
`FakePasskeys` SHALL expose `isSupported`, `immediateSignIn`, `unifiedPasswordSignIn`, `conditionalCreate`, `signalsSupported` and `autofillSupported` (all true by default); turning a flag off MUST make `capabilities()` report it false and make the matching operation fail with `NotSupported`.

#### Scenario: Passkeys unsupported
- **WHEN** `isSupported` is false
- **THEN** every operation fails with `NotSupported` and every capability is reported false

### Requirement: Scripted failures
`failNextWith(error)` SHALL queue errors in order, each failing the next operation, taking precedence over capability flags.

#### Scenario: Simulating a cancel
- **WHEN** a test calls `failNextWith(PasskeyError.Cancelled)` and then `register`
- **THEN** that `register` returns `Failure(Cancelled)` and the following call behaves normally

### Requirement: Autofill modelling
With autofill supported, `signIn(mode = Autofill)` SHALL complete at once when a passkey matches, as if the user picked the suggestion, and SHALL suspend until cancelled when none matches, never returning `NoCredentials` on its own and not blocking other operations.

#### Scenario: No matching passkey
- **WHEN** an autofill sign-in starts with no registered passkey for the RP
- **THEN** the call suspends until its coroutine is cancelled

### Requirement: Passwords and signals for assertions
`FakePasskeys` SHALL offer `savedPasswords` entries from `signInOrPassword` when no passkey matches, and SHALL record every validated signal in `receivedSignals` as `FakeSignal` values, applying them to the stored credentials.

#### Scenario: Asserting a signal
- **WHEN** the code under test calls `signals.unknownCredential(rpId, id)`
- **THEN** `receivedSignals` contains `FakeSignal.UnknownCredential(rpId, id)` and the credential is removed from the authenticator

### Requirement: Test-only extensions
The fake authenticator SHALL support `prf` and `largeBlob` (toggled by `prfSupported` and `largeBlobSupported`), with PRF computed deterministically as `HMAC-SHA256(secret, SHA-256("WebAuthn PRF" || 0x00 || salt))` from a secret derived from the credential id. It MUST be documented as test-only key material.

#### Scenario: Deterministic PRF
- **WHEN** the same fake passkey signs in twice with the same PRF salt
- **THEN** both assertions return the same 32 bytes

### Requirement: Concurrency safety
Operations SHALL be safe to call from concurrent coroutines; the public mutable collections `savedPasswords` and `receivedSignals` are not synchronised and are meant to be touched only before or after calls.

#### Scenario: Parallel registrations
- **WHEN** two coroutines register concurrently
- **THEN** both succeed with distinct credentials
