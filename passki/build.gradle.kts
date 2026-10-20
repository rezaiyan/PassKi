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
            implementation(libs.kotlinx.coroutines.core)
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

mavenPublishing {
    coordinates(artifactId = "passki")
    publishToMavenCentral()
    if (project.hasProperty("signingInMemoryKey")) signAllPublications()
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
