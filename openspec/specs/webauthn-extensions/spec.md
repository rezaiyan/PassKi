# webauthn-extensions Specification

## Purpose
Typed helpers (`PasskeyExtensions`) that add the WebAuthn `prf` and `largeBlob` extensions to server options on the device, and typed outputs on registration and assertion results.

## Requirements

### Requirement: Non-destructive options editing
Every `PasskeyExtensions` helper SHALL return new options JSON that keeps all other fields and extensions and any `{"publicKey": ...}` envelope, and MUST throw `IllegalArgumentException` when the input is not JSON, not an object, has a non-object `extensions` field, or is the wrong kind of options for the helper.

#### Scenario: Wrong options kind
- **WHEN** `withLargeBlobRead` is given creation options (with `rp` or `user`)
- **THEN** it throws `IllegalArgumentException` saying the extension needs request (sign-in) options

#### Scenario: Envelope kept
- **WHEN** a helper is given `{"publicKey": {...}}`
- **THEN** the returned JSON still has the `publicKey` envelope, with the extension added inside it

### Requirement: PRF extension
`withPrf(options, first, second?)` SHALL set `extensions.prf.eval` with base64url salts on creation or request options and keep any existing `evalByCredential`; `withPrf(creationOptions)` SHALL request PRF support at registration without salts.

#### Scenario: PRF at sign-in
- **WHEN** request options with `withPrf` salts are used to sign in and the provider supports PRF
- **THEN** `PasskeyAssertion.prf` holds one 32-byte result per salt, the same for the same passkey and salt

#### Scenario: PRF support at registration
- **WHEN** creation options with `withPrf` are used to register
- **THEN** `PasskeyRegistration.prfEnabled` reports whether the passkey supports PRF, and `prf` holds results when the provider evaluated salts at creation

### Requirement: largeBlob extension
`withLargeBlob(creationOptions, support)` SHALL request large-blob storage (`Preferred` by default, or `Required`); `withLargeBlobRead` and `withLargeBlobWrite(blob)` SHALL request a read or a write while signing in, and a write MUST replace any read request.

#### Scenario: Write a blob
- **WHEN** request options with `withLargeBlobWrite` (and the passkey named in `allowCredentials`) are used to sign in
- **THEN** `PasskeyAssertion.largeBlobWritten` reports whether the blob was stored

#### Scenario: Read a blob
- **WHEN** request options with `withLargeBlobRead` are used to sign in
- **THEN** `PasskeyAssertion.largeBlob` holds the stored bytes, or null when none is stored

### Requirement: Best-effort outputs
Extensions SHALL behave as in browsers: when the provider or OS does not support one (PRF needs iOS 18+ and is provider-dependent on Android; largeBlob needs iOS 17+), the request still succeeds and the corresponding output MUST be null.

#### Scenario: PRF on iOS 17
- **WHEN** PRF salts are requested on iOS 17
- **THEN** sign-in succeeds and `PasskeyAssertion.prf` is null
