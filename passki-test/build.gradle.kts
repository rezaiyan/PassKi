import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktechPublish)
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        // Calling abiValidation { } enables ABI validation in Kotlin 2.4 (the `enabled` property was removed).
    }

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
            implementation(libs.kotlinx.coroutines.core)
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

mavenPublishing {
    coordinates(artifactId = "passki-test")
    publishToMavenCentral()
    if (project.hasProperty("signingInMemoryKey")) signAllPublications()
    pom {
        name.set("PassKi Test")
        description.set("Test fakes for PassKi: FakePasskeys and a WebAuthn-valid fake authenticator.")
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
