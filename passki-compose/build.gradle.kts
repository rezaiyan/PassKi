import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktechPublish)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()
    // Apps on older Kotlin than PassKi's build must be able to read the published metadata and stdlib dependency.
    coreLibrariesVersion = libs.versions.kotlin.consumer.get()
    compilerOptions {
        val consumer = KotlinVersion.fromVersion(libs.versions.kotlin.consumer.get().substringBeforeLast('.'))
        languageVersion.set(consumer)
        apiVersion.set(consumer)
    }

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        // In Kotlin 2.2, ABI validation is off unless enabled; without this, checkLegacyAbi is silently skipped.
        enabled.set(true)
        klib { enabled.set(true) }
    }

    android {
        namespace = "com.alirezaiyan.passki.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Apps may compile against an older SDK than PassKi does; without this the AAR would require compileSdk.
        aarMetadata { minCompileSdk = libs.versions.android.minCompileSdk.get().toInt() }
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    // Browser tests load the Skiko runtime that Compose UI pulls in, which needs a webpack-bundled executable
    // (CMP-4906). It only affects test bundling: the published klib is the same.
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // No Wasm test run: in the browser, kotlinx-io (from passki-test's cryptography-kotlin) reads `import.meta` in
        // its Wasm glue, which the Karma runner loads as a classic script ("Cannot use 'import.meta' outside a
        // module"); on Node, Compose UI's Skiko runtime does not load. The same commonTest runs on JVM, JS, Android
        // and iOS.
        browser { testTask { enabled = false } }
        binaries.executable()
    }

    sourceSets {
        // Kotlin/JS and Wasm need the stdlib of the compiler that built them; coreLibrariesVersion is for JVM/Android.
        webMain.dependencies { implementation(kotlin("stdlib", libs.versions.kotlin.asProvider().get())) }
        commonMain.dependencies {
            api(project(":passki"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(project(":passki-test"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}

mavenPublishing {
    coordinates(artifactId = "passki-compose")
    publishToMavenCentral()
    if (project.hasProperty("signingInMemoryKey")) signAllPublications()
    pom {
        name.set("PassKi Compose")
        description.set("Compose Multiplatform helpers for PassKi: remembered passkeys, sign-in state, and a sign-in button.")
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
                url.set("https://alirezaiyan.com")
            }
        }
        scm {
            url.set("https://github.com/rezaiyan/PassKi")
            connection.set("scm:git:git://github.com/rezaiyan/PassKi.git")
            developerConnection.set("scm:git:ssh://git@github.com/rezaiyan/PassKi.git")
        }
    }
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
        // Every public declaration must have KDoc: dokkaGenerate warns "Undocumented: ..." otherwise.
        reportUndocumented.set(true)
        perPackageOption {
            // Implementation details; not part of the public API.
            matchingRegex.set(".*\\.internal.*")
            suppress.set(true)
        }
    }
}
