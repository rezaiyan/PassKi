# Design

## Context

- `PasskiPlugin.apply` creates the extension and registers `passkiSetup`, `passkiCheck`, `passkiOrigin` (and, with the
  Android application plugin, `passkiFingerprints`, `passkiWellKnown`, `passkiVerify`). It wires nothing into the
  build lifecycle; `gradle-check` "Network checks are opt-in" already keeps checks out of `check`.
- `PasskeyDoctor.run(rpId)` (commonMain `expect object`) returns a `DoctorReport` of `DoctorCheck`s.
- The case study's "check-only mode" proposal turned out to be unnecessary: the gap is documentation and a guarantee
  that stays true, not a new mode.

## Goals / Non-Goals

**Goals:**
- An app with its own server can adopt `passkiCheck` and the Doctor without adopting generation.
- One screen in a debug build that answers "why don't passkeys work here?".

**Non-Goals:**
- A standalone CLI or npm package (the passki-web verifier covers non-Gradle projects).
- Shipping the panel in release builds; apps decide where to show it.

## Decisions

1. **Guarantee by TestKit test, not by code change.** A functional test applies the plugin to an Android app fixture
   with only `rpId`, runs `build --dry-run` and asserts no `passki*` task is scheduled, and diffs merged manifests with
   and without the plugin.
2. **Relax `passkiCheck` inputs where needed.** Any input that is required today but derivable (package, fingerprints,
   team ID) becomes optional with a skip reason, matching the existing iOS-only mode behaviour.
3. **Last error recorded by the logging path.** `add-passkey-logging` already observes every failure in
   `passkeyCatching`; it also keeps the last non-cancellation failure in an internal holder the panel reads. If logging
   lands later, this change adds the holder itself at the same point. No new public API in core.
4. **Panel name `PasskeyDebugPanel`**, a plain composable (not an overlay), so apps can put it in a debug menu, a
   bottom sheet or a settings screen.

## Risks / Trade-offs

- [Developers ship the panel in production] → KDoc and README say "debug builds only"; it reveals only public setup
  data (fingerprints, RP ID), never secrets.
- [Doctor network calls from the panel] → Run only when shown and on re-run; results cached for the panel's lifetime.

## Migration Plan

Additive.
