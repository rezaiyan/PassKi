package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.Fingerprints
import com.alirezaiyan.passki.gradle.PasskiConfigException
import com.alirezaiyan.passki.gradle.WellKnownFiles
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Turns a raw SHA-256 signing fingerprint (for example the Play App Signing key from Play Console, which is in no
 * local keystore) into the Android WebAuthn origin and, with a package, the matching `assetlinks.json` statement.
 */
@DisableCachingByDefault(because = "Only logs")
public abstract class PasskiOriginTask : DefaultTask() {
    @get:Input
    @get:Optional
    @get:Option(option = "sha256", description = "SHA-256 signing fingerprint, colon-hex (AB:CD:…) or plain hex.")
    public abstract val sha256: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "package", description = "Android package name; adds the assetlinks.json statement.")
    public abstract val packageName: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    public fun print() {
        val raw = sha256.orNull
            ?: throw PasskiConfigException("Pass the fingerprint: ./gradlew passkiOrigin --sha256=AB:CD:… [--package=com.example.app]")
        val fingerprint = try {
            Fingerprints.normalize(raw.trim())
        } catch (e: IllegalArgumentException) {
            throw PasskiConfigException("--sha256: ${e.message}", e)
        }
        val text = buildString {
            appendLine("Android WebAuthn origin (server allow-list):")
            appendLine("  ${Fingerprints.androidOrigin(fingerprint)}")
            val pkg = packageName.orNull
            if (pkg == null) {
                appendLine("Add --package=<applicationId> to also print the assetlinks.json statement.")
            } else {
                appendLine("assetlinks.json (merge into the array you serve):")
                WellKnownFiles.assetLinks(mapOf(pkg to listOf(fingerprint))).lineSequence()
                    .filter { it.isNotEmpty() }
                    .forEach { appendLine("  $it") }
            }
        }
        logger.lifecycle(text.trimEnd())
    }
}
