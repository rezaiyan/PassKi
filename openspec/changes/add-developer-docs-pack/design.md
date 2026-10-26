# Design

## Context

- Docs live in `README.md` and flat `docs/*.md` pages (`setup.md`, `platforms.md`, `compose.md`, `autofill.md`, …);
  check pages in `docs/checks/`; plans in `docs/plans/`.
- `ci/doc-snippets.py` compiles every Kotlin block and configures every `passki { }` block, but only for
  `README.md` and `docs/*.md` (not subdirectories). Its prelude (`ci/doc-snippets/src/jvmMain/kotlin/Prelude.kt`)
  supplies placeholders like `api` and `log`. It has no server-side dependencies.
- Lexicon's code (client and server) is the worked example for most pages.

## Goals / Non-Goals

**Goals:**
- Situation-first pages that a developer can follow without reading PassKi's internals.
- Every Kotlin example on those pages compiled in CI, including server examples.

- A README a newcomer can read in two minutes, with nothing removed from the docs as a whole.

**Non-Goals:**
- A docs website or generator (pages stay Markdown in the repo; hosting is part of `harden-release-process`).
- Documenting `passki-server` (that change brings its own docs).

## Decisions

1. **`docs/guides/` for task pages.** Keeps reference pages (`setup.md`, `platforms.md`) separate from recipes;
   the README links each guide in a "Guides" table.
2. **Extend the snippet check to `docs/**/*.md`,** excluding `docs/plans/`, so guides are compiled too.
3. **Server snippets get their own source set.** Add a `server` source set (or module) to `ci/doc-snippets` with
   webauthn4j and java-webauthn-server on the classpath, and route code blocks tagged ` ```kotlin server ` there.
   Alternative: mark server examples as not compiled, rejected because server code is where copy-paste bugs hurt most.
4. **Write against the API that exists.** Pages use today's API; each API change in this plan (results, logging,
   signals, prefer-immediate) owns updating the guide text it affects, which the snippet check enforces.
5. **Lexicon as the example, anonymised.** Use `example.com`, `com.example.app` and neutral names; keep Lexicon's
   decisions (Hide My Email label, 404 → forget) as the recommended behaviour.

6. **README sections move, they don't disappear.** Mapping: "Get started" A and B (sample, demo server) →
   `docs/try-it.md`; "Words used here" and "How it works" → `docs/concepts.md`; "Usage" detail, the call table and the
   error table → `docs/usage.md`; Platforms, Compose, PRF / largeBlob, Testing, Setup and "Good to know" → the existing
   `platforms.md`, `compose.md`, `extensions.md`, `setup.md` and a new `docs/testing.md`; "Logging" → a new `docs/logging.md`; "Sample app" →
   `docs/try-it.md`. Links in the repo and in passki-web that point at old README anchors are updated in the same
   change; `ci/check-docs-links.py` already fails on dead anchors.
7. **The README length limit is enforced.** `ci/check-docs-links.py` fails when `README.md` exceeds 150 lines, so the
   README can't grow back by accretion; a deliberate increase changes the limit in the same commit.
8. **Quickstart assumes the app has its own server.** That's the common case (Lexicon, and every app that already has
   accounts). The demo-server path stays available in `docs/try-it.md` for evaluation.
9. **Security model claims are checked, not just written.** `ci/check-network-use.py` fails if a network API
   (`HttpURLConnection`, `URLConnection`, `NSURLSession`, `fetch(`, Ktor clients) appears in `passki/src` outside the
   doctor's fetchers (`android/UrlConnectionGetter.kt`, `apple/NSUrlSessionGetter.kt`; the web doctor makes no requests). The "no secrets in
   logs" claim links to the redaction test from `add-passkey-logging` once it lands.
10. **`llms.txt` follows the llmstxt.org format** (title, one-line summary, sections of links with one-line notes),
    lives in `passki-web/public/` so Next.js serves it statically, and links to GitHub URLs of the Markdown pages.
    `ci/check-docs-links.py` resolves each link to a repository file.

## Risks / Trade-offs

- [Moving README sections breaks links from outside the repo (Lexicon, blog posts)] → PassKi isn't announced yet, so
  the only known external links are Lexicon's; check its repository for README anchors and update them.
- [The network check misses a new network API] → The pattern list is in one place in the script; the security model
  page names the script so reviewers know it exists.

- [Guides drift from the API] → Snippet compilation in CI; each API change lists the guide in its tasks.
- [Upgrade-after-federated-sign-in facts are platform-dependent and may change] → The page cites the platform docs it
  relies on and records the date checked; if a platform behaviour can't be confirmed, the page says so instead of
  guessing.

## Migration Plan

None (docs).
