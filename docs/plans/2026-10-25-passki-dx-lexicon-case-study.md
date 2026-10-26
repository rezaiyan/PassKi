# PassKi developer experience: the Lexicon case study

**Date:** 2026-10-25 (revised the same day after a code review of both repositories) · **PassKi version:** 0.1.0-alpha04
(alpha05 unreleased) · **Integration:** Lexicon (KMP: Android, iOS, wasmJs) + its backend (Spring Boot, webauthn4j 0.30)

This document records what it took to add passkeys to a real production app with PassKi, and turns each friction point
into a concrete proposal for the API, docs, tooling or release process. The goal is for PassKi to feel like RevenueCat
or SimpleWebAuthn: one obvious way to do each thing, a good error when you get it wrong, and no glue code that every
app rewrites.

It is the **second round** of feedback. Round 1 (the first integration, 2026-10-24) already shipped in 0.1.0-alpha04
(section 13), so it isn't raised again.

**How this was checked.** Every claim about PassKi was checked against `main` at `4571df6`, and every claim about
Lexicon against the code in `~/projects/Lexicon` and `~/projects/lexicon.server`. Where the first draft was wrong, the
text below is corrected and the correction is listed in section 14.

**Bias to keep in mind.** One app, built by PassKi's own author. Internal knowledge hid some friction a first-time user
would hit (finding the right page, understanding the words), so the docs findings are probably undercounted. The next
case study should be someone else's app.

---

## 1. TL;DR

| | Verdict |
|---|---|
| **Client API** | Strong. `Passkeys()` with no Activity or UIWindow, typed `PasskeyError`, `checkId` / `fixHint` / `docsUrl` are the best parts. Web support in alpha04 needed **zero** Lexicon code changes. |
| **Testing** | Excellent. `FakePasskeys` produces real ES256 responses that webauthn4j accepts, so the server got real round-trip tests for free. |
| **Server side** | The biggest gap. PassKi stops at "your server checks the response". Lexicon wrote **657 lines** of server code (plus ~40 lines of config) and 292 lines of tests for this. |
| **Glue on the client** | Lexicon's `PassKiAuthenticator` is 90 lines, and about half of it is code every app will write: a sign-in mode decision with a retry, re-parsing the RP ID from the options, a result adapter, a log line and an error mapping. |
| **Setup and diagnosis** | Tooling is good, but Lexicon used **none** of it: no Gradle plugin, no `PasskeyDoctor`, no `passkiCheck`. The tooling assumes you adopt the plugin; a "bring your own server" app doesn't find its way in. |
| **Hardest bug** | One Android emulator image failed every passkey creation with AND-03 although the setup was correct. The hint sent us chasing the wrong cause for hours. |
| **Release process** | alpha04 was tagged but not released on Maven Central, so Lexicon's CI couldn't resolve it. Fixed since (`ed89c7c`), but the README badge still says alpha03 and nothing checks that a release resolves. |

**Top 5 changes, in order of impact:**

1. **AND-03 that tells the truth**: include the running app's SHA-256 and `android:apk-key-hash:` origin in the error,
   and name emulator images known to fail. Small, and it would have saved the most time. (Section 6.1)
2. **Logging and a debug mode** like RevenueCat's `logLevel` / `logHandler`, so setup errors show up in logcat with their
   fix and docs link without each app writing a log line. (Section 4.5)
3. **`rpId` on the results** and **result helpers** (`fold`, `isUserCancellation`, `category`), which delete most of
   Lexicon's adapter code. (Sections 4.2, 4.4, 4.6)
4. **A docs pack for the common case**: "Passkeys as an extra sign-in" end to end, "What to show users", "Choosing your
   RP ID", "Choosing user name and display name", and Doctor in path D. (Section 7)
5. **A decision on the server side**: a short design doc for `passki-server` (or for officially recommending
   java-webauthn-server), then a small first version. (Section 5.1)

---

## 2. What Lexicon built

Passkeys are an *extra* sign-in. Accounts are still created with Google or Apple; a signed-in user adds a passkey in
Settings and later signs in with it from the auth screen. Settings also lists, deletes and relabels passkeys, and tells
the device's credential provider about deletions (signals).

| Area | Lexicon code | PassKi used |
|---|---|---|
| Device ceremony | `PassKiAuthenticator` (90 lines) behind a domain `IPasskeyAuthenticator` (35) | `Passkeys`, `SignInMode`, `PasskeyError`, `signals` |
| Client network, repo, use cases, ViewModel, Settings UI | 672 lines in total on the client, including the authenticator | none (by design) |
| Auth screen | Its own `PasskeySignInButton` and double-tap guard in `AuthViewModel` | not `passki-compose` (section 4.9) |
| Server | Options, verification, challenge store, DTOs, well-known controller, account labels, migration: 657 lines, plus ~40 lines of origin config in `AppProperties` | none |
| Web (wasmJs) | Nothing: alpha04 just worked | `Passkeys()` on `navigator.credentials` |
| Tests | Client: 480 lines, `PassKiAuthenticatorTest` on `FakePasskeys` plus a domain-level fake. Server: 292 lines, `PasskeyServiceTest` with real round trips | `FakePasskeys`, `getOrThrow` |
| Setup | Well-known files generated by the server from config, iOS entitlement by hand | nothing (no plugin, no Doctor, no passkiCheck) |

**Timeline:** 8 client commits and 4 server commits, all within 2026-10-24/25.

---

## 3. What worked well (keep it)

These are the parts to protect while changing everything else.

- **`Passkeys()` with no platform handles.** The single best DX decision. It made a clean domain interface possible
  and kept the DI module to one line: `single<IPasskeyAuthenticator> { PassKiAuthenticator() }`.
- **Options JSON in, response JSON out.** The server sends options as a JSON tree; the app never models them. PassKi
  accepts the `{"publicKey": ...}` wrapper too. No mapping layer on the client.
- **Typed errors with `checkId`, `fixHint` and `docsUrl`.** Mapping `Misconfigured` to its own domain error
  ("Passkeys aren't working right now", not "your device can't") was possible only because PassKi separates "your setup
  is wrong" from "the device can't". Stripe puts a `doc_url` on API errors for the same reason.
- **`FakePasskeys` on the JVM server.** The server's `PasskeyServiceTest` does real register and sign-in round trips
  against webauthn4j. Most WebAuthn libraries leave you hand-crafting CBOR. This is a differentiator: say so louder,
  on the README's first screen.
- **Web for free.** Upgrading to alpha04 needed a version bump and two comment changes. That is what "multiplatform"
  should feel like.
- **Signals.** `unknownCredential`, `acceptedCredentials` and `currentUserDetails` are rare in any SDK. Lexicon used all
  three.
- **The README.** Paths A–D, "Words used here" and the button guidance are clear. The `ImmediateOnly` section prevented
  the "QR code for first-time users" bug before it happened.
- **Public API discipline.** `explicitApi()`, ABI validation dumps for every module, `@InternalPasskiApi` constructors
  and "keep an `else` branch" notes on sealed types. That is what lets the proposals below stay non-breaking.
- **CHANGELOG discipline.** Every change listed, with the reason.

---

## 4. Client API

### 4.1 The optional passkey button needs a policy, not a pattern to copy

**What happened.** The README recommends:

```kotlin
val mode = if (passkeys.capabilities().immediateSignIn) SignInMode.ImmediateOnly else SignInMode.Modal
```

In alpha04, Chromium reported `immediateGet` in `getClientCapabilities()` but rejected `mediation: "immediate"` as an
unknown enum value. PassKi surfaced that as `InvalidOptions`, so web sign-in failed with "Passkey didn't work". Lexicon
added a retry (`PassKiAuthenticator.kt:36-42`):

```kotlin
var result = passkeys.signIn(optionsJson, mode)
val rejected = (result as? PasskeyResult.Failure)?.error
if (mode == SignInMode.ImmediateOnly && (rejected is PasskeyError.InvalidOptions || rejected is PasskeyError.NotSupported)) {
    result = passkeys.signIn(optionsJson, SignInMode.Modal)
}
```

**Already fixed on `main` (`4571df6`, alpha05, unreleased).** The web implementation now probes `mediation:
"immediate"` once per page with an already-aborted request, reports `immediateSignIn = false` when the browser doesn't
accept it, maps a leftover `TypeError` to `NotSupported` and remembers it. With alpha05 the README one-liner works in
Chromium, and Lexicon's retry should drop `InvalidOptions` from its condition (otherwise genuinely bad server options
quietly fall back to the full sheet).

**What's still open.** The one-liner is a policy that every app copies: "device passkeys only where that works, else the
full sheet". On Android and iOS `immediateSignIn` is always true when passkeys work at all, so the policy only matters on
the web, where the fallback (`Modal`) can still show a QR code to a first-time user. That trade-off belongs in PassKi, in
one place:

- Add `SignInMode.PreferImmediate` (name open: `DeviceFirst`), documented as "`ImmediateOnly` where the platform
  really supports it, otherwise `Modal`", with the capability check and the `NotSupported` fallback inside PassKi.
- In the README, say plainly what happens on browsers without immediate mediation (and list which ones, in
  `docs/platforms.md`), so an app can choose to hide the button there instead.
- Let `FakePasskeys` simulate a platform without immediate mediation (`FakePasskeys(immediateSignIn = false)`), so apps
  can test both branches.

### 4.2 The results don't say which RP ID they belong to

**What happened.** After sign-in, Lexicon needs the RP ID for signals (forget a passkey the server no longer knows).
`PasskeyAssertion` has no `rpId`, so Lexicon re-parses the options JSON (`PassKiAuthenticator.kt:63-68`):

```kotlin
/** PassKi already refused options without an RP ID, so a successful sign-in always has one. */
private fun rpIdOf(optionsJson: String): String {
    val options = Json.parseToJsonElement(optionsJson).jsonObject
    val publicKey = options["publicKey"]?.jsonObject ?: options
    return publicKey["rpId"]?.jsonPrimitive?.content.orEmpty()
}
```

PassKi already parsed exactly this. Worse, the comment stopped being true in alpha04: on the web the RP ID may be left
out (it defaults to the page's host), and this returns `""`.

**Proposal.** Add `rpId: String` to `PasskeyAssertion` and `PasskeyRegistration`: the RP ID from the options, or on the
web the one the browser used. The constructors are `@InternalPasskiApi`, so this is non-breaking. (`PasskeyAssertion`
already has `userHandle`, so nothing else is missing for signals.)

### 4.3 Signals say `userId` but mean the WebAuthn user handle

```kotlin
suspend fun acceptedCredentials(rpId: String, userId: String, credentialIds: List<String>)
suspend fun currentUserDetails(rpId: String, userId: String, name: String, displayName: String)
```

In an app, "user id" means the account id. Here it must be the base64url WebAuthn user handle (`user.id` from the
creation options), which Lexicon deliberately keeps random and separate from the account id. Passing the account id
"works" without an error and silently does nothing, or leaks an internal id to the credential provider. Lexicon's own
interface already calls it `userHandle`, and so does `PasskeyAssertion.userHandle`.

**Proposal.**

- Rename the parameter to `userHandle`. A deprecated overload with the old name isn't possible: two functions that
  differ only in a parameter name have the same JVM signature and don't compile. In an alpha, rename it and say so in
  the CHANGELOG; only callers using named arguments break.
- KDoc: "base64url user handle, the `user.id` of the creation options; never your account id or email".
- Later, consider value classes (`UserHandle`, `CredentialId`). They make mix-ups a compile error and would allow a
  deprecation overload if a rename is ever needed after 1.0. At minimum, validate base64url and fail with
  `InvalidOptions` and a hint when the input isn't.

### 4.4 Converting `PasskeyResult` needs a `when` every time

`PasskeyResult` has `onSuccess`, `onFailure`, `map`, `getOrNull` and `getOrThrow`. Every app with its own result type
(Lexicon's `Try<T>`, `kotlin.Result`, Arrow's `Either`) still writes the same adapter:

```kotlin
private inline fun <T, R> PasskeyResult<T>.toTry(ceremony: String, transform: (T) -> R): Try<R> = when (this) {
    is PasskeyResult.Success -> Try.success(transform(value))
    is PasskeyResult.Failure -> Try.failure(error.toDomain(ceremony))
}
```

**Proposal.** Complete the set that `kotlin.Result` users expect: `fold(onSuccess, onFailure)`, `mapError`,
`recover`, `isSuccess` / `isFailure`, `errorOrNull()`, and `toResult(): Result<T>` (failure wrapped in the existing
`PasskeyException`). With `fold`, the adapter above is one line.

### 4.5 No logging: every app writes its own setup log line

Lexicon logs failures itself so they can be found in logcat (`PassKiAuthenticator.kt:70-75`):

```kotlin
if (this !is PasskeyError.Cancelled) {
    val hint = (this as? PasskeyError.Misconfigured)?.let { " fix=${it.fixHint} check=${it.checkId}" }.orEmpty()
    logPlatform(TAG, "Passkey $ceremony failed: $this$hint")
}
```

Two things show the DX problem:

- The cast isn't needed: `fixHint`, `checkId` and `docsUrl` are on every `PasskeyError`. The cast also skips the
  `fixHint` of `InvalidOptions`.
- `Misconfigured.toString()` already prints `fixHint` and `checkId`, so this line prints them twice, but never prints
  `docsUrl`. `PasskeyException.message` is the only string with everything in it, and nothing points there for logging.

Without such a line, an AND-03 in a release build is invisible: the user sees "Passkeys aren't working right now" and the
developer sees nothing. RevenueCat solves this with `Purchases.logLevel` and a pluggable `Purchases.logHandler`, and its
debug logs are written to be read by a developer.

**Proposal.**

- `PassKi.logLevel` (`Error` by default, `Debug` in development) and `PassKi.logHandler` to route into Timber, Kermit,
  Crashlytics or `println`. A process-wide setting is fine here: RevenueCat does the same, and there is one device.
- At `Error`, log every error with a `fixHint` (setup and options) and every `Unknown` once, with check ID, fix and docs
  link. At `Debug`, log each ceremony: mode, RP ID, capabilities used, platform error type and how it was mapped.
- Never log challenges, full credential ids, user handles or response JSON (truncate or hash). Write that rule into
  CONTRIBUTING.
- Make every `PasskeyError.toString()` include `docsUrl` when there is one, so `"$error"` is always the full story.

### 4.6 Every app writes the same error → UI mapping

Lexicon's mapping (`PassKiAuthenticator.toDomain`) will be the same in most apps:

| PassKi error | Lexicon shows |
|---|---|
| `Cancelled`, `Interrupted` | nothing |
| `NoCredentials` | neutral notice: "No passkey on this device yet" |
| `AlreadyRegistered` | notice |
| `NotSupported` | hide the passkey actions |
| `Misconfigured` | "Passkeys aren't working right now" (our fault, not the device's) |
| `InvalidOptions`, `Unknown` | generic failure |

Lexicon's `when` is exhaustive with no `else`, although the `PasskeyError` KDoc asks for one. It compiles today and
breaks when alpha06 adds a subtype. The README snippets do show `else`; the app just didn't copy that part.

**Proposal.**

- Add `PasskeyError.isUserCancellation` (true for `Cancelled` and `Interrupted`) and a coarse `category`
  (`UserAction`, `NoCredential`, `Unsupported`, `Setup`, `Unexpected`). Document the category set as stable, so apps can
  `when` on it without an `else` while new subtypes keep landing in an existing category.
- Publish this table in the docs as "What to show users", RevenueCat-style: one row per error with *what to show*,
  *what to log*, *retry or not*. Today the README only covers `Cancelled`, `NoCredentials` and `Misconfigured`.

### 4.7 Signals: best effort, but reported as failures

Signals often return `NotSupported` (iOS before 26, some Android credential providers). Every caller ignores the
failure. Lexicon calls two signals back to back every time Settings loads the list (`acceptedCredentials`, then
`currentUserDetails`, in `GetPasskeysUseCase`).

**Proposal.**

- Add `signals.syncAccount(rpId, userHandle, credentialIds, name, displayName)`: one call for "here is the current
  truth about this account", running both signals and skipping what the platform can't do. Additive.
- Document that `NotSupported` from signals is expected and should never reach the user.
- Optionally, a `SignalOutcome.Applied / Ignored(reason)` return type would say that in the type system, but changing
  the existing return types is **breaking**. Do it only on new functions such as `syncAccount`, or before beta.

### 4.8 A ViewModel-friendly sign-in helper

`passki-compose`'s `rememberPasskeySignIn` handles double taps and drops `Cancelled`, but it is Compose state. Apps with
MVVM can't use it and re-implement the same guard: Lexicon has `if (currentState.creating) return` in
`PasskeysViewModel` and an `authMutex` in `AuthViewModel`.

**Proposal.** Move the logic to `passki` core as a plain class (`PasskeySignInController` or `Passkeys.signInOnce(...)`):
one request at a time, joins a running request, filters cancellation. The Compose helper then wraps it.

### 4.9 `passki-compose` wasn't adopted

Lexicon draws its own `PasskeySignInButton` (`AuthExperienceScreen.kt:357`) and doesn't depend on `passki-compose`. Two
reasons:

- The PassKi button owns the whole flow (`state` fetches options, `onSignedIn` gets the assertion), but in a layered app
  the ViewModel and use case own it, and the button should only be a button.
- It is a Material `Button`. Apps with their own design system want the passkey icon and string, not the chrome.

**Proposal.** Split it: a stateless `PasskeySignInButton(onClick, isLoading, enabled, modifier, content)` that apps can
style, plus the current stateful version on top of it. Ship the passkey icon (the FIDO Alliance one) as a
public `ImageVector`, and the recommended strings in the docs.

### 4.10 Small items

- `capabilities()` is `suspend` and Lexicon calls it twice per sign-in. It is cheap on Android and iOS, and the web probe
  is cached per page since alpha05. Say so in its KDoc.
- `FakePasskeys.failNextWith` takes a `PasskeyError`. A `failNextWith(platformError = ...)` that goes through the real
  mapper would test the mappers instead of bypassing them.
- Lexicon's server tests wrap every `FakePasskeys` call in `runBlocking`. A JVM test is fine with that, but a
  `FakePasskeys.blocking` view (or Java-friendly functions) would make the fake easier to use from Java/Spring test code,
  which is where most server tests live.

---

## 5. Server side: the biggest gap

PassKi says, correctly, that it never talks to your server. But the server half is where most of the work and most of
the security risk is. Lexicon's server needed:

| Piece | Lines | Notes |
|---|---|---|
| `PasskeyService` | 263 | Options, verification, user handle generation, credential record mapping |
| `PasskeyChallenges` | 60 | Single-use challenges bound to ceremony and user, TTL, size limit |
| `PasskeyDto` | 84 | `PublicKeyCredentialCreationOptions` / `RequestOptions` JSON as data classes |
| `PasskeyController` | 89 | 6 endpoints, rate limiting on the public ones |
| `PasskeyWellKnownController` | 38 | `assetlinks.json` and `apple-app-site-association` from config |
| `AppProperties.PasskeyConfig` | ~40 | RP ID, origins, `android:apk-key-hash` computation |
| `PasskeyAccountName` | 19 | Account label, avoiding Apple's Hide My Email address |
| Entity, repository, migration | 104 | |

Some of it is the kind of code that is easy to get subtly wrong:

- `CredentialRecordImpl(NoneAttestationStatement(), uvInitialized, backupEligible, backupState, signCount, attested, null, null, null, transports)`: ten positional arguments.
- webauthn4j throws `NullPointerException` and `IllegalArgumentException` on malformed input, so every parse needs a
  `catch (RuntimeException)` (round 1 put this in the snippet; every app still has to copy it).
- Challenge binding: a registration challenge must be tied to the user it was issued for, or one account's response
  could register a passkey on another. Lexicon keys challenges by value and checks ceremony, user and expiry on consume.
- The user handle must be the same for all of an account's passkeys, and `excludeCredentials` must list them, or a
  second passkey shows up as a second account in the credential provider.
- The Android origin formula (`android:apk-key-hash:` + base64url of the SHA-256) lives in app config code.

The plugin's `build/passki/server-snippets/` helps only if you run `passkiSetup`, and a snippet is code you own and
maintain forever.

### 5.1 Proposal: decide, then ship a small `passki-server`

**Decide first.** A server artifact changes what PassKi is ("never talks to your server"), and it carries more security
weight than anything on the client: a verification bug there is an account takeover. A one-page design doc should
answer:

- **Why not recommend java-webauthn-server (Yubico)?** It already has a `RelyingParty` builder, a `CredentialRepository`
  interface, `startRegistration` / `finishRegistration` and typed failure exceptions. The README already names it. If
  the answer is "it's good", the cheaper option is a guide plus `FakePasskeys` examples against it, not a new library.
- **What does PassKi add?** The parts no server library knows about: the Android origin from fingerprints, the
  well-known files from the same config, and the client/server contract with signals. Those are PassKi-specific and
  small.
- **Who owns security fixes?** A `SECURITY.md` with a reporting address and a supported-versions table is a
  precondition (section 8).

**Then ship it in stages**, modelled on SimpleWebAuthn (a browser package and a server package that share types). Stage
1 is a thin, opinionated layer over webauthn4j (or Yubico's library, if the design doc says so):

```kotlin
val rp = PasskiRelyingParty(
    rpId = "example.com",
    rpName = "Example",
    android = AndroidApps(packageName = "com.example.app", sha256 = listOf("AB:CD:...")), // origins derived
    extraOrigins = listOf("https://app.example.com"),
    challenges = InMemoryChallengeStore(ttl = 5.minutes), // or your own ChallengeStore (Redis, DB)
)

// Registration
val options: String = rp.creationOptions(user = PasskeyUser(handle, name, displayName), exclude = existing)
val result: PasskeyVerification<NewCredential> = rp.verifyRegistration(responseJson, expectedUserHandle = handle)
// → persist credential.id, publicKey (bytes), signCount, backupEligible, backupState, transports

// Sign-in
val requestOptions: String = rp.requestOptions()
val verified = rp.verifySignIn(responseJson, lookup = { credentialId -> repository.find(credentialId)?.toStored() })
// → update signCount / backupState / lastUsedAt

// Well-known files, from the same config, so they can never disagree with the origins
rp.assetLinksJson(); rp.appleAppSiteAssociationJson(iosAppIds = listOf("TEAM.com.example.app"))
```

Design rules:

- **Never throws on bad input.** Returns a typed failure (`Expired`, `UnknownCredential`, `BadSignature`, `Malformed`,
  `WrongUser`), so a 400 vs 404 vs 401 is the app's choice and no app returns a 500 by accident.
- **Stored credential is a plain data class** the app persists however it likes. No JPA, no Spring in the core.
- **Challenges bound to ceremony and user by default.** One user handle per account by default.
- **Tested against `FakePasskeys`** in its own suite, on every origin type (web, iOS, Android).
- **Stage 2, `passki-server-spring` (optional):** auto-configuration from `passki.*` properties, the two well-known
  endpoints and a `ChallengeStore` bean. Lexicon's well-known controller and config would disappear.
- **A `server-config` endpoint helper** that returns `{"rpId", "origins"}` in the shape `passkiCheck` already reads
  (`server.configUrl`), so SRV-01..03 work for apps that don't use the plugin's generated code.

### 5.2 Proposal: a documented backend contract

Lexicon had to invent the shapes of everything around the four core calls, and it chose different paths from the
README's example (`/registration-options`, `/sign-in-options` instead of `/passkeys/register/options`,
`/passkeys/login/options`). Most apps will need the same set:

- `GET /passkeys` returning the list **plus** what signals need: `rpId`, `userHandle`, `accountName`,
  `accountDisplayName` (Lexicon's `PasskeyListDto`).
- `DELETE /passkeys/{id}`, then `signals.unknownCredential` on the device.
- Sign-in with a deleted passkey: answer **404**, so the app knows to call `unknownCredential` (Lexicon does this).
- How to send the response: Lexicon wraps the JSON string in `{"responseJson": "..."}`, so it is JSON inside a string.
  Recommend posting the response JSON as the request body, and show both options.
- Which endpoints need auth, and which public ones need rate limiting (sign-in options and sign-in).

Publish it as "Recommended server API" with the request and response shapes, and use it in `passki-demo` and the
`passki-server` examples so the whole stack shares one contract.

### 5.3 Proposal: "Choosing user name and display name"

Lexicon learned that the account label shown in the passkey picker matters:

- Apple sign-in users often have a Hide My Email address (`@privaterelay.appleid.com`); showing it as `user.name` is
  useless to them. Lexicon shows "Name (Apple ID)" instead.
- `user.id` (the user handle) must be random, stable per account and never the account id or email.
- One handle per account, so a second passkey shows up as the same account in the provider.
- When the label changes later, `currentUserDetails` relabels passkeys already saved.

A short docs section (or a `PasskeyUser.forAccount(email, name)` helper in `passki-server`) would save every app this
discovery.

---

## 6. Setup and diagnosis

### 6.1 AND-03 on one emulator image: the most expensive bug

**What happened.** On a Pixel 9 Pro Fold AVD (Play services image, signed in, screen lock set), every passkey creation
failed with Play services 50152 "RP ID cannot be validated" → `Misconfigured`, AND-03. The hint says to list the app in
`assetlinks.json`. We tried screen lock, cache headers, `asset_statements` in the manifest and the Digital Asset Links
API (which confirmed the domain *was* linked). None of it mattered. PassKi's own sample on its own demo domain failed the
same way on that AVD. On a real phone it worked.

`docs/verified-on.md` records the same sample working on an Android 16 (API 36) emulator on 2026-10-23, so this is not
"emulators can't do passkeys". It is that image, or the Play services version on it. We didn't record either, so the
root cause is still unknown.

**Proposal.**

- **Evidence-based hint (P0).** PassKi can read the running app's signing certificate. Put its SHA-256 and the matching
  `android:apk-key-hash:` origin straight into the AND-03 message, so the fix can be copied from logcat or Crashlytics.
  This also catches the Play App Signing case (an easy step to forget before the first Play release) without the
  developer ever opening Play Console.
- **Record and reproduce.** Note the failing image (device profile, system image, Play services version) in
  `docs/verified-on.md`, and add a "Still failing?" section to `docs/checks/AND-03.md`: "If `PasskeyDoctor` passes
  AND-01 to AND-04 and the fingerprint in this error is in your `assetlinks.json`, try a real device or another emulator
  image."
- **Emulator note in the hint.** When the device is an emulator, append that line to the AND-03 hint. Word it as
  "some emulator images", not as the likely cause, so a real misconfiguration on an emulator still gets fixed.

### 6.2 The tooling assumes you adopt the plugin

Lexicon serves its well-known files from the server (generated from the same config as the accepted origins, so they
can't drift), and set the iOS entitlement by hand. Nothing in path D points to the tooling that would still help:

- `PasskeyDoctor` works without the plugin, but path D never mentions it.
- `passkiCheck` would check the live domain, AASA and Xcode entitlements in CI, but only through the plugin, whose main
  job (generating and uploading files) Lexicon doesn't want.

**Proposal.**

- Path D, step 6: "In debug builds, log `PasskeyDoctor.run(rpId)` at startup" with a three-line snippet.
- A check-only mode: `passki { rpId = "..."; checkOnly() }`, or a standalone CLI (`npx passki check example.com` or a
  downloadable jar), so any project can gate CI on the live domain without `passkiSetup`.
- A `PasskeyDebugOverlay` composable (like RevenueCat's debug overlay) for debug builds: Doctor report, capabilities,
  last error with fix and docs link.

### 6.3 Choosing the RP ID deserves its own section

Lexicon first used its company's apex domain, then moved to the app's own subdomain because the apex also listed
another project's apps. **Changing the RP ID orphans every passkey already created.** The README defines "RP ID" but
doesn't help choose one.

**Proposal.** A "Choosing your RP ID" section: it can't change later; apex vs subdomain; one RP ID per product, not per
company; the web app must be on that domain or a subdomain; and the well-known files must be on the RP ID host exactly.
`passkiSetup` could print a one-time warning when the RP ID looks like an apex shared with other apps.

### 6.4 Hosting gotchas worth a "Good to know" line

- An SPA catch-all that answers `/.well-known/*` with `index.html` and status 200. No check page mentions it today;
  AND-01/02 and IOS-01/02 should name it when the body is HTML.
- A web deploy that mirrors the build folder (deleting extra files) wipes well-known files kept in the web dist. Serve
  them from the server, or keep them out of the dist folder.
- AASA must be served as `application/json` with no redirect. Lexicon's Spring controller sets it explicitly; a static
  file server often doesn't.

---

## 7. Docs: the missing pages

The README is good at explaining PassKi. What's missing is pages that start from the app's situation. RevenueCat's docs
work because most pages are "you want X, do this", not "here is our API".

| Page | What it answers | Source in this case study |
|---|---|---|
| **Passkeys as an extra sign-in** (recipe) | The most common rollout: accounts from Google / Apple / password, passkey added in Settings, optional button on the sign-in screen, list / delete / relabel with signals. Client and server, end to end. | Sections 2, 4.1, 4.7, 5.2 |
| **What to show users** | One row per `PasskeyError`: what to show, what to log, retry or not | 4.6 |
| **Choosing your RP ID** | The decision you can't undo | 6.3 |
| **Choosing user name and display name** | User handle rules and account labels | 5.3 |
| **Recommended server API** | Endpoint shapes, 404 → forget, rate limits | 5.2 |
| **Verifying on the server** | webauthn4j and java-webauthn-server examples, each with a `FakePasskeys` test | 5.1 |
| **Troubleshooting** | Symptom-first: "AND-03 but assetlinks.json is right", "works on debug, fails from Play", "web says Passkey didn't work" | 6.1, 6.4 |
| **Upgrading users** | When `upgrade()` applies. The README says "after a password sign-in"; say whether it works after Google or Apple sign-in on each platform, since that is Lexicon's case | — |

Also:

- Put "Path D: ship without the plugin" on equal footing with the plugin path in the README's table of contents. For
  apps with an existing backend it is the main path, not the fallback.
- Lexicon's domain KDoc still says passkeys don't work on the web (`IPasskeyAuthenticator.kt:14`). Release notes that
  say "you can now delete X from your app" help users clean up after upgrades.

---

## 8. Release and open-source hygiene

| Issue | Proposal |
|---|---|
| alpha04 was tagged and uploaded `USER_MANAGED` but not released, so consumers' CI failed to resolve it (fixed by `ed89c7c`) | Release workflow ends by polling `repo1.maven.org` until the POMs resolve, and fails otherwise |
| README version badge still says `0.1.0-alpha03` | Use a live Maven Central badge (shields.io `maven-central/v`) instead of a hardcoded one |
| The Chromium fix (`4571df6`) sits unreleased while Lexicon carries a workaround | Release alpha05 now; small, frequent alphas are cheap with the automatic release |
| No `SECURITY.md` in a library that handles authentication | Add one: how to report a vulnerability privately, supported versions, response time. A precondition for `passki-server` |
| No issue or PR templates | Add a bug template that asks for platform, PassKi version, the check ID and the `PasskeyDoctor` report. Those are what every passkey bug needs |
| Four artifacts plus a plugin, versions kept in sync by hand | Publish a BOM (`passki-bom`) and show `platform(...)` in Install |
| API reference only via `./gradlew dokkaGenerate` | Host Dokka output on `passki.alirezaiyan.com/api/` per version |
| No `@sample` in KDoc | Link KDoc to compiled samples, so the IDE shows working code on hover |
| Breaking changes are allowed in alphas | Keep it, but add a "Migrating from X" block per CHANGELOG entry once there are external users, with `@Deprecated(replaceWith = ...)` where a signature allows it |
| Docs examples are compiled in CI (great) | Also compile the *recommended patterns* (button, error table, logging) as one small sample module, so a pattern like the `ImmediateOnly` snippet is tested on every platform, including web |

---

## 9. Benchmark: what developers praise in RevenueCat, SimpleWebAuthn and Stripe

| They do | PassKi today | Gap |
|---|---|---|
| One configure call, one entry point (RevenueCat) | `Passkeys()`, no handles | ✓ none |
| `logLevel` + `logHandler`, readable debug logs (RevenueCat) | none | **4.5** |
| Errors with a docs link (Stripe `doc_url`) and a "what to do" table per error code (RevenueCat) | `checkId` + `docsUrl` for setup errors | "What to show users" table and `category` (**4.6**) |
| Debug overlay in the app (RevenueCat) | `PasskeyDoctor` (code only) | **6.2** |
| The backend is done for you (RevenueCat) / a server package sharing types with the client (SimpleWebAuthn) | Snippets via the plugin | **5.1, 5.2** |
| Small stateless server functions: generate options, verify response (SimpleWebAuthn) | — | Shape for `passki-server` stage 1 |
| Coroutine **and** `Result` flavours | `PasskeyResult` with `onSuccess` / `onFailure` / `map` / `getOrThrow` | `fold`, `toResult` (**4.4**) |
| Test helpers | `FakePasskeys`, real signatures | ✓ ahead of all three |
| Recipe-first docs ("you want X, do this") | API-first README | **7** |
| Hosted, versioned API reference | Generate locally | **8** |
| Sample apps per platform | Compose sample with Demo, Lab, Setup tabs | ✓ |
| Changelog with migration notes | Detailed changelog | Add migration blocks later |
| Security policy and supported versions (all mature SDKs) | none | **8** |

The pattern behind RevenueCat's reputation: **the SDK owns the hard, boring parts end to end**, and when something is
misconfigured it tells you in the log, in plain words, before your users notice. PassKi already has the second half
(check IDs); the first half (server, logging) is the remaining gap.

---

## 10. Principles for the next releases

Use these to judge new APIs, not just the items above.

1. **Delete glue.** If every app writes the same five lines, they belong in PassKi. Lexicon's `PassKiAuthenticator` is
   the test: each proposal above should shrink it.
2. **The error is the documentation.** Every failure a developer can fix carries the fix, the evidence (fingerprint,
   origin, RP ID) and a link. Never make them guess between two causes.
3. **One obvious way.** Prefer one function with a good default over a pattern in the README.
4. **Quiet in production, loud in debug.** Logging off by default except setup errors; a debug mode that explains every
   decision.
5. **No silent no-ops.** If an argument is wrong (account id instead of user handle), fail with a hint.
6. **Additive by default.** `@InternalPasskiApi` constructors, `else` branches and ABI dumps make growth cheap; keep
   breaking changes for renames that prevent misuse.
7. **Plugin optional.** Every check, hint and doc must work for an app with its own server and its own well-known
   files.

---

## 11. Prioritised backlog

| # | Change | Area | Size | Breaking? |
|---|---|---|---|---|
| P0 | AND-03: running app's SHA-256 + origin in the hint; record the failing emulator image; "Still failing?" in AND-03.md | Android / docs | S | No |
| P0 | Release alpha05 (Chromium immediate fix, `4571df6`) | Release | S | No |
| P0 | Live version badge; release workflow verifies Maven Central resolution | Release | S | No |
| P0 | `rpId` on `PasskeyAssertion` / `PasskeyRegistration` | API | S | No |
| P1 | `logLevel` / `logHandler`; `docsUrl` in every `PasskeyError.toString()` | API | M | No |
| P1 | `fold`, `mapError`, `recover`, `toResult`, `isUserCancellation`, `category` | API | S | No |
| P1 | Docs: "Passkeys as an extra sign-in" recipe, "What to show users", "Choosing your RP ID", "Choosing user name and display name", Doctor in path D | Docs | M | No |
| P1 | `userHandle` naming in signals | API | S | Named-argument callers only |
| P1 | `SECURITY.md`, issue templates | OSS | S | No |
| P1 | `passki-server` design doc (own it vs recommend java-webauthn-server) | Server | S | — |
| P2 | `passki-server` stage 1 (JVM): relying party, challenge store, origins, well-known JSON, typed failures | Server | L | New artifact |
| P2 | Recommended backend contract (list / delete / 404 → forget / request body) | Docs | S | No |
| P2 | `SignInMode.PreferImmediate`; README note on browsers without immediate mediation | API / docs | S | No |
| P2 | `signals.syncAccount` | API | S | No |
| P2 | ViewModel-friendly `PasskeySignInController` in core | API | M | No |
| P2 | Stateless `PasskeySignInButton` + public passkey icon | Compose | S | No |
| P2 | `passkiCheck` check-only mode or standalone CLI | Tooling | M | No |
| P2 | `PasskeyDebugOverlay` composable | Compose | M | No |
| P2 | BOM, hosted Dokka, `@sample` in KDoc | Release | S | No |
| P3 | `passki-server-spring` auto-configuration | Server | M | New artifact |
| P3 | `FakePasskeys`: no-immediate platform, failures through the real mapper, blocking view for Java tests | Test | M | No |
| P0 | `SignalOutcome` return type on every signal (decided 2026-10-26: before launch, in `improve-signals-api`) | API | S | Yes, free before launch |
| — | `UserHandle` / `CredentialId` value classes: decided against on 2026-10-26 (`settle-core-api` design decision 1) | API | — | — |

---

## 12. Follow-ups in Lexicon

Not PassKi's job, but found during the review:

- After alpha05, drop `InvalidOptions` from the sign-in retry (`PassKiAuthenticator.kt:40`). Once `PreferImmediate`
  exists, the retry and the mode decision go away.
- Add an `else` branch to `PasskeyError.toDomain()`, as the `PasskeyError` KDoc asks.
- Log `"$this"` without the `Misconfigured` cast (or `PasskeyException(this).message` for the docs link), so
  `InvalidOptions` hints are logged too.
- Update `IPasskeyAuthenticator.isAvailable` KDoc: passkeys work on the web since alpha04.
- Record the failing AVD image and Play services version for section 6.1.

---

## 13. Already fixed

**After round 1, in 0.1.0-alpha04:**

- `passkiOrigin --sha256=...` for keys in no local keystore (Play App Signing).
- webauthn4j snippet: `ServerProperty.builder()`, `RuntimeException` guards around parsing.
- `passki-test` exposes `kotlinx-coroutines-core` as `api`.
- README: the "Sign in with a passkey" button section, testing your server, path D.
- `docs/setup.md`: by-hand setup, origin formula, App Store provisioning profile regeneration, Apple CDN cache.

**After alpha04, on `main`:**

- Auto-release to Maven Central after upload (`ed89c7c`).
- Web: `immediateSignIn` is probed instead of trusted; a leftover `TypeError` is `NotSupported`, not `InvalidOptions`
  (`4571df6`, alpha05).

---

## 14. Corrections to the first draft

The first version of this document (`bc73d02`) was reviewed against the code. Changed:

- **`userHandle` on the results** was listed as missing. `PasskeyAssertion` already has it; only `rpId` is missing.
- **Section 4.1** was written before `4571df6`. The web mapping and caching are done; what's left is the policy
  (`PreferImmediate`), moved from P0 to P2.
- **Section 4.3** proposed a deprecated `userId` alias. It can't compile (same JVM signature); the proposal is now a
  plain rename.
- **Section 4.5** said `Misconfigured` lacked the hint in its string. It has `fixHint` and `checkId`; only `docsUrl` is
  missing, and `fixHint` / `checkId` / `docsUrl` are on every `PasskeyError`.
- **Section 4.7** marked `SignalOutcome` as non-breaking. Changing existing return types is breaking.
- **Section 6.1** implied emulators can't validate RP IDs. `docs/verified-on.md` shows an API 36 emulator working;
  the failure is specific to one image.
- **Section 5.1** jumped to building `passki-server` without weighing java-webauthn-server, which the README already
  recommends. A design doc now comes first.
- Added: section 4.9 (`passki-compose` not adopted), section 7 (docs pages), the server request-body note, OSS
  hygiene (`SECURITY.md`, templates), principles, and Lexicon's own follow-ups.
