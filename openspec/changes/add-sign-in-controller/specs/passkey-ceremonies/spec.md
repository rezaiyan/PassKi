# Spec Delta

## ADDED Requirements

### Requirement: Single-flight sign-in controller
`PasskeySignInController(passkeys, mode, requestOptions)` SHALL run at most one sign-in at a time: `signIn()` fetches fresh options from `requestOptions`, shows the sheet and returns the result, and a call made while one is running MUST join it instead of starting a second sheet. It SHALL expose `isRunning` and `lastError` as `StateFlow`.

#### Scenario: Double tap in a ViewModel
- **WHEN** a ViewModel calls `controller.signIn()` twice before the first completes
- **THEN** one system sheet is shown and both calls receive the same result

#### Scenario: Options fetch fails
- **WHEN** `requestOptions` throws a network exception
- **THEN** `signIn()` returns `PasskeyError.Unknown` with that exception as `cause`, `lastError` holds it, and nothing is rethrown

#### Scenario: Running state
- **WHEN** a sign-in is in flight
- **THEN** `isRunning.value` is true, and false again after it completes

### Requirement: Cancellation is not an error state
The controller MUST NOT store `Cancelled` or `Interrupted` in `lastError`, MUST clear `lastError` when a new sign-in starts, and when the coroutine that started a running request is cancelled, callers that joined it SHALL receive `PasskeyError.Cancelled`.

#### Scenario: User closes the sheet
- **WHEN** the user dismisses the sheet
- **THEN** `signIn()` returns `Failure(Cancelled)` and `lastError` stays null

#### Scenario: Starter scope cancelled
- **WHEN** the ViewModel scope that started the request is cancelled while another caller joined it
- **THEN** the joined caller receives `Failure(Cancelled)`
