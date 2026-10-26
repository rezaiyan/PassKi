# PassKi DX roadmap (OpenSpec changes)

**Date:** 2026-10-25 · **Source:** [Lexicon case study](2026-10-25-passki-dx-lexicon-case-study.md), backlog in
section 11.

Every backlog item is planned as one of 12 OpenSpec changes in `openspec/changes/`. Each has a proposal, delta specs
(except the docs-only change), a design and a task list. Start one with `/opsx:apply <change>`.

## Order

Updated 2026-10-26: PassKi isn't announced yet, so the changes are split into a launch gate and work for after the
announcement. The reasons, the best-practice audit and the launch test are in
[launch readiness](2026-10-26-passki-launch-readiness.md).

### Before launch

| Step | Change | Covers (case study §) | Spec deltas | Breaking? |
|---|---|---|---|---|
| 1 | `improve-setup-diagnostics` (12/13 done) | 6.1, 6.3, 6.4: AND-03 evidence and emulator note, HTML at well-known paths, shared-apex warning | `passkey-errors`, `setup-check-catalog`, `gradle-setup` | No |
| 2 | `settle-core-api` | Launch readiness §2: identifiers stay `String`, threading and cancellation contract, one modal request at a time, open / closed labels | `passkey-ceremonies` | Behaviour: second modal request fails |
| 2 | `improve-signals-api` | 4.3, 4.7, plus `SignalOutcome` | `passkey-signals`, `passkey-ceremonies` | Return types; named arguments |
| 2 | `add-result-and-error-helpers` | 4.2, 4.4, 4.6, 4.5 (`docsUrl` in `toString`), 4.10 | `passkey-errors`, `passkey-ceremonies` | No |
| 3 | `add-passkey-logging` | 4.5 | `passkey-logging` (new) | No |
| 4 | `add-developer-docs-pack`: guides, short README, quickstart, security model page, `llms.txt` | 3, 5.2, 5.3, 6.3, 6.4, 7 | none (`skip_specs`) | No |
| 5 | `harden-release-process`: Central verification, BOM, hosted reference, badge, `SECURITY.md`, templates, versioning policy, code of conduct, Renovate, minified release build | 8 | `release-process` (new) | No |
| 6 | Launch test with outside developers | launch readiness §4 | — | — |

### After launch

| Change | Covers (case study §) | Spec deltas | Breaking? |
|---|---|---|---|
| `add-prefer-immediate-sign-in` | 4.1 | `passkey-ceremonies`, `test-fakes` | No |
| `add-sign-in-controller` | 4.8, 4.9 | `passkey-ceremonies`, `compose-ui` | No |
| `improve-own-server-setup-tooling` | 6.2 | `gradle-check`, `compose-ui` | No |
| `improve-test-fakes` | 4.10 | `test-fakes` | No |
| `add-passki-server` (decision gate first) | 5, 5.1 | `relying-party-server` (new) | New artifact |

## Dependencies between changes

- `add-passkey-logging` uses `PasskeyError.category` from `add-result-and-error-helpers`.
- `improve-own-server-setup-tooling` reuses the logging failure hook for the debug panel's last error (or adds the
  hook itself if it lands first).
- `add-prefer-immediate-sign-in` relies on the web capability probe in `4571df6` (alpha05).
- `settle-core-api` and `improve-signals-api` touch different requirements of `passkey-ceremonies` and can land in
  either order, in the same alpha. `SignalOutcome` is "Closed:" per `settle-core-api`'s labelling rule.
- Tasks in steps 2–3 that say "README section" mean the page that holds that section once
  `add-developer-docs-pack` has moved it (design decision 6 there).
- `add-passki-server` needs `SECURITY.md` (`harden-release-process`) and its ADR approved before code.
- Docs pages from `add-developer-docs-pack` are updated by each later API change that affects them; the snippet check
  enforces it.

## Changed from the case study during planning

- **Check-only Gradle mode dropped.** Applying the plugin already only registers tasks; `improve-own-server-setup-tooling`
  specifies and tests that guarantee and documents `passkiCheck` for apps with their own server instead. A standalone
  CLI is left out: the passki-web verifier covers non-Gradle projects.
- **`FakePasskeys(immediateSignIn = false)` already exists.** Only the `PreferImmediate` modelling is new.
- **Base64url validation of signal arguments already exists.** The signals change improves the error text instead.
- **Docs snippets in subfolders weren't checked.** `ci/doc-snippets.py` scans only `docs/*.md`; the docs change
  extends it and adds a server source set.

## Deferred (not planned yet)

- Nothing breaking. `SignalOutcome` moved into `improve-signals-api`; `UserHandle` / `CredentialId` value classes
  were decided against in `settle-core-api` (design decision 1).
- `passki-server-spring` auto-configuration (after `passki-server` ships in one alpha).
- Lexicon-side follow-ups (case study section 12) live in the Lexicon repository.
