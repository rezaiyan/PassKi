# PassKi launch readiness

**Date:** 2026-10-26 · **Goal:** before PassKi is announced, a developer who has never seen it should get a passkey
working from the docs alone, without feeling overwhelmed, and should trust it enough to put it in front of their
users' accounts.

PassKi 0.1.0-alpha04 is on Maven Central but unannounced; Lexicon is the only adopter. Until the announcement,
breaking changes cost almost nothing, so this is the time to settle names and remove anything that doesn't earn its
place. Inputs: the [Lexicon case study](2026-10-25-passki-dx-lexicon-case-study.md), the
[DX roadmap](2026-10-25-passki-dx-roadmap.md), and an audit of the repository on 2026-10-26 (below).

## 1. What "developer friendly" means for PassKi

Libraries developers recommend (RevenueCat, Retrofit, OkHttp, Coil, Ktor) share a few habits. These are the bar:

1. **One obvious way.** The first screen of the README shows the one call most apps need. Options come later.
2. **Progressive disclosure.** Short README, then a quickstart per platform, then guides, then reference. Nobody has to
   read about PRF, signals or autofill to create their first passkey.
3. **Errors that say what to do.** Every failure names the cause, the fix and a docs page. One switch turns on
   readable logs (`Purchases.logLevel = DEBUG` is the model).
4. **Safe defaults, no surprises.** Works with no configuration; no telemetry, no network calls except the setup
   check you run yourself, no secrets in logs or `toString()`, no global side effects beyond documented start-up.
5. **Copy-paste docs that compile.** Every snippet is checked in CI against the released API.
6. **Testable without devices.** A fake that behaves like the real thing, including errors, and whose output passes
   real server verifiers.
7. **Predictable releases.** A written versioning and deprecation policy, a CHANGELOG with migration notes, a BOM,
   and a compatibility table that CI enforces.
8. **Trustworthy for security code.** A security policy, a statement of what the library does and doesn't protect,
   signed artifacts and kept-up dependencies.
9. **Easy to get help.** Issue templates that ask for what's needed to diagnose, a troubleshooting page, a sample app.

## 2. Audit against that bar

| Area | Practice | PassKi today | Gap | Planned in |
|---|---|---|---|---|
| API | Small core surface | About 20 public types in `com.alirezaiyan.passki`; internal JSON types gated by `@InternalPasskiApi` (opt-in level ERROR) | None | — |
| API | `explicitApi()` and ABI checks | All three libraries use `explicitApi()`; `checkLegacyAbi` dumps in `passki/api` | None | — |
| API | Names settled before launch | `userId` in signals is misleading; value classes for user handle / credential ID deferred to "before beta" | Decided: identifiers stay `String`; signals return `SignalOutcome` | `settle-core-api` (decisions), `improve-signals-api` (`SignalOutcome`) |
| API | Threading and cancellation contract | Cancellation documented for autofill only; nothing says calls are main-safe or which thread results arrive on | Contract in KDoc and docs, tested per platform; one modal request at a time | `settle-core-api` |
| API | Result helpers that remove app code | `onSuccess` / `onFailure` exist; Lexicon cast errors needlessly and missed `docsUrl` | Add only helpers that shorten real call sites | `add-result-and-error-helpers` |
| Errors | Fix hint, check ID, docs link on every setup error | Yes, plus catalog shared by Doctor, `passkiCheck` and passki-web | AND-03 evidence and web-page detection done (alpha05) | `improve-setup-diagnostics` |
| Errors | One logging switch, redacted | None yet | Logging with a redaction test | `add-passkey-logging` |
| Docs | README a newcomer can scan | 602 lines, 13-entry contents list, four "Get started" paths, stale `alpha03` badge | Rewrite under ~150 lines; move the rest to pages; CI length limit | `add-developer-docs-pack` 3 |
| Docs | Quickstart per platform, guides, troubleshooting | Setup, platforms, compose, autofill, check pages; no guides | Quickstart page, guides and symptom-first troubleshooting | `add-developer-docs-pack` 2, 3.5 |
| Docs | Hosted API reference with samples | Dokka builds locally only | Pages hosting, `@sample` links | `harden-release-process` 3, 4.5 |
| Docs | Snippets compile in CI | `ci/doc-snippets.py` checks README and `docs/*.md` | Subfolders and server snippets | `add-developer-docs-pack` 1 |
| Docs | Docs readable by AI assistants | None | `llms.txt` on passki-web listing the pages | `add-developer-docs-pack` 5 |
| API | Every enum and sealed type labelled open or closed | `PasskeyError` KDoc asks for `else`; others unlabelled | Labels plus a CI check | `settle-core-api` 3 |
| Testing | Fake for app tests | `passki-test` `FakePasskeys`, responses pass webauthn4j | Platform-name errors, blocking JVM view | `improve-test-fakes` |
| Compatibility | Stated and enforced toolchain floor | README requirements table; `ci/consumers` builds a Kotlin 2.2.21 KMP app and an AGP 9.1.1 Android app | None | — |
| Compatibility | Works under R8 / minified release builds | Sample has `isMinifyEnabled = false`; no consumer rules; CI consumer builds debug only | Minified release build of the Android consumer in CI; add consumer rules if it fails | `harden-release-process` 5 |
| Compatibility | Start-up can be turned off | `androidx.startup` with `PasskiAndroid.init(application)` documented | None | — |
| Release | Versioning and deprecation policy users can read | None in README or docs; planned for `CONTRIBUTING.md` | `docs/versioning.md`, linked from the README and `CONTRIBUTING.md` | `harden-release-process` 4.4 |
| Release | BOM, verified publishing, current badge | None | BOM, `verify-published`, Central badge | `harden-release-process` 1, 2, 4.1 |
| Security | `SECURITY.md` and private reporting | Missing | Add | `harden-release-process` 4.2 |
| Security | What PassKi protects and what stays the server's job | Scattered ("your server checks the response") | One short "Security model" page: PassKi never verifies, never stores, makes network calls only when `PasskeyDoctor` runs, sends no telemetry; a CI check keeps the network claim true | `add-developer-docs-pack` 4 |
| Security | Dependency updates | No Renovate or Dependabot | Renovate with grouped updates, holding the Kotlin 2.2 floor | `harden-release-process` 4.7 |
| Community | Issue and PR templates | Missing | Bug template asking for platform, image, PassKi version, check ID, Doctor report | `harden-release-process` 4.3 |
| Community | Code of conduct | Missing | Contributor Covenant | `harden-release-process` 4.6 |
| Community | Sample app | Android, iOS, desktop and web samples with a demo server | None | — |

## 3. Launch gate

Everything here ships before the announcement. Items are ordered so each release is usable on its own.

1. **Diagnostics** (`improve-setup-diagnostics`) — done except the on-device check (task 5.1).
2. **Settle the API** — `settle-core-api` (identifiers stay `String`, threading and cancellation contract, one modal
   request at a time, open / closed labels), `improve-signals-api` (`userHandle`, `SignalOutcome`, `syncAccount`) and
   `add-result-and-error-helpers` (only helpers that shorten real call sites). One alpha with every breaking change in
   it.
3. **Logging** (`add-passkey-logging`) — one switch, redacted, with the Timber / Kermit / Crashlytics examples.
4. **Docs** (`add-developer-docs-pack`) — the short README, the quickstart, the guides, the troubleshooting page, the
   security model page and `llms.txt`.
5. **Release and trust** (`harden-release-process`) — BOM, `verify-published`, hosted reference, Central
   badge, `SECURITY.md`, templates, a user-facing versioning policy, Renovate, code of conduct, and a minified release
   build in CI.
6. **The launch test** (section 4). Fix what it finds, then announce.

## 4. Launch test

Two or three developers who haven't seen PassKi integrate it into a KMP app with their own server, using only the
public docs. For each, record:

- time from adding the dependency to the first passkey created and verified on Android, iOS and web;
- every place they stopped, searched or asked;
- every error they saw and whether its message alone was enough.

Target: under one hour per platform, and no question that the docs or an error message couldn't have answered. Each
stop becomes a docs or error fix before the announcement.

## 5. After launch

These are useful, but each one adds API or a new artifact. Launching without them keeps the first impression small.

- `add-prefer-immediate-sign-in`
- `add-sign-in-controller`
- `improve-own-server-setup-tooling` (the `passkiCheck` guarantee could move earlier if the launch test shows
  own-server apps struggle)
- `improve-test-fakes`
- `add-passki-server`, after its decision gate
- Supply-chain extras: SBOM, build provenance, OpenSSF Scorecard
