import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktechPublish)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()

    compilerOptions {
        // PasskeyDoctor is an expect object; expect/actual classes are Beta (KT-61573).
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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

    listOf(
        iosArm64() to ("iphoneos" to "arm64-apple-ios16.0"),
        iosSimulatorArm64() to ("iphonesimulator" to "arm64-apple-ios16.0-simulator"),
    ).forEach { (target, sdk) ->
        val outDir = layout.buildDirectory.dir("passkiSwift/${target.name}")
        val buildSwift = tasks.register<Exec>("buildPassKiSwift${target.name.replaceFirstChar { it.uppercase() }}") {
            val script = rootProject.file("passki-ios-bridge/build.sh")
            inputs.dir(rootProject.file("passki-ios-bridge"))
            outputs.dir(outDir)
            commandLine(script.absolutePath, sdk.first, sdk.second, outDir.get().asFile.absolutePath)
        }
        target.compilations.getByName("main").cinterops.create("passkiSwift") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/passkiSwift.def"))
            includeDirs(rootProject.file("passki-ios-bridge"))
            extraOpts("-libraryPath", outDir.get().asFile.absolutePath)
            tasks.named(interopProcessingTaskName) { dependsOn(buildSwift) }
        }
    }

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
        perPackageOption {
            // Implementation details; not part of the public API.
            matchingRegex.set(".*\\.internal.*")
            suppress.set(true)
        }
    }
}
