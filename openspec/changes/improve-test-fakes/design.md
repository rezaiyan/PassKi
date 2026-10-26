# Design

## Context

- `WebPasskeys.kt` maps rejected WebAuthn calls by DOM error name (`Throwable.toPasskeyError(conditionalCreate,
  immediate)`), with web-specific hint text for `SecurityError`.
- `AndroidErrorMapper` maps Credential Manager DOM error types (`SecurityError`, `InvalidStateError`, …) with Android
  hints (asset links); iOS maps `ASAuthorizationError` codes.
- `passki-test` has only `commonMain` / `commonTest` / `jvmTest`; `FakePasskeys` operations are `suspend`, and
  `failNextWith(error)` queues ready-made errors ahead of capability flags.

## Goals / Non-Goals

**Goals:**
- Tests can exercise the real name → error rules, including mode-dependent ones (`NotAllowedError`).
- Server tests on the JVM call the fake without coroutine plumbing.

**Non-Goals:**
- Simulating iOS `ASAuthorizationError` codes or Android exception classes (DOM names cover the shared WebAuthn
  semantics; platform-specific mappings stay tested in their own source sets).
- A blocking view on Android, iOS or JS.

## Decisions

1. **Extract one DOM-error table into commonMain.** `domErrorToPasskeyError(name, message, operation, mode, hints)`
   holds the rules; `hints` supplies platform text (web RP ID hint, Android asset-links hint, fake: the web hint since
   fakes use http(s) or Android origins). The web mapper becomes a thin call; the Android DOM branch uses it for the
   shared names and keeps its provider-specific cases. Marked `@InternalPasskiApi` so `passki-test` can call it.
   Alternative: duplicate the table in `passki-test`, rejected because drift is exactly the bug this is meant to catch.
2. **Queue entries become a sealed type** (`Ready(error)` / `Platform(name, message)`) so both queue methods keep one
   FIFO order.
3. **Blocking view uses `runBlocking`** in a new `passki-test` `jvmMain` source set; it is a separate class
   (`BlockingFakePasskeys`) rather than extra members, so common code doesn't see JVM-only API.

4. **Held sheet uses the shared modal gate.** `holdNextSheet()` returns a `FakeSheet` handle; the fake's modal
   ceremonies run through its own `ModalGate` instance (not the process-wide one, so tests stay independent), and a
   held ceremony keeps the gate until `finish()` or `dismiss()`. Without a held sheet the fake completes at once, as
   today. Autofill on the fake waits for the gate like the platforms do.

## Risks / Trade-offs

- [Refactoring the web and Android mappers risks behaviour changes] → Existing mapper tests run unchanged; add a
  table-driven test asserting the shared table for each name × mode.
- [`runBlocking` inside a coroutine test deadlocks] → KDoc says the view is for non-coroutine tests only.

## Migration Plan

Additive. Tests using `failNextWith(PasskeyError…)` keep working.
