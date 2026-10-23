# compose-ui Specification

## Purpose
`passki-compose` provides Compose Multiplatform building blocks for passkey sign-in: a ready-made button, a headless sign-in state, and a username field that offers passkeys as keyboard suggestions.

## Requirements

### Requirement: Remembered Passkeys instance
`rememberPasskeys()` SHALL return the platform `Passkeys`, remembered across recompositions, with no platform handles required.

#### Scenario: Recomposition
- **WHEN** the composable that called `rememberPasskeys()` recomposes
- **THEN** the same `Passkeys` instance is returned

### Requirement: Headless sign-in state
`rememberPasskeySignIn(requestOptions, passkeys, mode)` SHALL return a `PasskeySignInState` whose `signIn()` fetches fresh options from the latest `requestOptions` lambda and then shows the system sheet, exposing `isRunning` and `lastError` as Compose state.

#### Scenario: Double tap
- **WHEN** `signIn()` is called while a sign-in is already running
- **THEN** no second system sheet starts; the call joins the running request and returns its result

#### Scenario: Options fetch fails
- **WHEN** the `requestOptions` lambda throws (for example a network failure)
- **THEN** `signIn()` returns `PasskeyError.Unknown` with that exception as `cause` and does not rethrow it

#### Scenario: Cancelled sheet
- **WHEN** the user dismisses the sheet
- **THEN** `lastError` stays null, because `Cancelled` is never stored there

#### Scenario: Starter coroutine cancelled
- **WHEN** the coroutine that started the running request is cancelled
- **THEN** callers that joined it receive `PasskeyError.Cancelled`

### Requirement: Sign-in button
`PasskeySignInButton` SHALL be a Material 3 button that calls `PasskeySignInState.signIn`, is disabled and shows a progress indicator while running, calls `onSignedIn` with the assertion, and calls `onError` for every failure except `Cancelled`.

#### Scenario: Default label
- **WHEN** no `content` is given
- **THEN** the button reads "Sign in with a passkey"

### Requirement: Passkey username field
`PasskeyUsernameField` SHALL keep a `SignInMode.Autofill` request open while it is focused and autofill is supported, call `onSignedIn` when the user picks a suggestion, and withdraw the offer when it loses focus or leaves composition. Typing a username MUST work as a normal text field.

#### Scenario: Android in a dialog
- **WHEN** the field is shown inside a `Dialog` or `ModalBottomSheet` on Android 15+
- **THEN** the autofill request is anchored to the field's own view, and the passkey appears as a keyboard suggestion

#### Scenario: iOS QuickType bar
- **WHEN** the field is focused on iOS 16+
- **THEN** it is a native `UITextField` with the username content type, and passkeys appear in the QuickType bar

#### Scenario: Platform without autofill
- **WHEN** the field runs on a platform without autofill (JVM, web, or older OS)
- **THEN** it is a plain Material 3 outlined text field and makes no passkey request

#### Scenario: Dismissal is not an error
- **WHEN** the user dismisses the suggestion
- **THEN** `onError` is not called, and tapping the field again brings the suggestion back

#### Scenario: Unrecoverable autofill error
- **WHEN** autofill fails with `NotSupported`, `Misconfigured` or `InvalidOptions`
- **THEN** `onError` is called once and the field stops offering passkeys

#### Scenario: Transient error
- **WHEN** `requestOptions` throws
- **THEN** `onError` is called and the field retries on the next focus or tap
