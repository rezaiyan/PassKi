# Design

## Context

- `release.yml` (macOS runner, 90-minute timeout) builds and signs into Maven Local, checks signatures, then runs
  `publishAndReleaseToMavenCentral` for the four libraries and the plugin build (`-p passki-gradle-plugin`). It
  doesn't check that anything reaches `repo1.maven.org`.
- Dokka 2.2 aggregates `passki`, `passki-test`, `passki-compose` and `passki-demo` in the root build; output is only
  generated locally.
- `ci/check-docs-links.py` and `ci/doc-snippets.py` already check README/docs in CI.
- `GROUP=com.alirezaiyan.passki`, versions from `VERSION_NAME` in `gradle.properties`.

## Goals / Non-Goals

**Goals:**
- A green release run means adopters can resolve it.
- Versions stay aligned without adopters repeating them.

**Non-Goals:**
- Changing the signing or upload mechanism.
- Automating CHANGELOG generation.

## Decisions

1. **Separate `verify-published` job.** Runs on `ubuntu-latest` after `publish`, polls each POM URL with backoff
   (every 2 minutes, up to 75 minutes) and writes the coordinates to the step summary. Separating it keeps the
   expensive macOS runner free while Central syncs. The coordinate list is derived from the Gradle projects (a small
   `./gradlew printPublications` task or a list in `ci/`), so new artifacts can't be forgotten.
2. **`passki-bom` as a `java-platform` module** published with the same vanniktech publishing setup as the libraries
   and included in the library deployment. The Gradle plugin is not in the BOM (plugins resolve through the plugin
   portal mechanism, not dependency constraints).
3. **API reference hosting: GitHub Pages** from a `gh-pages` branch, one folder per version plus `latest/`, published by
   a job that needs only `GITHUB_TOKEN`. Alternative: copy to the passki-web host, rejected for now because it would
   put deploy credentials for the VPS into CI. A custom path on `passki.alirezaiyan.com` can proxy to Pages later
   without changing the spec.
4. **Badge:** shields.io `maven-central/v/com.alirezaiyan.passki/passki` with `include_prereleases`.
5. **Repo hygiene files are plain Markdown/YAML**; the bug template's fields mirror what the AND-03 hunt needed
   (platform and OS version, device or emulator image, PassKi version, check ID, Doctor report).
6. **Versioning policy lives with the user docs.** `docs/versioning.md` is what adopters read before depending on an
   alpha; `CONTRIBUTING.md` links to it rather than holding it. Until 1.0 any alpha may break, but every break has a
   CHANGELOG "Migrating from" block; from beta, breaks need a deprecation cycle of one minor version.
7. **Minified build: CI builds, a person runs once per release.** CI adds `assembleRelease` with R8 to the Android
   consumer (debug signing, so no secrets). R8 fails on missing classes, which catches most missing rules; reflection
   problems only show at run time, so the release checklist runs the minified sample on a device once. kotlinx-serialization
   and Credential Manager ship their own consumer rules, so PassKi adds `consumer-rules.pro` only if one of those
   checks fails, keeping each rule with a comment naming what needs it.
8. **Renovate holds the toolchain floor.** Grouped updates (Kotlin and KMP plugins, AndroidX, Compose, test tooling)
   on a weekly schedule. Packages whose versions the README ties to Kotlin 2.2 (`kotlin-consumer`,
   kotlinx-serialization, Ktor in `passki-demo`, Compose Multiplatform in `passki-compose`) are held with
   `allowedVersions` until the floor moves deliberately. Build-only tools (AGP for the samples, Dokka, vanniktech) update
   freely; the `ci/consumers` builds still guard the floor.
9. **Code of conduct: Contributor Covenant 2.1,** with reports to the maintainer's private address, so the community
   standards page on GitHub is complete before launch.

## Risks / Trade-offs

- [R8 passes the build but breaks at run time] → The once-per-release device run of the minified sample; a failure
  there blocks the release and adds a consumer rule with a test case.
- [Renovate noise] → Grouping and a weekly schedule; held packages don't open PRs.

- [Central sync slower than the limit] → Limit configurable via workflow input; the failure message says the release
  may still appear and how to re-run only the verification job.
- [BOM published while the plugin fails validation] → Same as today for libraries: the fix is a new version; the
  verification job makes the mismatch visible immediately.
- [gh-pages branch grows with every version] → Reference HTML is small; prune nothing before 1.0.

## Migration Plan

Land the workflow and BOM changes, then tag 0.1.0-alpha05 and confirm the verification job and Pages publish run.
Rollback: revert the workflow commit; already-published artifacts are unaffected.

## Open Questions

- Whether to serve the reference under `passki.alirezaiyan.com/api/` as well (a proxy rule; doesn't change the spec).
