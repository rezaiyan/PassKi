# Proposal

## Why

PassKi's README explains PassKi well, but the Lexicon integration needed pages that start from the app's situation:
how to roll out passkeys as an extra sign-in next to Google and Apple, what to show users for each error, how to choose
an RP ID you can never change, how to label accounts, what the server endpoints should look like, and how to verify
responses on the server. Lexicon had to discover each of these itself. RevenueCat's docs are praised because most pages
are "you want X, do this". See `docs/plans/2026-10-25-passki-dx-lexicon-case-study.md`, sections 5.2, 5.3, 6.3, 6.4
and 7.

## What Changes

New pages under `docs/guides/`, linked from the README:

- **Passkeys as an extra sign-in** (recipe): accounts from another sign-in method, passkey created in Settings,
  optional sign-in button, list / delete / relabel with signals; client and server, end to end.
- **What to show users**: one row per `PasskeyError` (and category): what to show, what to log, retry or not.
- **Choosing your RP ID**: permanence, apex vs subdomain, one RP ID per product, web app domain, well-known host.
- **Choosing user name and display name**: random, stable user handle per account; Hide My Email addresses; relabel
  with `currentUserDetails`.
- **Recommended server API**: endpoints, request/response shapes (response JSON as the request body), 404 → forget,
  auth and rate limits.
- **Verifying on the server**: webauthn4j and java-webauthn-server examples, each with a `FakePasskeys` test.
- **Troubleshooting**: symptom-first ("AND-03 but assetlinks.json is right", "works in debug, fails from Play",
  "web says the passkey didn't work").
- **Upgrading users**: when `upgrade()` applies, including after Google or Apple sign-in, per platform.

A first look that doesn't overwhelm (from `docs/plans/2026-10-26-passki-launch-readiness.md`, sections 1 and 2):

- **Short README.** Today it is 602 lines with a 13-entry table of contents and four "Get started" paths. The new
  README stays under 150 lines: tagline, one GIF pair, install, one quickstart (your server, then setup with or without
  the plugin as equal options), a "Next" table linking the pages, and the licence. Everything else moves to `docs/`
  pages without losing content. The first screen says `FakePasskeys` produces responses real server libraries verify.
- **Quickstart page** (`docs/quickstart.md`): from an empty KMP app with its own server to a first verified passkey,
  one section each for Android, iOS and web. This is the page the launch test times.
- **Security model page** (`docs/security-model.md`): what PassKi does and what stays your server's job. PassKi never
  verifies responses and never stores credentials; it makes no network calls except `PasskeyDoctor` fetching your
  well-known files when you run it (`passki-demo` talks to the demo server, the Gradle plugin's check tasks fetch your
  files); no telemetry; secrets stay out of `toString()` and logs. A CI check keeps the network claim true.
- **`llms.txt`** served by passki-web at `/llms.txt`: a short index of the docs pages and check pages for AI coding
  assistants, checked for broken links in CI.

Docs only: no spec-level behaviour changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. (`skip_specs: true`: this change adds documentation only.)

## Impact

- `docs/guides/*.md`, `docs/quickstart.md`, `docs/security-model.md` and the pages that receive the README's moved
  sections (new); `README.md` (rewritten); `docs/setup.md`, `docs/platforms.md`, `docs/compose.md` (links to moved
  README anchors).
- `passki-web/public/llms.txt` (new); `ci/check-docs-links.py` (README length limit, `llms.txt` links); new
  `ci/check-network-use.py`.
- Code snippets in the guides are compiled by the existing docs snippet check in CI.
- Pages that describe APIs from other changes (`fold`, `category`, `PreferImmediate`, `syncAccount`, logging) are
  written against the API that exists when each page lands, and updated by those changes.
