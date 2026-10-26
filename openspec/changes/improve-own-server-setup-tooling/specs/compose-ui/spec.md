# Spec Delta

## ADDED Requirements

### Requirement: Debug panel
`passki-compose` SHALL provide `PasskeyDebugPanel(rpId, modifier)`, which runs `PasskeyDoctor.run(rpId)` when shown and lists each check with its status, message, fix hint and catalog ID, followed by the device's `PasskeyCapabilities` and a way to re-run.

#### Scenario: Broken asset links in a debug build
- **WHEN** the panel is shown on Android for an RP ID whose `assetlinks.json` lacks the app's fingerprint
- **THEN** it shows `AND-03` as failed with the fingerprint to add

#### Scenario: Unsupported platform
- **WHEN** the panel is shown on JVM desktop
- **THEN** it shows the Doctor's skipped checks and all capabilities as false, without crashing

### Requirement: Last passkey error in the debug panel
`PasskeyDebugPanel` SHALL also show the most recent failed PassKi operation in the process (operation, error, check ID, fix hint and docs link), or a line saying there was none. User cancellations MUST NOT replace the last error.

#### Scenario: After a failed sign-in
- **WHEN** a sign-in failed with `Misconfigured` and the developer opens the panel
- **THEN** the panel shows that error with its docs link

#### Scenario: After a cancelled sheet
- **WHEN** the last operation was cancelled by the user
- **THEN** the panel still shows the previous real error, or "no errors yet"
