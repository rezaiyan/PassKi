# Spec Delta

## ADDED Requirements

### Requirement: Platform errors through the real mapping
`failNextWithPlatformError(name, message)` SHALL queue a WebAuthn DOM error that fails the next operation after being mapped exactly as a browser rejection of that operation and mode would be. It SHALL share the queue and precedence of `failNextWith`.

#### Scenario: NotAllowedError on an immediate sign-in
- **WHEN** a test queues `failNextWithPlatformError("NotAllowedError", "…")` and calls `signIn(options, ImmediateOnly)`
- **THEN** the result is `Failure(NoCredentials)`, as on a real browser

#### Scenario: NotAllowedError on a modal sign-in
- **WHEN** the same error is queued before `signIn(options, Modal)`
- **THEN** the result is `Failure(Cancelled)`

#### Scenario: Unknown DOM error name
- **WHEN** a test queues an error named `"WeirdError"`
- **THEN** the result is `Failure(Unknown)` with `platformCode` `"WeirdError"`

#### Scenario: Same mapping as the browser
- **WHEN** the web implementation and `FakePasskeys` see the same DOM error name for the same operation and mode
- **THEN** they produce equal `PasskeyError`s apart from platform-specific hint text

### Requirement: Blocking view on the JVM
On the JVM, `FakePasskeys.blocking()` SHALL return a view whose `register`, `signIn`, `signInOrPassword`, `upgrade` and signal functions are non-suspending, return the same results as the suspending ones and share the same stored passkeys.

#### Scenario: Java server test
- **WHEN** a JUnit test written in Java calls `fake.blocking().register(optionsJson)`
- **THEN** it receives a `PasskeyResult` whose registration JSON a real server library verifies

#### Scenario: Shared state
- **WHEN** a passkey is registered through the blocking view
- **THEN** a suspending `signIn` on the same `FakePasskeys` can use it

### Requirement: Held sheet in the fake
`FakePasskeys` SHALL let a test keep the next ceremony pending until the test finishes or dismisses it. While a modal ceremony is pending, another modal ceremony on the same fake MUST fail with `PasskeyError.Interrupted`, as on the platforms, and an autofill sign-in MUST wait until it ends.

#### Scenario: Double tap while the sheet is showing
- **WHEN** a test holds the next sheet, starts `register`, and calls `register` again before finishing it
- **THEN** the second call returns `Failure(PasskeyError.Interrupted)`
- **AND** after `finish()` the first call returns a registration

#### Scenario: User dismisses the held sheet
- **WHEN** a test holds the next sheet and calls `dismiss()`
- **THEN** the pending ceremony returns `Failure(PasskeyError.Cancelled)`
