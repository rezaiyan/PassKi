package com.alirezaiyan.passki.gradle

import com.alirezaiyan.passki.gradle.tasks.PasskiAppleTask
import com.alirezaiyan.passki.gradle.tasks.PasskiCheckTask
import com.alirezaiyan.passki.gradle.tasks.PasskiFingerprintsTask
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
 * Registers the [PasskiExtension] (`passki { }`). When `com.android.application` is applied it also
 * registers `passkiFingerprints`, `passkiWellKnown`, `passkiVerify`, `passkiSetup` and `passkiCheck`, fed from the
 * finalized Android DSL.
 */
public class PasskiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("passki", PasskiExtension::class.java)
        extension.apple.developerMode.convention(true)
        project.pluginManager.withPlugin("com.android.application") {
            AndroidWiring(project, extension).wire()
        }
    }

    private class AndroidWiring(private val project: Project, private val extension: PasskiExtension) {
        // Plain values captured in finalizeDsl; tasks only see these providers (configuration-cache safe).
        private val applicationId: Property<String> = project.objects.property(String::class.java)
        private val variants: ListProperty<VariantSpec> = project.objects.listProperty(VariantSpec::class.java)

        fun wire() {
            val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            components.finalizeDsl { dsl ->
                AndroidSigning.applicationId(dsl)?.let(applicationId::set)
                variants.set(AndroidSigning.variants(dsl))
            }

            project.tasks.register("passkiFingerprints", PasskiFingerprintsTask::class.java) { task ->
                task.group = GROUP
                task.description = "Prints Android packages, signing SHA-256 fingerprints and apk-key-hash origins."
                configureSigning(task)
            }
            project.tasks.register("passkiWellKnown", PasskiWellKnownTask::class.java) { task ->
                task.group = GROUP
                task.description = "Generates assetlinks.json and apple-app-site-association into build/passki/well-known."
                configureSigning(task)
                task.appleAppIds.set(appleAppIds())
                task.outputDir.set(project.layout.buildDirectory.dir("passki/well-known"))
            }
            project.tasks.register("passkiVerify", PasskiVerifyTask::class.java) { task ->
                task.group = GROUP
                task.description = "Checks the hosted .well-known files and entitlements against this build (network)."
                configureSigning(task)
                task.rpId.set(extension.rpId)
                task.appleAppIds.set(appleAppIds())
                task.entitlementsFile.set(extension.apple.entitlementsFile)
                task.baseUrl.set(project.providers.gradleProperty("passki.verify.baseUrl"))
            }
            project.tasks.register("passkiSetup", PasskiSetupTask::class.java) { task ->
                task.group = GROUP
                task.description = "Generates well-known files, writes and wires iOS entitlements (via xcconfig) and server config."
                configureApple(task)
                task.developerMode.set(extension.apple.developerMode)
                task.outputDir.set(project.layout.buildDirectory.dir("passki"))
            }
            project.tasks.register("passkiCheck", PasskiCheckTask::class.java) { task ->
                task.group = GROUP
                task.description = "Runs the setup checklist (AND/IOS/SRV catalog checks) and writes build/reports/passki (network)."
                configureApple(task)
                val providers = project.providers
                task.serverConfigUrl.set(extension.server.configUrl)
                task.baseUrl.set(providers.gradleProperty("passki.check.baseUrl").orElse(providers.gradleProperty("passki.verify.baseUrl")))
                task.googleDalUrl.set(providers.gradleProperty("passki.check.googleDalUrl").orElse("https://digitalassetlinks.googleapis.com"))
                task.appleCdnUrl.set(providers.gradleProperty("passki.check.appleCdnUrl").orElse("https://app-site-association.cdn-apple.com"))
                task.warningsAsErrors.set(providers.gradleProperty("passki.check.warningsAsErrors").map { it.toBoolean() }.orElse(false))
                task.reportDir.set(project.layout.buildDirectory.dir("reports/passki"))
            }
        }

        fun configureApple(task: PasskiAppleTask) {
            configureSigning(task)
            val apple = extension.apple
            task.rpId.set(extension.rpId)
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

    private companion object {
        const val GROUP = "passki"
    }
}
