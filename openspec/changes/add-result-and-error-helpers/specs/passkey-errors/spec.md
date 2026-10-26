# Spec Delta

## ADDED Requirements

### Requirement: Result folding and conversion
`PasskeyResult` SHALL offer `fold(onSuccess, onFailure)`, `mapError(transform)`, `recover(transform)`, `isSuccess`, `isFailure`, `errorOrNull()` and `toResult()`, with the same meaning as the matching `kotlin.Result` functions; `toResult()` MUST wrap a failure in `PasskeyException` carrying the original error.

#### Scenario: Fold to an app result type
- **WHEN** an app calls `result.fold(onSuccess = { AppResult.Ok(it.json) }, onFailure = { AppResult.Err(it) })`
- **THEN** exactly one lambda runs and its value is returned

#### Scenario: Recover from a failure
- **WHEN** `recover { fallback }` is called on a `Failure`
- **THEN** the result is `Success(fallback)`; on a `Success` it is returned unchanged

#### Scenario: Convert to kotlin.Result
- **WHEN** `toResult()` is called on `Failure(NoCredentials)`
- **THEN** the returned `Result` is a failure whose exception is a `PasskeyException` with `error == NoCredentials`

#### Scenario: Error accessors
- **WHEN** `errorOrNull()` is called on a `Success`
- **THEN** it returns null, and `isSuccess` is true while `isFailure` is false

### Requirement: User cancellation check
`PasskeyError.isUserCancellation` SHALL be true for `Cancelled` and `Interrupted` and false for every other error, so apps can skip error UI for anything the user ended.

#### Scenario: Sheet dismissed
- **WHEN** the user dismisses the system sheet
- **THEN** the error's `isUserCancellation` is true

#### Scenario: Setup error
- **WHEN** the error is `Misconfigured`
- **THEN** `isUserCancellation` is false

### Requirement: Error categories
`PasskeyError.category` SHALL return one of `UserCancelled`, `NoCredential`, `AlreadyExists`, `Unsupported`, `Setup` or `Unexpected`. The category set is closed: it MUST NOT gain entries in minor releases, and every new `PasskeyError` subtype MUST map to an existing category.

#### Scenario: Mapping of current errors
- **WHEN** `category` is read for each current subtype
- **THEN** `Cancelled` and `Interrupted` → `UserCancelled`, `NoCredentials` → `NoCredential`, `AlreadyRegistered` → `AlreadyExists`, `NotSupported` → `Unsupported`, `Misconfigured` and `InvalidOptions` → `Setup`, `Unknown` → `Unexpected`

#### Scenario: Exhaustive when without else
- **WHEN** an app writes `when (error.category)` covering all six entries without `else`
- **THEN** it keeps compiling after a minor release adds a `PasskeyError` subtype

### Requirement: Errors print their docs link
`toString()` of every `PasskeyError` that has a `docsUrl` SHALL include it, after the fix hint and check ID; errors without one print as before.

#### Scenario: Logging a setup error
- **WHEN** a `Misconfigured` error with check ID `AND-03` is converted to a string
- **THEN** the string contains its `message`, `fixHint`, `checkId` and `docsUrl`

#### Scenario: Logging a cancellation
- **WHEN** `Cancelled` is converted to a string
- **THEN** the output has no docs link
