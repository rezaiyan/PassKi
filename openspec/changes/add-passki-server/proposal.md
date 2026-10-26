# Proposal

## Why

PassKi stops at "your server checks the response", and that's where most of the work and the security risk sit.
Lexicon's server needed 657 lines (options, verification, challenges, DTOs, well-known files, Android origins, account
labels) plus ~40 lines of config. Parts of it are easy to get subtly wrong: a 10-argument positional credential record,
parsers that throw `NullPointerException` on bad input, challenges that must be bound to the user, the
`android:apk-key-hash:` formula. SimpleWebAuthn shows the shape developers want: a browser package and a server package
that share one contract. See `docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, sections 5 and 5.1.

## What Changes

- **Decision gate first:** a short architecture decision record (ADR) in `docs/decisions/` compares building on
  webauthn4j, building on Yubico's java-webauthn-server, and only documenting those libraries. It also settles security
  ownership (`SECURITY.md` from `harden-release-process` is a precondition). If the ADR picks "document only", the
  ceremony requirements are removed from this change before implementation and only the PassKi-specific pieces
  (origins, well-known files, server config) ship.
- New JVM artifact `passki-server` with `PasskiRelyingParty`:
  - configuration from RP ID, RP name, Android apps (package + SHA-256 fingerprints) and extra origins, with
    origins derived for Android;
  - creation and request options as WebAuthn JSON;
  - single-use challenges bound to ceremony and user, with a pluggable `ChallengeStore` and an in-memory default;
  - registration and sign-in verification that never throw on bad input and return typed failures;
  - `assetlinks.json`, `apple-app-site-association` and the `passkiCheck` server-config JSON from the same config.
- Docs: a `passki-server` README section and a Spring example; `FakePasskeys` round-trip tests for web, iOS and Android
  origins in the artifact's own suite.
- Not in this change: `passki-server-spring` auto-configuration (a later change once this API is stable).
- New artifact; no change to existing artifacts' APIs.

## Capabilities

### New Capabilities

- `relying-party-server`: server-side relying-party behaviour PassKi provides on the JVM: configuration and origins,
  options, challenges, verification results and well-known files.

### Modified Capabilities

None.

## Impact

- New Gradle module `passki-server` (**JVM only**, Java 11 bytecode like the other libraries); no effect on Android,
  iOS, JS or Wasm targets of `passki`.
- New dependency for adopters of `passki-server` only: the verification library chosen by the ADR.
- `passki-test` used in `passki-server` tests; release workflow, BOM (`harden-release-process`) and ABI validation
  include the new artifact.
- README, `docs/guides/verify-on-server.md` (`add-developer-docs-pack`), CHANGELOG.
