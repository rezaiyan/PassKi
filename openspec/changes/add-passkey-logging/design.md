# Design

## Context

- All platform implementations funnel failures through `internal/passkeyCatching` (commonMain), which maps throwables
  to `PasskeyError`. That is the natural single place for outcome logging.
- There is no global PassKi configuration object today; Android has `PasskiInitializer` for its context.
- The `passkey-errors` spec already requires secrets to stay out of `toString()` (`SignInCredential.Password`,
  `PrfResults`), and results print the JSON length, not the JSON.
- `add-result-and-error-helpers` introduces `PasskeyError.category`, used here to decide what is logged at `Error`.

## Goals / Non-Goals

**Goals:**
- Zero-code visibility of setup errors; one switch for full traces while developing.

**Non-Goals:**
- Structured logging or metrics/analytics events.
- Log levels per module.
- Remote reporting (apps route the handler to their crash reporter themselves).

## Decisions

1. **A process-wide `PassKi` object** (`PassKi.logLevel`, `PassKi.logHandler`) rather than a parameter on
   `Passkeys()`. Logging is a process concern, the doctor and signals log too, and RevenueCat uses the same shape.
   Alternative: `Passkeys(config = …)`, rejected because it splits configuration across instances and the
   platform-free `Passkeys()` call is the best part of the API.
2. **Thread-safety:** both properties are backed by atomics (`kotlinx.atomicfu` or `AtomicReference` per platform via
   `expect`), read once per log call.
3. **`PasskiLogHandler` is a `fun interface`** `(level, tag, message, throwable?) -> Unit`, so Kotlin callers pass a
   lambda and Swift callers can implement it.
4. **Where logging happens:** outcome logging in `passkeyCatching` plus a thin wrapper around each public operation
   for start/outcome at `Debug`. Platform mappers add the raw platform error type to a debug line before mapping.
5. **Redaction helpers** (`shortId`, `lengthOf`) live next to the logger, and every log call site uses them; a test
   scans debug output from a fake round trip for the challenge, user handle and JSON.
6. **`Error` logs once per failure,** not once per process: each failed call is a separate event developers need to see.

## Risks / Trade-offs

- [Noisy logcat in apps that already log PassKi errors] → Apps can set `logLevel = None`; CHANGELOG says so.
- [Global mutable state in tests] → `passki-test` documents resetting `PassKi` in `@AfterTest`; FakePasskeys does not
  change the level itself.
- [Secrets leak through a future log line] → Redaction test over a full debug trace runs in CI.

## Migration Plan

Additive. Apps with their own `Misconfigured` log line can delete it or set `logLevel = None` to avoid duplicates.
