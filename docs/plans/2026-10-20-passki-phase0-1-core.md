# PassKi Core (Phase 0 + Phase 1 core) Implementation Plan

> **For agentic workers:** Use claude-kit:implement to execute this plan task-by-task.

**Goal:** Ship `passki` 0.1.0-alpha01: a single-dependency KMP passkey library. It has a clean common API, typed errors with fix hints, Android (Credential Manager) and iOS (AuthenticationServices) implementations, and `passki-test`, a fake authenticator whose output a real WebAuthn server verifier accepts.

**Architecture:**
- One public artifact, `passki`, targets android, iosArm64, iosSimulatorArm64, jvm, js and wasmJs. Unsupported targets return `PasskeyError.NotSupported`, so `commonMain` consumers compile everywhere.
- The public surface is the `Passkeys` interface, created with the `Passkeys()` factory. The JSON-in/JSON-out WebAuthn models live in `com.alirezaiyan.passki.internal`, gated by the `@InternalPasskiApi` opt-in so `passki-test` can reuse them.
- Platform handles are resolved automatically:
  - Android: Activity via an `androidx.startup` initializer
  - iOS: the key window

**Tech Stack:**
- Kotlin 2.4.20 KMP, AGP 9.1.1 (`com.android.kotlin.multiplatform.library`), Gradle 9.5.1
- kotlinx-serialization-json 1.11.0, kotlinx-coroutines 1.11.0
- androidx.credentials 1.6.0, androidx.startup 1.2.0
- cryptography-kotlin 0.6.0 (fake authenticator)
- webauthn4j-core 0.30.0.RELEASE (verification tests)
- vanniktech maven-publish 0.34.0

Spec: `.claude/plans/2026-10-20-kmp-passkey-library.md`

---

## Decisions taken (defaults; change before Task 1 if wrong)

| Decision | Value | Why |
|---|---|---|
| Maven group | `com.alirezaiyan.passki` | Matches the existing package. Needs alirezaiyan.com DNS verification on the Central Portal. |
| Artifacts | `passki`, `passki-test` | Single dependency is the DX goal |
| Code package | `com.alirezaiyan.passki` (API), `.internal`, `.android`, `.apple`, `.test` | |
| Android namespaces | `com.alirezaiyan.passki.core`, `com.alirezaiyan.passki.testing` | The app already uses `com.alirezaiyan.passki`; Android namespaces must be unique |
| License | Apache-2.0 | KMP ecosystem standard |
| Android minSdk | 24 (library). Passkeys need API 28+ at runtime and report `NotSupported` below that. | Template default; no consumer minSdk bump |
| iOS | Passkeys need 16+; upgrade needs 18+ (runtime checked) | |
| Sample RP ID | `alirezaiyan.com`, with `.well-known` files served by the existing Next.js site | No new infra |
| Version | `0.1.0-alpha01` | 0.1.0 final after follow-up Plans 2–3 |

## Scope

**In this plan:**
- Phase 0: competitor DX benchmark
- Project hygiene
- Core API, models and errors
- Android: register, signIn (modal / immediate-only), signInOrPassword, upgrade (conditional create), signals
- iOS: register, signIn, signInOrPassword, upgrade (iOS 18). Signals report `NotSupported` (Swift-only API).
- `passki-test`: CBOR, FakeAuthenticator, FakePasskeys, TestOptions, webauthn4j verification, recorded-fixture contract test
- Sample app, RP domain setup, manual E2E, README, publish config

**Follow-up plans (not here):**
- **Plan 2 — Tooling:** `passki-gradle-plugin` (generate/validate assetlinks.json + AASA, signing SHA check) and the `Passkeys.doctor()` runtime check
- **Plan 3 — UI & autofill:** `passki-compose` (`rememberPasskeys`, `PasskeySignInButton`, `PasskeyUsernameField`), `SignInMode.Autofill` on Android/iOS, the iOS Swift shim for `ASCredentialUpdater` signals, and PRF/largeBlob typed helpers
- **Plan 4+:** Web, Windows, macOS, handoff/Linux (spec phases 2–5)

## File Structure

```
gradle/libs.versions.toml                         (modify) new versions/libs/plugins
settings.gradle.kts                               (modify) include :passki, :passki-test
build.gradle.kts                                  (modify) apply-false serialization + publish plugins
gradle.properties                                 (modify) GROUP, VERSION_NAME
.gitignore, LICENSE, README.md                    (create)
docs/benchmarks/dx-comparison.md                  (create) Phase 0 output

passki/build.gradle.kts                           (create)
passki/src/commonMain/kotlin/com/alirezaiyan/passki/
  InternalPasskiApi.kt                            opt-in marker
  PasskeyError.kt                                 sealed errors + PasskeyException
  PasskeyResult.kt                                result type + extensions
  Passkeys.kt                                     interface + expect factory + value types
  internal/Base64Url.kt
  internal/WebAuthnJson.kt                        serializable option/response models + PasskiJson
  internal/WebAuthnOptions.kt                     parse + normalize server options
  internal/WebAuthnResponses.kt                   build response JSON + parse to typed results
  internal/SignalRequests.kt                      WebAuthn signal JSON builders
  internal/PasskeyCatching.kt                     exception → PasskeyResult bridge
  internal/UnsupportedPasskeys.kt                 NotSupported implementation
passki/src/commonTest/kotlin/com/alirezaiyan/passki/...Test.kt
passki/src/jvmMain/kotlin/com/alirezaiyan/passki/Passkeys.jvm.kt
passki/src/jvmTest/kotlin/com/alirezaiyan/passki/UnsupportedPasskeysTest.kt
passki/src/webMain/kotlin/com/alirezaiyan/passki/Passkeys.web.kt
passki/src/androidMain/AndroidManifest.xml
passki/src/androidMain/kotlin/com/alirezaiyan/passki/Passkeys.android.kt
passki/src/androidMain/kotlin/com/alirezaiyan/passki/PasskiAndroid.kt
passki/src/androidMain/kotlin/com/alirezaiyan/passki/android/ActivityTracker.kt
passki/src/androidMain/kotlin/com/alirezaiyan/passki/android/PasskiInitializer.kt
passki/src/androidMain/kotlin/com/alirezaiyan/passki/android/AndroidErrorMapper.kt
passki/src/androidMain/kotlin/com/alirezaiyan/passki/android/AndroidPasskeys.kt
passki/src/androidHostTest/kotlin/com/alirezaiyan/passki/android/AndroidErrorMapperTest.kt
passki/src/iosMain/kotlin/com/alirezaiyan/passki/Passkeys.ios.kt
passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/NSDataInterop.kt
passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/IosPlatform.kt
passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/AppleErrorMapper.kt
passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/AuthorizationSession.kt
passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/AppleRequests.kt
passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/IosPasskeys.kt
passki/src/iosTest/kotlin/com/alirezaiyan/passki/apple/NSDataInteropTest.kt
passki/src/iosTest/kotlin/com/alirezaiyan/passki/apple/AppleErrorMapperTest.kt

passki-test/build.gradle.kts
passki-test/src/commonMain/kotlin/com/alirezaiyan/passki/test/
  CborWriter.kt, FakeAuthenticator.kt, FakePasskeys.kt, TestOptions.kt
passki-test/src/commonTest/kotlin/com/alirezaiyan/passki/test/
  CborWriterTest.kt, FakePasskeysTest.kt
passki-test/src/jvmTest/kotlin/com/alirezaiyan/passki/test/
  WebAuthn4jVerificationTest.kt, RecordedFixturesTest.kt
passki-test/src/jvmTest/resources/fixtures/.gitkeep

shared/build.gradle.kts                           (modify) depend on :passki
shared/src/commonMain/kotlin/com/alirezaiyan/passki/App.kt              (replace) demo UI
shared/src/commonMain/kotlin/com/alirezaiyan/passki/DemoRelyingParty.kt (create)
docs/setup/well-known/assetlinks.json             (create) template
docs/setup/well-known/apple-app-site-association  (create) template
```

**Test commands used throughout:**
- Common logic: `./gradlew :passki:jvmTest`. This runs `commonTest` on the JVM; fastest loop.
- Android: `./gradlew :passki:testAndroidHostTest`
- iOS: `./gradlew :passki:iosSimulatorArm64Test` (needs Xcode 26.3, present)
- Fake: `./gradlew :passki-test:jvmTest`
- JS/Wasm *tests* need a headless browser and are not part of the gate. Compilation is the gate: `./gradlew :passki:assemble`.

---

## Phase 0

### Task 0: Competitor DX benchmark

**Files:**
- Create: `docs/benchmarks/dx-comparison.md`

- [ ] **Step 1: Clone both competitors outside the repo**

```bash
mkdir -p "$TMPDIR/passki-bench" && cd "$TMPDIR/passki-bench"
git clone --depth 1 https://github.com/AndroidPoet/passkeys-kmp
git clone --depth 1 https://github.com/szijpeter/webauthn-kotlin-multiplatform
```

- [ ] **Step 2: Measure each one.** For each repo, find the smallest sample code that performs register + sign-in from `commonMain`:
  - `grep -rn "create(\|authenticate(\|register\|signIn" --include=*.kt <repo>/sample* | head -40`

  Then record:
  - (a) number of Gradle dependencies a consumer must add
  - (b) lines of Kotlin for register + sign-in, including error handling
  - (c) whether platform handles (Activity/UIWindow) must be passed manually
  - (d) error type shape
  - (e) feature checklist: unified password, conditional create, signals, restore credentials, fake/test support, setup diagnostics

- [ ] **Step 3: Write `docs/benchmarks/dx-comparison.md`** using this exact structure. Fill each cell with the measured value from Step 2. The PassKi column holds the target from this plan.

```markdown
# DX Comparison (measured 2026-10-20)

| Metric | passkeys-kmp 0.2.0 | webauthn-kmp | PassKi target |
|---|---|---|---|
| Consumer dependencies | | | 1 (`passki`) |
| LOC register + sign-in (with errors) | | | ≤ 15 |
| Manual platform handles | | | No |
| Error type | | | sealed `PasskeyError` + `fixHint` |
| Unified password + passkey | | | Yes |
| Conditional create (upgrade) | | | Yes |
| Signal API | | | Yes (Android), iOS via Plan 3 |
| Fake authenticator for tests | | | Yes (`passki-test`) |
| Setup diagnostics | | | Plan 2 |

## Notes
- <observations per library, one bullet each>
```

- [ ] **Step 4: Commit** (repo is initialised in Task 1; if Task 0 runs first, commit after Task 1)

```bash
git add docs/benchmarks/dx-comparison.md
git commit -m "docs: add competitor DX benchmark"
```

---

## Phase 1

### Task 1: Repository hygiene + version catalog

**Files:**
- Create: `.gitignore`, `LICENSE`
- Modify: `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`

- [ ] **Step 1: Init git** (the project is not a repo yet). **Ask the user before running.**

```bash
cd /Users/ali/projects/deblock/PassKi && git init -b main
```

- [ ] **Step 2: Create `.gitignore`** (skip any lines already present if the file exists)

```gitignore
.gradle/
build/
.kotlin/
local.properties
.idea/
*.iml
.DS_Store
captures/
.externalNativeBuild/
.cxx/
iosApp/iosApp.xcodeproj/xcuserdata/
iosApp/iosApp.xcodeproj/project.xcworkspace/xcuserdata/
kotlin-js-store/
```

- [ ] **Step 3: Create `LICENSE`.** Use the full Apache-2.0 text from https://www.apache.org/licenses/LICENSE-2.0.txt, saved verbatim. The copyright line goes in README, not LICENSE.

- [ ] **Step 4: Add to `gradle/libs.versions.toml`.** Merge into the existing sections.

```toml
[versions]
kotlinx-serialization = "1.11.0"
androidx-credentials = "1.6.0"
androidx-startup = "1.2.0"
cryptography = "0.6.0"
webauthn4j = "0.30.0.RELEASE"
vanniktech-publish = "0.34.0"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
androidx-credentials = { module = "androidx.credentials:credentials", version.ref = "androidx-credentials" }
androidx-credentials-playServices = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "androidx-credentials" }
androidx-startup = { module = "androidx.startup:startup-runtime", version.ref = "androidx-startup" }
cryptography-core = { module = "dev.whyoleg.cryptography:cryptography-core", version.ref = "cryptography" }
cryptography-provider-optimal = { module = "dev.whyoleg.cryptography:cryptography-provider-optimal", version.ref = "cryptography" }
webauthn4j-core = { module = "com.webauthn4j:webauthn4j-core", version.ref = "webauthn4j" }

[plugins]
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
vanniktechPublish = { id = "com.vanniktech.maven.publish", version.ref = "vanniktech-publish" }
```

- [ ] **Step 5: Root `build.gradle.kts`.** Add inside `plugins {}`:

```kotlin
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.vanniktechPublish) apply false
```

- [ ] **Step 6: `settings.gradle.kts`.** Append:

```kotlin
include(":passki")
include(":passki-test")
```

- [ ] **Step 7: `gradle.properties`.** Append:

```properties
#Publishing
GROUP=com.alirezaiyan.passki
VERSION_NAME=0.1.0-alpha01
```

- [ ] **Step 8: Verify the catalog parses.** Modules don't exist yet, so temporarily comment out the two `include` lines.

Run: `./gradlew help -q`
Expected: exit 0, no output.

Then restore the includes.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "chore: init repo, license, version catalog for passki"
```

---

### Task 2: `passki` module skeleton

**Files:**
- Create: `passki/build.gradle.kts`
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/InternalPasskiApi.kt`

- [ ] **Step 1: Create `passki/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    android {
        namespace = "com.alirezaiyan.passki.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        all {
            languageSettings.optIn("com.alirezaiyan.passki.InternalPasskiApi")
        }
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServices)
            implementation(libs.androidx.startup)
        }
    }
}
```

If `abiValidation` or `ExperimentalAbiValidation` doesn't resolve on Kotlin 2.4.20, remove that block. Then add plugin `org.jetbrains.kotlinx.binary-compatibility-validator` (latest) instead, and use `apiDump` / `apiCheck` wherever this plan says `updateLegacyAbi` / `checkLegacyAbi`.

- [ ] **Step 2: Create `InternalPasskiApi.kt`**

```kotlin
package com.alirezaiyan.passki

/**
 * Marks declarations shared between PassKi modules. Not part of the stable API.
 */
@RequiresOptIn(
    message = "Internal PassKi API. It may change without notice.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class InternalPasskiApi
```

- [ ] **Step 3: Verify the module configures.** Comment out `include(":passki-test")` until Task 15.

Run: `./gradlew :passki:tasks --group verification -q`
Expected: lists `jvmTest`, `testAndroidHostTest`, `iosSimulatorArm64Test`, `allTests`.

- [ ] **Step 4: Commit**

```bash
git add passki settings.gradle.kts
git commit -m "build: add passki KMP library module"
```

---

### Task 3: Base64Url

**Files:**
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/internal/Base64Url.kt`
- Test: `passki/src/commonTest/kotlin/com/alirezaiyan/passki/internal/Base64UrlTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base64UrlTest {
    private val bytes = byteArrayOf(1, 2, 3, -1)

    @Test
    fun encodesUrlSafeWithoutPadding() {
        assertEquals("AQID_w", Base64Url.encode(bytes))
    }

    @Test
    fun decodesUnpaddedBase64Url() {
        assertContentEquals(bytes, Base64Url.decode("AQID_w"))
    }

    @Test
    fun decodesStandardPaddedBase64Leniently() {
        assertContentEquals(bytes, Base64Url.decode("AQID/w=="))
    }

    @Test
    fun rejectsInvalidCharacters() {
        assertFailsWith<IllegalArgumentException> { Base64Url.decode("***") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.internal.Base64UrlTest"`
Expected: FAIL, compilation error `Unresolved reference 'Base64Url'`.

- [ ] **Step 3: Implement**

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import kotlin.io.encoding.Base64

/** base64url without padding, as WebAuthn JSON requires. Decoding also accepts standard base64. */
@InternalPasskiApi
public object Base64Url {
    private val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    public fun encode(bytes: ByteArray): String = codec.encode(bytes)

    public fun decode(value: String): ByteArray =
        codec.decode(value.replace('+', '-').replace('/', '_').trimEnd('='))
}
```

If the compiler reports `This declaration needs opt-in`, add `@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)` on the object.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.internal.Base64UrlTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add passki/src
git commit -m "feat(core): add lenient base64url codec"
```

---

### Task 4: PasskeyError, PasskeyException, PasskeyResult

**Files:**
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/PasskeyError.kt`
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/PasskeyResult.kt`
- Test: `passki/src/commonTest/kotlin/com/alirezaiyan/passki/PasskeyResultTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PasskeyResultTest {
    @Test
    fun onSuccessRunsOnlyForSuccess() {
        var seen: String? = null
        PasskeyResult.Success("a")
            .onSuccess { seen = it }
            .onFailure { error("must not run") }
        assertEquals("a", seen)
    }

    @Test
    fun onFailureRunsOnlyForFailure() {
        var seen: PasskeyError? = null
        val result: PasskeyResult<String> = PasskeyResult.Failure(PasskeyError.Cancelled)
        result.onSuccess { error("must not run") }.onFailure { seen = it }
        assertEquals(PasskeyError.Cancelled, seen)
    }

    @Test
    fun getOrNullReturnsValueOrNull() {
        assertEquals(1, PasskeyResult.Success(1).getOrNull())
        assertNull(PasskeyResult.Failure(PasskeyError.NoCredentials).getOrNull())
    }

    @Test
    fun getOrThrowWrapsErrorInPasskeyException() {
        val e = assertFailsWith<PasskeyException> {
            PasskeyResult.Failure(PasskeyError.NoCredentials).getOrThrow()
        }
        assertEquals(PasskeyError.NoCredentials, e.error)
    }

    @Test
    fun mapTransformsSuccessAndKeepsFailure() {
        assertEquals(PasskeyResult.Success(2), PasskeyResult.Success(1).map { it + 1 })
        val failure: PasskeyResult<Int> = PasskeyResult.Failure(PasskeyError.Interrupted)
        assertEquals(failure, failure.map { it + 1 })
    }

    @Test
    fun onlyActionableErrorsExposeFixHint() {
        assertEquals("do X", PasskeyError.Misconfigured("broken", "do X").fixHint)
        assertNull(PasskeyError.Cancelled.fixHint)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.PasskeyResultTest"`
Expected: FAIL, `Unresolved reference 'PasskeyResult'`.

- [ ] **Step 3: Implement `PasskeyError.kt`**

```kotlin
package com.alirezaiyan.passki

/**
 * Every way a passkey operation can fail, across all platforms.
 * [fixHint] is non-null when a developer can fix the cause (configuration, options).
 */
public sealed class PasskeyError {
    public abstract val message: String
    public open val fixHint: String? get() = null

    public data object Cancelled : PasskeyError() {
        override val message: String = "The user cancelled the passkey request."
    }

    public data object NoCredentials : PasskeyError() {
        override val message: String = "No passkey for this account is available on this device."
    }

    public data object AlreadyRegistered : PasskeyError() {
        override val message: String =
            "A passkey for this account already exists on this device (excludeCredentials matched)."
    }

    public data object Interrupted : PasskeyError() {
        override val message: String = "The passkey request was interrupted. Retry it."
    }

    public data class NotSupported(override val message: String) : PasskeyError()

    public data class InvalidOptions(override val message: String) : PasskeyError() {
        override val fixHint: String =
            "Pass the server's WebAuthn options JSON unchanged " +
                "(PublicKeyCredentialCreationOptionsJSON / PublicKeyCredentialRequestOptionsJSON). " +
                "Binary fields (challenge, user.id, credential ids) must be base64url."
    }

    public data class Misconfigured(
        override val message: String,
        override val fixHint: String,
    ) : PasskeyError()

    public data class Unknown(
        override val message: String,
        val platformCode: String?,
    ) : PasskeyError()
}

/** Thrown by [getOrThrow] and used internally to short-circuit with a typed error. */
public class PasskeyException(public val error: PasskeyError) : Exception(error.message)
```

Explicit API mode: if the compiler asks for `public` on `val platformCode`, write `public val platformCode: String?`.

- [ ] **Step 4: Implement `PasskeyResult.kt`**

```kotlin
package com.alirezaiyan.passki

public sealed interface PasskeyResult<out T> {
    public data class Success<out T>(public val value: T) : PasskeyResult<T>
    public data class Failure(public val error: PasskeyError) : PasskeyResult<Nothing>
}

public inline fun <T> PasskeyResult<T>.onSuccess(action: (T) -> Unit): PasskeyResult<T> {
    if (this is PasskeyResult.Success) action(value)
    return this
}

public inline fun <T> PasskeyResult<T>.onFailure(action: (PasskeyError) -> Unit): PasskeyResult<T> {
    if (this is PasskeyResult.Failure) action(error)
    return this
}

public fun <T> PasskeyResult<T>.getOrNull(): T? = (this as? PasskeyResult.Success)?.value

public fun <T> PasskeyResult<T>.getOrThrow(): T = when (this) {
    is PasskeyResult.Success -> value
    is PasskeyResult.Failure -> throw PasskeyException(error)
}

public inline fun <T, R> PasskeyResult<T>.map(transform: (T) -> R): PasskeyResult<R> = when (this) {
    is PasskeyResult.Success -> PasskeyResult.Success(transform(value))
    is PasskeyResult.Failure -> this
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.PasskeyResultTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add passki/src
git commit -m "feat(core): add PasskeyError and PasskeyResult"
```

---

### Task 5: Public API surface + unsupported platforms

**Files:**
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/Passkeys.kt`
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/internal/UnsupportedPasskeys.kt`
- Create: `passki/src/jvmMain/kotlin/com/alirezaiyan/passki/Passkeys.jvm.kt`
- Create: `passki/src/webMain/kotlin/com/alirezaiyan/passki/Passkeys.web.kt`
- Create (temporary, replaced in Tasks 11/14): `passki/src/androidMain/kotlin/com/alirezaiyan/passki/Passkeys.android.kt`, `passki/src/iosMain/kotlin/com/alirezaiyan/passki/Passkeys.ios.kt`
- Test: `passki/src/jvmTest/kotlin/com/alirezaiyan/passki/UnsupportedPasskeysTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

class UnsupportedPasskeysTest {
    private val passkeys = Passkeys()

    @Test
    fun capabilitiesReportUnsupported() = runTest {
        assertFalse(passkeys.capabilities().isSupported)
    }

    @Test
    fun everyOperationFailsWithNotSupported() = runTest {
        listOf(
            passkeys.register("{}"),
            passkeys.signIn("{}"),
            passkeys.signInOrPassword("{}"),
            passkeys.upgrade("{}"),
            passkeys.signals.unknownCredential("example.com", "AQID"),
        ).forEach { result ->
            val failure = assertIs<PasskeyResult.Failure>(result)
            assertIs<PasskeyError.NotSupported>(failure.error)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.UnsupportedPasskeysTest"`
Expected: FAIL, `Unresolved reference 'Passkeys'`.

- [ ] **Step 3: Implement `Passkeys.kt`**

```kotlin
package com.alirezaiyan.passki

/**
 * Entry point. Obtain with [Passkeys]; no platform handles needed.
 *
 * Every `optionsJson` parameter is the WebAuthn options JSON produced by your server,
 * optionally wrapped in `{"publicKey": ...}`.
 * Every returned `json` is ready to POST to your server's verifier.
 */
public interface Passkeys {
    /** What this device/OS supports, so UI can adapt before showing passkey buttons. */
    public suspend fun capabilities(): PasskeyCapabilities

    /** Creates a passkey. */
    public suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration>

    /** Signs in with a passkey. */
    public suspend fun signIn(
        optionsJson: String,
        mode: SignInMode = SignInMode.Modal,
    ): PasskeyResult<PasskeyAssertion>

    /** One system sheet offering passkeys and saved passwords. */
    public suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential>

    /**
     * Silently creates a passkey for a user who just signed in with a password
     * (conditional create). The provider decides whether to create one; no sheet is shown.
     */
    public suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration>

    /** Keeps credential providers in sync with the server (WebAuthn Signal API). */
    public val signals: PasskeySignals
}

/** Creates the [Passkeys] implementation for the current platform. */
public expect fun Passkeys(): Passkeys

public enum class SignInMode {
    /** Standard sheet; shows hybrid (phone) / security-key options when no local passkey exists. */
    Modal,

    /** Only use passkeys already on this device; fails fast with [PasskeyError.NoCredentials] otherwise. */
    ImmediateOnly,
}

public interface PasskeySignals {
    /** The server doesn't recognise [credentialId]; providers may hide or delete it. */
    public suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit>

    /** The complete list of valid credential ids for [userId]; providers may remove others. */
    public suspend fun acceptedCredentials(
        rpId: String,
        userId: String,
        credentialIds: List<String>,
    ): PasskeyResult<Unit>

    /** The user's current name / display name, so providers can update saved passkeys. */
    public suspend fun currentUserDetails(
        rpId: String,
        userId: String,
        name: String,
        displayName: String,
    ): PasskeyResult<Unit>
}

public class PasskeyCapabilities(
    public val isSupported: Boolean,
    public val immediateSignIn: Boolean,
    public val unifiedPasswordSignIn: Boolean,
    public val conditionalCreate: Boolean,
    public val signals: Boolean,
) {
    override fun toString(): String =
        "PasskeyCapabilities(isSupported=$isSupported, immediateSignIn=$immediateSignIn, " +
            "unifiedPasswordSignIn=$unifiedPasswordSignIn, conditionalCreate=$conditionalCreate, signals=$signals)"
}

/** Result of [Passkeys.register]. Send [json] to the server. */
public class PasskeyRegistration(
    public val json: String,
    public val credentialId: String,
    public val transports: List<String>,
    public val authenticatorAttachment: String?,
) {
    override fun toString(): String = "PasskeyRegistration(credentialId=$credentialId, json=$json)"
}

/** Result of [Passkeys.signIn]. Send [json] to the server. */
public class PasskeyAssertion(
    public val json: String,
    public val credentialId: String,
    public val userHandle: String?,
) {
    override fun toString(): String = "PasskeyAssertion(credentialId=$credentialId, json=$json)"
}

public sealed interface SignInCredential {
    public class Passkey(public val assertion: PasskeyAssertion) : SignInCredential {
        override fun toString(): String = "SignInCredential.Passkey($assertion)"
    }

    public class Password(public val id: String, public val password: String) : SignInCredential {
        override fun toString(): String = "SignInCredential.Password(id=$id, password=***)"
    }
}
```

- [ ] **Step 4: Implement `internal/UnsupportedPasskeys.kt`**

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyRegistration
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskeySignals
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode

internal class UnsupportedPasskeys(platform: String) : Passkeys {
    private val failure = PasskeyResult.Failure(
        PasskeyError.NotSupported("PassKi does not support $platform yet. It is on the roadmap."),
    )

    override suspend fun capabilities(): PasskeyCapabilities =
        PasskeyCapabilities(
            isSupported = false,
            immediateSignIn = false,
            unifiedPasswordSignIn = false,
            conditionalCreate = false,
            signals = false,
        )

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> = failure

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> = failure

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> = failure

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> = failure

    override val signals: PasskeySignals = object : PasskeySignals {
        override suspend fun unknownCredential(rpId: String, credentialId: String) = failure

        override suspend fun acceptedCredentials(rpId: String, userId: String, credentialIds: List<String>) = failure

        override suspend fun currentUserDetails(rpId: String, userId: String, name: String, displayName: String) =
            failure
    }
}
```

If the compiler complains about inferred return types in the anonymous object (explicit API doesn't apply to internal, but type inference to `PasskeyResult.Failure` may mismatch), annotate each with `: PasskeyResult<Unit>`.

- [ ] **Step 5: Platform actuals**

`passki/src/jvmMain/kotlin/com/alirezaiyan/passki/Passkeys.jvm.kt`:

```kotlin
package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.UnsupportedPasskeys

public actual fun Passkeys(): Passkeys = UnsupportedPasskeys("JVM desktop")
```

`passki/src/webMain/kotlin/com/alirezaiyan/passki/Passkeys.web.kt`:

```kotlin
package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.UnsupportedPasskeys

public actual fun Passkeys(): Passkeys = UnsupportedPasskeys("the browser")
```

`passki/src/androidMain/kotlin/com/alirezaiyan/passki/Passkeys.android.kt` (temporary):

```kotlin
package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.UnsupportedPasskeys

public actual fun Passkeys(): Passkeys = UnsupportedPasskeys("Android")
```

`passki/src/iosMain/kotlin/com/alirezaiyan/passki/Passkeys.ios.kt` (temporary):

```kotlin
package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.UnsupportedPasskeys

public actual fun Passkeys(): Passkeys = UnsupportedPasskeys("iOS")
```

- [ ] **Step 6: Run test + compile all targets**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.UnsupportedPasskeysTest" :passki:assemble`
Expected: PASS (2 tests), BUILD SUCCESSFUL for all targets.

If `webMain` isn't picked up (error: no actual for js/wasmJs), move the file to both `jsMain` and `wasmJsMain`.

- [ ] **Step 7: Commit**

```bash
git add passki/src
git commit -m "feat(core): add Passkeys API surface and unsupported-platform fallback"
```

---

### Task 6: WebAuthn JSON models + options normalizer

**Files:**
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/internal/WebAuthnJson.kt`
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/internal/WebAuthnOptions.kt`
- Test: `passki/src/commonTest/kotlin/com/alirezaiyan/passki/internal/WebAuthnOptionsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebAuthnOptionsTest {
    private val creation = """
        {"rp":{"id":"example.com","name":"Example"},
         "user":{"id":"AQID","name":"alice","displayName":"Alice"},
         "challenge":"AAEC",
         "pubKeyCredParams":[{"type":"public-key","alg":-7}],
         "excludeCredentials":[{"id":"BAUG","type":"public-key"}],
         "authenticatorSelection":{"residentKey":"required","userVerification":"preferred"},
         "extensions":{"credProps":true},
         "unknownField":true}
    """.trimIndent()

    @Test
    fun parsesCreationOptions() {
        val parsed = WebAuthnOptions.parseCreation(creation)
        assertEquals("example.com", parsed.model.rp.id)
        assertEquals("alice", parsed.model.user.name)
        assertEquals("Alice", parsed.model.user.displayName)
        assertEquals(listOf("BAUG"), parsed.model.excludeCredentials.map { it.id })
        assertEquals("preferred", parsed.model.authenticatorSelection?.userVerification)
    }

    @Test
    fun keepsUnknownFieldsAndExtensionsInNormalizedJson() {
        val json = WebAuthnOptions.parseCreation(creation).json
        assertTrue("unknownField" in json)
        assertTrue("credProps" in json)
    }

    @Test
    fun unwrapsPublicKeyEnvelope() {
        val parsed = WebAuthnOptions.parseCreation("""{"publicKey":$creation}""")
        assertEquals("AAEC", parsed.model.challenge)
        assertFalse("publicKey" in parsed.json)
    }

    @Test
    fun parsesRequestOptions() {
        val parsed = WebAuthnOptions.parseRequest(
            """{"challenge":"AAEC","rpId":"example.com",
               "allowCredentials":[{"id":"BAUG","type":"public-key","transports":["internal"]}],
               "userVerification":"required"}""",
        )
        assertEquals("example.com", parsed.model.rpId)
        assertEquals(listOf("internal"), parsed.model.allowCredentials.single().transports)
        assertEquals("required", parsed.model.userVerification)
    }

    @Test
    fun missingChallengeIsInvalidOptions() {
        val e = assertFailsWith<PasskeyException> {
            WebAuthnOptions.parseCreation("""{"rp":{"name":"x"},"user":{"id":"AQID","name":"a"}}""")
        }
        assertIs<PasskeyError.InvalidOptions>(e.error)
    }

    @Test
    fun nonJsonIsInvalidOptions() {
        val e = assertFailsWith<PasskeyException> { WebAuthnOptions.parseRequest("not json") }
        assertIs<PasskeyError.InvalidOptions>(e.error)
    }

    @Test
    fun nonBase64ChallengeIsInvalidOptions() {
        val e = assertFailsWith<PasskeyException> { WebAuthnOptions.parseRequest("""{"challenge":"***"}""") }
        assertIs<PasskeyError.InvalidOptions>(e.error)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.internal.WebAuthnOptionsTest"`
Expected: FAIL, `Unresolved reference 'WebAuthnOptions'`.

- [ ] **Step 3: Implement `WebAuthnJson.kt`**

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@InternalPasskiApi
public val PasskiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@InternalPasskiApi
@Serializable
public data class CreationOptionsJson(
    public val rp: RelyingPartyJson,
    public val user: UserJson,
    public val challenge: String,
    public val pubKeyCredParams: List<PubKeyCredParamJson> = emptyList(),
    public val timeout: Long? = null,
    public val excludeCredentials: List<CredentialDescriptorJson> = emptyList(),
    public val authenticatorSelection: AuthenticatorSelectionJson? = null,
    public val attestation: String? = null,
    public val extensions: JsonObject? = null,
)

@InternalPasskiApi
@Serializable
public data class RelyingPartyJson(public val id: String? = null, public val name: String)

@InternalPasskiApi
@Serializable
public data class UserJson(public val id: String, public val name: String, public val displayName: String = "")

@InternalPasskiApi
@Serializable
public data class PubKeyCredParamJson(public val type: String = "public-key", public val alg: Int)

@InternalPasskiApi
@Serializable
public data class CredentialDescriptorJson(
    public val id: String,
    public val type: String = "public-key",
    public val transports: List<String> = emptyList(),
)

@InternalPasskiApi
@Serializable
public data class AuthenticatorSelectionJson(
    public val authenticatorAttachment: String? = null,
    public val residentKey: String? = null,
    public val requireResidentKey: Boolean? = null,
    public val userVerification: String? = null,
)

@InternalPasskiApi
@Serializable
public data class RequestOptionsJson(
    public val challenge: String,
    public val timeout: Long? = null,
    public val rpId: String? = null,
    public val allowCredentials: List<CredentialDescriptorJson> = emptyList(),
    public val userVerification: String? = null,
    public val extensions: JsonObject? = null,
)

@InternalPasskiApi
@Serializable
public data class RegistrationResponseJson(
    public val id: String,
    public val rawId: String,
    public val type: String = "public-key",
    public val response: AttestationResponseJson,
    public val authenticatorAttachment: String? = null,
    public val clientExtensionResults: JsonObject = JsonObject(emptyMap()),
)

@InternalPasskiApi
@Serializable
public data class AttestationResponseJson(
    public val clientDataJSON: String,
    public val attestationObject: String,
    public val transports: List<String> = emptyList(),
)

@InternalPasskiApi
@Serializable
public data class AuthenticationResponseJson(
    public val id: String,
    public val rawId: String,
    public val type: String = "public-key",
    public val response: AssertionResponseJson,
    public val authenticatorAttachment: String? = null,
    public val clientExtensionResults: JsonObject = JsonObject(emptyMap()),
)

@InternalPasskiApi
@Serializable
public data class AssertionResponseJson(
    public val clientDataJSON: String,
    public val authenticatorData: String,
    public val signature: String,
    public val userHandle: String? = null,
)
```

- [ ] **Step 4: Implement `WebAuthnOptions.kt`**

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

@InternalPasskiApi
public class NormalizedOptions<T>(
    /** Options JSON with any `{"publicKey": ...}` envelope removed; unknown fields preserved. */
    public val json: String,
    public val model: T,
)

@InternalPasskiApi
public object WebAuthnOptions {
    public fun parseCreation(json: String): NormalizedOptions<CreationOptionsJson> =
        parse(json, CreationOptionsJson.serializer()) { options ->
            Base64Url.decode(options.challenge)
            Base64Url.decode(options.user.id)
            options.excludeCredentials.forEach { Base64Url.decode(it.id) }
        }

    public fun parseRequest(json: String): NormalizedOptions<RequestOptionsJson> =
        parse(json, RequestOptionsJson.serializer()) { options ->
            Base64Url.decode(options.challenge)
            options.allowCredentials.forEach { Base64Url.decode(it.id) }
        }

    private fun <T> parse(json: String, serializer: KSerializer<T>, validate: (T) -> Unit): NormalizedOptions<T> {
        val element = try {
            PasskiJson.parseToJsonElement(json)
        } catch (e: IllegalArgumentException) {
            throw invalid("Options are not valid JSON: ${e.message}")
        }
        val root = element as? JsonObject ?: throw invalid("Options must be a JSON object.")
        val unwrapped = root["publicKey"] as? JsonObject ?: root
        val model = try {
            PasskiJson.decodeFromJsonElement(serializer, unwrapped)
        } catch (e: IllegalArgumentException) {
            throw invalid("Options do not match the WebAuthn JSON format: ${e.message}")
        }
        try {
            validate(model)
        } catch (e: IllegalArgumentException) {
            throw invalid("A binary field is not base64url: ${e.message}")
        }
        return NormalizedOptions(unwrapped.toString(), model)
    }

    private fun invalid(message: String) = PasskeyException(PasskeyError.InvalidOptions(message))
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.internal.WebAuthnOptionsTest"`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit**

```bash
git add passki/src
git commit -m "feat(core): add WebAuthn JSON models and options normalizer"
```

---

### Task 7: Response builders, typed parsing, signal JSON

**Files:**
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/internal/WebAuthnResponses.kt`
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/internal/SignalRequests.kt`
- Test: `passki/src/commonTest/kotlin/com/alirezaiyan/passki/internal/WebAuthnResponsesTest.kt`
- Test: `passki/src/commonTest/kotlin/com/alirezaiyan/passki/internal/SignalRequestsTest.kt`

- [ ] **Step 1: Write the failing tests**

`WebAuthnResponsesTest.kt`:

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class WebAuthnResponsesTest {
    private val clientData = "{}".encodeToByteArray() // base64url "e30"

    private fun registrationJson() = WebAuthnResponses.registration(
        credentialId = byteArrayOf(1, 2, 3),
        clientDataJson = clientData,
        attestationObject = byteArrayOf(9),
        transports = listOf("internal"),
        attachment = "platform",
    )

    @Test
    fun registrationJsonHasWebAuthnShape() {
        val obj = PasskiJson.parseToJsonElement(registrationJson()).jsonObject
        assertEquals("AQID", obj["id"]!!.jsonPrimitive.content)
        assertEquals("AQID", obj["rawId"]!!.jsonPrimitive.content)
        assertEquals("public-key", obj["type"]!!.jsonPrimitive.content)
        assertEquals("platform", obj["authenticatorAttachment"]!!.jsonPrimitive.content)
        assertEquals(JsonObject(emptyMap()), obj["clientExtensionResults"])
        val response = obj["response"]!!.jsonObject
        assertEquals("e30", response["clientDataJSON"]!!.jsonPrimitive.content)
        assertEquals("CQ", response["attestationObject"]!!.jsonPrimitive.content)
        assertEquals(listOf("internal"), response["transports"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun authenticationJsonOmitsNulls() {
        val json = WebAuthnResponses.authentication(
            credentialId = byteArrayOf(1, 2, 3),
            clientDataJson = clientData,
            authenticatorData = byteArrayOf(1),
            signature = byteArrayOf(2),
            userHandle = null,
            attachment = null,
        )
        val obj = PasskiJson.parseToJsonElement(json).jsonObject
        assertFalse("authenticatorAttachment" in obj)
        assertFalse("userHandle" in obj["response"]!!.jsonObject)
    }

    @Test
    fun parseRegistrationExposesTypedFields() {
        val json = registrationJson()
        val registration = parseRegistration(json)
        assertEquals(json, registration.json)
        assertEquals("AQID", registration.credentialId)
        assertEquals(listOf("internal"), registration.transports)
        assertEquals("platform", registration.authenticatorAttachment)
    }

    @Test
    fun parseAssertionExposesUserHandle() {
        val json = WebAuthnResponses.authentication(
            credentialId = byteArrayOf(1, 2, 3),
            clientDataJson = clientData,
            authenticatorData = byteArrayOf(1),
            signature = byteArrayOf(2),
            userHandle = byteArrayOf(7),
            attachment = "platform",
        )
        val assertion = parseAssertion(json)
        assertEquals("AQID", assertion.credentialId)
        assertEquals("Bw", assertion.userHandle)
    }

    @Test
    fun malformedPlatformJsonBecomesUnknown() {
        val e = assertFailsWith<PasskeyException> { parseRegistration("""{"id":"x"}""") }
        assertIs<PasskeyError.Unknown>(e.error)
    }
}
```

`SignalRequestsTest.kt`:

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SignalRequestsTest {
    @Test
    fun unknownCredential() {
        assertEquals(
            """{"rpId":"example.com","credentialId":"AQID"}""",
            SignalRequests.unknownCredential("example.com", "AQID"),
        )
    }

    @Test
    fun acceptedCredentials() {
        assertEquals(
            """{"rpId":"example.com","userId":"AQID","allAcceptedCredentialIds":["BAUG"]}""",
            SignalRequests.acceptedCredentials("example.com", "AQID", listOf("BAUG")),
        )
    }

    @Test
    fun currentUserDetails() {
        assertEquals(
            """{"rpId":"example.com","userId":"AQID","name":"alice","displayName":"Alice"}""",
            SignalRequests.currentUserDetails("example.com", "AQID", "alice", "Alice"),
        )
    }

    @Test
    fun rejectsNonBase64Ids() {
        val e = assertFailsWith<PasskeyException> { SignalRequests.unknownCredential("example.com", "***") }
        assertIs<PasskeyError.InvalidOptions>(e.error)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.internal.WebAuthnResponsesTest" --tests "com.alirezaiyan.passki.internal.SignalRequestsTest"`
Expected: FAIL, `Unresolved reference 'WebAuthnResponses'` / `'SignalRequests'`.

- [ ] **Step 3: Implement `WebAuthnResponses.kt`**

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyRegistration
import kotlinx.serialization.json.JsonObject

/** Builds W3C RegistrationResponseJSON / AuthenticationResponseJSON from raw authenticator bytes. */
@InternalPasskiApi
public object WebAuthnResponses {
    public fun registration(
        credentialId: ByteArray,
        clientDataJson: ByteArray,
        attestationObject: ByteArray,
        transports: List<String>,
        attachment: String?,
        extensions: JsonObject = JsonObject(emptyMap()),
    ): String {
        val id = Base64Url.encode(credentialId)
        val model = RegistrationResponseJson(
            id = id,
            rawId = id,
            response = AttestationResponseJson(
                clientDataJSON = Base64Url.encode(clientDataJson),
                attestationObject = Base64Url.encode(attestationObject),
                transports = transports,
            ),
            authenticatorAttachment = attachment,
            clientExtensionResults = extensions,
        )
        return PasskiJson.encodeToString(RegistrationResponseJson.serializer(), model)
    }

    public fun authentication(
        credentialId: ByteArray,
        clientDataJson: ByteArray,
        authenticatorData: ByteArray,
        signature: ByteArray,
        userHandle: ByteArray?,
        attachment: String?,
        extensions: JsonObject = JsonObject(emptyMap()),
    ): String {
        val id = Base64Url.encode(credentialId)
        val model = AuthenticationResponseJson(
            id = id,
            rawId = id,
            response = AssertionResponseJson(
                clientDataJSON = Base64Url.encode(clientDataJson),
                authenticatorData = Base64Url.encode(authenticatorData),
                signature = Base64Url.encode(signature),
                userHandle = userHandle?.let(Base64Url::encode),
            ),
            authenticatorAttachment = attachment,
            clientExtensionResults = extensions,
        )
        return PasskiJson.encodeToString(AuthenticationResponseJson.serializer(), model)
    }
}

@InternalPasskiApi
public fun parseRegistration(json: String): PasskeyRegistration {
    val model = try {
        PasskiJson.decodeFromString(RegistrationResponseJson.serializer(), json)
    } catch (e: IllegalArgumentException) {
        throw PasskeyException(PasskeyError.Unknown("Platform returned malformed registration JSON: ${e.message}", null))
    }
    return PasskeyRegistration(
        json = json,
        credentialId = model.id,
        transports = model.response.transports,
        authenticatorAttachment = model.authenticatorAttachment,
    )
}

@InternalPasskiApi
public fun parseAssertion(json: String): PasskeyAssertion {
    val model = try {
        PasskiJson.decodeFromString(AuthenticationResponseJson.serializer(), json)
    } catch (e: IllegalArgumentException) {
        throw PasskeyException(PasskeyError.Unknown("Platform returned malformed assertion JSON: ${e.message}", null))
    }
    return PasskeyAssertion(json = json, credentialId = model.id, userHandle = model.response.userHandle)
}
```

- [ ] **Step 4: Implement `SignalRequests.kt`**

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** JSON bodies defined by the WebAuthn Signal API (signalUnknownCredential & co). */
@InternalPasskiApi
public object SignalRequests {
    public fun unknownCredential(rpId: String, credentialId: String): String {
        requireBase64Url(credentialId, "credentialId")
        return buildJsonObject {
            put("rpId", rpId)
            put("credentialId", credentialId)
        }.toString()
    }

    public fun acceptedCredentials(rpId: String, userId: String, credentialIds: List<String>): String {
        requireBase64Url(userId, "userId")
        credentialIds.forEach { requireBase64Url(it, "credentialIds") }
        return buildJsonObject {
            put("rpId", rpId)
            put("userId", userId)
            putJsonArray("allAcceptedCredentialIds") { credentialIds.forEach { add(it) } }
        }.toString()
    }

    public fun currentUserDetails(rpId: String, userId: String, name: String, displayName: String): String {
        requireBase64Url(userId, "userId")
        return buildJsonObject {
            put("rpId", rpId)
            put("userId", userId)
            put("name", name)
            put("displayName", displayName)
        }.toString()
    }

    private fun requireBase64Url(value: String, field: String) {
        try {
            Base64Url.decode(value)
        } catch (e: IllegalArgumentException) {
            throw PasskeyException(PasskeyError.InvalidOptions("$field must be base64url: ${e.message}"))
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.internal.WebAuthnResponsesTest" --tests "com.alirezaiyan.passki.internal.SignalRequestsTest"`
Expected: PASS (9 tests).

- [ ] **Step 6: Commit**

```bash
git add passki/src
git commit -m "feat(core): build and parse WebAuthn responses, signal requests"
```

---

### Task 8: `passkeyCatching` bridge

**Files:**
- Create: `passki/src/commonMain/kotlin/com/alirezaiyan/passki/internal/PasskeyCatching.kt`
- Test: `passki/src/commonTest/kotlin/com/alirezaiyan/passki/internal/PasskeyCatchingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PasskeyCatchingTest {
    private val mapped = PasskeyError.Unknown("mapped", null)

    @Test
    fun wrapsValueInSuccess() {
        assertEquals(PasskeyResult.Success(1), passkeyCatching({ mapped }) { 1 })
    }

    @Test
    fun passkeyExceptionKeepsItsOwnError() {
        val result = passkeyCatching<Int>({ mapped }) { throw PasskeyException(PasskeyError.Cancelled) }
        assertEquals(PasskeyResult.Failure(PasskeyError.Cancelled), result)
    }

    @Test
    fun otherThrowablesGoThroughMapper() {
        val result = passkeyCatching<Int>({ mapped }) { throw IllegalStateException("boom") }
        assertEquals(PasskeyResult.Failure(mapped), result)
    }

    @Test
    fun cancellationIsRethrown() {
        assertFailsWith<CancellationException> {
            passkeyCatching<Int>({ mapped }) { throw CancellationException("stop") }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.internal.PasskeyCatchingTest"`
Expected: FAIL, `Unresolved reference 'passkeyCatching'`.

- [ ] **Step 3: Implement**

```kotlin
package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block]; typed [PasskeyException]s keep their error, other throwables go through [mapError].
 * Coroutine cancellation is never swallowed.
 */
internal inline fun <T> passkeyCatching(
    mapError: (Throwable) -> PasskeyError,
    block: () -> T,
): PasskeyResult<T> = try {
    PasskeyResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: PasskeyException) {
    PasskeyResult.Failure(e.error)
} catch (e: Throwable) {
    PasskeyResult.Failure(mapError(e))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :passki:jvmTest --tests "com.alirezaiyan.passki.internal.PasskeyCatchingTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add passki/src
git commit -m "feat(core): add passkeyCatching exception bridge"
```

---

### Task 9: Android Activity auto-resolution

**Files:**
- Create: `passki/src/androidMain/AndroidManifest.xml`
- Create: `passki/src/androidMain/kotlin/com/alirezaiyan/passki/android/ActivityTracker.kt`
- Create: `passki/src/androidMain/kotlin/com/alirezaiyan/passki/android/PasskiInitializer.kt`
- Create: `passki/src/androidMain/kotlin/com/alirezaiyan/passki/PasskiAndroid.kt`

No unit test: lifecycle callbacks need a real Application. This is verified in Task 21 (sample on emulator).

- [ ] **Step 1: Create `ActivityTracker.kt`**

```kotlin
package com.alirezaiyan.passki.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/** Tracks the foreground Activity so callers never pass one to PassKi. */
internal object ActivityTracker : Application.ActivityLifecycleCallbacks {
    @Volatile
    var application: Application? = null
        private set

    @Volatile
    private var current: WeakReference<Activity>? = null

    val currentActivity: Activity?
        get() = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    @Synchronized
    fun install(app: Application) {
        if (application != null) return
        application = app
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        current = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
```

- [ ] **Step 2: Create `PasskiInitializer.kt`**

```kotlin
package com.alirezaiyan.passki.android

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

/** Auto-installs [ActivityTracker] via androidx.startup. */
public class PasskiInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        ActivityTracker.install(context.applicationContext as Application)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

- [ ] **Step 3: Create `PasskiAndroid.kt`** (manual init when apps disable androidx.startup)

```kotlin
package com.alirezaiyan.passki

import android.app.Application
import com.alirezaiyan.passki.android.ActivityTracker

public object PasskiAndroid {
    /** Only needed if you removed PassKi's androidx.startup initializer. Call from Application.onCreate(). */
    public fun init(application: Application) {
        ActivityTracker.install(application)
    }
}
```

- [ ] **Step 4: Create `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application>
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="com.alirezaiyan.passki.android.PasskiInitializer"
                android:value="androidx.startup" />
        </provider>
    </application>
</manifest>
```

- [ ] **Step 5: Compile Android**

Run: `./gradlew :passki:compileAndroidMain`
Expected: BUILD SUCCESSFUL. If the task name differs, run `./gradlew :passki:assemble`.

- [ ] **Step 6: Commit**

```bash
git add passki/src/androidMain
git commit -m "feat(android): auto-resolve foreground Activity via androidx.startup"
```

---

### Task 10: Android error mapping

**Files:**
- Create: `passki/src/androidMain/kotlin/com/alirezaiyan/passki/android/AndroidErrorMapper.kt`
- Test: `passki/src/androidHostTest/kotlin/com/alirezaiyan/passki/android/AndroidErrorMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki.android

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.alirezaiyan.passki.PasskeyError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidErrorMapperTest {
    @Test
    fun createCancellationIsCancelled() {
        assertEquals(PasskeyError.Cancelled, CreateCredentialCancellationException("x").toPasskeyError())
    }

    @Test
    fun getCancellationIsCancelled() {
        assertEquals(PasskeyError.Cancelled, GetCredentialCancellationException("x").toPasskeyError())
    }

    @Test
    fun noCredentialIsNoCredentials() {
        assertEquals(PasskeyError.NoCredentials, NoCredentialException("x").toPasskeyError())
    }

    @Test
    fun interruptedIsInterrupted() {
        assertEquals(PasskeyError.Interrupted, CreateCredentialInterruptedException("x").toPasskeyError())
    }

    @Test
    fun invalidStateDomErrorIsAlreadyRegistered() {
        val error = CreatePublicKeyCredentialDomException(InvalidStateError(), "exists").toPasskeyError()
        assertEquals(PasskeyError.AlreadyRegistered, error)
    }

    @Test
    fun notAllowedDomErrorIsCancelled() {
        val error = GetPublicKeyCredentialDomException(NotAllowedError(), "dismissed").toPasskeyError()
        assertEquals(PasskeyError.Cancelled, error)
    }

    @Test
    fun securityDomErrorIsMisconfiguredWithAssetLinksHint() {
        val error = GetPublicKeyCredentialDomException(SecurityError(), "rp mismatch").toPasskeyError()
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertTrue("assetlinks.json" in misconfigured.fixHint)
    }

    @Test
    fun providerConfigurationIsMisconfigured() {
        val error = CreateCredentialProviderConfigurationException("no provider").toPasskeyError()
        assertIs<PasskeyError.Misconfigured>(error)
    }

    @Test
    fun unsupportedIsNotSupported() {
        assertIs<PasskeyError.NotSupported>(GetCredentialUnsupportedException("old").toPasskeyError())
    }

    @Test
    fun unknownCreateExceptionKeepsMessageAndType() {
        val error = assertIs<PasskeyError.Unknown>(CreateCredentialUnknownException("boom").toPasskeyError())
        assertEquals("boom", error.message)
        assertTrue(error.platformCode!!.isNotEmpty())
    }

    @Test
    fun illegalArgumentIsInvalidOptions() {
        assertIs<PasskeyError.InvalidOptions>(IllegalArgumentException("bad json").toPasskeyError())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki:testAndroidHostTest --tests "com.alirezaiyan.passki.android.AndroidErrorMapperTest"`
Expected: FAIL, `Unresolved reference 'toPasskeyError'`.

- [ ] **Step 3: Implement**

```kotlin
package com.alirezaiyan.passki.android

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.DomError
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.alirezaiyan.passki.PasskeyError

internal const val ASSET_LINKS_HINT: String =
    "The RP ID is not associated with this app. Serve https://<rpId>/.well-known/assetlinks.json containing " +
        "relation \"delegate_permission/common.get_login_creds\" with this app's package name and SHA-256 " +
        "signing certificate fingerprint. Debug, release and Play App Signing keys differ; list each one."

private const val PROVIDER_HINT: String =
    "No credential provider is available. Make sure Google Play services is up to date and a provider " +
        "(Google Password Manager, 1Password, ...) is enabled in Settings > Passwords & accounts."

internal fun Throwable.toPasskeyError(): PasskeyError = when (this) {
    is CreateCredentialCancellationException, is GetCredentialCancellationException -> PasskeyError.Cancelled
    is NoCredentialException -> PasskeyError.NoCredentials
    is CreateCredentialInterruptedException, is GetCredentialInterruptedException -> PasskeyError.Interrupted
    is CreateCredentialProviderConfigurationException, is GetCredentialProviderConfigurationException ->
        PasskeyError.Misconfigured(messageOr("No credential provider configured."), PROVIDER_HINT)
    is CreateCredentialUnsupportedException, is GetCredentialUnsupportedException ->
        PasskeyError.NotSupported(messageOr("Passkeys are not supported on this device."))
    is CreatePublicKeyCredentialDomException -> domError.toPasskeyError(messageOr(domError.type))
    is GetPublicKeyCredentialDomException -> domError.toPasskeyError(messageOr(domError.type))
    is IllegalArgumentException -> PasskeyError.InvalidOptions(messageOr("Invalid request."))
    is CreateCredentialException -> PasskeyError.Unknown(messageOr(type), platformCode = type)
    is GetCredentialException -> PasskeyError.Unknown(messageOr(type), platformCode = type)
    else -> PasskeyError.Unknown(message ?: this::class.simpleName.orEmpty(), platformCode = this::class.simpleName)
}

private fun DomError.toPasskeyError(message: String): PasskeyError = when (this) {
    is InvalidStateError -> PasskeyError.AlreadyRegistered
    is NotAllowedError -> PasskeyError.Cancelled
    is SecurityError -> PasskeyError.Misconfigured(message, ASSET_LINKS_HINT)
    is NotSupportedError -> PasskeyError.NotSupported(message)
    else -> PasskeyError.Unknown(message, platformCode = type)
}

private fun Throwable.messageOr(fallback: String): String = message?.takeIf { it.isNotBlank() } ?: fallback
```

If host tests throw `Method ... not mocked` from android.jar stubs, change `withHostTest {}` in `passki/build.gradle.kts` to `withHostTest { isReturnDefaultValues = true }`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :passki:testAndroidHostTest --tests "com.alirezaiyan.passki.android.AndroidErrorMapperTest"`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add passki/src
git commit -m "feat(android): map Credential Manager exceptions to PasskeyError"
```

---

### Task 11: AndroidPasskeys implementation

**Files:**
- Create: `passki/src/androidMain/kotlin/com/alirezaiyan/passki/android/AndroidPasskeys.kt`
- Modify: `passki/src/androidMain/kotlin/com/alirezaiyan/passki/Passkeys.android.kt`

This needs a real Credential Manager, so behaviour is verified in Task 21. The gate here is compilation plus the existing tests.

- [ ] **Step 1: Create `AndroidPasskeys.kt`**

```kotlin
package com.alirezaiyan.passki.android

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.SignalAllAcceptedCredentialIdsRequest
import androidx.credentials.SignalCredentialStateRequest
import androidx.credentials.SignalCurrentUserDetailsRequest
import androidx.credentials.SignalUnknownCredentialRequest
import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyRegistration
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskeySignals
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.internal.SignalRequests
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import com.alirezaiyan.passki.internal.passkeyCatching

private const val INIT_HINT: String =
    "Call PassKi while an Activity is in the foreground. If you removed PassKi's androidx.startup " +
        "initializer, call PasskiAndroid.init(application) in Application.onCreate()."

internal class AndroidPasskeys(private val tracker: ActivityTracker = ActivityTracker) : Passkeys {

    private val manager: CredentialManager by lazy { CredentialManager.create(requireApplication()) }

    private val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    override suspend fun capabilities(): PasskeyCapabilities = PasskeyCapabilities(
        isSupported = supported,
        immediateSignIn = supported,
        unifiedPasswordSignIn = supported,
        conditionalCreate = supported,
        signals = supported,
    )

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = false)

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = true)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireSupported()
            val options = WebAuthnOptions.parseRequest(optionsJson)
            val request = GetCredentialRequest(
                credentialOptions = listOf(GetPublicKeyCredentialOption(requestJson = options.json)),
                preferImmediatelyAvailableCredentials = mode == SignInMode.ImmediateOnly,
            )
            when (val credential = manager.getCredential(requireActivity(), request).credential) {
                is PublicKeyCredential -> parseAssertion(credential.authenticationResponseJson)
                else -> throw unexpected(credential)
            }
        }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireSupported()
            val options = WebAuthnOptions.parseRequest(optionsJson)
            val request = GetCredentialRequest(
                credentialOptions = listOf(
                    GetPublicKeyCredentialOption(requestJson = options.json),
                    GetPasswordOption(),
                ),
            )
            when (val credential = manager.getCredential(requireActivity(), request).credential) {
                is PublicKeyCredential ->
                    SignInCredential.Passkey(parseAssertion(credential.authenticationResponseJson))
                is PasswordCredential -> SignInCredential.Password(credential.id, credential.password)
                else -> throw unexpected(credential)
            }
        }

    override val signals: PasskeySignals = object : PasskeySignals {
        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> =
            signal { SignalUnknownCredentialRequest(SignalRequests.unknownCredential(rpId, credentialId)) }

        override suspend fun acceptedCredentials(
            rpId: String,
            userId: String,
            credentialIds: List<String>,
        ): PasskeyResult<Unit> = signal {
            SignalAllAcceptedCredentialIdsRequest(SignalRequests.acceptedCredentials(rpId, userId, credentialIds))
        }

        override suspend fun currentUserDetails(
            rpId: String,
            userId: String,
            name: String,
            displayName: String,
        ): PasskeyResult<Unit> = signal {
            SignalCurrentUserDetailsRequest(SignalRequests.currentUserDetails(rpId, userId, name, displayName))
        }
    }

    private suspend fun createPasskey(optionsJson: String, conditional: Boolean): PasskeyResult<PasskeyRegistration> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireSupported()
            val options = WebAuthnOptions.parseCreation(optionsJson)
            val request = CreatePublicKeyCredentialRequest(requestJson = options.json, isConditional = conditional)
            when (val response = manager.createCredential(requireActivity(), request)) {
                is CreatePublicKeyCredentialResponse -> parseRegistration(response.registrationResponseJson)
                else -> throw unexpected(response)
            }
        }

    private suspend fun signal(build: () -> SignalCredentialStateRequest): PasskeyResult<Unit> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireSupported()
            manager.signalCredentialState(build())
            Unit
        }

    private fun requireSupported() {
        if (!supported) throw PasskeyException(PasskeyError.NotSupported("Passkeys require Android 9 (API 28) or newer."))
    }

    private fun requireActivity(): Activity = tracker.currentActivity
        ?: throw PasskeyException(PasskeyError.Misconfigured("No foreground Activity to show the passkey sheet.", INIT_HINT))

    private fun requireApplication(): Application = tracker.application
        ?: throw PasskeyException(PasskeyError.Misconfigured("PassKi is not initialised.", INIT_HINT))

    private fun unexpected(value: Any): PasskeyException =
        PasskeyException(PasskeyError.Unknown("Unexpected credential type ${value::class.simpleName}.", null))
}
```

If `CreatePublicKeyCredentialRequest(requestJson =, isConditional =)` doesn't compile (no default for the other params), use the 6-arg constructor: `CreatePublicKeyCredentialRequest(options.json, null, false, null, false, conditional)`.

- [ ] **Step 2: Replace `Passkeys.android.kt`**

```kotlin
package com.alirezaiyan.passki

import com.alirezaiyan.passki.android.AndroidPasskeys

public actual fun Passkeys(): Passkeys = AndroidPasskeys()
```

- [ ] **Step 3: Compile + run all Android/common tests**

Run: `./gradlew :passki:testAndroidHostTest :passki:jvmTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add passki/src
git commit -m "feat(android): implement Passkeys with Credential Manager"
```

---

### Task 12: iOS NSData interop + platform helpers

**Files:**
- Create: `passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/NSDataInterop.kt`
- Create: `passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/IosPlatform.kt`
- Test: `passki/src/iosTest/kotlin/com/alirezaiyan/passki/apple/NSDataInteropTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki.apple

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class NSDataInteropTest {
    @Test
    fun roundTripsBytes() {
        val bytes = byteArrayOf(0, 1, -1, 127, -128)
        assertContentEquals(bytes, bytes.toNSData().toByteArray())
    }

    @Test
    fun roundTripsEmpty() {
        assertContentEquals(ByteArray(0), ByteArray(0).toNSData().toByteArray())
    }

    @Test
    fun simulatorRunsAtLeastIos16() {
        assertTrue(iosAtLeast(16))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki:iosSimulatorArm64Test --tests "com.alirezaiyan.passki.apple.NSDataInteropTest"`
Expected: FAIL, `Unresolved reference 'toNSData'`.

- [ ] **Step 3: Implement `NSDataInterop.kt`**

```kotlin
package com.alirezaiyan.passki.apple

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
```

- [ ] **Step 4: Implement `IosPlatform.kt`**

```kotlin
package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

@OptIn(ExperimentalForeignApi::class)
internal fun iosAtLeast(major: Int, minor: Int = 0): Boolean =
    NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
        cValue<NSOperatingSystemVersion> {
            majorVersion = major.toLong()
            minorVersion = minor.toLong()
            patchVersion = 0
        },
    )

internal fun requireIos(major: Int, feature: String) {
    if (!iosAtLeast(major)) throw PasskeyException(PasskeyError.NotSupported("$feature requires iOS $major or newer."))
}

/** The window passkey sheets attach to; resolved automatically so callers never pass one. */
internal fun keyWindow(): UIWindow? {
    val windows = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
    return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :passki:iosSimulatorArm64Test --tests "com.alirezaiyan.passki.apple.NSDataInteropTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add passki/src
git commit -m "feat(ios): add NSData interop and OS helpers"
```

---

### Task 13: iOS error mapping

**Files:**
- Create: `passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/AppleErrorMapper.kt`
- Test: `passki/src/iosTest/kotlin/com/alirezaiyan/passki/apple/AppleErrorMapperTest.kt`

ASAuthorizationError codes:
- 1000 unknown
- 1001 canceled
- 1002 invalidResponse
- 1003 notHandled
- 1004 failed
- 1005 notInteractive
- 1006 matchedExcludedCredential (iOS 18)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleErrorMapperTest {
    @Test
    fun canceledIsCancelled() {
        assertEquals(PasskeyError.Cancelled, appleErrorToPasskeyError(1001, "The operation couldn't be completed."))
    }

    @Test
    fun notInteractiveIsNoCredentials() {
        assertEquals(PasskeyError.NoCredentials, appleErrorToPasskeyError(1005, "not interactive"))
    }

    @Test
    fun matchedExcludedCredentialIsAlreadyRegistered() {
        assertEquals(PasskeyError.AlreadyRegistered, appleErrorToPasskeyError(1006, "excluded"))
    }

    @Test
    fun domainAssociationFailureIsMisconfiguredWithAasaHint() {
        val error = appleErrorToPasskeyError(
            1004,
            "Application with identifier ABCDE12345.com.example is not associated with domain example.com",
        )
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertTrue("apple-app-site-association" in misconfigured.fixHint)
    }

    @Test
    fun otherFailuresAreUnknownWithCode() {
        val error = assertIs<PasskeyError.Unknown>(appleErrorToPasskeyError(1004, "failed"))
        assertEquals("ASAuthorizationError.1004", error.platformCode)
    }

    @Test
    fun authorizationExceptionGoesThroughMapper() {
        assertEquals(PasskeyError.Cancelled, AppleAuthorizationException(1001, "x").toPasskeyError())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki:iosSimulatorArm64Test --tests "com.alirezaiyan.passki.apple.AppleErrorMapperTest"`
Expected: FAIL, `Unresolved reference 'appleErrorToPasskeyError'`.

- [ ] **Step 3: Implement**

```kotlin
package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError

internal const val AASA_HINT: String =
    "Add the Associated Domains entitlement 'webcredentials:<rpId>' to your app target and serve " +
        "https://<rpId>/.well-known/apple-app-site-association with {\"webcredentials\":{\"apps\":[\"<TeamID>.<BundleID>\"]}}. " +
        "For local testing use 'webcredentials:<rpId>?mode=developer' and enable Associated Domains Development in " +
        "Settings > Developer."

internal class AppleAuthorizationException(val code: Long, message: String) : Exception(message)

internal fun appleErrorToPasskeyError(code: Long, message: String): PasskeyError = when {
    message.contains("not associated with domain", ignoreCase = true) -> PasskeyError.Misconfigured(message, AASA_HINT)
    code == 1001L -> PasskeyError.Cancelled
    code == 1005L -> PasskeyError.NoCredentials
    code == 1006L -> PasskeyError.AlreadyRegistered
    else -> PasskeyError.Unknown(message, platformCode = "ASAuthorizationError.$code")
}

internal fun Throwable.toPasskeyError(): PasskeyError = when (this) {
    is AppleAuthorizationException -> appleErrorToPasskeyError(code, message.orEmpty())
    is IllegalArgumentException -> PasskeyError.InvalidOptions(message ?: "Invalid request.")
    else -> PasskeyError.Unknown(message ?: this::class.simpleName.orEmpty(), platformCode = null)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :passki:iosSimulatorArm64Test --tests "com.alirezaiyan.passki.apple.AppleErrorMapperTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add passki/src
git commit -m "feat(ios): map ASAuthorizationError to PasskeyError"
```

---

### Task 14: iOS AuthenticationServices implementation

**Files:**
- Create: `passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/AuthorizationSession.kt`
- Create: `passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/AppleRequests.kt`
- Create: `passki/src/iosMain/kotlin/com/alirezaiyan/passki/apple/IosPasskeys.kt`
- Modify: `passki/src/iosMain/kotlin/com/alirezaiyan/passki/Passkeys.ios.kt`

Real ceremonies are verified in Task 21. The gate here is compiling for iosArm64 and iosSimulatorArm64.

K/N naming note: if an `NS_ENUM` constant below doesn't resolve, type the enum name in the IDE and use autocomplete. K/N exposes NS_ENUM entries as `EnumName.FullConstantName`.

- [ ] **Step 1: Create `AuthorizationSession.kt`**

```kotlin
package com.alirezaiyan.passki.apple

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationControllerRequestOptionPreferImmediatelyAvailableCredentials
import platform.AuthenticationServices.ASAuthorizationRequest
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bridges one ASAuthorizationController run to a suspend call. */
internal class AuthorizationSession private constructor(
    private val anchor: () -> UIWindow?,
    private val continuation: CancellableContinuation<ASAuthorization>,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        active.remove(this)
        if (continuation.isActive) continuation.resume(didCompleteWithAuthorization)
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        active.remove(this)
        if (continuation.isActive) {
            continuation.resumeWithException(
                AppleAuthorizationException(didCompleteWithError.code, didCompleteWithError.localizedDescription),
            )
        }
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor =
        anchor() ?: UIWindow()

    companion object {
        // ASAuthorizationController.delegate is weak: keep sessions alive until a callback fires. Main thread only.
        private val active = mutableSetOf<AuthorizationSession>()

        suspend fun perform(
            requests: List<ASAuthorizationRequest>,
            anchor: () -> UIWindow?,
            preferImmediatelyAvailable: Boolean,
        ): ASAuthorization = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val session = AuthorizationSession(anchor, continuation)
                active += session
                val controller = ASAuthorizationController(authorizationRequests = requests)
                controller.delegate = session
                controller.presentationContextProvider = session
                continuation.invokeOnCancellation {
                    dispatch_async(dispatch_get_main_queue()) {
                        controller.cancel()
                        active.remove(session)
                    }
                }
                if (preferImmediatelyAvailable) {
                    controller.performRequestsWithOptions(ASAuthorizationControllerRequestOptionPreferImmediatelyAvailableCredentials)
                } else {
                    controller.performRequests()
                }
            }
        }
    }
}
```

If the compiler reports conflicting overloads for the two `authorizationController` overrides, annotate the `didCompleteWithError` one with `@kotlinx.cinterop.ObjCSignatureOverride`.

- [ ] **Step 2: Create `AppleRequests.kt`**

```kotlin
package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.internal.Base64Url
import com.alirezaiyan.passki.internal.CreationOptionsJson
import com.alirezaiyan.passki.internal.CredentialDescriptorJson
import com.alirezaiyan.passki.internal.RequestOptionsJson
import com.alirezaiyan.passki.internal.WebAuthnResponses
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertionRequest
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistrationRequest
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttachment
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialRegistrationRequestStyle

/** Maps WebAuthn JSON options to AuthenticationServices requests and back. */
internal object AppleRequests {

    fun registration(
        options: CreationOptionsJson,
        conditional: Boolean,
    ): ASAuthorizationPlatformPublicKeyCredentialRegistrationRequest {
        val rpId = options.rp.id ?: throw invalid("rp.id is required on iOS. Set the RP ID in your server's registration options.")
        val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier = rpId)
        val request = provider.createCredentialRegistrationRequestWithChallenge(
            challenge = Base64Url.decode(options.challenge).toNSData(),
            name = options.user.name,
            userID = Base64Url.decode(options.user.id).toNSData(),
        )
        if (options.user.displayName.isNotEmpty()) request.displayName = options.user.displayName
        options.authenticatorSelection?.userVerification?.let { request.userVerificationPreference = it }
        options.attestation?.let { request.attestationPreference = it }
        if (options.excludeCredentials.isNotEmpty() && iosAtLeast(17, 4)) {
            request.excludedCredentials = options.excludeCredentials.map { it.toApple() }
        }
        if (conditional) {
            request.requestStyle =
                ASAuthorizationPublicKeyCredentialRegistrationRequestStyle.ASAuthorizationPublicKeyCredentialRegistrationRequestStyleConditional
        }
        return request
    }

    fun assertion(options: RequestOptionsJson): ASAuthorizationPlatformPublicKeyCredentialAssertionRequest {
        val rpId = options.rpId ?: throw invalid("rpId is required on iOS. Set rpId in your server's authentication options.")
        val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier = rpId)
        val request = provider.createCredentialAssertionRequestWithChallenge(Base64Url.decode(options.challenge).toNSData())
        if (options.allowCredentials.isNotEmpty()) {
            request.allowedCredentials = options.allowCredentials.map { it.toApple() }
        }
        options.userVerification?.let { request.userVerificationPreference = it }
        return request
    }

    fun registrationJson(credential: ASAuthorizationPlatformPublicKeyCredentialRegistration): String =
        WebAuthnResponses.registration(
            credentialId = credential.credentialID.toByteArray(),
            clientDataJson = credential.rawClientDataJSON.toByteArray(),
            attestationObject = credential.rawAttestationObject?.toByteArray() ?: throw missing("attestationObject"),
            transports = listOf("hybrid", "internal"),
            attachment = attachmentOf(credential.attachment),
        )

    fun assertionJson(credential: ASAuthorizationPlatformPublicKeyCredentialAssertion): String =
        WebAuthnResponses.authentication(
            credentialId = credential.credentialID.toByteArray(),
            clientDataJson = credential.rawClientDataJSON.toByteArray(),
            authenticatorData = credential.rawAuthenticatorData?.toByteArray() ?: throw missing("authenticatorData"),
            signature = credential.signature?.toByteArray() ?: throw missing("signature"),
            userHandle = credential.userID?.toByteArray(),
            attachment = attachmentOf(credential.attachment),
        )

    private fun attachmentOf(value: ASAuthorizationPublicKeyCredentialAttachment): String =
        if (iosAtLeast(16, 6) &&
            value == ASAuthorizationPublicKeyCredentialAttachment.ASAuthorizationPublicKeyCredentialAttachmentCrossPlatform
        ) {
            "cross-platform"
        } else {
            "platform"
        }

    private fun CredentialDescriptorJson.toApple() =
        ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID = Base64Url.decode(id).toNSData())

    private fun invalid(message: String) = PasskeyException(PasskeyError.InvalidOptions(message))

    private fun missing(field: String) =
        PasskeyException(PasskeyError.Unknown("iOS returned no $field.", platformCode = null))
}
```

Note: the `attachment` property was introduced in iOS 16.6. Reading it on 16.0–16.5 is guarded by `iosAtLeast(16, 6)` only for the comparison. If that crashes on 16.0–16.5 simulators, wrap the property read itself: `if (iosAtLeast(16, 6)) attachmentOf(credential.attachment) else "platform"`.

- [ ] **Step 3: Create `IosPasskeys.kt`**

```kotlin
package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyRegistration
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskeySignals
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import com.alirezaiyan.passki.internal.passkeyCatching
import platform.AuthenticationServices.ASAuthorizationPasswordProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASPasswordCredential
import platform.UIKit.UIWindow

internal class IosPasskeys(private val anchor: () -> UIWindow? = ::keyWindow) : Passkeys {

    override suspend fun capabilities(): PasskeyCapabilities = PasskeyCapabilities(
        isSupported = iosAtLeast(16),
        immediateSignIn = iosAtLeast(16),
        unifiedPasswordSignIn = iosAtLeast(16),
        conditionalCreate = iosAtLeast(18),
        signals = false,
    )

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = false)

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        createPasskey(optionsJson, conditional = true)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireIos(16, "Passkeys")
            val options = WebAuthnOptions.parseRequest(optionsJson).model
            val authorization = AuthorizationSession.perform(
                requests = listOf(AppleRequests.assertion(options)),
                anchor = anchor,
                preferImmediatelyAvailable = mode == SignInMode.ImmediateOnly,
            )
            when (val credential = authorization.credential) {
                is ASAuthorizationPlatformPublicKeyCredentialAssertion -> parseAssertion(AppleRequests.assertionJson(credential))
                else -> throw unexpected(credential)
            }
        }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireIos(16, "Passkeys")
            val options = WebAuthnOptions.parseRequest(optionsJson).model
            val authorization = AuthorizationSession.perform(
                requests = listOf(AppleRequests.assertion(options), ASAuthorizationPasswordProvider().createRequest()),
                anchor = anchor,
                preferImmediatelyAvailable = false,
            )
            when (val credential = authorization.credential) {
                is ASAuthorizationPlatformPublicKeyCredentialAssertion ->
                    SignInCredential.Passkey(parseAssertion(AppleRequests.assertionJson(credential)))
                is ASPasswordCredential -> SignInCredential.Password(credential.user, credential.password)
                else -> throw unexpected(credential)
            }
        }

    override val signals: PasskeySignals = object : PasskeySignals {
        private val failure = PasskeyResult.Failure(
            PasskeyError.NotSupported(
                "The Signal API on iOS (ASCredentialUpdater, iOS 26) is Swift-only and is not available yet.",
            ),
        )

        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> = failure

        override suspend fun acceptedCredentials(
            rpId: String,
            userId: String,
            credentialIds: List<String>,
        ): PasskeyResult<Unit> = failure

        override suspend fun currentUserDetails(
            rpId: String,
            userId: String,
            name: String,
            displayName: String,
        ): PasskeyResult<Unit> = failure
    }

    private suspend fun createPasskey(optionsJson: String, conditional: Boolean): PasskeyResult<PasskeyRegistration> =
        passkeyCatching(Throwable::toPasskeyError) {
            requireIos(16, "Passkeys")
            if (conditional) requireIos(18, "Conditional passkey creation (upgrade)")
            val options = WebAuthnOptions.parseCreation(optionsJson).model
            val authorization = AuthorizationSession.perform(
                requests = listOf(AppleRequests.registration(options, conditional)),
                anchor = anchor,
                preferImmediatelyAvailable = false,
            )
            when (val credential = authorization.credential) {
                is ASAuthorizationPlatformPublicKeyCredentialRegistration ->
                    parseRegistration(AppleRequests.registrationJson(credential))
                else -> throw unexpected(credential)
            }
        }

    private fun unexpected(value: Any?): PasskeyException =
        PasskeyException(PasskeyError.Unknown("Unexpected credential type ${value?.let { it::class.simpleName }}.", null))
}
```

- [ ] **Step 4: Replace `Passkeys.ios.kt`**

```kotlin
package com.alirezaiyan.passki

import com.alirezaiyan.passki.apple.IosPasskeys

public actual fun Passkeys(): Passkeys = IosPasskeys()
```

- [ ] **Step 5: Compile both iOS targets + run iOS tests**

Run: `./gradlew :passki:compileKotlinIosArm64 :passki:iosSimulatorArm64Test`
Expected: BUILD SUCCESSFUL; NSDataInteropTest + AppleErrorMapperTest pass (9 tests).

- [ ] **Step 6: Commit**

```bash
git add passki/src
git commit -m "feat(ios): implement Passkeys with AuthenticationServices"
```

---

### Task 15: `passki-test` module + CBOR writer

**Files:**
- Create: `passki-test/build.gradle.kts`
- Create: `passki-test/src/commonMain/kotlin/com/alirezaiyan/passki/test/CborWriter.kt`
- Test: `passki-test/src/commonTest/kotlin/com/alirezaiyan/passki/test/CborWriterTest.kt`
- Modify: `settings.gradle.kts` (re-enable `include(":passki-test")`)

- [ ] **Step 1: Create `passki-test/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    explicitApi()

    android {
        namespace = "com.alirezaiyan.passki.testing"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()
    jvm()
    js { browser() }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        all {
            languageSettings.optIn("com.alirezaiyan.passki.InternalPasskiApi")
        }
        commonMain.dependencies {
            api(project(":passki"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.webauthn4j.core)
        }
    }
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.alirezaiyan.passki.test

import kotlin.test.Test
import kotlin.test.assertContentEquals

class CborWriterTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun smallPositiveInt() = assertContentEquals(bytes(0x01), CborWriter().int(1).toByteArray())

    @Test
    fun negativeInts() {
        assertContentEquals(bytes(0x20), CborWriter().int(-1).toByteArray())
        assertContentEquals(bytes(0x26), CborWriter().int(-7).toByteArray())
    }

    @Test
    fun oneByteLengthInt() = assertContentEquals(bytes(0x18, 0xFF), CborWriter().int(255).toByteArray())

    @Test
    fun twoByteLengthInt() = assertContentEquals(bytes(0x19, 0x01, 0x00), CborWriter().int(256).toByteArray())

    @Test
    fun textString() = assertContentEquals(bytes(0x63, 'f'.code, 'm'.code, 't'.code), CborWriter().text("fmt").toByteArray())

    @Test
    fun byteStringWithOneByteLength() {
        val encoded = CborWriter().bytes(ByteArray(32)).toByteArray()
        assertContentEquals(bytes(0x58, 0x20), encoded.copyOfRange(0, 2))
        assertContentEquals(ByteArray(32), encoded.copyOfRange(2, 34))
    }

    @Test
    fun mapHeader() {
        assertContentEquals(bytes(0xA5), CborWriter().mapHeader(5).toByteArray())
        assertContentEquals(bytes(0xA0), CborWriter().mapHeader(0).toByteArray())
    }
}
```

- [ ] **Step 3: Re-enable `include(":passki-test")` in `settings.gradle.kts` and run the test to verify it fails**

Run: `./gradlew :passki-test:jvmTest --tests "com.alirezaiyan.passki.test.CborWriterTest"`
Expected: FAIL, `Unresolved reference 'CborWriter'`.

- [ ] **Step 4: Implement `CborWriter.kt`**

```kotlin
package com.alirezaiyan.passki.test

/** Minimal canonical CBOR encoder: just what WebAuthn attestation objects and COSE keys need. */
internal class CborWriter {
    private val out = ArrayList<Byte>()

    fun int(value: Long): CborWriter = apply { if (value >= 0) head(0, value) else head(1, -1 - value) }

    fun int(value: Int): CborWriter = int(value.toLong())

    fun bytes(value: ByteArray): CborWriter = apply {
        head(2, value.size.toLong())
        value.forEach { out += it }
    }

    fun text(value: String): CborWriter = apply {
        val encoded = value.encodeToByteArray()
        head(3, encoded.size.toLong())
        encoded.forEach { out += it }
    }

    fun mapHeader(size: Int): CborWriter = apply { head(5, size.toLong()) }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun head(major: Int, value: Long) {
        val m = major shl 5
        when {
            value < 24 -> out += (m or value.toInt()).toByte()
            value < 0x100 -> {
                out += (m or 24).toByte()
                out += value.toByte()
            }
            value < 0x10000 -> {
                out += (m or 25).toByte()
                out += (value shr 8).toByte()
                out += value.toByte()
            }
            else -> {
                out += (m or 26).toByte()
                for (shift in intArrayOf(24, 16, 8, 0)) out += (value shr shift).toByte()
            }
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :passki-test:jvmTest --tests "com.alirezaiyan.passki.test.CborWriterTest"`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit**

```bash
git add passki-test settings.gradle.kts
git commit -m "feat(test): add passki-test module with minimal CBOR writer"
```

---

### Task 16: FakeAuthenticator + TestOptions, verified by webauthn4j

**Files:**
- Create: `passki-test/src/commonMain/kotlin/com/alirezaiyan/passki/test/TestOptions.kt`
- Create: `passki-test/src/commonMain/kotlin/com/alirezaiyan/passki/test/FakeAuthenticator.kt`
- Test: `passki-test/src/jvmTest/kotlin/com/alirezaiyan/passki/test/WebAuthn4jVerificationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.internal.Base64Url
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class WebAuthn4jVerificationTest {
    private val origin = "https://passki.example"
    private val rpId = "passki.example"
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager()

    private fun serverProperty(challenge: ByteArray) =
        ServerProperty(Origin(origin), rpId, DefaultChallenge(challenge))

    @Test
    fun registrationAndAssertionVerifyWithRealServerLibrary() = runTest {
        val authenticator = FakeAuthenticator(origin)

        val registrationChallenge = Random.nextBytes(32)
        val registrationJson = authenticator.register(
            TestOptions.creation(rpId = rpId, challenge = Base64Url.encode(registrationChallenge)),
        )
        val registration = manager.parseRegistrationResponseJSON(registrationJson)
        manager.verify(
            registration,
            RegistrationParameters(serverProperty(registrationChallenge), null, true, true),
        )

        val record = CredentialRecordImpl(
            registration.attestationObject!!,
            registration.collectedClientData,
            registration.clientExtensions,
            registration.transports,
        )

        val assertionChallenge = Random.nextBytes(32)
        val assertionJson = authenticator.signIn(
            TestOptions.request(rpId = rpId, challenge = Base64Url.encode(assertionChallenge)),
        )
        val assertion = manager.parseAuthenticationResponseJSON(assertionJson)
        manager.verify(
            assertion,
            AuthenticationParameters(serverProperty(assertionChallenge), record, null, true, true),
        )
        assertEquals(1L, assertion.authenticatorData!!.signCount)
    }
}
```

If webauthn4j 0.30 has no 3-arg `ServerProperty(origin, rpId, challenge)`, use `ServerProperty.builder().origin(Origin(origin)).rpId(rpId).challenge(DefaultChallenge(challenge)).build()`. Check https://webauthn4j.github.io/webauthn4j/en/ for the exact 0.30 signature. The `RegistrationParameters` / `AuthenticationParameters` arg order is `(serverProperty, pubKeyCredParams|credentialRecord, allowCredentials?, userVerificationRequired, userPresenceRequired)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki-test:jvmTest --tests "com.alirezaiyan.passki.test.WebAuthn4jVerificationTest"`
Expected: FAIL, `Unresolved reference 'FakeAuthenticator'`.

- [ ] **Step 3: Implement `TestOptions.kt`**

```kotlin
package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.internal.Base64Url
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/** Builds server-style WebAuthn options JSON for tests. Not cryptographically random; tests only. */
public object TestOptions {
    public fun creation(
        rpId: String,
        challenge: String = randomBase64Url(32),
        userId: String = randomBase64Url(16),
        userName: String = "alice@example.com",
        displayName: String = "Alice",
        excludeCredentialIds: List<String> = emptyList(),
    ): String = buildJsonObject {
        putJsonObject("rp") {
            put("id", rpId)
            put("name", rpId)
        }
        putJsonObject("user") {
            put("id", userId)
            put("name", userName)
            put("displayName", displayName)
        }
        put("challenge", challenge)
        putJsonArray("pubKeyCredParams") {
            addJsonObject {
                put("type", "public-key")
                put("alg", -7)
            }
            addJsonObject {
                put("type", "public-key")
                put("alg", -257)
            }
        }
        putJsonArray("excludeCredentials") {
            excludeCredentialIds.forEach { id ->
                addJsonObject {
                    put("id", id)
                    put("type", "public-key")
                }
            }
        }
        putJsonObject("authenticatorSelection") {
            put("residentKey", "required")
            put("userVerification", "preferred")
        }
        put("attestation", "none")
    }.toString()

    public fun request(
        rpId: String,
        challenge: String = randomBase64Url(32),
        allowCredentialIds: List<String> = emptyList(),
    ): String = buildJsonObject {
        put("challenge", challenge)
        put("rpId", rpId)
        put("userVerification", "preferred")
        putJsonArray("allowCredentials") {
            allowCredentialIds.forEach { id ->
                addJsonObject {
                    put("id", id)
                    put("type", "public-key")
                }
            }
        }
    }.toString()

    private fun randomBase64Url(size: Int): String = Base64Url.encode(Random.nextBytes(size))
}
```

- [ ] **Step 4: Implement `FakeAuthenticator.kt`**

```kotlin
package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.internal.Base64Url
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.WebAuthnResponses
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** A passkey stored by [FakeAuthenticator]. */
public class FakeCredential internal constructor(
    public val id: String,
    public val rpId: String,
    public val userId: String,
    public val userName: String,
    internal val privateKey: ECDSA.PrivateKey,
) {
    public var signCount: Int = 0
        internal set
}

/**
 * In-memory platform authenticator producing cryptographically valid WebAuthn responses
 * (ES256, attestation "none"), so they pass real server verifiers.
 */
public class FakeAuthenticator(
    public val origin: String,
    private val random: Random = Random.Default,
) {
    private val crypto = CryptographyProvider.Default
    private val credentials = mutableListOf<FakeCredential>()

    public val storedCredentials: List<FakeCredential> get() = credentials.toList()

    /** Returns RegistrationResponseJSON, or throws [PasskeyException]. */
    public suspend fun register(optionsJson: String): String {
        val options = WebAuthnOptions.parseCreation(optionsJson).model
        val rpId = options.rp.id ?: hostOf(origin)
        val excluded = options.excludeCredentials.map { it.id }.toSet()
        if (credentials.any { it.rpId == rpId && it.id in excluded }) {
            throw PasskeyException(PasskeyError.AlreadyRegistered)
        }

        val keyPair = crypto.get(ECDSA).keyPairGenerator(EC.Curve.P256).generateKey()
        val rawPublicKey = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW) // 0x04 || X || Y
        val credentialId = random.nextBytes(16)

        val clientData = clientDataJson("webauthn.create", options.challenge)
        val authData = authenticatorData(rpId, FLAGS_REGISTER, signCount = 0) +
            attestedCredentialData(credentialId, rawPublicKey)
        val attestationObject = CborWriter()
            .mapHeader(3)
            .text("fmt").text("none")
            .text("attStmt").mapHeader(0)
            .text("authData").bytes(authData)
            .toByteArray()

        credentials += FakeCredential(
            id = Base64Url.encode(credentialId),
            rpId = rpId,
            userId = options.user.id,
            userName = options.user.name,
            privateKey = keyPair.privateKey,
        )
        return WebAuthnResponses.registration(
            credentialId = credentialId,
            clientDataJson = clientData,
            attestationObject = attestationObject,
            transports = listOf("internal", "hybrid"),
            attachment = "platform",
        )
    }

    /** Returns AuthenticationResponseJSON, or throws [PasskeyException]. */
    public suspend fun signIn(optionsJson: String): String {
        val options = WebAuthnOptions.parseRequest(optionsJson).model
        val rpId = options.rpId ?: hostOf(origin)
        val allowed = options.allowCredentials.map { it.id }.toSet()
        val credential = credentials.lastOrNull { it.rpId == rpId && (allowed.isEmpty() || it.id in allowed) }
            ?: throw PasskeyException(PasskeyError.NoCredentials)

        credential.signCount += 1
        val clientData = clientDataJson("webauthn.get", options.challenge)
        val authData = authenticatorData(rpId, FLAGS_ASSERT, credential.signCount)
        val signature = credential.privateKey
            .signatureGenerator(SHA256, ECDSA.SignatureFormat.DER)
            .generateSignature(authData + sha256(clientData))

        return WebAuthnResponses.authentication(
            credentialId = Base64Url.decode(credential.id),
            clientDataJson = clientData,
            authenticatorData = authData,
            signature = signature,
            userHandle = Base64Url.decode(credential.userId),
            attachment = "platform",
        )
    }

    public fun remove(credentialId: String) {
        credentials.removeAll { it.id == credentialId }
    }

    public fun retainOnly(rpId: String, userId: String, credentialIds: List<String>) {
        credentials.removeAll { it.rpId == rpId && it.userId == userId && it.id !in credentialIds }
    }

    private fun clientDataJson(type: String, challenge: String): ByteArray = buildJsonObject {
        put("type", type)
        put("challenge", Base64Url.encode(Base64Url.decode(challenge)))
        put("origin", origin)
        put("crossOrigin", false)
    }.toString().encodeToByteArray()

    private suspend fun authenticatorData(rpId: String, flags: Int, signCount: Int): ByteArray =
        sha256(rpId.encodeToByteArray()) + byteArrayOf(flags.toByte()) + signCount.toBigEndianBytes()

    private fun attestedCredentialData(credentialId: ByteArray, rawPublicKey: ByteArray): ByteArray {
        val x = rawPublicKey.copyOfRange(1, 33)
        val y = rawPublicKey.copyOfRange(33, 65)
        val coseKey = CborWriter()
            .mapHeader(5)
            .int(1).int(2) // kty: EC2
            .int(3).int(-7) // alg: ES256
            .int(-1).int(1) // crv: P-256
            .int(-2).bytes(x)
            .int(-3).bytes(y)
            .toByteArray()
        val aaguid = ByteArray(16)
        val length = byteArrayOf((credentialId.size shr 8).toByte(), credentialId.size.toByte())
        return aaguid + length + credentialId + coseKey
    }

    private suspend fun sha256(data: ByteArray): ByteArray = crypto.get(SHA256).hasher().hash(data)

    private fun Int.toBigEndianBytes(): ByteArray =
        byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), toByte())

    private fun hostOf(origin: String): String =
        origin.substringAfter("://").substringBefore("/").substringBefore(":")

    private companion object {
        // UP | UV | BE | BS | AT
        const val FLAGS_REGISTER = 0x01 or 0x04 or 0x08 or 0x10 or 0x40

        // UP | UV | BE | BS
        const val FLAGS_ASSERT = 0x01 or 0x04 or 0x08 or 0x10
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :passki-test:jvmTest --tests "com.alirezaiyan.passki.test.WebAuthn4jVerificationTest"`
Expected: PASS (1 test). A webauthn4j `*VerificationException` means a byte-layout bug in FakeAuthenticator. Fix it there, never in the test.

- [ ] **Step 6: Commit**

```bash
git add passki-test
git commit -m "feat(test): add FakeAuthenticator verified against webauthn4j"
```

---

### Task 17: FakePasskeys

**Files:**
- Create: `passki-test/src/commonMain/kotlin/com/alirezaiyan/passki/test/FakePasskeys.kt`
- Test: `passki-test/src/commonTest/kotlin/com/alirezaiyan/passki/test/FakePasskeysTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.getOrThrow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakePasskeysTest {
    private val rpId = "passki.example"
    private val passkeys = FakePasskeys(origin = "https://$rpId")

    @Test
    fun registerThenSignInReturnsSameCredential() = runTest {
        val registration = passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        val assertion = passkeys.signIn(TestOptions.request(rpId)).getOrThrow()
        assertEquals(registration.credentialId, assertion.credentialId)
    }

    @Test
    fun signInWithoutPasskeyIsNoCredentials() = runTest {
        assertEquals(PasskeyResult.Failure(PasskeyError.NoCredentials), passkeys.signIn(TestOptions.request(rpId)))
    }

    @Test
    fun excludedCredentialIsAlreadyRegistered() = runTest {
        val first = passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        val second = passkeys.register(TestOptions.creation(rpId, excludeCredentialIds = listOf(first.credentialId)))
        assertEquals(PasskeyResult.Failure(PasskeyError.AlreadyRegistered), second)
    }

    @Test
    fun failNextWithInjectsOneError() = runTest {
        passkeys.failNextWith(PasskeyError.Cancelled)
        assertEquals(PasskeyResult.Failure(PasskeyError.Cancelled), passkeys.register(TestOptions.creation(rpId)))
        assertIs<PasskeyResult.Success<*>>(passkeys.register(TestOptions.creation(rpId)))
    }

    @Test
    fun unknownCredentialSignalRemovesPasskey() = runTest {
        val registration = passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        passkeys.signals.unknownCredential(rpId, registration.credentialId).getOrThrow()
        assertEquals(PasskeyResult.Failure(PasskeyError.NoCredentials), passkeys.signIn(TestOptions.request(rpId)))
        assertEquals(listOf<FakeSignal>(FakeSignal.UnknownCredential(rpId, registration.credentialId)), passkeys.receivedSignals)
    }

    @Test
    fun signInOrPasswordFallsBackToSavedPassword() = runTest {
        passkeys.savedPasswords["alice"] = "hunter2"
        val credential = assertIs<SignInCredential.Password>(passkeys.signInOrPassword(TestOptions.request(rpId)).getOrThrow())
        assertEquals("alice", credential.id)
        assertEquals("hunter2", credential.password)
    }

    @Test
    fun signInOrPasswordPrefersPasskey() = runTest {
        passkeys.savedPasswords["alice"] = "hunter2"
        passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        assertIs<SignInCredential.Passkey>(passkeys.signInOrPassword(TestOptions.request(rpId)).getOrThrow())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :passki-test:jvmTest --tests "com.alirezaiyan.passki.test.FakePasskeysTest"`
Expected: FAIL, `Unresolved reference 'FakePasskeys'`.

- [ ] **Step 3: Implement**

```kotlin
package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyCapabilities
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyRegistration
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskeySignals
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.internal.parseAssertion
import com.alirezaiyan.passki.internal.parseRegistration
import kotlin.random.Random

public sealed interface FakeSignal {
    public data class UnknownCredential(val rpId: String, val credentialId: String) : FakeSignal
    public data class AcceptedCredentials(val rpId: String, val userId: String, val credentialIds: List<String>) : FakeSignal
    public data class CurrentUserDetails(val rpId: String, val userId: String, val name: String, val displayName: String) :
        FakeSignal
}

/** Drop-in [Passkeys] for unit tests: real WebAuthn responses, no device. */
public class FakePasskeys(origin: String, random: Random = Random.Default) : Passkeys {
    public val authenticator: FakeAuthenticator = FakeAuthenticator(origin, random)

    public var capabilitiesResult: PasskeyCapabilities = PasskeyCapabilities(
        isSupported = true,
        immediateSignIn = true,
        unifiedPasswordSignIn = true,
        conditionalCreate = true,
        signals = true,
    )

    /** Username → password offered by [signInOrPassword] when no passkey matches. */
    public val savedPasswords: MutableMap<String, String> = linkedMapOf()

    public val receivedSignals: MutableList<FakeSignal> = mutableListOf()

    private val queuedErrors = ArrayDeque<PasskeyError>()

    /** The next operation fails with [error]; later ones behave normally. */
    public fun failNextWith(error: PasskeyError) {
        queuedErrors.addLast(error)
    }

    override suspend fun capabilities(): PasskeyCapabilities = capabilitiesResult

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        result { parseRegistration(authenticator.register(optionsJson)) }

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> = register(optionsJson)

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> =
        result { parseAssertion(authenticator.signIn(optionsJson)) }

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> = result {
        try {
            SignInCredential.Passkey(parseAssertion(authenticator.signIn(optionsJson)))
        } catch (e: PasskeyException) {
            val password = savedPasswords.entries.firstOrNull()
            if (e.error != PasskeyError.NoCredentials || password == null) throw e
            SignInCredential.Password(password.key, password.value)
        }
    }

    override val signals: PasskeySignals = object : PasskeySignals {
        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> = result {
            receivedSignals += FakeSignal.UnknownCredential(rpId, credentialId)
            authenticator.remove(credentialId)
        }

        override suspend fun acceptedCredentials(
            rpId: String,
            userId: String,
            credentialIds: List<String>,
        ): PasskeyResult<Unit> = result {
            receivedSignals += FakeSignal.AcceptedCredentials(rpId, userId, credentialIds)
            authenticator.retainOnly(rpId, userId, credentialIds)
        }

        override suspend fun currentUserDetails(
            rpId: String,
            userId: String,
            name: String,
            displayName: String,
        ): PasskeyResult<Unit> = result {
            receivedSignals += FakeSignal.CurrentUserDetails(rpId, userId, name, displayName)
        }
    }

    private inline fun <T> result(block: () -> T): PasskeyResult<T> {
        queuedErrors.removeFirstOrNull()?.let { return PasskeyResult.Failure(it) }
        return try {
            PasskeyResult.Success(block())
        } catch (e: PasskeyException) {
            PasskeyResult.Failure(e.error)
        }
    }
}
```

Explicit API: add `public` to the `val`s inside the `FakeSignal` data classes if the compiler asks.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :passki-test:jvmTest`
Expected: PASS (CborWriterTest 7, WebAuthn4jVerificationTest 1, FakePasskeysTest 7).

Then run on iOS too (cryptography uses the Apple provider there):
Run: `./gradlew :passki-test:iosSimulatorArm64Test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add passki-test
git commit -m "feat(test): add FakePasskeys drop-in for app unit tests"
```

---

### Task 18: Recorded-fixture contract test

**Files:**
- Create: `passki-test/src/jvmTest/kotlin/com/alirezaiyan/passki/test/RecordedFixturesTest.kt`
- Create: `passki-test/src/jvmTest/resources/fixtures/.gitkeep`

Fixture format: one file per real-device response, `fixtures/<platform>-<yyyymmdd>-registration.json`:

```json
{"rpId": "alirezaiyan.com", "response": { ...RegistrationResponseJSON exactly as PassKi returned it... }}
```

Origin and challenge are read from the response's own clientDataJSON. The test proves the bytes PassKi emits on real devices are valid WebAuthn that a production verifier accepts.

- [ ] **Step 1: Write the test**

```kotlin
package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.internal.Base64Url
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test

class RecordedFixturesTest {
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager()

    @Test
    fun recordedRegistrationsVerifyWithWebAuthn4j() {
        val files = File("src/jvmTest/resources/fixtures")
            .listFiles { file -> file.name.endsWith("-registration.json") }
            .orEmpty()
        files.forEach { file ->
            val fixture = Json.parseToJsonElement(file.readText()).jsonObject
            val rpId = fixture["rpId"]!!.jsonPrimitive.content
            val response = fixture["response"]!!.jsonObject
            val clientData = Json.parseToJsonElement(
                Base64Url.decode(response["response"]!!.jsonObject["clientDataJSON"]!!.jsonPrimitive.content)
                    .decodeToString(),
            ).jsonObject
            val origin = clientData["origin"]!!.jsonPrimitive.content
            val challenge = Base64Url.decode(clientData["challenge"]!!.jsonPrimitive.content)

            val data = manager.parseRegistrationResponseJSON(response.toString())
            manager.verify(
                data,
                RegistrationParameters(ServerProperty(Origin(origin), rpId, DefaultChallenge(challenge)), null, false, true),
            )
        }
        println("Verified ${files.size} recorded registration fixture(s)")
    }
}
```

Use the same `ServerProperty` construction you settled on in Task 16.

- [ ] **Step 2: Create the empty fixtures dir and run**

```bash
mkdir -p passki-test/src/jvmTest/resources/fixtures && touch passki-test/src/jvmTest/resources/fixtures/.gitkeep
```

Run: `./gradlew :passki-test:jvmTest --tests "com.alirezaiyan.passki.test.RecordedFixturesTest" -i | grep "Verified"`
Expected: PASS, prints `Verified 0 recorded registration fixture(s)`. Fixtures are added in Task 21.

- [ ] **Step 3: Commit**

```bash
git add passki-test
git commit -m "test: add recorded real-device fixture contract test"
```

---

### Task 19: Sample app wiring

**Files:**
- Modify: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/alirezaiyan/passki/DemoRelyingParty.kt`
- Replace: `shared/src/commonMain/kotlin/com/alirezaiyan/passki/App.kt`

- [ ] **Step 1: Add the dependency.** In `shared/build.gradle.kts` → `commonMain.dependencies { ... }`, add:

```kotlin
            implementation(project(":passki"))
```

- [ ] **Step 2: Create `DemoRelyingParty.kt`**

```kotlin
package com.alirezaiyan.passki

import kotlin.io.encoding.Base64
import kotlin.random.Random

/**
 * Client-side stand-in for a server, for the demo only. A real app gets these
 * options from its backend and verifies the responses there.
 */
object DemoRelyingParty {
    const val RP_ID = "alirezaiyan.com"
    const val USER_ID = "cGFzc2tpLWRlbW8" // base64url("passki-demo")

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val credentialIds = mutableListOf<String>()

    fun creationOptions(): String {
        val exclude = credentialIds.joinToString(",") { """{"type":"public-key","id":"$it"}""" }
        return """
            {"rp":{"id":"$RP_ID","name":"PassKi Demo"},
             "user":{"id":"$USER_ID","name":"demo@passki.dev","displayName":"PassKi Demo"},
             "challenge":"${challenge()}",
             "pubKeyCredParams":[{"type":"public-key","alg":-7},{"type":"public-key","alg":-257}],
             "excludeCredentials":[$exclude],
             "authenticatorSelection":{"residentKey":"required","userVerification":"preferred"},
             "attestation":"none","timeout":60000}
        """.trimIndent()
    }

    fun requestOptions(): String =
        """{"challenge":"${challenge()}","rpId":"$RP_ID","userVerification":"preferred","timeout":60000}"""

    private fun challenge(): String = base64Url.encode(Random.nextBytes(32))
}
```

- [ ] **Step 3: Replace `App.kt`**

```kotlin
package com.alirezaiyan.passki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun App(passkeys: Passkeys = remember { Passkeys() }) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        var log by remember { mutableStateOf("Ready. RP ID: ${DemoRelyingParty.RP_ID}") }

        fun run(label: String, action: suspend () -> PasskeyResult<*>) {
            scope.launch {
                log = "$label…"
                log = when (val result = action()) {
                    is PasskeyResult.Success -> "$label ✓\n${result.value}"
                    is PasskeyResult.Failure -> "$label ✗ ${result.error}\n${result.error.fixHint.orEmpty()}"
                }
                println("PASSKI_DEMO $log")
            }
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { run("Capabilities") { PasskeyResult.Success(passkeys.capabilities()) } }) {
                Text("Capabilities")
            }
            Button(onClick = {
                run("Register") {
                    passkeys.register(DemoRelyingParty.creationOptions())
                        .onSuccess { DemoRelyingParty.credentialIds += it.credentialId }
                }
            }) { Text("Register passkey") }
            Button(onClick = { run("Sign in") { passkeys.signIn(DemoRelyingParty.requestOptions()) } }) {
                Text("Sign in")
            }
            Button(onClick = {
                run("Sign in (device only)") {
                    passkeys.signIn(DemoRelyingParty.requestOptions(), SignInMode.ImmediateOnly)
                }
            }) { Text("Sign in (device only)") }
            Button(onClick = { run("Sign in or password") { passkeys.signInOrPassword(DemoRelyingParty.requestOptions()) } }) {
                Text("Sign in (passkey or password)")
            }
            Button(onClick = { run("Upgrade") { passkeys.upgrade(DemoRelyingParty.creationOptions()) } }) {
                Text("Upgrade (conditional create)")
            }
            Button(onClick = {
                run("Signal unknown") {
                    val id = DemoRelyingParty.credentialIds.lastOrNull() ?: "AA"
                    passkeys.signals.unknownCredential(DemoRelyingParty.RP_ID, id)
                }
            }) { Text("Signal: forget last passkey") }
            SelectionContainer {
                Text(log, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
```

- [ ] **Step 4: Build all sample apps**

Run: `./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin :webApp:compileKotlinWasmJs :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

Then run the desktop sample to see the graceful fallback: `./gradlew :desktopApp:run`. Click "Register passkey".
Expected log: `Register ✗ NotSupported(message=PassKi does not support JVM desktop yet. It is on the roadmap.)`

- [ ] **Step 5: Commit**

```bash
git add shared
git commit -m "feat(sample): demo app exercising every Passkeys operation"
```

---

### Task 20: RP domain association (assetlinks + AASA + entitlement)

**Files:**
- Create: `docs/setup/well-known/assetlinks.json`
- Create: `docs/setup/well-known/apple-app-site-association`
- External: `~/projects/alirezaiyan.com/public/.well-known/` (**publishing is outward-facing; ask the user before deploying**)

- [ ] **Step 1: Get the debug signing SHA-256**

Run: `./gradlew :androidApp:signingReport | grep -A4 "Variant: debug" | grep SHA-256`
Expected: `SHA-256: AB:CD:...` (64 hex pairs)

- [ ] **Step 2: Create `docs/setup/well-known/assetlinks.json`.** Paste the SHA-256 printed in Step 1 as the array element.

```json
[
  {
    "relation": [
      "delegate_permission/common.handle_all_urls",
      "delegate_permission/common.get_login_creds"
    ],
    "target": {
      "namespace": "android_app",
      "package_name": "com.alirezaiyan.passki",
      "sha256_cert_fingerprints": ["<SHA-256 from Step 1>"]
    }
  }
]
```

- [ ] **Step 3: Ask the user for their Apple Team ID** (10 chars; developer.apple.com → Membership). Set it in `iosApp/Configuration/Config.xcconfig` as `TEAM_ID=<id>`. The bundle id becomes `com.alirezaiyan.passki.PassKi<TeamID>`.

- [ ] **Step 4: Create `docs/setup/well-known/apple-app-site-association`** (no extension). Fill in the Team ID from Step 3.

```json
{
  "webcredentials": {
    "apps": ["<TeamID>.com.alirezaiyan.passki.PassKi<TeamID>"]
  }
}
```

- [ ] **Step 5: Add the iOS entitlement.** In Xcode: open `iosApp/iosApp.xcodeproj` → target iosApp → Signing & Capabilities → `+ Capability` → Associated Domains → add `webcredentials:alirezaiyan.com?mode=developer`. This creates `iosApp/iosApp/iosApp.entitlements` and sets `CODE_SIGN_ENTITLEMENTS`. On the test device, enable Settings → Developer → Associated Domains Development.

- [ ] **Step 6: Publish both files. CONFIRM WITH USER FIRST.**

```bash
mkdir -p ~/projects/alirezaiyan.com/public/.well-known
cp docs/setup/well-known/assetlinks.json docs/setup/well-known/apple-app-site-association ~/projects/alirezaiyan.com/public/.well-known/
```

Then deploy with `ali site` (user's ops gateway). The alirezaiyan.com repo needs its own commit; the user decides.

- [ ] **Step 7: Verify serving**

Run: `curl -s https://alirezaiyan.com/.well-known/assetlinks.json | head -c 200` and `curl -s https://alirezaiyan.com/.well-known/apple-app-site-association`
Expected: the JSON from Steps 2 and 4, HTTP 200, no redirect.

Run: `curl -s "https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://alirezaiyan.com&relation=delegate_permission/common.get_login_creds"`
Expected: a statement listing `com.alirezaiyan.passki`.

If Next.js serves the AASA file with the wrong content type, add a `headers()` rule in `next.config` for `/.well-known/apple-app-site-association` → `Content-Type: application/json`.

- [ ] **Step 8: Commit**

```bash
git add docs/setup iosApp
git commit -m "docs: add RP association templates and iOS associated domains"
```

---

### Task 21: Manual E2E on devices + record fixtures

**Files:**
- Create: `passki-test/src/jvmTest/resources/fixtures/android-<yyyymmdd>-registration.json`
- Create: `passki-test/src/jvmTest/resources/fixtures/ios-<yyyymmdd>-registration.json`

- [ ] **Step 1: Android.** Use an emulator with Google Play (API 34+), signed into a Google account, with a screen lock set. Or use a real device.

Run: `./gradlew :androidApp:installDebug`, then launch PassKi and capture logs with `adb logcat -s System.out | grep PASSKI_DEMO`.

Check each button:
- [ ] Capabilities → `isSupported=true`
- [ ] Register → `Register ✓ PasskeyRegistration(credentialId=…)` and a GPM bottom sheet appears
- [ ] Register again → `AlreadyRegistered` (excludeCredentials)
- [ ] Sign in → `Sign in ✓`
- [ ] Sign in (device only) after removing the passkey in GPM → `NoCredentials`, with no sheet
- [ ] Sign in (passkey or password) → sheet lists the passkey and any saved passwords
- [ ] Upgrade → success or provider-declined result; no crash
- [ ] Signal → `Signal unknown ✓`
- [ ] Temporarily break the assetlinks fingerprint (or test with a different RP ID) → `Misconfigured` with the assetlinks hint

- [ ] **Step 2: iOS.** Use a device (iOS 18+ recommended) or a simulator signed into iCloud with Face ID enrolled. Build and run `iosApp` from Xcode.

Check each button:
- [ ] Capabilities, Register, Register again (AlreadyRegistered on 17.4+), Sign in, Sign in or password, Upgrade (18+), Signal → `NotSupported`
- [ ] Remove the Associated Domains entitlement → `Misconfigured` with the AASA hint (restore it afterwards)

- [ ] **Step 3: Record fixtures.** From each platform's successful Register log, copy the `"json"` value. Wrap it as `{"rpId":"alirezaiyan.com","response":<json>}` and save it as `android-<yyyymmdd>-registration.json` / `ios-<yyyymmdd>-registration.json`.

Run: `./gradlew :passki-test:jvmTest --tests "com.alirezaiyan.passki.test.RecordedFixturesTest" -i | grep Verified`
Expected: `Verified 2 recorded registration fixture(s)`.

If Android fails on origin: that's expected behaviour for `android:apk-key-hash:` origins. webauthn4j `Origin` accepts that scheme. If it doesn't, file the finding in the README "Server setup" section.

- [ ] **Step 4: Write down any failure** as an issue/test before fixing it (debug skill: root cause first).

- [ ] **Step 5: Commit**

```bash
git add passki-test/src/jvmTest/resources/fixtures
git commit -m "test: add real-device registration fixtures"
```

---

### Task 22: README, publish config, ABI baseline

**Files:**
- Create: `README.md` (replace template)
- Modify: `passki/build.gradle.kts`, `passki-test/build.gradle.kts`

- [ ] **Step 1: Add publishing.** In both `passki/build.gradle.kts` and `passki-test/build.gradle.kts`, add `alias(libs.plugins.vanniktechPublish)` to `plugins {}` and append the block below. Use artifactId `passki` for the first and `passki-test` for the second; use the matching name/description.

```kotlin
mavenPublishing {
    publishToMavenCentral()
    if (project.hasProperty("signingInMemoryKey")) signAllPublications()
    coordinates(property("GROUP").toString(), "passki", property("VERSION_NAME").toString())
    pom {
        name.set("PassKi")
        description.set("Passkeys for Kotlin Multiplatform: one API, native authenticators, typed errors.")
        url.set("https://github.com/rezaiyan/PassKi")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("alirezaiyan")
                name.set("Ali Rezaiyan")
            }
        }
        scm {
            url.set("https://github.com/rezaiyan/PassKi")
        }
    }
}
```

Confirm the GitHub repo URL with the user before the first real publish.

- [ ] **Step 2: Write `README.md`**

````markdown
# PassKi

Passkeys for Kotlin Multiplatform. One dependency, one call, native authenticators, and errors that tell you how to fix them.

| Platform | Status |
|---|---|
| Android 9+ (Credential Manager) | ✅ register, sign in, passkey-or-password, upgrade, signals |
| iOS 16+ (AuthenticationServices) | ✅ register, sign in, passkey-or-password, upgrade (18+); signals coming |
| Web, Windows, macOS, Linux | 🛣️ roadmap; returns `PasskeyError.NotSupported` today |

## Install

```kotlin
commonMain.dependencies { implementation("com.alirezaiyan.passki:passki:0.1.0-alpha01") }
commonTest.dependencies { implementation("com.alirezaiyan.passki:passki-test:0.1.0-alpha01") }
```

## Use

```kotlin
val passkeys = Passkeys() // no Activity, no UIWindow

passkeys.register(api.registrationOptions())
    .onSuccess { api.finishRegistration(it.json) }
    .onFailure { error ->
        when (error) {
            PasskeyError.Cancelled -> Unit
            is PasskeyError.Misconfigured -> log(error.fixHint) // tells you exactly what's wrong
            else -> showError(error.message)
        }
    }

passkeys.signIn(api.authenticationOptions()).onSuccess { api.finishSignIn(it.json) }
passkeys.signInOrPassword(options)   // one sheet: passkeys + saved passwords
passkeys.upgrade(options)            // silently add a passkey after password login
passkeys.signals.unknownCredential(rpId, credentialId) // keep password managers in sync
```

Options are your server's WebAuthn JSON, used as-is (a `{"publicKey": …}` envelope is fine). Verify the returned `json` on your server with java-webauthn-server, webauthn4j, SimpleWebAuthn or py_webauthn.

## Test without devices

```kotlin
val passkeys = FakePasskeys(origin = "https://example.com") // real ES256 responses, accepted by webauthn4j
passkeys.failNextWith(PasskeyError.Cancelled)
```

## Server setup

- **Android:** serve `https://<rpId>/.well-known/assetlinks.json` with `delegate_permission/common.get_login_creds` and your signing SHA-256. Allow the origin `android:apk-key-hash:<base64url-sha256>` in your verifier.
- **iOS:** add the Associated Domains `webcredentials:<rpId>` entitlement and serve `/.well-known/apple-app-site-association`.
- See `docs/setup/well-known/` for templates.

## License

Apache-2.0 © Ali Rezaiyan
````

- [ ] **Step 3: Create the ABI baseline**

Run: `./gradlew :passki:updateLegacyAbi :passki-test:updateLegacyAbi`. If the task names differ, find them with `./gradlew :passki:tasks --all | grep -i abi`.
Expected: `passki/api/` dump files are created.

Run: `./gradlew :passki:checkLegacyAbi`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Publish locally**

Run: `./gradlew publishToMavenLocal`
Expected: BUILD SUCCESSFUL. `ls ~/.m2/repository/com/alirezaiyan/passki/` lists `passki`, `passki-android`, `passki-iosarm64`, `passki-jvm`, `passki-test`, …

- [ ] **Step 5: Full gate**

Run: `./gradlew :passki:jvmTest :passki:testAndroidHostTest :passki:iosSimulatorArm64Test :passki-test:jvmTest :passki-test:iosSimulatorArm64Test :passki:assemble :passki:checkLegacyAbi`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "docs: README, publishing config, ABI baseline for 0.1.0-alpha01"
```

The actual Maven Central release is **not** part of this plan. It needs the user's Central Portal account, the alirezaiyan.com namespace verification and signing keys.

---

## Self-review

**Spec coverage (Phase 0 + Phase 1):**

| Requirement | Where |
|---|---|
| Competitor DX benchmark | Task 0 |
| Gradle setup, publishing, binary-compatibility, docs | Tasks 1, 2, 22 (Dokka moved to Plan 2) |
| `passki-core` models, base64url, errors, capabilities | Tasks 3–8 |
| Android bridge, error mapping, auto Activity | Tasks 9–11 |
| iOS bridge, JSON ↔ native, cancellation | Tasks 12–14 |
| Unified password + passkey, upgrade, signal API, typed errors with `fixHint` | Tasks 4, 11, 14 |
| `passki-test` fake authenticator | Tasks 15–17 |
| Contract tests vs a real verifier | Tasks 16, 18, 21 |
| Sample app + manual E2E | Tasks 19–21 |
| Setup docs (assetlinks/AASA, Android origin) | Tasks 20, 22 |

**Deferred, by design:**

| Item | Plan |
|---|---|
| Autofill sign-in | Plan 3 |
| Gradle plugin, `doctor()` | Plan 2 |
| Compose helpers | Plan 3 |
| iOS signals | Plan 3 (Swift shim) |
| Dokka | Plan 2 |

**Type consistency:**
- `Passkeys`, `PasskeyResult`, `PasskeyError`, `PasskeyRegistration`, `PasskeyAssertion`, `SignInCredential`, `SignInMode`, `PasskeySignals`, `PasskeyCapabilities` are defined in Tasks 4–5. Later tasks use them unchanged.
- `WebAuthnOptions.parseCreation/parseRequest → NormalizedOptions(json, model)`: Task 6. Used in 11, 14, 16.
- `WebAuthnResponses.registration/authentication`, `parseRegistration/parseAssertion`: Task 7. Used in 11, 14, 16, 17.
- `passkeyCatching(mapError, block)`: Task 8. Used in 11, 14.
- `toPasskeyError()` exists separately in the `android` and `apple` packages; there's no clash because the source sets differ.

**Known API-name risks, each with a fallback noted inline:**

| Risk | Task |
|---|---|
| `abiValidation` DSL in Kotlin 2.4.20 | 2 |
| `ExperimentalEncodingApi` opt-in | 3 |
| `webMain` source set | 5 |
| `CreatePublicKeyCredentialRequest` named args | 11 |
| K/N NS_ENUM names (`requestStyle`, `attachment`), ObjC overload annotation | 14 |
| webauthn4j 0.30 `ServerProperty` constructor | 16 |
