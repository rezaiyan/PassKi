# PassKi Plan 2: Setup Tooling (Gradle plugin + Doctor + API docs)

> **For agentic workers:** Use claude-kit:implement to execute this plan task-by-task.

**Goal:** Remove the #1 real-world passkey pain, configuration. This plan delivers:
- a Gradle plugin that generates and verifies `assetlinks.json` / `apple-app-site-association` from the build's own signing configs and app IDs
- `PasskeyDoctor.run(rpId)`, a runtime check that explains exactly what is misconfigured on the device
- published API docs (Dokka)

**Architecture:** two independent tracks.
- **Track A, Gradle plugin:**
  - Lives in an *included build* at `passki-gradle-plugin/`. The sample `androidApp` can apply it (dogfooding) via `pluginManagement { includeBuild(...) }`.
  - Zero runtime dependencies besides the Gradle API, with AGP `gradle-api` as `compileOnly`. JSON uses Groovy's `JsonSlurper`/`JsonOutput`, which ship with Gradle.
  - Compiled with Kotlin `apiVersion`/`languageVersion` = **2.3**, matching Gradle 9.5.1's embedded Kotlin 2.3.20, so it never calls stdlib APIs that Gradle doesn't ship.
  - Must be configuration-cache compatible.
- **Track B, Doctor:**
  - Lives in `:passki`. A top-level `PasskeyDoctor` expect object; the `Passkeys` interface stays untouched (binary-compat policy).
  - Pure checkers in `commonMain`, unit-tested on the JVM. Platform actuals only gather facts (HTTP fetch, package signing cert / bundle id).
  - No new library dependency: Android uses `HttpURLConnection`, iOS uses `NSURLSession`.

Known duplication: the plugin and the doctor each contain a small well-known-file matcher. This is deliberate. The plugin can't depend on `passki-jvm` (Kotlin 2.4 stdlib/serialization on Gradle's 2.3 classpath).

**Tech stack:**
- Gradle 9.5.1 (embedded Kotlin 2.3.20), AGP 9.1.1 `gradle-api`, Gradle TestKit, JUnit 4 / kotlin-test
- `java.net.http.HttpClient` (plugin), `com.sun.net.httpserver.HttpServer` (tests)
- Dokka 2.x

Spec: `.claude/plans/2026-10-20-kmp-passkey-library.md` (Phase 1 differentiators: Gradle plugin + doctor).

---

## Decisions (defaults)

| Decision | Value |
|---|---|
| Plugin id | `com.alirezaiyan.passki` |
| Plugin artifact | `com.alirezaiyan.passki:passki-gradle-plugin` |
| Tasks | `passkiFingerprints`, `passkiWellKnown`, `passkiVerify` (network; never wired into `check` automatically) |
| Output dir | `build/passki/well-known/{assetlinks.json, apple-app-site-association}` |
| Doctor entry point | `PasskeyDoctor.run(rpId: String): DoctorReport` |
| Doctor status levels | `Pass`, `Warn`, `Fail`, `Skipped` |
| Dokka | `org.jetbrains.dokka` on `:passki` and `:passki-test`, output `build/dokka/html` |

## Plugin DSL (target)

```kotlin
plugins { id("com.alirezaiyan.passki") }

passki {
    rpId = "example.com"
    android {
        // package + signing SHA-256s are read from the Android build automatically.
        extraFingerprints.add("AB:CD:...")   // e.g. Play App Signing key
    }
    apple {
        teamId = "ABCDE12345"
        bundleId = "com.example.app"
        entitlementsFile = file("../iosApp/iosApp/iosApp.entitlements") // optional, verified by passkiVerify
    }
}
```

## File Structure

```
settings.gradle.kts                                (modify) pluginManagement { includeBuild("passki-gradle-plugin") }
passki-gradle-plugin/settings.gradle.kts           (create) reuses ../gradle/libs.versions.toml
passki-gradle-plugin/build.gradle.kts              (create) java-gradle-plugin, kotlin jvm (api 2.3), publish
passki-gradle-plugin/src/main/kotlin/com/alirezaiyan/passki/gradle/
  PasskiPlugin.kt               apply(): extension + tasks; reacts to com.android.application
  PasskiExtension.kt            rpId, android{}, apple{}
  Fingerprints.kt               keystore → SHA-256 colon hex
  WellKnownFiles.kt             build assetlinks / AASA JSON strings
  WellKnownVerifier.kt          pure: compare remote JSON + entitlements vs expected → List<Problem>
  tasks/PasskiFingerprintsTask.kt
  tasks/PasskiWellKnownTask.kt
  tasks/PasskiVerifyTask.kt
passki-gradle-plugin/src/test/kotlin/...           unit tests + TestKit functional tests

passki/src/commonMain/kotlin/com/alirezaiyan/passki/
  PasskeyDoctor.kt              expect object + DoctorReport/DoctorCheck/DoctorStatus
  internal/doctor/HttpFetch.kt  HttpResult + fetch abstraction
  internal/doctor/AssetLinksCheck.kt
  internal/doctor/AasaCheck.kt
passki/src/androidMain/.../PasskeyDoctor.android.kt + android/AndroidDoctorFacts.kt
passki/src/iosMain/.../PasskeyDoctor.ios.kt + apple/IosDoctorFacts.kt
passki/src/jvmMain/.../PasskeyDoctor.jvm.kt, webMain/.../PasskeyDoctor.web.kt
shared/.../App.kt                                  (modify) "Doctor" button
androidApp/build.gradle.kts                        (modify) apply plugin (dogfood)
README.md                                          (modify) Setup tooling section
```

**Gates:**
- Plugin: `./gradlew -p passki-gradle-plugin test`
- Library: `./gradlew :passki:jvmTest :passki:testAndroidHostTest :passki:iosSimulatorArm64Test :passki:checkKotlinAbi`

Run Gradle through `ctx_execute`; the Bash hook blocks `./gradlew`.

---

## Track A: Gradle plugin

### Task A1: Included-build skeleton

**Files:**
- Create: `passki-gradle-plugin/settings.gradle.kts`
- Create: `passki-gradle-plugin/build.gradle.kts`
- Create: `PasskiPlugin.kt`
- Create: `PasskiExtension.kt`
- Test: `src/test/kotlin/com/alirezaiyan/passki/gradle/PluginApplyTest.kt`
- Modify: root `settings.gradle.kts`

- [ ] **Step 1: `passki-gradle-plugin/settings.gradle.kts`**

```kotlin
rootProject.name = "passki-gradle-plugin"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
```

- [ ] **Step 2: `passki-gradle-plugin/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlinJvm)
}

group = "com.alirezaiyan.passki"
version = "0.1.0-alpha01"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Must not exceed Gradle's embedded Kotlin (Gradle 9.5.1 → 2.3.20).
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle-api:${libs.versions.agp.get()}")
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("passki") {
            id = "com.alirezaiyan.passki"
            implementationClass = "com.alirezaiyan.passki.gradle.PasskiPlugin"
            displayName = "PassKi"
            description = "Generates and verifies assetlinks.json / apple-app-site-association for passkeys."
        }
    }
}

tasks.test {
    // Functional tests build a real Android project.
    environment("ANDROID_HOME", providers.environmentVariable("ANDROID_HOME").orElse(System.getProperty("user.home") + "/Library/Android/sdk").get())
}
```

The Android SDK path for TestKit: read `sdk.dir` from the root `local.properties` if `ANDROID_HOME` is unset, and write a `local.properties` into each TestKit project.

- [ ] **Step 3: `PasskiExtension.kt`**

```kotlin
package com.alirezaiyan.passki.gradle

import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import javax.inject.Inject

public abstract class PasskiExtension @Inject constructor(objects: ObjectFactory) {
    /** Relying Party ID, e.g. "example.com". Required by passkiVerify. */
    public abstract val rpId: Property<String>

    @get:Nested
    public val android: AndroidConfig = objects.newInstance(AndroidConfig::class.java)

    @get:Nested
    public val apple: AppleConfig = objects.newInstance(AppleConfig::class.java)

    public fun android(action: Action<AndroidConfig>): Unit = action.execute(android)

    public fun apple(action: Action<AppleConfig>): Unit = action.execute(apple)

    public abstract class AndroidConfig {
        /** Overrides the applicationId read from the Android build. */
        public abstract val packageName: Property<String>

        /** Fingerprints not derivable from local keystores (e.g. Play App Signing). Colon-hex SHA-256. */
        public abstract val extraFingerprints: ListProperty<String>
    }

    public abstract class AppleConfig {
        public abstract val teamId: Property<String>
        public abstract val bundleId: Property<String>

        /** Optional .entitlements file; passkiVerify checks it lists webcredentials:<rpId>. */
        public abstract val entitlementsFile: RegularFileProperty
    }
}
```

- [ ] **Step 4: `PasskiPlugin.kt`** (skeleton; tasks wired in A4 and A5)

```kotlin
package com.alirezaiyan.passki.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

public class PasskiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("passki", PasskiExtension::class.java)
    }
}
```

- [ ] **Step 5: Write the failing test `PluginApplyTest.kt`** (ProjectBuilder)

```kotlin
package com.alirezaiyan.passki.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

class PluginApplyTest {
    @Test
    fun registersExtension() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.alirezaiyan.passki")
        assertNotNull(project.extensions.findByType(PasskiExtension::class.java))
    }
}
```

Write the test before Step 4 to see red, then add Step 4. Run: `./gradlew -p passki-gradle-plugin test`. Expected: PASS.

- [ ] **Step 6: Root `settings.gradle.kts`.** Add `includeBuild("passki-gradle-plugin")` inside the existing `pluginManagement {}` block, then run `./gradlew help -q` and expect exit 0.

- [ ] **Step 7: Commit:** `build(plugin): add passki-gradle-plugin included build`

### Task A2: Fingerprints

**Files:**
- `Fingerprints.kt`
- Test: `FingerprintsTest.kt`

- [ ] **Step 1: Write the test first.** It generates a real keystore with the JDK `keytool` into a temp dir, then:
  - asserts the fingerprint matches the SHA-256 that `keytool -list -v` reports for the same keystore (parse the `SHA256:` line)
  - asserts the format is uppercase colon-hex, 32 bytes long
  - asserts a wrong password throws `PasskiConfigException` with a message naming the keystore path

```kotlin
// keytool -genkeypair -keystore <tmp>/k.jks -storetype PKCS12 -storepass secret123 -keypass secret123
//   -alias key0 -keyalg RSA -keysize 2048 -validity 1 -dname "CN=PassKi Test"
```

- [ ] **Step 2: Implement**

```kotlin
package com.alirezaiyan.passki.gradle

import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

public class PasskiConfigException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal object Fingerprints {
    fun sha256(storeFile: File, storePassword: String, keyAlias: String, storeType: String?): String {
        val keyStore = try {
            KeyStore.getInstance(storeType ?: KeyStore.getDefaultType()).apply {
                storeFile.inputStream().use { load(it, storePassword.toCharArray()) }
            }
        } catch (e: Exception) {
            throw PasskiConfigException("Cannot open keystore ${storeFile.absolutePath}: ${e.message}", e)
        }
        val certificate = keyStore.getCertificate(keyAlias)
            ?: throw PasskiConfigException("Alias '$keyAlias' not found in ${storeFile.absolutePath}")
        return format(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))
    }

    fun format(digest: ByteArray): String = digest.joinToString(":") { "%02X".format(it) }

    /** Accepts "ab:cd…" or "abcd…"; returns canonical uppercase colon-hex. */
    fun normalize(value: String): String {
        val hex = value.replace(":", "").uppercase()
        require(hex.length == 64 && hex.all { it in "0123456789ABCDEF" }) { "Not a SHA-256 fingerprint: $value" }
        return hex.chunked(2).joinToString(":")
    }
}
```

Also test `normalize` on lowercase input, input without colons, and invalid input.

- [ ] **Step 3: Commit:** `feat(plugin): compute signing certificate fingerprints`

### Task A3: Well-known file builders

**Files:**
- `WellKnownFiles.kt`
- Test: `WellKnownFilesTest.kt`

- [ ] **Step 1: Write golden tests first.** Expected outputs, pretty-printed with 2-space indent and a trailing newline:
  - `assetLinks("com.example", listOf("AA:…"))` → exactly the JSON from `docs/setup/well-known/assetlinks.json` (relations `handle_all_urls` + `get_login_creds`)
  - `aasa(listOf("ABCDE12345.com.example"))` → `{"webcredentials":{"apps":["ABCDE12345.com.example"]}}`, pretty-printed
  - fingerprints are de-duplicated and sorted

- [ ] **Step 2: Implement** with `groovy.json.JsonOutput.prettyPrint(JsonOutput.toJson(...))`. Normalise indent to 2 spaces, since Groovy uses 4: re-indent line-by-line by halving the leading spaces. Order keys exactly as in the golden output by building with `LinkedHashMap`.

- [ ] **Step 3: Commit:** `feat(plugin): build assetlinks.json and AASA documents`

### Task A4: Wire AGP + `passkiFingerprints` / `passkiWellKnown` tasks

**Files:**
- `PasskiPlugin.kt`
- `tasks/PasskiFingerprintsTask.kt`
- `tasks/PasskiWellKnownTask.kt`
- Test: `functional/WellKnownFunctionalTest.kt`

- [ ] **Step 1: Write the functional test first** with TestKit:
  - The temp project has `settings.gradle.kts` (google + mavenCentral repos), `local.properties` (`sdk.dir`) and `build.gradle.kts`:
    - `plugins { id("com.android.application") version "<agp>"; id("com.alirezaiyan.passki") }`
    - `android { namespace = "com.example.app"; compileSdk = 36; defaultConfig { applicationId = "com.example.app"; minSdk = 24 } }`
    - `signingConfigs { create("release") { storeFile = file("k.jks"); storePassword = "secret123"; keyAlias = "key0"; keyPassword = "secret123" } }`
    - `buildTypes { release { signingConfig = signingConfigs.getByName("release") } }`
    - `passki { rpId = "example.com"; android { extraFingerprints.add("<64 hex>") }; apple { teamId = "ABCDE12345"; bundleId = "com.example.app" } }`
  - Create the keystore with keytool, as in A2.
  - Use `withPluginClasspath()`. AGP comes from repositories, so the test needs network on its first run.
  - Assertions:
    - `passkiWellKnown --configuration-cache` succeeds
    - `build/passki/well-known/assetlinks.json` contains `com.example.app`, the release fingerprint, the debug fingerprint (from `~/.android/debug.keystore` if present, otherwise just the release one) and the extra fingerprint
    - the AASA contains `ABCDE12345.com.example.app`
    - running again reports `UP-TO-DATE`
  - Second scenario: `apple {}` not configured → no AASA is generated and the log explains why.

- [ ] **Step 2: Implement AGP wiring.** Use `pluginManager.withPlugin("com.android.application")`, then the `ApplicationAndroidComponentsExtension.finalizeDsl { dsl -> … }` callback.

  From the DSL, capture:
  - `defaultConfig.applicationId`
  - for each build type, the effective `signingConfig`, plus `signingConfigs` entries: `storeFile`, `storePassword`, `keyAlias`, `storeType`
  - the debug build type's signing config. AGP provides a default debug signing config (`~/.android/debug.keystore`, `android`/`androiddebugkey`).

  Store these as plain serializable values in task inputs:
  - `keystores: ListProperty<KeystoreSpec>`, where `KeystoreSpec` is a data class with path, password, alias and type, and implements `java.io.Serializable`; mark it `@Input`
  - inputs the store files as `@InputFiles @PathSensitive(NONE)`

  Verify the exact AGP 9.1.1 DSL types against the `gradle-api` jar in `~/.gradle/caches` with `javap`: `com.android.build.api.dsl.ApplicationExtension`, `SigningConfig.storeFile`, and so on.

- [ ] **Step 3: `PasskiWellKnownTask`** (`@CacheableTask`)
  - Inputs:
    - `packageName`
    - `keystores`
    - `extraFingerprints`
    - optional `appleAppIds: ListProperty<String>`, built from `teamId` + `.` + `bundleId`
  - Output: `@OutputDirectory outputDir`.
  - Missing keystore files are skipped with a warning, not a failure. A typical example is a CI machine without the release keystore.
  - Fails with `PasskiConfigException` only if no fingerprint at all can be produced.

- [ ] **Step 4: `PasskiFingerprintsTask`.** Prints a table with columns `variant/buildType | keystore | SHA-256`, plus the extra fingerprints and the Android WebAuthn origin `android:apk-key-hash:<base64url(sha256 bytes)>` for each fingerprint, for server allow-lists. Not cacheable; it only logs.

- [ ] **Step 5: Run the functional + unit tests.** Then commit: `feat(plugin): generate well-known files from Android signing configs`

### Task A5: `passkiVerify`

**Files:**
- `WellKnownVerifier.kt`
- `tasks/PasskiVerifyTask.kt`
- Tests: `WellKnownVerifierTest.kt`, `functional/VerifyFunctionalTest.kt`

- [ ] **Step 1: Pure verifier tests first.** The signature is `verify(expected: Expected, assetLinks: Fetched?, aasa: Fetched?, entitlements: String?): List<Problem>`, where `Fetched(status, contentType, body, redirected)`.

  Cases, each producing a `Problem` with an actionable fix text:

  | Case | Expected result |
  |---|---|
  | All good | empty list |
  | 404 | `Problem("assetlinks.json not found at https://<rpId>/.well-known/assetlinks.json", fix = "Upload build/passki/well-known/assetlinks.json")` |
  | Redirect | "must be served without redirects" |
  | Non-JSON body | problem |
  | Missing relation `get_login_creds` | problem |
  | Package missing | problem |
  | Fingerprint missing | lists exactly which fingerprints are absent |
  | AASA missing app ID | problem |
  | AASA `Content-Type` not `application/json` | **warning** only |
  | Entitlements without `webcredentials:<rpId>` | problem; also accept `webcredentials:<rpId>?mode=developer`, parsed via `javax.xml` plist reading of the `com.apple.developer.associated-domains` array |

  `Problem` has a `severity` field (`ERROR`/`WARNING`).

- [ ] **Step 2: Implement the verifier.**

- [ ] **Step 3: Task.**
  - Use `java.net.http.HttpClient` with `followRedirects(NEVER)` and a 10 s timeout. Fetch `https://<rpId>/.well-known/assetlinks.json`, and `/.well-known/apple-app-site-association` if Apple is configured.
  - The base URL can be overridden by an internal `@Input @Optional baseUrl` set from the Gradle property `passki.verify.baseUrl`, for tests.
  - Inputs are the same as `passkiWellKnown`, plus `rpId` and the entitlements file contents.
  - Fail the build on any ERROR. Print warnings. Never cacheable: `outputs.upToDateWhen { false }`.

- [ ] **Step 4: Functional test.** Start `com.sun.net.httpserver.HttpServer` on a random port and serve good files, then bad ones. Run `passkiVerify -Ppasski.verify.baseUrl=http://127.0.0.1:<port>`:
  - good files → success
  - missing fingerprint → `FAILED`, and the output contains the missing fingerprint

- [ ] **Step 5: Commit:** `feat(plugin): verify hosted well-known files and entitlements`

### Task A6: Dogfood in sample + publish config

**Files:** `androidApp/build.gradle.kts`, `passki-gradle-plugin/build.gradle.kts`, `docs/setup/well-known/README.md`

- [ ] **Step 1: Apply the plugin in `androidApp`:**
  - `id("com.alirezaiyan.passki")`
  - `passki { rpId = "alirezaiyan.com"; apple { bundleId = "com.alirezaiyan.passki.PassKi" } }`
  - `teamId` is left unset until the user provides it.
  - Run `./gradlew :androidApp:passkiWellKnown :androidApp:passkiFingerprints --configuration-cache`.
  - Expected: the output matches `docs/setup/well-known/assetlinks.json` content-wise.

- [ ] **Step 2: Publishing config** via the vanniktech plugin's Gradle-plugin support (`GradlePlugin` platform). The POM matches the library's. Verify with `./gradlew -p passki-gradle-plugin publishToMavenLocal`.

- [ ] **Step 3: `docs/setup/well-known/README.md`:** "These files are generated by `./gradlew :androidApp:passkiWellKnown`".

- [ ] **Step 4: Commit:** `build(sample): dogfood passki Gradle plugin`

---

## Track B: Doctor

### Task B1: Doctor API + pure checkers

**Files:**
- `PasskeyDoctor.kt` (commonMain)
- `internal/doctor/{HttpFetch,AssetLinksCheck,AasaCheck}.kt`
- Tests in commonTest
- `jvmMain` / `webMain` actuals (Skipped)

- [ ] **Step 1: Public API** (all KDoc'd; constructors `@InternalPasskiApi`)

```kotlin
package com.alirezaiyan.passki

/** Diagnoses passkey setup for [rpId] on this device. Debug builds only: it performs network requests. */
public expect object PasskeyDoctor {
    public suspend fun run(rpId: String): DoctorReport
}

public class DoctorReport @InternalPasskiApi constructor(public val rpId: String, public val checks: List<DoctorCheck>) {
    public val isHealthy: Boolean get() = checks.none { it.status == DoctorStatus.Fail }
    /** Multi-line, human-readable; safe to log. */
    override fun toString(): String
}

public class DoctorCheck @InternalPasskiApi constructor(
    public val name: String,
    public val status: DoctorStatus,
    public val message: String,
    public val fixHint: String?,
)

/** New statuses may be added; keep an else branch. */
public enum class DoctorStatus { Pass, Warn, Fail, Skipped }
```

  `toString()` format:

  ```
  PassKi doctor for alirezaiyan.com: 1 failure, 1 warning
  ✓ Platform support — Android 14 (API 34)
  ✗ assetlinks.json — fingerprint C9:64:… missing
     fix: add it to https://alirezaiyan.com/.well-known/assetlinks.json
  ```

- [ ] **Step 2: Internal facts and HTTP abstraction**

```kotlin
internal class HttpResult(val url: String, val status: Int, val contentType: String?, val body: String?, val redirectedTo: String?, val error: String?)
internal fun interface HttpGetter { suspend fun get(url: String): HttpResult }
```

- [ ] **Step 3: Pure checkers, TDD.**
  - `AssetLinksCheck.evaluate(result, packageName, certFingerprints: List<String>): DoctorCheck`:
    - Pass, or Fail when the file is missing, redirected, unparsable, lacks the relation, lacks the package, or lacks a fingerprint. For a missing fingerprint, the fix hint carries the exact JSON statement to add.
    - Warn when `Content-Type` isn't JSON.
  - `AasaCheck.evaluate(direct, cdn, bundleId): DoctorCheck`:
    - Fail when the direct file is missing or `webcredentials.apps` has no `*.<bundleId>` entry. The report names the team ID it found.
    - Warn when the CDN copy differs from the direct one or is missing: "Apple's CDN can lag up to 24h; use webcredentials:<rpId>?mode=developer while testing".
  - `androidOrigin(fingerprint): String` → `android:apk-key-hash:<base64url>`. Test it against a known vector, e.g. the debug key `C9:64:…:49:03` → base64url of those 32 bytes.

  Use `PasskiJson` (kotlinx) for parsing. Tests cover each branch.

- [ ] **Step 4: Actuals on jvm and web** return one `Skipped` check ("Doctor supports Android and iOS"). Add a `jvmTest` for this.

- [ ] **Step 5: Run `:passki:jvmTest`, then `:passki:updateKotlinAbi`. Commit:** `feat(doctor): add PasskeyDoctor API and well-known checkers`

### Task B2: Android doctor

**Files:**
- `PasskeyDoctor.android.kt`
- `android/AndroidDoctorFacts.kt`
- Host tests

- [x] **Checks, in order:**
  1. **Platform:** API ≥ 28, else Fail.
  2. **PassKi initialised:** ActivityTracker has an application, else Fail with the INIT hint.
  3. **INTERNET permission:** `checkSelfPermission`. If it's missing, Warn and skip the network checks.
  4. **assetlinks:** fetch with `HttpURLConnection` (`instanceFollowRedirects = false`, 10 s timeouts) on `Dispatchers.IO`.
     - Signing certs come from `PackageManager.getPackageInfo(pkg, GET_SIGNING_CERTIFICATES)`.
     - Use `signingInfo.apkContentsSigners`, or `signingCertificateHistory` when `hasMultipleSigners()` is false.
     - SHA-256 → colon hex, then `AssetLinksCheck`.
  5. **Origin:** a Pass/info check listing `android:apk-key-hash:…` for each signer ("allow this origin on your server").
- [x] **Host tests:** cert bytes → fingerprint formatting, using a pure function; and a fake `HttpGetter` driving the report composition. Put the composition in a pure internal function: `composeAndroidReport(facts, getter)`.
- [x] **Commit:** `feat(doctor): Android setup diagnostics`

### Task B3: iOS doctor

**Files:**
- `PasskeyDoctor.ios.kt`
- `apple/IosDoctorFacts.kt`
- iosTest

- [x] **Checks:**
  1. iOS ≥ 16.
  2. A foreground key window is available. Warn if not; this is only needed at call time.
  3. **Entitlement:** report `Skipped`. iOS can't read its own entitlements at runtime. The fix hint points to the Gradle plugin's `passkiVerify` entitlements check.
  4. **AASA:** fetch direct `https://<rpId>/.well-known/apple-app-site-association` and the CDN `https://app-site-association.cdn-apple.com/a/v1/<rpId>`.
     - Use `NSURLSession.sharedSession.dataTaskWithRequest` bridged with `suspendCancellableCoroutine`, and cancel the task on cancellation.
     - Disable redirects with a delegate if feasible. Otherwise record the final URL and compare it with the requested one.
     - `bundleId` comes from `NSBundle.mainBundle.bundleIdentifier`. Run `AasaCheck`.
- [x] **iosTest:** compose the report with a fake getter. Real network is not used in tests.
- [x] **Commit:** `feat(doctor): iOS setup diagnostics`

### Task B4: Sample "Doctor" button

- [ ] In `App.kt`, add a `Button("Run doctor")` that calls `PasskeyDoctor.run(DemoRelyingParty.RP_ID)` and puts `report.toString()` into the log.
- [ ] Build all sample targets: `:androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlin`.
- [ ] Commit: `feat(sample): run PasskeyDoctor from demo`

---

## Track C: Docs (after A and B)

### Task C1: Dokka + README

- [ ] Add the Dokka plugin (latest 2.x that resolves) to `:passki` and `:passki-test`, with module docs `passki/Module.md`. Gate: `./gradlew :passki:dokkaGenerate` succeeds and `build/dokka/html/index.html` exists.
- [ ] README: a new **Setup tooling** section covering the plugin DSL, the three tasks, `PasskeyDoctor.run(rpId)` with example output, and "debug builds only".
- [ ] Full gate:
  - `./gradlew -p passki-gradle-plugin test`
  - `./gradlew :passki:jvmTest :passki:testAndroidHostTest :passki:iosSimulatorArm64Test :passki-test:jvmTest :passki:checkKotlinAbi :passki-test:checkKotlinAbi :androidApp:assembleDebug`
- [ ] Commit: `docs: Dokka, setup tooling README`

---

## Risks

| Risk | Mitigation |
|---|---|
| **AGP 9 DSL access to signing configs.** `finalizeDsl` gives the DSL, but `storePassword` may come from env providers. | Read the values at configuration time inside `finalizeDsl`, then pass them as task inputs. Passwords become task inputs and therefore end up in the configuration cache; document that, or mark them `@Internal` and only track the keystore files. **Decision:** passwords go in `@Internal` fields, and the keystore file plus alias are the tracked inputs. |
| **TestKit + real AGP needs network and the Android SDK.** | If the SDK is unavailable, functional tests skip with `Assume`. |
| **Kotlin stdlib skew in the plugin.** | apiVersion 2.3. Don't use stdlib APIs newer than 2.3. |
| **iOS doctor redirect detection.** | `NSURLSession` follows redirects by default; fall back to final-URL comparison. |
| **The doctor's network calls in production.** | KDoc + README say debug-only. It never runs automatically. |
