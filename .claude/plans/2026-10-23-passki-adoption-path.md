<!-- plan-type: feature -->
# PassKi adoption path: try it, test it, ship it

## Goal
Make the path from "I found PassKi" to "passkeys work in my app" short and hard to break. A developer should be
able to (A) run the sample in minutes, (B) try PassKi inside their own app against the hosted demo server without
writing a server or owning a domain, and (C) move to their own domain with one command and one check. The rule for
every change: **easier, never more hidden.** Each shortcut prints what it did, the files it touched and the data
it sent, and the developer can still do every step by hand.

## Background

### What a new user meets today (reviewed 2026-10-23)

| # | Finding | Evidence | Effect on a new user |
|---|---|---|---|
| 1 | **Nothing is published.** README tells people to add `com.alirezaiyan.passki:passki:0.1.0-alpha02` and plugin `com.alirezaiyan.passki`. | search.maven.org: 0 results for `g:com.alirezaiyan.passki`; repo1 `maven-metadata.xml` 404; Plugin Portal 404. CHANGELOG says alpha02 "(unreleased)". | Install step 1 fails. Nothing else matters until this is fixed. |
| 2 | **No way to try PassKi in your own app without your own domain + server.** The demo RP accepts only the sample's committed keystore (Android) and self-registered Team IDs (iOS). | `passki-web/app/api/demo/` has `ios-apps` registration only, no Android equivalent. | To test, a developer must set up a domain, `.well-known` files and a WebAuthn server first. That is the hardest part, so most will stop here. |
| 3 | **The demo server client lives only in the sample** (`shared/.../DemoRelyingParty.kt`, Ktor). | Not an artifact. | A developer who wants "register + verify" in their app has to copy 177 lines. |
| 4 | **Plugin is a silent no-op on the wrong module.** It wires tasks only `withPlugin("com.android.application")`. | `PasskiPlugin.apply`. | Applying it to `shared` (the natural KMP place) gives no tasks and no message. iOS-only apps can't use it at all. |
| 5 | **Five tasks, two of them overlapping.** `passkiVerify` ⊂ `passkiCheck`; `passkiWellKnown` ⊂ `passkiSetup`. | `PasskiPlugin.wire`. | Users don't know which one to run. |
| 6 | **`rpId` is only checked for blank.** `https://example.com` or `example.com/` pass the DSL and fail later in confusing ways. | `PasskiAppleTask.requireRpId`. | Late, unclear failures. |
| 7 | **`passkiSetup` edits iOS files with no preview.** It logs each write, but there is no dry run and no end summary. | `PasskiSetupTask.setup`. | Developers are rightly nervous about a tool that edits their Xcode config. |
| 8 | **Runtime errors don't name a check ID.** `PasskeyError.Misconfigured` has `message` + `fixHint`, but no link to the catalog (`AND-02`, `IOS-02`, ...) that `passkiCheck`, the doctor and the dashboard all use. | `PasskeyError.kt`, `AndroidErrorMapper.kt`, `AppleErrorMapper.kt`. | The same problem has an ID in three tools and no ID at the place users see it first. |
| 9 | **iOS sample needs edits to tracked files.** `Config.xcconfig` commits the owner's `TEAM_ID=VFCFJC7Y5J` and bundle ID. A cloner must change both (bundle IDs are unique across teams) and then register on the web page. | `iosApp/Configuration/Config.xcconfig`. | Dirty working tree, signing errors, three manual steps. |
| 10 | **Template leftovers in the sample.** `Greeting`, `GreetingUtil`, `Platform*`, `SharedLogic*Test`. | `shared/src/`. | Noise when reading the sample to learn the API. |
| 11 | **README is 650 lines** and mixes "try it", "integrate it", reference and verification logs. Plugin task is shown as `:androidApp:passkiSetup` although `./gradlew passkiSetup` works from the root. | `README.md`. | The 3-step path is buried after PRF and testing sections. |
| 12 | **No CI.** No `.github/workflows`. | `git ls-files .github` empty. | Nothing proves the "clone and run" path still works after a change. |

### What already works well (keep it)
- One `Passkeys()` call, no Activity/UIWindow; typed `PasskeyResult` / `PasskeyError` with `fixHint`.
- One check catalog shared by `passkiCheck`, `PasskeyDoctor` and the web verifier, with a docs page per ID.
- `passkiSetup` never touches `project.pbxproj`, and logs every file it writes.
- `passkiCheck` JSON + HTML report and CI exit codes.
- `FakePasskeys` with real ES256 output.

## Design: three paths, one set of tools

| Path | Who | Steps after this plan |
|---|---|---|
| **A. Run the sample** | "Does this library work?" | Android: open project, run. iOS: put your Team ID in `Local.xcconfig`, `./gradlew passkiSetup`, run. |
| **B. Try in my app (demo server)** | "Does it work in *my* app?" | Add 2 deps + plugin, `passki { useDemoServer() }`, `./gradlew passkiSetup`, call `PasskiDemoServer`. No domain, no server. |
| **C. Production** | "Ship it." | `passki { rpId = "example.com" }`, `./gradlew passkiSetup`, upload 2 files, `./gradlew passkiCheck`. |

Path B and C use the same tasks and the same report; only the RP changes. Moving from B to C is replacing
`useDemoServer()` with `rpId = "..."` and `PasskiDemoServer` with your API.

### Transparency rules (apply to every item)
- Every task ends with a **summary block**: files created / changed / unchanged (paths), data sent over the network
  (exact fields and destination), and next steps.
- Every task that writes files has `--preview`, which prints the same summary plus a diff and writes nothing. (Not `--dry-run`: Gradle's own `--dry-run` flag would swallow it and silently skip every task.)
- Demo registration sends **public data only** (package, SHA-256 fingerprints, `TEAM.bundle`), the same data that
  sits in any public `assetlinks.json` / AASA. The task prints it before sending and prints the expiry date after.
- Abstractions return everything the raw path returns: `PasskiDemoServer` returns the full `ServerVerdict`, and
  `PasskeyRegistration.json` / `PasskeyAssertion.json` stay public.
- Nothing moves out of the README without a link to where it went.

## User Stories
- [ ] As a developer evaluating PassKi, I want the install snippet in the README to work, so I can add the library in one step.
- [ ] As a developer, I want to try register + sign-in inside my own app against a hosted server, so I don't need a domain or a WebAuthn server to evaluate PassKi.
- [ ] As a developer, I want the Gradle plugin to tell me when I applied it to the wrong module, so I never get a silent no-op.
- [ ] As a developer, I want to see what `passkiSetup` will change before it changes it, so I trust it with my Xcode config.
- [ ] As a developer, I want a runtime `Misconfigured` error to name the same check ID as `passkiCheck`, so I can find the fix page directly.
- [ ] As a developer cloning the repo, I want the iOS sample to run after setting only my Team ID, without editing tracked files.
- [ ] As a maintainer, I want CI that runs the clone-and-run path, so the onboarding path can't silently break.

## Implementation Steps

### Phase 0: make install possible (blocker)
- [ ] Publish `passki`, `passki-compose`, `passki-test` and `passki-gradle-plugin` (incl. plugin marker) `0.1.0-alpha02` to Maven Central. **Needs you:** Sonatype account, namespace `com.alirezaiyan`, GPG signing key in `~/.gradle/gradle.properties`. Then verify with a fresh throwaway project that resolves only from `mavenCentral()`.
- [ ] Until it's live, README "Install" says so plainly and gives the working alternative (`./gradlew publishToMavenLocal` + `mavenLocal()`), with the exact commands.
- [ ] README: show `pluginManagement { repositories { mavenCentral() } }` next to the plugin snippet (the plugin is on Maven Central, not the Plugin Portal), or also publish to the Plugin Portal (open question).

### Phase 1: plugin is robust and obvious
- [x] **Wrong-module guard:** if `com.alirezaiyan.passki` is applied and no Android application plugin is present after evaluation, register `passkiSetup`/`passkiCheck` anyway in **iOS-only mode** (AND checks reported as `SKIP: no Android app module`), and log one line: `passki: no com.android.application in :shared; apply the plugin to your Android app module (e.g. :androidApp or :composeApp) to include Android`. TDD with TestKit (KMP library module, iOS-only root).
- [x] **Validate `rpId`** (when a task runs, before any work): bare lowercase domain, no scheme/path/port/trailing dot. Fail with the corrected value in the message (`rpId must be a bare domain: use "example.com", not "https://example.com/"`). Unit tests for each bad form.
- [x] **Two front-door tasks:** README and `./gradlew tasks` show `passkiSetup` and `passkiCheck` in group `passki`, plus `passkiFingerprints` (info only). Move `passkiWellKnown` and `passkiVerify` to group `passki (advanced)`; `passkiVerify` logs `passkiVerify is a subset of passkiCheck; use passkiCheck` and keeps working (no breakage for current users).
- [x] **`passkiSetup --preview`** (`@Option`): computes everything, prints a diff per file and the summary, writes and sends nothing. Functional test asserts no file changed; a preview with nothing to do says so.
- [x] **Summary block** at the end of `passkiSetup` (created / changed / unchanged with paths, then numbered next steps). Keep the existing per-step lines.
- [x] README uses `./gradlew passkiSetup` / `./gradlew passkiCheck` from the root (Gradle runs the task in every project that has it).

### Phase 2: try PassKi in your own app (demo server)
- [x] **Demo RP: self-service Android registration** (deployed 2026-10-23), same rules as iOS: `POST /api/demo/android-apps` with package + SHA-256 list, 7-day TTL, rate limit per IP, entry cap. Served from the demo `assetlinks.json` and `server-config` origins. Input validation + tests in `passki-web`.
- [x] **Plugin `passki { useDemoServer() }`:** sets `rpId = "passki.alirezaiyan.com"`, `server.configUrl` and `apple.developerMode = true`. In this mode `passkiSetup` skips the "upload files" step and instead registers the app with the demo RP (Android package + fingerprints from the build; `TEAM.bundle` from DSL/xcodebuild). It prints the exact JSON it sends, the URL, and the expiry date. `--dry-run` prints without sending. `passkiCheck` then runs unchanged against the demo domain.
- [x] **`passki-demo` artifact** (new, Ktor lives here only, never in `passki`): `PasskiDemoServer` with `registrationOptions()`, `verifyRegistration(json)`, `authenticationOptions()`, `verifyAuthentication(json)`, returning the full `ServerVerdict`. Move it from `shared/DemoRelyingParty.kt`; the sample depends on it. Its KDoc and README say it is for evaluation only (24 h users, public demo domain).
- [x] README **"Try it without a server"** section: 4 snippets (deps, plugin block, `passkiSetup`, 6-line Kotlin), then "Going to production: replace `useDemoServer()` with `rpId`, and `PasskiDemoServer` with your API".

Phase 1 + 2 status (2026-10-23): plugin 139 tests, passki-web 139 tests, passki-demo 7 tests on JVM / iOS simulator /
wasm, CONTRIBUTING check list green, sample autorun on the Android 16 emulator talks to the live demo server through
`passki-demo` (Setup check 0 failures, SRV-01/02 pass). Deployed 2026-10-23 (main 971335a). A throwaway app with
`useDemoServer()` registered against the live server; passkiCheck: AND-01..03 and SRV-01/02 pass, AND-04 warns until
Google's cache picks up the new key. Open: an interactive register/sign-in through `passki-demo` on a device.

### Phase 3: errors and doctor speak the catalog
- [x] Add `checkId: String?` to `PasskeyError.Misconfigured` (and `docsUrl` derived from it, pointing at `passki.alirezaiyan.com/checks/<ID>`). Map: assetlinks errors → `AND-02/AND-03`, AASA / 1004 → `IOS-02`, entitlements → `IOS-04`, no provider → `DEV-01`, not initialised → `CFG-01` (confirm IDs against `docs/checks/catalog.json`). `fixHint` text unchanged. Update `api/` dumps + CHANGELOG (alpha, binary change noted).
- [x] `toString()` of `Misconfigured` includes the ID, so a plain log line shows it.
- [x] Tests in `AndroidErrorMapperTest` / `AppleErrorMapperTest` assert the ID per error case.

Phase 3 status (2026-10-23): `checkId` + `docsUrl` live on the `PasskeyError` base (also on `InvalidOptions` for
`CFG-01`), old constructors kept (ABI additions only). Mapping: asset links → AND-03, provider → DEV-01, init / no
Activity → AND-06, iOS association → IOS-02, RP ID → CFG-01; autofill-without-field and no-window stay null.
`FakePasskeys` and the sample's failure log follow. Tests green on JVM, Android host, iOS simulator.

### Phase 4: sample clones and runs
- [x] iOS: `Config.xcconfig` gets `#include? "Local.xcconfig"` (gitignored) and `PRODUCT_BUNDLE_IDENTIFIER = com.alirezaiyan.passki.PassKi.$(TEAM_ID)`, so the only per-developer value is `TEAM_ID`. Commit `Local.xcconfig.example`. The owner's Team ID moves to the owner's own `Local.xcconfig`. Check: `passkiSetup` still resolves Team/bundle via `xcodebuild -showBuildSettings`, and the demo AASA keeps the owner's existing entry.
- [x] The sample's `passki { }` block uses `useDemoServer()`, so `./gradlew passkiSetup` registers the cloner's iOS app with the demo RP. The web registration page stays for people without Gradle.
- [x] Remove KMP template leftovers: `Greeting.kt`, `GreetingUtil.kt`, `Platform*.kt` (if unused after removal), `SharedLogic*Test.kt`, `SharedCommonTest.kt`.
- [x] Sample start: if the doctor finds failures on launch (debug), show a banner "Setup has N problems" that opens the Setup tab, with the check IDs. No auto-fix, no hiding.

Phase 4 status (2026-10-23): the pbxproj had `DEVELOPMENT_TEAM = VFCFJC7Y5J` hardcoded on the target (it overrode
`TEAM_ID`), now `"$(TEAM_ID)"`. `Config.xcconfig` keeps the bundle ID default (the maintainer's permanent AASA entry) and
`#include? "Local.xcconfig"`; the example sets `PRODUCT_BUNDLE_IDENTIFIER = com.alirezaiyan.passki.PassKi.$(TEAM_ID)`.
Checked with xcodebuild: maintainer → VFCFJC7Y5J.com.alirezaiyan.passki.PassKi, cloner → suffixed bundle, no Local →
builds for the simulator without a team. Platform* stays (used for the platform name).

### Phase 5: docs that follow the paths
- [x] README top: "Get started" with the three paths (A/B/C), each ≤ 5 steps, before any feature sections.
- [x] Move long reference sections (capability table, PRF/largeBlob platform notes, autofill details, "Verified on", plugin option reference) to `docs/*.md`, each linked from the README where it used to be. Target README ≤ ~300 lines. Nothing deleted.
- [x] Quick start defines the `api` it calls (a 4-function interface), so readers know exactly what their server must provide, and links to the generated `build/passki/server-snippets/`.

Phase 5 status (2026-10-23): README 766 → 450 lines (header and media kept; ~300 was too tight without cutting the
Compose example and the error table). Moved verbatim to docs/platforms.md, compose.md, autofill.md, extensions.md,
setup.md, verified-on.md; a script confirmed every old line is in the README or a docs page (only headings, contents
links and the old "Try it" intro were reworded), and every relative link and anchor resolves. Install now says plainly
that alpha02 isn't on Maven Central, with the mavenLocal commands (checked: all artifacts and the plugin publish).
Path B checked from mavenLocal in a throwaway app: plugin resolves, the README snippet compiles, the APK builds.
**Found two adoption blockers** (not fixed yet, stated as requirements in Install): the libraries are compiled with
Kotlin 2.4.20, so apps on Kotlin ≤ 2.2 (e.g. AGP 9.1.1's built-in Kotlin) can't read them; and the Android AARs
require consumers' compileSdk 37 (the library's compileSdk becomes the AAR minCompileSdk; okhttp 5.5.0 from
passki-demo needs 37 too). Fix: `languageVersion`/`apiVersion` 2.1 or 2.2 and `aarMetadata.minCompileSdk` 35/36.

Blockers fixed (2026-10-23): `kotlin-consumer = "2.2.21"` in the catalog drives `languageVersion`/`apiVersion` 2.2 and
`coreLibrariesVersion` in all four libraries (2.1 is deprecated in Kotlin 2.4 and the code uses `when` guards and
stable Base64); `webMain` keeps the build's stdlib (Kotlin/Wasm requires it). `aarMetadata.minCompileSdk = 35`.
passki-demo's Android engine is Ktor `android`, not OkHttp 5 (which needs 37). Checked from mavenLocal: Android app with
AGP 9.1.1 built-in Kotlin 2.2.10 builds at compileSdk 36 and 35; KMP consumer on Kotlin 2.2.21 compiles JVM.
**Still open, needs a decision:** the iOS klib has ABI 2.4.0 and Kotlin/Native can't write an older one
(`-Xklib-abi-version` is test-only), so KMP iOS/JS/Wasm consumers need Kotlin 2.4.20+. Only fix: build PassKi with an
older Kotlin toolchain.

Adopted (2026-10-23): built with Kotlin 2.2.21. Dependencies at releases built with Kotlin 2.2 (checked in each klib
manifest): serialization 1.9.0, Ktor 3.3.3, cryptography 0.5.0, Compose MP 1.10.3 / material3 1.10.0-alpha05, lifecycle
2.10.0, kotlin-wrappers 2025.12.4; coroutines 1.11.0 already was. Fixes it needed: `abiValidation { enabled; klib }`
(Kotlin 2.2 silently skips the check otherwise; tasks are checkLegacyAbi/updateLegacyAbi), `-rpath /usr/lib/swift` in
passkiSwift.def (2.2-linked test binaries crashed on libswift_Concurrency), one Compose test off `ui.test.v2`. Checked:
909 tests green, Xcode iOS build, emulator autorun, and apps on Kotlin 2.2.21 and 2.4.20 (KMP: iOS simulator + JVM
tests) and AGP 9.1.1 built-in Kotlin (compileSdk 36) building against the mavenLocal artifacts.

### Phase 6: prove it stays easy
- [x] GitHub Actions: (1) unit + plugin functional tests; (2) **fresh-clone job**: clone, `./gradlew :androidApp:assembleDebug passkiCheck` against the demo domain; (3) macOS job: iOS build + `passkiCheck` Xcode checks.
  Done 2026-10-23 (`.github/workflows/ci.yml`): library + plugin (Ubuntu, with docs links and test counts; plugin
  TestKit tests must not be skipped), passki-web, sample clone-and-run against the demo server, and macOS 26 (iOS tests,
  ABI dumps, Xcode sample build, consumer apps in `ci/consumers/` on Kotlin 2.2.21 and AGP built-in Kotlin /
  compileSdk 35). First run caught two real problems: the plugin build had no JDK 17 toolchain resolver (fresh clones
  without JDK 17 failed) and the Swift bridge task ran `xcrun` on Linux via dokkaGenerate. Second run: all green.
- [ ] Manual "new user" run before release (partly covered: path B was followed word for word in throwaway apps on Kotlin 2.2.10/2.2.21/2.4.20; the
  interactive register/sign-in on a device is still open): throwaway KMP project from the JetBrains wizard, follow README path B word for word on Android emulator + iOS simulator, record every place a step was unclear, fix the doc.

## Open Questions
- Publishing: do you already have a Sonatype namespace for `com.alirezaiyan` and a signing key? Also publish the plugin to the Gradle Plugin Portal (easier for users: no `pluginManagement` change)?
- `passki-demo` as a separate artifact vs. a documented copy-paste file in the README. The artifact is easier; the cost is one more module to publish and keep in sync with `passki-web`.
- Self-service Android demo registration opens the same abuse surface as the iOS one (public data, TTL, caps). OK to mirror the iOS limits?
- Keep `webApp` and `desktopApp` in the sample? They only show `NotSupported` today; they prove the library compiles there but add build time.

## Risks
- **Demo server becomes a dependency of people's evaluations.** Mitigate: clear "evaluation only" labels, 24 h users, health check in CI, and the same tasks work with any `rpId`.
- **`checkId` on `Misconfigured` is a binary API change.** Alpha, listed in CHANGELOG; constructor param has a default.
- **Hiding tasks could break existing users' scripts.** Tasks are only regrouped, not removed; `passkiVerify` keeps working with a hint.
- **xcconfig `$(TEAM_ID)` bundle suffix** changes the sample's bundle ID; the owner's existing demo AASA entry and fixtures reference the old ID. Re-register once; keep the old entry until it expires.
- **Scope:** Phase 0 is the only blocker. Phases 1 and 3 are small and independent; Phase 2 is the biggest win for "try it". Ship in that order.
