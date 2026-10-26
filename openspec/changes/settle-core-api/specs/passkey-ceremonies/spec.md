# Spec Delta

## ADDED Requirements

### Requirement: Main-safe calls
Every `Passkeys` and `PasskeySignals` call SHALL be safe to call from any coroutine dispatcher, including the main dispatcher and background dispatchers; PassKi MUST move platform UI work to the platform's main thread itself and MUST NOT block the calling thread. The result MUST be delivered in the caller's coroutine context.

#### Scenario: Called from a background dispatcher
- **WHEN** an app calls `register(options)` from `Dispatchers.Default`
- **THEN** the platform sheet is presented as if called from the main thread, and the result is returned to the calling coroutine

#### Scenario: Called from the main thread
- **WHEN** an app calls `signIn(options)` from the main dispatcher
- **THEN** the main thread is not blocked while the sheet is showing

### Requirement: Cancellation withdraws the request
Cancelling the coroutine that called a `Passkeys` or `PasskeySignals` operation SHALL withdraw the pending platform request: PassKi MUST ask the platform to dismiss any sheet it showed for that request where the platform offers a way to do so, the call MUST end with `CancellationException` rather than a `PasskeyResult`, and no result from the withdrawn request MAY be delivered later. Cancellation MUST NOT be reported as `PasskeyError.Cancelled`, which means the user dismissed the sheet.

#### Scenario: Screen closed while the sheet is showing
- **WHEN** the scope that called `signIn` is cancelled while the passkey sheet is visible on iOS or in a browser
- **THEN** the sheet is dismissed and the call ends with `CancellationException`

#### Scenario: Provider ignores the dismissal on Android
- **WHEN** the calling scope is cancelled on Android and the credential provider keeps its sheet visible
- **THEN** the call still ends with `CancellationException`, and a credential the user picks afterwards is discarded

#### Scenario: User dismisses the sheet
- **WHEN** the user closes the sheet without cancelling the coroutine
- **THEN** the call returns `Failure(PasskeyError.Cancelled)`

### Requirement: One modal request at a time
While a modal request started by PassKi (`register`, `signIn` with `Modal` or `ImmediateOnly`, `signInOrPassword` or `upgrade`) is pending, another modal request SHALL fail immediately with `PasskeyError.Interrupted` whose message says another passkey request is in progress, without affecting the pending request. Autofill requests MUST keep waiting for pending modal requests to finish instead of failing.

#### Scenario: Double tap on Create passkey
- **WHEN** the app calls `register` twice in quick succession
- **THEN** the second call returns `Failure(PasskeyError.Interrupted)` naming the request in progress
- **AND** the first call completes normally

#### Scenario: Request after the first finished
- **WHEN** a modal request has returned and the app starts another
- **THEN** the new request runs normally

#### Scenario: Autofill while a modal sheet is showing
- **WHEN** an autofill sign-in is started while a modal `signIn` is pending
- **THEN** the autofill request waits until the modal request finishes, then starts

## MODIFIED Requirements

### Requirement: Binary-compatible public types
Result and capability types (`PasskeyCapabilities`, `PasskeyRegistration`, `PasskeyAssertion`, `SignInCredential` subtypes) MUST have constructors marked `@InternalPasskiApi`, so fields can be added without breaking binaries; new members on `Passkeys` and `PasskeySignals` MUST be added only with default implementations; enums and sealed hierarchies documented as open MAY gain entries in minor releases. The KDoc of every public enum and sealed type MUST state whether it is open (may gain entries; callers use an `else` branch) or closed (never gains entries).

#### Scenario: Adopter implements Passkeys
- **WHEN** an adopter implements `Passkeys` (for example a hand-written fake) and PassKi adds a member in a minor release
- **THEN** the adopter's implementation stays source- and binary-compatible

#### Scenario: Results are safe to log
- **WHEN** a `PasskeyRegistration` or `PasskeyAssertion` is converted to a string
- **THEN** the output shows the credential id and the JSON length, not the JSON itself

#### Scenario: Adopter switches over an open type
- **WHEN** an adopter writes a `when` over `PasskeyError` or `SignInMode`
- **THEN** the type's KDoc says it is open and tells them to add an `else` branch

#### Scenario: Unlabelled type added
- **WHEN** a public enum or sealed type is added without an open or closed label in its KDoc
- **THEN** CI fails and names the type
