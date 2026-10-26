# Proposal

## Why

Lexicon serves its own well-known files from its server and set the iOS entitlement by hand, so it used none of
PassKi's setup tooling: no `PasskeyDoctor`, no `passkiCheck`. Applying the Gradle plugin only registers tasks (it
generates or uploads nothing until `passkiSetup` runs), so `passkiCheck` already works as a CI gate for such apps, but
nothing says so, and path D ("ship it without the plugin") never mentions the Doctor either. A debug-build view of the
Doctor report and the last passkey error would also have shortened the AND-03 hunt. See
`docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, section 6.2.

## What Changes

- `passkiCheck` is specified and tested as safe for apps with their own server and files: applying the plugin adds no
  task to `assemble`, `build` or `check`, and `passkiCheck` needs only `rpId` (plus what it can read from the Android
  and Xcode projects).
- README path D gains two steps: log `PasskeyDoctor.run(rpId)` in debug builds, and gate CI with `passkiCheck` by
  applying the plugin with just `rpId`.
- New `PasskeyDebugPanel` composable in `passki-compose` for debug builds: the Doctor report, device capabilities and
  the last passkey error with its fix and docs link.
- Not in this change: a standalone (non-Gradle) CLI; the passki-web verifier already covers checking a domain without
  Gradle.
- Additive; not breaking.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gradle-check`: applying the plugin never changes the build; `passkiCheck` runs with only `rpId`.
- `compose-ui`: debug panel showing Doctor results, capabilities and the last error.

## Impact

- `passki-gradle-plugin`: functional tests (TestKit) for the "no build change" guarantee; possibly relaxing required
  inputs of `passkiCheck`.
- `passki-compose` commonMain: new composable (**Android**, **iOS**, **JVM**, **JS / Wasm** via Compose
  Multiplatform; on JVM and web the Doctor reports unsupported checks as skipped).
- README path D, `docs/setup.md`, CHANGELOG; ABI dump for `passki-compose`.
