# Tasks

## 1. Decision gate

- [ ] 1.1 Write `docs/decisions/0001-passki-server.md` comparing webauthn4j, java-webauthn-server and "document only" on the criteria in design.md, with a recommendation and the security owner; verify the maintainer has approved it in the PR before any group below starts
- [ ] 1.2 If the ADR picks "document only", remove the ceremony requirements from `specs/relying-party-server/spec.md` and the matching tasks below; verify `openspec validate add-passki-server --strict` passes after the edit
- [ ] 1.3 Confirm `SECURITY.md` exists (from `harden-release-process`); verify it lists `passki-server` as supported

## 2. Module and configuration

- [ ] 2.1 Add the `passki-server` JVM module (explicit API, ABI validation, Java 11 bytecode, publishing); verify `./gradlew :passki-server:build` and `python3 ci/check-jvm-bytecode.py` pass
- [ ] 2.2 Implement `PasskiRelyingParty` configuration with derived Android origins and construction-time validation; verify unit tests including a cross-test that origins equal `passkiOrigin` output for the same fingerprint

## 3. Options and challenges

- [ ] 3.1 Implement creation and request options JSON; verify tests parse them with `passki`'s options parser and assert every field in the spec
- [ ] 3.2 Implement `ChallengeStore` and the bounded in-memory default with an injected `Clock`; verify tests for single use, expiry, wrong ceremony, wrong user and the size bound

## 4. Verification

- [ ] 4.1 Implement registration verification with typed failures; verify `FakePasskeys` round trips for an https origin, an iOS-style origin and an Android origin, plus `Malformed`, `Expired`, `WrongUser` and `AlreadyRegistered` cases
- [ ] 4.2 Implement sign-in verification with a lookup function; verify round trips and `UnknownCredential`, `WrongUser`, `BadSignature` (mutated signature) cases
- [ ] 4.3 Add a malformed-input test that feeds truncated, mutated and random JSON to both verifiers; verify no exception escapes over at least 1,000 generated inputs

## 5. Well-known files and server config

- [ ] 5.1 Render `assetlinks.json`, `apple-app-site-association` and server-config JSON; verify the plugin's `WellKnownVerifier` accepts the rendered files in a test, and `passkiCheck` SRV-01/02 pass against a local server serving the config

## 6. Docs and release

- [ ] 6.1 Add a `passki-server` README section and a Spring Boot example in `docs/guides/verify-on-server.md` using Lexicon's endpoints as the shape; verify the snippets compile as `kotlin server` blocks with `python3 ci/doc-snippets.py`
- [ ] 6.2 Add `passki-server` to the release workflow, the published-coordinates list and the BOM; verify a dry-run release builds and signs it
- [ ] 6.3 Add a CHANGELOG entry; verify it marks the artifact alpha and links the ADR

## Workflow follow-up

- Open a separate change for `passki-server-spring` (auto-configuration, well-known endpoints, `ChallengeStore` bean) once this API has shipped in one alpha.
