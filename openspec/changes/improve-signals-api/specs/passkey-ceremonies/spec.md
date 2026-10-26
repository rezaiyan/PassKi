# Spec Delta

## MODIFIED Requirements

### Requirement: Unsupported platforms fail predictably
On the JVM desktop target, `Passkeys()` SHALL return an implementation whose capabilities are all false, whose every ceremony fails with `PasskeyError.NotSupported` naming the platform, and whose every signal returns `Success(SignalOutcome.Skipped(reason))`.

#### Scenario: Calling register on JVM desktop
- **WHEN** `register` is called on the JVM target
- **THEN** the result is `Failure(PasskeyError.NotSupported)` naming the platform

#### Scenario: Signal on JVM desktop
- **WHEN** `signals.unknownCredential(rpId, credentialId)` is called on the JVM target
- **THEN** the result is `Success(SignalOutcome.Skipped(reason))` and the reason names the platform
