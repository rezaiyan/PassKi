# passkey-errors Specification

## Purpose
Typed results and errors for every PassKi operation, mapped consistently from Android Credential Manager and iOS AuthenticationServices failures, with fix hints a developer can act on.

## Requirements

### Requirement: Typed result instead of exceptions
Every passkey operation SHALL return `PasskeyResult<T>`, either `Success(value)` or `Failure(error: PasskeyError)`, and MUST NOT throw for platform or option failures.

#### Scenario: Chaining handlers
- **WHEN** a caller uses `onSuccess`, `onFailure`, `map`, `getOrNull` or `getOrThrow`
- **THEN** `onSuccess`/`onFailure` run only for the matching case and return the result unchanged, `map` transforms only success values, `getOrNull` returns null on failure, and `getOrThrow` throws `PasskeyException` on failure

#### Scenario: getOrThrow message
- **WHEN** `getOrThrow` throws for an error with a fix hint
- **THEN** the `PasskeyException` message is `"<message> Fix: <fixHint>"` and its `error` property is the original `PasskeyError`

### Requirement: Error taxonomy
`PasskeyError` SHALL be a sealed hierarchy with `Cancelled`, `NoCredentials`, `AlreadyRegistered`, `Interrupted`, `NotSupported(message)`, `InvalidOptions(message)`, `Misconfigured(message, fixHint)` and `Unknown(message, platformCode, cause)`. New subtypes MAY be added in minor releases.

#### Scenario: Fix hints
- **WHEN** the error is `InvalidOptions` or `Misconfigured`
- **THEN** `fixHint` is non-null and says what the developer should change
- **AND** for `Cancelled`, `NoCredentials`, `AlreadyRegistered`, `Interrupted`, `NotSupported` and `Unknown` it is null

#### Scenario: Value equality
- **WHEN** two `Unknown` errors have the same message and platform code but different causes
- **THEN** they are equal, because `cause` is excluded from `equals` and `hashCode`

### Requirement: Coroutine cancellation is never swallowed
Operations MUST rethrow `CancellationException` (including platform cancellations surfaced as one) and JVM/Kotlin `Error`s instead of converting them to a `PasskeyResult`.

#### Scenario: Caller scope cancelled mid-request
- **WHEN** the coroutine running `register` or `signIn` is cancelled
- **THEN** the call ends with `CancellationException`, not `Failure(Cancelled)`

### Requirement: Android error mapping
Android Credential Manager exceptions SHALL map to the closest `PasskeyError`, and anything unclassified MUST become `Unknown` with the platform exception type as `platformCode` and the exception as `cause`.

#### Scenario: Direct mappings
- **WHEN** Credential Manager throws a create/get cancellation, `NoCredentialException`, an interrupted exception, an unsupported exception or `IllegalArgumentException`
- **THEN** the result is `Cancelled`, `NoCredentials`, `Interrupted`, `NotSupported` or `InvalidOptions` respectively

#### Scenario: DOM errors
- **WHEN** a public-key credential DOM error is `InvalidStateError`, `NotAllowedError` or `NotSupportedError`
- **THEN** the result is `AlreadyRegistered`, `Cancelled` or `NotSupported` respectively

#### Scenario: No credential provider
- **WHEN** Credential Manager reports a provider configuration error
- **THEN** the result is `Misconfigured` with a fix hint about the device's credential provider

#### Scenario: Asset links missing
- **WHEN** Credential Manager rejects a request with a DOM `SecurityError` because the domain does not trust the app
- **THEN** the result is `Misconfigured` whose fix hint names the `assetlinks.json` change to make

#### Scenario: Signal API not supported by provider
- **WHEN** a signal call fails with a provider configuration error
- **THEN** the result is `NotSupported` saying no provider supports the Signal API

### Requirement: iOS error mapping
`ASAuthorizationError` codes SHALL map as follows: 1001 → `Cancelled`, except `NoCredentials` for an immediate-only request that never presented a sheet and `NotSupported` for conditional create; 1005 → `NoCredentials`; 1006 → `AlreadyRegistered`; 1010 → `NotSupported` (device not set up for passkey creation); other codes → `Unknown` with `platformCode` `ASAuthorizationError.<code>`. Apple's generic "The operation couldn't be completed." prefix MUST be removed from messages.

#### Scenario: App not associated with the domain
- **WHEN** iOS reports that app `<TEAM.bundle>` is not associated with domain `<rpId>`
- **THEN** the result is `Misconfigured("App <TEAM.bundle> is not associated with <rpId>.")` whose fix hint names the exact `webcredentials:<rpId>` entitlement and the `apple-app-site-association` content to serve

### Requirement: Secrets never printed
`toString` of credentials and secrets MUST NOT reveal secret material: `SignInCredential.Password` masks the password and `PrfResults` prints only byte sizes.

#### Scenario: Logging a password credential
- **WHEN** a `SignInCredential.Password` is logged
- **THEN** the output is `SignInCredential.Password(id=<id>, password=***)`
