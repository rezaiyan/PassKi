<!-- plan-type: feature -->
# PassKi Setup Experience: one command, one dashboard, verified end to end

## Goal
Make passkey setup boring. A developer runs **one command** or opens **one web page**, gets every file and setting generated for them, and sees a **green/red checklist** proving each step really works: domain files, app signing, iOS entitlements, server config and device state. The sample app works out of the box against a **hosted demo relying party** with real server-side verification, so "clean, reliable data" means real challenges, real verification and real results, never client-faked ones.

## Background

### What actually broke during our own setup (2026-10-20/21)
| # | Failure | Where it surfaced | Today's tooling |
|---|---|---|---|
| 1 | assetlinks.json missing → "RP ID cannot be validated" | Android runtime | doctor / passkiVerify catch it ✅ |
| 2 | AASA missing → 1004 "not associated" | iOS runtime | doctor catches it ✅ |
| 3 | **Entitlements file not wired into the Xcode project** (`CODE_SIGN_ENTITLEMENTS` missing from the committed pbxproj) | iOS runtime, silently, only on Xcode builds | **nothing**: the doctor can't read entitlements, and passkiVerify checks only the file, not the wiring |
| 4 | Xcode rewrote the pbxproj while it was open, discarding edits | developer workflow | nothing |
| 5 | Android needs a screen lock for GPM | emulator | nothing (looks like a hang) |
| 6 | Simulator needs enrolled Face ID; the hardware keyboard hides QuickType | iOS simulator | nothing |
| 7 | Server must allow `android:apk-key-hash:…` origins | server | passkiFingerprints prints it; nothing verifies the server |
| 8 | Debug, release and Play App Signing keys differ | release time | passkiWellKnown groups per package; Play key must be added manually |
| 9 | Apple CDN lag | after deploy | doctor warns ✅ |

The current tools cover the **files**. The gaps are **project wiring (iOS)**, **server config**, **device prerequisites**, and **a single place that says "you're done"**.

### Constraints
- Browsers can't fetch other domains' `.well-known` files (CORS), so any web verifier needs a server-side fetch.
- The iOS Team ID is per developer. One shared AASA can't cover every developer without self-service registration.
- Fingerprints, package names and Team/Bundle IDs are **public** data: they are published in assetlinks/AASA anyway, so a dashboard handling them has no secret-data risk. Keystores and passwords never leave the developer's machine.

## Solution: one check catalog, three front-ends

### 1. Shared check catalog (the backbone)
One versioned list of checks with stable IDs, used by the Gradle plugin, the on-device doctor and the web dashboard, so every surface gives the same name and fix for the same problem:
- `AND-01` assetlinks reachable (HTTPS, 200, no redirect, JSON)
- `AND-02` statement grants `get_login_creds` for package P
- `AND-03` fingerprint F listed (per package/variant)
- `AND-04` Google Digital Asset Links API agrees
- `AND-05` Play App Signing key added (release)
- `IOS-01` AASA reachable
- `IOS-02` `webcredentials.apps` has TEAM.bundle
- `IOS-03` Apple CDN has it
- `IOS-04` entitlements file lists `webcredentials:<rpId>`
- **`IOS-05` entitlements file is wired into the build** (`CODE_SIGN_ENTITLEMENTS` resolved by `xcodebuild -showBuildSettings`)
- `IOS-06` signed app actually contains the entitlement (`codesign -d --entitlements` on the built .app)
- `SRV-01` server RP ID = rpId
- `SRV-02` server allows `https://<rpId>` + every `android:apk-key-hash:` origin
- `SRV-03` a real registration verifies (contract test)
- `DEV-01` Android screen lock / provider present
- `DEV-02` iOS Face ID enrolled / developer-mode associated domains
- Each ID has a docs page: what, why, exact fix, and copy-paste snippets.

### 2. One command: `./gradlew passkiSetup` + `./gradlew passkiCheck`
- **`passkiSetup`** (idempotent, non-interactive, driven by the existing `passki { }` DSL):
  - generates assetlinks + AASA (exists)
  - **writes the iOS entitlements file and wires it through `Config.xcconfig`** (`CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements`). This avoids editing `project.pbxproj` at all, which fixes failures #3 and #4. The KMP template already uses `Config.xcconfig` as the base configuration.
  - reads the Team ID / bundle ID from `xcodebuild -showBuildSettings` when not set in the DSL
  - writes `build/passki/server-config.json` plus snippets for popular verifiers (SimpleWebAuthn, webauthn4j, java-webauthn-server, py_webauthn) with rpId and the full origins allow-list
  - prints a numbered "upload these 2 files to https://<rpId>/.well-known/" next step
- **`passkiCheck`** runs every catalog check that can run from the build machine (AND-01…05, IOS-01…06, SRV-01/02 via an optional `serverConfigUrl`) and prints a checklist:
  - exit code ≠ 0 on failure (CI gate)
  - a `--json` report and an HTML report at `build/reports/passki/index.html`
  - a "share this report" link (Phase 3)
- An optional **`passkiCheck --device`** runs the doctor on a connected emulator/device via adb, or simctl for iOS, and folds the results in (DEV-01/02).

### 3. Online dashboard: "PassKi Setup"
- **Hosting:** Next.js on the existing alirezaiyan.com VPS (`/passki/setup` + API routes for the server-side fetches), or a dedicated domain (open question).
- **Generator tab:** enter rpId, package(s), SHA-256(s) (paste output of `passkiFingerprints`), Team ID and bundle ID. Get both files, the entitlements snippet, the xcconfig line and the server origins list. Download as a zip.
- **Verifier tab:** enter the rpId. The server fetches assetlinks, AASA, Google DAL and the Apple CDN, then runs the same catalog checks with the same IDs and fixes. Re-check live (polling) while the developer deploys: red turns to green without reload.
- **Shareable report:** a read-only URL with the results (no secrets involved), useful in PRs and support threads.
- **Server check (SRV-*):** optional. The developer points at their registration-options endpoint; the dashboard calls it and checks rpId, challenge format and the user.id length.
- **No accounts and no stored data**, except optional shared reports with a 7-day TTL.

### 4. Sample app with clean, reliable data: hosted demo RP
- **Demo relying party** (same Next.js app): `/api/demo/register/options|verify`, `/api/demo/login/options|verify`.
  - Built on **SimpleWebAuthn** for real challenge storage and real signature verification.
  - Anonymous demo users with a 24h TTL, in SQLite.
  - Also exposes `/api/demo/server-config` for SRV checks.
- **Android, zero config:** commit a **sample debug keystore** to the repo (standard practice for samples), so every clone produces the same SHA-256, which the demo domain's assetlinks already lists.
- **iOS, near zero config:** the Team ID differs per developer, so the dashboard offers **self-service demo registration**. You enter your Team ID; it is added to the demo AASA for 7 days with `?mode=developer` (bypasses the Apple CDN). Rate-limited, max N entries, auto-expiry.
- **Sample changes:**
  - `DemoRelyingParty` talks to the demo RP instead of faking challenges locally.
  - Every result shows the **server's verdict** ("verified by server ✓, signCount 3").
  - A **"Setup check" screen** runs `PasskeyDoctor` plus the server config check, showing the same catalog IDs.
- **Contract tests:** the demo RP stores the last N real responses per platform (opt-in button in the sample). That feeds new fixtures automatically, replacing the manual copy from logcat.

## User Stories
- [ ] As a KMP developer, I want to run one Gradle command that generates every file and wires iOS entitlements, so I never edit `project.pbxproj` by hand.
- [ ] As a developer, I want a checklist with pass/fail per step and exact fixes, so I know when setup is truly done.
- [ ] As a developer without Gradle access to the iOS side, I want a web page that generates the files and verifies my live domain, so I can finish setup from the browser.
- [ ] As a CI owner, I want `passkiCheck` to fail the build when the hosted files drift from the signing keys, so releases never ship broken passkeys.
- [ ] As a developer evaluating PassKi, I want to clone the repo and run the sample with real server verification and no setup on Android (one form on iOS), so I can trust it works before integrating.
- [ ] As a support engineer, I want a shareable report link, so users can show me exactly what's misconfigured.

## Implementation Steps

### Phase 1: Check catalog + one command (no infra)
- [x] Define the catalog (`passki-setup-rules`: JSON/Kotlin data with IDs, titles, fix templates). Reuse it in the Gradle plugin and the doctor (doctor check names gain IDs).
- [x] `passkiSetup`: entitlements file + `Config.xcconfig` wiring (fallback: print the exact Xcode steps if no xcconfig exists), Team/Bundle discovery via `xcodebuild -showBuildSettings`, server-config.json + verifier snippets.
- [x] `passkiCheck`: IOS-05 (resolved `CODE_SIGN_ENTITLEMENTS`), IOS-06 (codesign on the last built .app if present), JSON + HTML report, CI exit codes.
- [x] Docs page per check ID (in the repo `docs/checks/`; later on the dashboard).
- [x] Dogfood: run on this repo. It must catch the missing `CODE_SIGN_ENTITLEMENTS` we hit today.

### Phase 2: Demo RP + sample with real data
- [x] Demo RP API (Next.js + SimpleWebAuthn + SQLite, TTL cleanup) on the chosen domain. Live at https://passki.alirezaiyan.com.
- [x] Commit the sample debug keystore. Demo assetlinks lists its SHA-256. `androidApp/sample-debug.keystore` (PKCS12, alias/password `passki-sample`, NOT for release, see `androidApp/README.md`), SHA-256 `59:E5:92:32:4E:6C:E6:FA:5F:E2:F0:8A:39:18:13:2D:A5:1D:DE:5D:F9:BD:11:F0:5C:E8:23:57:88:E2:06:C0`, origin `android:apk-key-hash:WeWSMk5s5vpf4vCKORgTLaUd3l35vRHwXOgjV4jiBsA`; live assetlinks and server-config list both.
- [x] Sample: `DemoRelyingParty` → HTTP client (Ktor in the sample only, never in the library), server verdict in the UI, Setup-check screen. Sample rpId `passki.alirezaiyan.com` (`passkiSetup` added `webcredentials:passki.alirezaiyan.com` + `?mode=developer`; the `alirezaiyan.com` entries stay). Ktor 3.6.0 (okhttp android/jvm, darwin ios, js js/wasmJs) with timeouts + retry on network errors (emulators drop idle pooled connections: before the retry, two calls in the first Android run failed with `Socket timeout`). Setup check = `PasskeyDoctor.run` + SRV-01/02 from `/api/demo/server-config` + catalog IDs.
- [x] Opt-in "share response for PassKi tests" → fixture capture endpoint. Checkbox → `register/verify` with `capture.platform`; Android capture confirmed in `GET /api/demo/fixtures?platform=android` (id 1, credential `2hnq4zvzBdxhxuuBW2rXIA`, verified).
- [x] E2E on both emulators with server verification green. Android API 36 (GPM) and iOS 26.3 simulator (2026-10-22): register, modal / device-only / passkey-or-password / autofill sign-in, PRF stable, signals; fixtures shared and recorded in `passki-test` (details in Plan 3 Task 6).
  - **Android emulator-5554 (API 36, GPM), 2026-10-21/22: green.** Setup check: doctor 0 failures (DEV-03, DEV-01, AND-06, AND-03 PASS), SRV-01/02 PASS. Register → `verified by server ✓ signCount 0 userVerified true backedUp true` (aaguid ea9b8d66…, GPM). Modal sign-in (`PasskeySignInButton`), device-only, autofill (Gboard chip), passkey-or-password: all `verified by server ✓`. PRF: 32 bytes, `60d4951e6da94aeb` twice (stable). Signals: `unknownCredential(2hnq…)` → next device-only sign-in `NoCredentials` (GPM honours it); `currentUserDetails` renamed the passkey to "PassKi demo user" in the GPM chip. Upgrade declined (status 17, expected).
  - **iOS simulator 26.3, 2026-10-22: partial.** Build OK; `passkiCheck` IOS-04/05/06 PASS. `-passkiAutorun` UI-free steps: doctor 0 failures (IOS-03 PASS, IOS-04 SKIP at runtime), SRV-01/02 PASS, server options OK, unknown-credential device-only → `NoCredentials`, `Signal unknown ✓` (real ASCredentialUpdater call), Upgrade declined (expected). The "Add a passkey?" sheet for `demo-…` on passki.alirezaiyan.com appeared (association works), but the Mac screen was locked so the simulator could not be tapped: server-verified register/sign-in/autofill/PRF on iOS are **still open**.

### Phase 3: Web dashboard
- [x] Generator (zip download), Verifier (server-side fetches, live re-check), report pages with shareable links (7-day TTL).
- [x] SRV checks against the developer's options endpoint.
- [x] Self-service iOS demo registration (Team ID → demo AASA, 7-day TTL, rate limits).

### Phase 4: Polish
- [ ] `passkiCheck --device` (adb/simctl doctor run).
- [x] README "Setup in 5 minutes" built around `passkiSetup` → upload → `passkiCheck`.
- [ ] Video/GIF of the dashboard turning green.

## Decisions (2026-10-21)
- **Domain:** `passki.alirezaiyan.com`, a subdomain on the existing VPS behind Caddy. Its own `.well-known` (demo assetlinks/AASA) stays isolated from the personal site.
- **Scope:** all phases.
- **Self-service iOS demo registration:** yes, with a 7-day TTL, rate limits, an entry cap and `?mode=developer` only.
- **Code layout:**
  - `passki-gradle-plugin/` (Phase 1 tasks)
  - shared check catalog in core `passki/` (doctor IDs) plus a mirror in the plugin
  - new `passki-web/` (Next.js app: dashboard + demo RP + generator/verifier API), deployed as its own container
- **Deploy requires explicit user confirmation:** DNS record, Caddy site block, VPS compose service, `ali` deploy target.
- **Execution:** Phase 1 and the Phase 2/3 web app run in parallel (no shared files). The sample app changes from Phase 2 wait until Plan 3 Task 4 (which edits the sample) is done.

## Open Questions (resolved ones moved to Decisions)
- Domain for the dashboard and demo RP: a subpath of alirezaiyan.com, a subdomain (`passki.alirezaiyan.com`), or a dedicated domain (e.g. `passki.dev`, needs purchase)? A dedicated domain looks more trustworthy to OSS users and isolates demo AASA churn from your personal site.
- Self-service iOS demo registration: acceptable abuse surface (anyone can add a Team ID to the demo domain for 7 days)? Alternative: iOS demo only via the developer's own domain.
- Commit a sample debug keystore? Standard for samples, but it must never be used for release.
- Dashboard tech: reuse the Next.js site (fast, already deployed with `ali site`), or a separate service/container on the VPS?
- Is `passkiCheck --device` worth it vs. just the in-app Setup screen?

## Risks
- **Editing Xcode projects:** mitigated by wiring through `Config.xcconfig` only. If a project has no base xcconfig, print instructions instead of patching the pbxproj.
- **Demo domain abuse** (self-service AASA entries): TTL, rate limits, max entries, `?mode=developer` only, no custom paths.
- **Apple CDN caching:** demo uses `?mode=developer`; the dashboard shows both CDN and direct state.
- **Server-side fetch SSRF** in the dashboard verifier: only fetch `https://<domain>/.well-known/{assetlinks.json,apple-app-site-association}` plus the fixed Google/Apple endpoints; block IP literals and private ranges; size and time limits.
- **Scope creep:** Phase 1 alone fixes the worst failure we hit (iOS wiring) and needs no hosting. Ship it first.
