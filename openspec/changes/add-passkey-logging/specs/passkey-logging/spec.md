# Spec Delta

## Purpose

Lets developers see why passkeys fail without writing log code: PassKi logs setup problems by default and explains
every ceremony in debug mode, through a handler the app can redirect, without ever logging secrets.

## ADDED Requirements

### Requirement: Configurable log level
`PassKi.logLevel` SHALL accept `None`, `Error`, `Warn`, `Info` and `Debug`, default to `Error`, and take effect for calls made after it is set. At `None`, PassKi MUST NOT log anything.

#### Scenario: Default level
- **WHEN** an app never sets `PassKi.logLevel`
- **THEN** setup errors are logged and ceremony details are not

#### Scenario: Logging off
- **WHEN** `PassKi.logLevel = PasskiLogLevel.None`
- **THEN** no PassKi log line is emitted on any platform

### Requirement: Pluggable log handler
`PassKi.logHandler` SHALL receive every log entry as level, tag `PassKi`, message and optional throwable, replacing the default platform output while set. The default handler MUST write to the platform log: Android `Log`, iOS `NSLog`, browser `console`, JVM standard error.

#### Scenario: Routing to Crashlytics
- **WHEN** an app sets `PassKi.logHandler = { level, tag, message, throwable -> crashlytics.log("$tag $message") }`
- **THEN** PassKi log entries reach that lambda and nothing is written to the platform log

#### Scenario: Default output on Android
- **WHEN** no handler is set and a setup error occurs on Android
- **THEN** a line with tag `PassKi` appears in logcat at error priority

### Requirement: Setup failures logged by default
At `Error` and above, every failed operation whose error has a `fixHint`, and every `Unknown`, SHALL be logged once at error level with the operation, the error message, check ID, fix hint and docs link. Errors with category `UserCancelled`, `NoCredential` or `AlreadyExists` MUST NOT be logged at `Error`.

#### Scenario: AND-03 in a release build
- **WHEN** a sign-in fails with `Misconfigured` check `AND-03` at the default level
- **THEN** one error line names the operation, `AND-03`, the fix and the docs URL

#### Scenario: User closes the sheet
- **WHEN** a sign-in fails with `Cancelled`
- **THEN** nothing is logged at `Error`

### Requirement: Ceremony tracing in debug
At `Debug`, PassKi SHALL log each operation's start and outcome: operation, sign-in mode, RP ID, the capability flags it relied on, the platform error type when one occurred and the `PasskeyError` it was mapped to.

#### Scenario: Tracing an immediate sign-in that falls back
- **WHEN** `PassKi.logLevel = PasskiLogLevel.Debug` and a sign-in runs
- **THEN** the log shows the mode used, the RP ID and how any platform error was mapped

### Requirement: No secrets in logs
PassKi log output MUST NOT contain challenges, user handles, response or options JSON, passwords, PRF outputs or large-blob data, and MUST show credential IDs only as their first 8 characters followed by `…`.

#### Scenario: Debug trace of a sign-in
- **WHEN** a sign-in succeeds at `Debug`
- **THEN** the log line shows a shortened credential ID and the JSON length, and no challenge or user handle

#### Scenario: Password credential
- **WHEN** `signInOrPassword` returns a password at `Debug`
- **THEN** the log shows the credential type and username, never the password
