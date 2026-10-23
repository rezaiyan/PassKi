# setup-check-catalog Specification

## Purpose
One shared catalog of setup checks with stable IDs, used by the on-device doctor, the Gradle plugin's `passkiCheck` and the passki-web dashboard, so the same problem has the same ID and fix text everywhere.

## Requirements

### Requirement: Single source of truth
The catalog SHALL live in `docs/checks/catalog.json`, list every check with `id`, `platform`, `severity` (`error` or `warning`), `title` and `fix`, and have one `docs/checks/<id>.md` page per check.

#### Scenario: Adding a check
- **WHEN** a new check is added to any consumer
- **THEN** it is added to `catalog.json` with a docs page first, and the consumer uses its catalog ID

### Requirement: Stable IDs
Check IDs MUST be stable once published and SHALL use the prefixes `AND-` (Android association), `IOS-` (Apple association and entitlements), `SRV-` (WebAuthn server), `CFG-` (configuration) and `DEV-` (device state).

#### Scenario: Same problem in two tools
- **WHEN** both `PasskeyDoctor` and `passkiCheck` detect a missing signing fingerprint
- **THEN** both report it as `AND-03`

### Requirement: Consumers stay in sync
Each consumer's set of IDs MUST be a subset of the catalog, enforced by tests.

#### Scenario: Consumer uses an unknown ID
- **WHEN** a consumer reports an ID missing from `catalog.json`
- **THEN** that consumer's catalog sync test fails

### Requirement: Fix placeholders
Catalog fix texts MAY contain placeholders (`<rpId>`, `<package>`, `<fingerprint>`, `<teamId>`, `<bundleId>`, `<path>`) that consumers SHALL fill with concrete values when known.

#### Scenario: Concrete fix in passkiCheck
- **WHEN** `passkiCheck` reports `AND-03` for package `com.example.app`
- **THEN** the printed fix names `com.example.app` and the actual fingerprint instead of the placeholders
