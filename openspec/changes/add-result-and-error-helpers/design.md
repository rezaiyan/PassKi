# Design

## Context

- `PasskeyResult` (`passki/src/commonMain/.../PasskeyResult.kt`) is a sealed interface with `Success` / `Failure` data
  classes and `onSuccess`, `onFailure`, `getOrNull`, `getOrThrow`, `map`.
- `PasskeyError` already has `fixHint`, `checkId` and `docsUrl` on the base class; only `Misconfigured` and `Unknown`
  override `toString()`, and neither prints `docsUrl`.
- Result classes have `@InternalPasskiApi` constructors, so adding a constructor parameter is source- and
  binary-compatible for adopters (see `passkey-ceremonies` "Binary-compatible public types").
- Each platform already parses the options (`WebAuthnOptions`) and so knows the RP ID; the web implementation knows the
  page host when the options omit it.

## Goals / Non-Goals

**Goals:**
- Delete the adapter, RP ID re-parse and most of the error `when` from a typical app.

**Non-Goals:**
- Arrow / `Either` integration (apps can `fold` into it).
- Changing `PasskeyError` subtypes or messages.
- A "what to show users" copy deck in code (that's a docs page, see `add-developer-docs-pack`).

## Decisions

1. **Mirror `kotlin.Result` names.** `fold`, `recover`, `isSuccess`, `exceptionOrNull`-style `errorOrNull` are what
   Kotlin developers try first. `mapError` is not in `kotlin.Result` but is the common name (Arrow `mapLeft`, Rust
   `map_err`). All are `inline` where they take lambdas, like the existing helpers.
2. **`isSuccess` / `isFailure` as extension properties**, not members, so `PasskeyResult` (an interface adopters may
   pattern-match) gets no new abstract members.
3. **Category is a closed enum with a computed property on `PasskeyError`.** A closed set is what makes `else`-less
   `when` safe; the open set stays `PasskeyError` itself. `InvalidOptions` goes to `Setup` because a developer must fix
   it (the server or the options), the same audience as `Misconfigured`. Alternative: a `retryable` boolean only,
   rejected as too little for UI decisions.
4. **`rpId` is non-null `String`.** Every successful ceremony has one; on the web it comes from the options or
   `window.location.hostname` (the effective domain WebAuthn uses by default). Put it last in the constructors.
5. **`toString` format.** Append `, docsUrl=<url>` to the existing `Misconfigured` / `Unknown` / `InvalidOptions`
   strings; data objects keep their default names.

## Risks / Trade-offs

- [The category for a future error is debatable] → The KDoc of each new subtype states its category; the
  `passkey-errors` scenario lists the mapping and a test pins it.
- [`recover` hides real errors] → KDoc example limited to `NoCredentials`; README keeps `fold` as the main pattern.
- [ABI dump churn] → One `updateLegacyAbi` in the same commit.

## Migration Plan

Additive. Apps can delete `rpIdOf`-style helpers and their `when` adapters at their own pace.
