# Proposal

## Why

alpha04 was tagged and uploaded but not released, so Lexicon's CI couldn't resolve it; the workflow now releases
automatically (`ed89c7c`), but nothing checks that a release actually resolves, the README badge still says alpha03,
and a finished fix (`4571df6`) sits unreleased while apps carry workarounds. For an authentication library, adopters
also expect a security policy, a BOM to keep five artifacts in step, and a hosted API reference. See
`docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, section 8.

## What Changes

- The release workflow waits until every published artifact (four libraries, the Gradle plugin and its marker) resolves
  from `repo1.maven.org` and fails the run if it doesn't within the time limit.
- New `passki-bom` artifact; the README Install section shows `platform("com.alirezaiyan.passki:passki-bom:…")`.
- Each release publishes the Dokka API reference at a stable, versioned public URL, with a `latest` alias, linked from
  the README.
- README version badge reads the latest version from Maven Central instead of a hardcoded value.
- `SECURITY.md` (private reporting, supported versions, response time) and GitHub issue / PR templates (bug template
  asks for platform, PassKi version, check ID and the `PasskeyDoctor` report).
- Versioning and deprecation policy for users in `docs/versioning.md`, linked from the README in one line: what
  alpha, beta and 1.0 promise; small, frequent alphas; a "Migrating from" block in the CHANGELOG for every breaking
  entry; `@Deprecated(replaceWith = …)` whenever a signature allows it after 1.0. `CONTRIBUTING.md` links to it and
  adds the contributor side (KDoc `@sample` links to compiled samples for the main entry points).
- Minified release builds: the Android consumer in `ci/consumers/android` also builds a release variant with R8, so a
  missing keep rule fails CI instead of an adopter's release; PassKi ships consumer keep rules if that build or an
  on-device run of the minified sample needs them.
- Dependency updates through Renovate, grouped, with the Kotlin 2.2 toolchain floor held (the versions the README
  promises stay put; the `ci/consumers` builds catch anything else).
- `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1) with a private contact.
- First use: release 0.1.0-alpha05 with the Chromium immediate sign-in fix.

## Capabilities

### New Capabilities

- `release-process`: what a PassKi release publishes and guarantees to adopters (resolvable artifacts, BOM, hosted API
  reference, minified release builds that work without app-side keep rules).

### Modified Capabilities

None.

## Impact

- `.github/workflows/release.yml` (verification job, Dokka publishing), `.github/workflows/ci.yml` (minified consumer
  build), new `passki-bom` Gradle module, root `build.gradle.kts` Dokka config, `ci/consumers/android`, `README.md`,
  `CONTRIBUTING.md`, new `docs/versioning.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `renovate.json`,
  `.github/ISSUE_TEMPLATE/`, `.github/pull_request_template.md`, CHANGELOG.
- No public API change. Android may gain a `consumer-rules.pro` in `passki` (and `passki-compose`) if R8 needs it;
  no other platform impact.
