package com.alirezaiyan.passki.gradle

import com.alirezaiyan.passki.gradle.tasks.PasskiAppleTask
import com.alirezaiyan.passki.gradle.tasks.PasskiCheckTask
import com.alirezaiyan.passki.gradle.tasks.PasskiFingerprintsTask
import com.alirezaiyan.passki.gradle.tasks.PasskiOriginTask
import com.alirezaiyan.passki.gradle.tasks.PasskiSetupTask
import com.alirezaiyan.passki.gradle.tasks.PasskiSigningTask
import com.alirezaiyan.passki.gradle.tasks.PasskiVerifyTask
import com.alirezaiyan.passki.gradle.tasks.PasskiWellKnownTask
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Gradle plugin `com.alirezaiyan.passki`.
 *
 * Registers the [PasskiExtension] (`passki { }`), the two front-door tasks, `passkiSetup` and `passkiCheck`, and the
 * advanced `passkiOrigin`.
 * When `com.android.application` is applied they include the Android app (fed from the finalized Android DSL), and
 * `passkiFingerprints` plus the advanced `passkiWellKnown` / `passkiVerify` are registered too. Without it the
 * tasks run in iOS-only mode and say so.
 */
public class PasskiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("passki", PasskiExtension::class.java)
        extension.apple.developerMode.convention(true)
        val wiring = Wiring(project, extension)
        wiring.registerFrontDoor()
        project.pluginManager.withPlugin("com.android.application") { AndroidWiring(project, wiring).wire() }
        ANDROID_LIBRARY_PLUGINS.forEach { id -> project.pluginManager.withPlugin(id) { wiring.androidLibrary.set(true) } }
    }

    /** Task registration and the values Android wiring fills in; holds no AGP types, so iOS-only builds never load AGP. */
    private class Wiring(val project: Project, val extension: PasskiExtension) {
        // Plain values captured in finalizeDsl; tasks only see these providers (configuration-cache safe).
        val applicationId: Property<String> = project.objects.property(String::class.java)
        val variants: ListProperty<VariantSpec> = project.objects.listProperty(VariantSpec::class.java).convention(emptyList())
        val androidApp: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)
        val androidLibrary: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

        fun registerFrontDoor() {
            project.tasks.register("passkiSetup", PasskiSetupTask::class.java) { task ->
                task.group = GROUP
                task.description = "Writes and wires everything passkeys need (well-known files, iOS entitlements, server config). Add --preview to see the changes first."
                configureApple(task)
                task.developerMode.set(extension.apple.developerMode)
                task.outputDir.set(project.layout.buildDirectory.dir("passki"))
                task.rootDirectory.set(project.rootProject.layout.projectDirectory)
            }
            project.tasks.register("passkiCheck", PasskiCheckTask::class.java) { task ->
                task.group = GROUP
                task.description = "Checks the whole setup end to end (AND/IOS/SRV catalog checks) and writes build/reports/passki (network)."
                configureApple(task)
                val providers = project.providers
                task.serverConfigUrl.set(extension.server.configUrl)
                task.baseUrl.set(providers.gradleProperty("passki.check.baseUrl").orElse(providers.gradleProperty("passki.verify.baseUrl")))
                task.googleDalUrl.set(providers.gradleProperty("passki.check.googleDalUrl").orElse("https://digitalassetlinks.googleapis.com"))
                task.appleCdnUrl.set(providers.gradleProperty("passki.check.appleCdnUrl").orElse("https://app-site-association.cdn-apple.com"))
                task.warningsAsErrors.set(providers.gradleProperty("passki.check.warningsAsErrors").map { it.toBoolean() }.orElse(false))
                task.reportDir.set(project.layout.buildDirectory.dir("reports/passki"))
            }
            project.tasks.register("passkiOrigin", PasskiOriginTask::class.java) { task ->
                task.group = ADVANCED_GROUP
                task.description = "Prints the apk-key-hash origin and assetlinks.json statement for a raw SHA-256 (--sha256, --package), e.g. the Play App Signing key."
                task.packageName.convention(extension.android.packageName.orElse(applicationId))
            }
        }

        fun configureApple(task: PasskiAppleTask) {
            configureSigning(task)
            val apple = extension.apple
            task.rpId.set(extension.rpId)
            task.demoServerUrl.set(extension.demoServerUrl.map { url -> project.providers.gradleProperty("passki.demo.url").getOrElse(url) })
            task.androidApp.set(androidApp)
            task.androidLibrary.set(androidLibrary)
            task.teamId.set(apple.teamId)
            task.bundleId.set(apple.bundleId)
            task.entitlementsFile.set(apple.entitlementsFile)
            task.xcodeProject.set(apple.xcodeProject)
            task.scheme.set(apple.scheme)
            task.xcconfig.set(apple.xcconfig)
            task.searchRoots.from(project.projectDir, project.rootDir)
            task.xcodebuild.set(project.providers.gradleProperty("passki.xcodebuild").orElse("xcodebuild"))
            task.codesign.set(project.providers.gradleProperty("passki.codesign").orElse("codesign"))
        }

        fun configureSigning(task: PasskiSigningTask) {
            task.packageName.set(extension.android.packageName.orElse(applicationId))
            task.variants.set(variants)
            task.extraFingerprints.set(
                extension.android.extraFingerprints.map { values ->
                    values.map { value ->
                        try {
                            Fingerprints.normalize(value)
                        } catch (e: IllegalArgumentException) {
                            throw PasskiConfigException("passki.android.extraFingerprints: ${e.message}", e)
                        }
                    }
                },
            )
        }

        fun appleAppIds(): Provider<List<String>> = extension.apple.teamId
            .zip(extension.apple.bundleId) { team, bundle -> listOf("$team.$bundle") }
            .orElse(emptyList())
    }

    /** Only loaded once `com.android.application` is applied, so AGP is on the classpath. */
    private class AndroidWiring(private val project: Project, private val wiring: Wiring) {
        fun wire() {
            wiring.androidApp.set(true)
            val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            components.finalizeDsl { dsl ->
                AndroidSigning.applicationId(dsl)?.let(wiring.applicationId::set)
                wiring.variants.set(AndroidSigning.variants(dsl))
            }

            project.tasks.register("passkiFingerprints", PasskiFingerprintsTask::class.java) { task ->
                task.group = GROUP
                task.description = "Prints Android packages, signing SHA-256 fingerprints and apk-key-hash origins."
                wiring.configureSigning(task)
            }
            project.tasks.register("passkiWellKnown", PasskiWellKnownTask::class.java) { task ->
                task.group = ADVANCED_GROUP
                task.description = "Only the well-known files of passkiSetup: writes assetlinks.json and apple-app-site-association to build/passki/well-known."
                wiring.configureSigning(task)
                task.appleAppIds.set(wiring.appleAppIds())
                task.outputDir.set(project.layout.buildDirectory.dir("passki/well-known"))
            }
            project.tasks.register("passkiVerify", PasskiVerifyTask::class.java) { task ->
                task.group = ADVANCED_GROUP
                task.description = "Only the hosted-file checks of passkiCheck, without a report (network). Prefer passkiCheck."
                wiring.configureSigning(task)
                task.rpId.set(wiring.extension.rpId)
                task.appleAppIds.set(wiring.appleAppIds())
                task.entitlementsFile.set(wiring.extension.apple.entitlementsFile)
                task.baseUrl.set(project.providers.gradleProperty("passki.verify.baseUrl"))
            }
        }
    }

    private companion object {
        const val GROUP = "passki"
        const val ADVANCED_GROUP = "passki advanced"
        val ANDROID_LIBRARY_PLUGINS = listOf("com.android.library", "com.android.kotlin.multiplatform.library")
    }
}
