# Proposal

## Why

An AND-03 in a release build is invisible unless the app writes its own log line: the user sees "Passkeys aren't
working right now" and the developer sees nothing. Lexicon wrote that line by hand (and got it slightly wrong). SDKs
developers praise, RevenueCat first, ship a log level and a pluggable log handler so setup problems show up in logcat
or the crash reporter without any app code. See `docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, section 4.5.

## What Changes

- New `PassKi` configuration object with `logLevel` (`None`, `Error`, `Warn`, `Info`, `Debug`; default `Error`) and
  `logHandler` (default: the platform log).
- At `Error`, PassKi logs every failure that has a `fixHint` (setup and options errors) and every `Unknown`, once per
  failure, with message, check ID, fix and docs link.
- At `Debug`, PassKi also logs each ceremony: operation, mode, RP ID, capabilities used, the platform error type and
  the `PasskeyError` it was mapped to.
- Logs never contain challenges, full credential IDs, user handles, response JSON, passwords or PRF / large-blob data.
- `CONTRIBUTING.md` gains the logging rule.
- Additive; not breaking.

## Capabilities

### New Capabilities

- `passkey-logging`: what PassKi logs, at which level, where it goes and what it must never contain.

### Modified Capabilities

None.

## Impact

- `passki` commonMain: new `PassKi` object, `PasskiLogLevel`, `PasskiLogHandler`; calls from the shared
  `passkeyCatching` path so every platform logs the same events.
- Default handlers: **Android** `android.util.Log` (tag `PassKi`), **iOS** `NSLog`, **JS / Wasm** `console`, **JVM**
  `System.err`.
- `passki-test`: `FakePasskeys` logs through the same path so apps can assert logs in tests.
- ABI dumps, README "Logging" section, CHANGELOG.
