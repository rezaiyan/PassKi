# Tasks

## 1. Snippet checking for guides

- [ ] 1.1 Extend `ci/doc-snippets.py` to scan `docs/**/*.md` except `docs/plans/`; verify `python3 ci/doc-snippets.py` still passes and reports the new file count
- [ ] 1.2 Add a server source set to `ci/doc-snippets` with webauthn4j and java-webauthn-server, and route `kotlin server` blocks to it; verify a sample server block compiles and a deliberately broken one fails the script

## 2. Guides

- [ ] 2.1 Write `docs/guides/extra-sign-in.md` (client and server, settings list / delete / relabel, optional button); verify every snippet compiles with `python3 ci/doc-snippets.py`
- [ ] 2.2 Write `docs/guides/what-to-show-users.md` with one row per `PasskeyError`; verify a test or script fails if a `PasskeyError` subtype is missing from the table
- [ ] 2.3 Write `docs/guides/choosing-rp-id.md`; verify it links the AND-01 / IOS-01 check pages and `docs/setup.md`
- [ ] 2.4 Write `docs/guides/user-names.md` (user handle rules, Hide My Email, relabel); verify its server snippet compiles
- [ ] 2.5 Write `docs/guides/server-api.md` (endpoints, shapes, 404 → forget, auth, rate limits); verify it matches `passki-demo`'s client calls or lists the differences
- [ ] 2.6 Write `docs/guides/verify-on-server.md` with webauthn4j and java-webauthn-server examples, each with a `FakePasskeys` round-trip test; verify both compile as `kotlin server` blocks
- [ ] 2.7 Write `docs/guides/troubleshooting.md` (symptom-first, linking check pages); also cover AASA served without `application/json` or behind a redirect, and deploys that mirror a build folder and wipe well-known files; verify every check ID it names exists in `docs/checks/catalog.json`
- [ ] 2.8 Write `docs/guides/upgrading-users.md`; verify each platform statement cites its source and the date checked

## 3. README and first look

- [ ] 3.1 Move every README section to its page per design decision 6 (`docs/try-it.md`, `docs/concepts.md`,
  `docs/usage.md`, `docs/testing.md`, and the existing `platforms.md`, `compose.md`, `extensions.md`, `setup.md`);
  verify a script lists each old README heading and the page that now holds it, with none missing
- [ ] 3.2 Rewrite `README.md` under 150 lines: tagline, GIF pair, install, one quickstart (own server; setup with the
  plugin or by hand as equal options), the `FakePasskeys` line on the first screen, a "Next" table (quickstart,
  guides, concepts, usage, platforms, troubleshooting, security model, API reference) and licence; verify
  `wc -l README.md` is at most 150 and `python3 ci/doc-snippets.py` passes
- [ ] 3.3 Update links to old README anchors in `docs/`, passki-web and Lexicon; verify `python3 ci/check-docs-links.py`
  passes and `grep -rn 'README.md#'` in all three repositories finds only anchors that exist
- [ ] 3.4 Add the 150-line README limit to `ci/check-docs-links.py`; verify a 151-line README fails it
- [ ] 3.5 Write `docs/quickstart.md` (empty KMP app with its own server → first verified passkey; Android, iOS and web
  sections); verify every snippet compiles and follow it once on the Android emulator, iOS simulator and Chrome,
  recording the time taken

## 4. Security model

- [ ] 4.1 Add `ci/check-network-use.py` (design decision 9) and run it in the `library` CI job; verify it passes on
  `main` and fails when `java.net.URL(...).openConnection()` is added to a non-doctor file
- [ ] 4.2 Write `docs/security-model.md` (never verifies, never stores, network only in `PasskeyDoctor` and the Gradle
  check tasks, no telemetry, secrets out of `toString()` and logs, what the server must check); verify each claim
  links to the code, test or script that backs it

## 5. llms.txt

- [ ] 5.1 Add `passki-web/public/llms.txt` per design decision 10, listing the quickstart, guides, concepts, usage,
  platform, troubleshooting, security model and check pages; verify `npm run build` serves it at `/llms.txt`
- [ ] 5.2 Teach `ci/check-docs-links.py` to resolve `llms.txt` links to repository files; verify a dead link in
  `llms.txt` fails it
