# Versioning and deprecation

What each PassKi version promises, so you can decide what to depend on.

## Before 1.0: alphas and betas

| Stage | Versions | What may change | How you find out |
|---|---|---|---|
| **Alpha** (now) | `0.1.0-alphaNN` | Anything, including breaking API changes, in any alpha | Every breaking entry in the [CHANGELOG](../CHANGELOG.md) is marked **BREAKING** and has a "Migrating from" block with before and after code |
| **Beta** | `0.x.0-betaNN` | No breaking change without a deprecation first: the old API keeps working, marked `@Deprecated`, for at least one more minor version | The CHANGELOG and the compiler warning |
| **1.0** | `1.x.y` | Semantic versioning: no breaking change before 2.0 | — |

Alphas are small and frequent: a finished fix is released, not held for a bigger version. Pin an exact version (the
[BOM](usage.md#install) makes that one number) and read the CHANGELOG before you upgrade.

## What counts as breaking

- Removing or renaming a public declaration, or changing a parameter's type or order.
- Renaming a parameter (it breaks calls with named arguments; the CHANGELOG says "BREAKING (named arguments)").
- Changing documented behaviour, such as which error a situation returns (the CHANGELOG says "BREAKING (behaviour)").
- Raising the minimum Kotlin, Android SDK, iOS or Gradle version an app needs.

Not breaking, in any release:

- **New error subtypes and enum entries** where a type is documented as **Open** (its KDoc starts a paragraph with
  "Open:"): keep an `else` branch in a `when` over `PasskeyError`. Types documented as **Closed**, such as
  `PasskeyErrorCategory` and `PasskeyResult`, never grow, so a `when` over them needs no `else`.
- **New members of interfaces** you may implement (`Passkeys`, `PasskeySignals`): they always come with a default
  implementation.
- **New fields in result types:** their constructors are `@InternalPasskiApi`, so PassKi can add fields without
  breaking binaries.
- **Better error messages and fix hints:** match on types, `category` and `checkId`, never on message text.

Each module's public API is recorded in `*/api/` dumps, and CI fails when it changes without the dump being updated,
so no change slips into a release unnoticed.

## Deprecations

From the first beta on, an API that changes is first deprecated:

```kotlin
@Deprecated("Renamed to formatLabel.", ReplaceWith("formatLabel(name)"))
fun label(name: String): String = formatLabel(name)

fun formatLabel(name: String): String = name.trim()
```

`ReplaceWith` lets the IDE fix call sites for you. A deprecation stays for at least one minor version (a whole major
version after 1.0) before the old API is removed. Some changes can't be deprecated in place, for example a renamed
parameter, since two functions that differ only in a parameter name have the same JVM signature; those wait for the
next breaking window, and the CHANGELOG says so.

## Toolchain floor

PassKi is built so apps on older toolchains can still use it: Kotlin 2.1+ on Android and Kotlin 2.2+ on iOS and the
web ([requirements](usage.md#install)). Dependency updates that would raise that floor are held back, and CI builds two
consumer apps on the oldest supported toolchains against every change. Raising the floor is a breaking change.
