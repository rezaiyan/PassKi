# Tasks

## 1. Release verification

- [ ] 1.1 Add a way to list every published coordinate (Gradle task or `ci/` list checked against the projects); verify it prints the four libraries, the BOM, the plugin and the plugin marker
- [ ] 1.2 Add the `verify-published` job to `release.yml` polling `repo1.maven.org` with a configurable limit and a step summary; verify with a manual `workflow_dispatch` run against the already-released 0.1.0-alpha04 coordinates (passes) and a fake version (fails naming each artifact)

## 2. BOM

- [ ] 2.1 Add the `passki-bom` `java-platform` module with constraints for the four libraries and publish it with the libraries; verify `./gradlew :passki-bom:publishToMavenLocal` produces a POM with all four constraints
- [ ] 2.2 Add a BOM consumer to `ci/consumers/kmp` (dependencies without versions); verify the consumer build in CI resolves the Maven Local versions
- [ ] 2.3 Show the BOM in the README Install section; verify `python3 ci/doc-snippets.py` accepts the coordinates

## 3. API reference

- [ ] 3.1 Add a Pages publishing job that generates Dokka and writes `<version>/` and `latest/` to `gh-pages`; verify a dry run on a branch produces both folders
- [ ] 3.2 Link the reference from the README; verify `python3 ci/check-docs-links.py` passes

## 4. Repository hygiene

- [ ] 4.1 Replace the hardcoded README badge with the Maven Central badge; verify it renders the current version on GitHub
- [ ] 4.2 Add `SECURITY.md` (private reporting via GitHub security advisories, supported versions, response time); verify GitHub shows the security policy tab
- [ ] 4.3 Add a bug template, a feature template and a PR template; verify GitHub's new-issue page lists them
- [ ] 4.4 Write `docs/versioning.md` (design decision 6: alpha / beta / 1.0 promises, "Migrating from" blocks, `@Deprecated(replaceWith)`), link it from one README line and from `CONTRIBUTING.md` (which adds the `@sample` rule); verify `python3 ci/check-docs-links.py` passes and the page links the CHANGELOG format
- [ ] 4.5 Add `@sample` links from `Passkeys`, `PasskeyResult` and `FakePasskeys` KDoc to compiled samples; verify Dokka output shows the samples

- [ ] 4.6 Add `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1, private contact); verify GitHub's community standards page shows it
- [ ] 4.7 Add `renovate.json` per design decision 8 (groups, weekly schedule, `allowedVersions` holds for the Kotlin 2.2 floor); verify `npx --yes --package renovate renovate-config-validator` accepts it and a dry run lists no PR for the held packages

## 5. Minified release builds

- [ ] 5.1 Add a release build type with `isMinifyEnabled = true` (debug signing) to `ci/consumers/android` and run `assembleRelease` in the CI `apple` job next to the existing consumer build; verify it builds locally against Maven Local
- [ ] 5.2 Build the Android sample as a minified release, run create-account and sign-in against the demo server on a device or emulator, and trigger one setup error; verify both ceremonies succeed and the error keeps its fix hint and check ID. If anything fails, add the needed rules to `passki/consumer-rules.pro` (wired with `consumerProguardFiles`) with a comment per rule, and repeat
- [ ] 5.3 Add "run the minified sample once" to the release checklist in `CONTRIBUTING.md`; verify the checklist names the command

## 6. First release

- [ ] 6.1 Bump `VERSION_NAME` to 0.1.0-alpha05, finalise the CHANGELOG, tag and push (after confirming with the maintainer); verify the release run and `verify-published` both succeed and the badge shows alpha05
