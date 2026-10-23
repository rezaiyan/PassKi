# setup-doctor Specification

## Purpose
`PasskeyDoctor` runs on the device in debug builds and checks platform support, app configuration and the relying party's well-known association files, so a broken setup shows up as a clear list of failures to fix.

## Requirements

### Requirement: On-device diagnosis
`PasskeyDoctor.run(rpId)` SHALL run every applicable check for the bare-domain `rpId` and return a `DoctorReport` with one `DoctorCheck` per item in run order; it MUST NOT throw for setup problems.

#### Scenario: Missing fingerprint on Android
- **WHEN** the app's signing certificate is not listed in `https://<rpId>/.well-known/assetlinks.json`
- **THEN** the report contains a `Fail` check with id `AND-03` and a fix hint naming the fingerprint to add

#### Scenario: Invalid RP ID
- **WHEN** `rpId` is not a bare domain
- **THEN** the report contains a `Fail` check with id `CFG-01`

### Requirement: Check identity and status
Each `DoctorCheck` SHALL carry a name, a `DoctorStatus` (`Pass`, `Warn`, `Fail`, `Skipped`), a one-sentence message, an optional fix hint and, when the check has a catalog entry, the stable catalog `id`. The on-device doctor MAY report only `AND-01`, `AND-02`, `AND-03`, `AND-06`, `IOS-01`..`IOS-04`, `CFG-01` and `DEV-01`..`DEV-03`.

#### Scenario: PassKi not initialised on Android
- **WHEN** the app removed PassKi's `androidx.startup` initializer and did not call `PasskiAndroid.init`
- **THEN** the report contains a `Fail` check with id `AND-06`

### Requirement: Report summary
`DoctorReport` SHALL expose `isHealthy` (no `Fail` checks), `failures` and `warnings`, and its `toString` SHALL be a multi-line, secret-free summary using ASCII markers `[PASS]`, `[WARN]`, `[FAIL]`, `[SKIP]` with indented `fix:` lines.

#### Scenario: Healthy report with warnings
- **WHEN** every check passes except a stale Apple CDN copy (`Warn`)
- **THEN** `isHealthy` is true and `warnings` contains that check

### Requirement: Unsupported platforms
On platforms other than Android and iOS, `PasskeyDoctor.run` SHALL return a report holding a single `Skipped` check.

#### Scenario: Running on JVM desktop
- **WHEN** `PasskeyDoctor.run("example.com")` is called on the JVM target
- **THEN** the report has exactly one check with status `Skipped`
