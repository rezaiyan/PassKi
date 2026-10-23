<!-- plan-type: feature -->
# PassKi sample app redesign

## Goal
Turn the shared Compose sample (Android, iOS, desktop, web) from a wall of test buttons and a monospace log into a product demo with a lab. It opens like a real app: branded passkey sign-in, then an account screen with the server's verdict. A Lab tab keeps every advanced flow for testing, and a Setup tab shows the doctor and server checks the same way the website does.

## Background
- One `App()` in `shared/commonMain` drives all four targets. iOS hosts it through `MainViewController`.
- Contract to keep: the `PASSKI_DEMO`, `PASSKI_FIXTURE` and `PASSKI_ASSERTION` log lines, the autorun flag and its step order (README, CONTRIBUTING, device-test notes), `failureLog()` (covered by `FailureLogTest`), and `App(passkeys, autorun, appOrigin, rp)`.
- Visual identity matches passki-web: night navy and violet, the same pass/warn/fail colours, Bricolage display type, light and dark.

## User Stories
- [x] As a developer evaluating PassKi, I want the sample to look like a real sign-in so I can picture it in my app
- [x] As a developer, I want to see the server-verified result (signCount, backedUp, credential) after signing in
- [x] As a contributor, I want every advanced flow (device-only, passkey or password, PRF, upgrade, signals, autofill in a dialog) one tap away with a readable activity history
- [x] As someone debugging setup, I want doctor and server checks shown as a checklist with catalog IDs and fixes

## Implementation Steps
- [x] Theme: colour schemes, Bricolage display font as a Compose resource, shapes, typography
- [x] Icons (ImageVector) and shared components: status icon, ID chip, cards, activity timeline
- [x] `DemoController`: move the flow logic out of `App.kt`, keep the log lines, record structured activity
- [x] Demo tab: sign-in screen (autofill field, sign-in button, create account), then the account screen with the verdict
- [x] Lab tab: grouped flows with a status per row, a fixture-sharing switch, an activity timeline
- [x] Setup tab: run checks, summary, checklist (doctor + SRV-01/02), device capabilities
- [x] Adaptive layout: bottom bar on phones, navigation rail and centred content on wide screens
- [x] Verify: shared tests, desktop and web runs, Android and iOS builds; screenshots

## Open Questions
- Android or iOS on-device screenshots need an emulator or simulator; desktop and web can be captured locally.

## Risks
- `PasskeyUsernameField` and `PasskeySignInButton` take their look from MaterialTheme; on iOS the field may be a native view, so theming is limited there.
- A font resource adds binary size to every target (keep it to two static weights).
