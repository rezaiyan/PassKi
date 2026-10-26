# Spec Delta

## ADDED Requirements

### Requirement: Prefer-immediate modelling
`FakePasskeys` SHALL implement `SignInMode.PreferImmediate` with its `immediateSignIn` flag: when the flag is true it behaves like `ImmediateOnly`, and when false like `Modal`, so apps can test both branches of an optional passkey button.

#### Scenario: Testing the web fallback
- **WHEN** a test sets `immediateSignIn = false` and calls `signIn(options, PreferImmediate)` with a stored passkey
- **THEN** the sign-in succeeds as a modal sign-in

#### Scenario: Testing the first-time user
- **WHEN** a test keeps `immediateSignIn = true`, stores no passkey and calls `signIn(options, PreferImmediate)`
- **THEN** the result is `Failure(NoCredentials)`
