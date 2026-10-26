# Spec Delta

## ADDED Requirements

### Requirement: Applying the plugin changes nothing in the build
Applying `com.alirezaiyan.passki` SHALL only register PassKi tasks and the `passki { }` extension: it MUST NOT add dependencies to `assemble`, `build` or `check`, change Android manifests or resources, or modify Xcode projects unless the developer runs `passkiSetup`.

#### Scenario: App with its own well-known files
- **WHEN** an app applies the plugin with only `passki { rpId = "example.com" }` and runs `./gradlew build`
- **THEN** no PassKi task runs and the build outputs are identical to a build without the plugin

### Requirement: Check with only an RP ID
`passkiCheck` SHALL run with `rpId` as its only configured value, taking the Android package and signing fingerprints from the Android application module and the team and bundle ID from the Xcode project when present, and reporting checks it can't run as skipped with the missing setting named.

#### Scenario: CI gate for a bring-your-own-server app
- **WHEN** CI runs `./gradlew passkiCheck` in an app module whose `passki { }` block sets only `rpId`
- **THEN** the hosted-file, entitlement and signing checks run and the task fails if any of them fails

#### Scenario: No Xcode project found
- **WHEN** no Xcode project is configured or found
- **THEN** the iOS entitlement checks are skipped with a reason naming `passki { apple { xcodeProject = … } }`
