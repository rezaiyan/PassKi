# Design

## Context

- PassKi's README already recommends checking responses with java-webauthn-server, webauthn4j, SimpleWebAuthn or
  py_webauthn. The plugin's `passkiSetup` writes webauthn4j server snippets and `build/passki/server-config.json`
  (`{"rpId", "origins", "appleAppIds"}`), the contract `passkiCheck` reads for SRV-01..03.
- `passki-demo` / `passki-web` run a demo relying party in TypeScript on SimpleWebAuthn (`demo-relying-party` spec).
- Lexicon's server (`PasskeyService`, `PasskeyChallenges`, `PasskeyWellKnownController`, `PasskeyConfig`) is a working
  reference on webauthn4j 0.30, including the error handling every app has to copy.
- `FakePasskeys` produces ES256 `none`-attestation responses that webauthn4j verifies today.

## Goals / Non-Goals

**Goals:**
- Delete most of a typical JVM backend's passkey code without hiding WebAuthn.
- Make the security-relevant defaults (challenge binding, UV, single handle per account) the only easy path.

**Non-Goals:**
- Persistence, HTTP routing or a web framework in the core (Spring auto-configuration is a later change).
- Attestation verification or authenticator allow-lists (Lexicon and most consumer apps use `none`).
- Kotlin Multiplatform server targets (Ktor native, Node); JVM only.

## Decisions

1. **ADR before code (task group 1).** Options compared on: API fit with the typed-result rules, maintenance and
   release cadence, licence (webauthn4j Apache-2.0, java-webauthn-server BSD-2-Clause), dependency size, Kotlin
   ergonomics, and who ships security fixes. Working assumption: **wrap webauthn4j**, since Lexicon, Spring Security
   and PassKi's own snippets already use it and its `FakePasskeys` compatibility is proven. The ADR may overturn it;
   the spec is library-agnostic.
2. **Typed results, no exceptions.** A sealed `PasskeyVerification<T>` (`Verified(value)` / `Failed(reason)`) mirrors
   the client's `PasskeyResult`. All parser and verifier exceptions (including `RuntimeException` from malformed
   input) are caught at the boundary and mapped to a reason; `Error`s are never caught.
3. **Plain data in and out.** `StoredCredential` / `NewCredential` are data classes with bytes and primitives; the app
   persists them (JPA, Exposed, JDBC) itself. A credential lookup is a `(credentialId) -> StoredCredential?` function.
4. **Options as JSON strings**, built with kotlinx-serialization from internal types, so they flow into the client's
   `register` / `signIn` unchanged and no framework JSON config matters.
5. **ChallengeStore interface** (`issue`, `consume`) with an in-memory default bounded by size and TTL (like Lexicon's
   Caffeine store, but without the Caffeine dependency). Multi-instance apps plug in Redis or a DB.
6. **Configuration shared with the plugin's contract.** The server-config JSON uses the exact keys `passkiCheck`
   reads, and the Android origin formula is the same code path as `passkiOrigin` (moved to a small shared JVM source or
   duplicated with a cross-test against the plugin).
7. **Clock injection** (`java.time.Clock`) for expiry, so tests don't sleep.

## Risks / Trade-offs

- [PassKi becomes responsible for server-side security] → ADR names the owner and response process; the artifact is
  marked alpha; fuzz-style tests feed malformed JSON and mutated `FakePasskeys` responses.
- [Wrapper lags the underlying library] → Pin and test against one minor version; Dependabot / Renovate for updates;
  the typed results don't expose the library's types, so upgrades stay internal.
- [Scope creep into a framework] → Non-goals above; Spring support only in a separate artifact and change.

## Migration Plan

New artifact; apps adopt it by replacing their service and controller internals. A migration guide uses Lexicon's
server as the before/after example.
