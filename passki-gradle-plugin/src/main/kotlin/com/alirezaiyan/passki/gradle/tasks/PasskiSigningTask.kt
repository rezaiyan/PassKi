package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.KeystoreSpec
import com.alirezaiyan.passki.gradle.PasskiConfigException
import com.alirezaiyan.passki.gradle.ResolvedKeystore
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * Shared inputs of the passki tasks: the Android package and its signing fingerprints.
 *
 * Keystore passwords are deliberately [Internal]: up-to-date checks track the keystore files
 * (content only) plus label, alias and type, never secrets.
 */
public abstract class PasskiSigningTask : DefaultTask() {
    /** Android package name (applicationId). */
    @get:Input
    @get:Optional
    public abstract val packageName: Property<String>

    /** Keystores captured from the Android DSL, passwords included; not tracked as an input. */
    @get:Internal
    public abstract val keystores: ListProperty<KeystoreSpec>

    /** Tracked, password-free identity of [keystores]. */
    @get:Input
    public abstract val keystoreIdentities: ListProperty<String>

    /** Keystore files referenced by [keystores]; missing files are allowed. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val keystoreFiles: ConfigurableFileCollection

    /** Already-normalized extra fingerprints (e.g. Play App Signing). */
    @get:Input
    public abstract val extraFingerprints: ListProperty<String>

    init {
        keystoreIdentities.set(keystores.map { specs -> specs.map { it.identity } })
        keystoreFiles.from(keystores.map { specs -> specs.map { it.path } })
    }

    internal fun requirePackageName(): String = packageName.orNull
        ?: throw PasskiConfigException(
            "No Android package name: set android.defaultConfig.applicationId or passki { android { packageName = \"…\" } }",
        )

    /** Resolves every keystore, logging a warning for each skipped one. */
    internal fun resolveKeystores(): List<ResolvedKeystore> = keystores.get().map { spec ->
        ResolvedKeystore.of(spec).also {
            if (it is ResolvedKeystore.Skipped) logger.warn("passki: skipping '${spec.label}' — ${it.reason}")
        }
    }

    /** All fingerprints (local keystores + extras); fails if there are none. */
    internal fun allFingerprints(resolved: List<ResolvedKeystore>): List<String> {
        val fingerprints = (resolved.filterIsInstance<ResolvedKeystore.Ok>().map { it.sha256 } + extraFingerprints.get())
            .distinct()
            .sorted()
        if (fingerprints.isEmpty()) {
            throw PasskiConfigException(
                "No signing fingerprint available: no readable keystore and no passki { android { extraFingerprints } }",
            )
        }
        return fingerprints
    }
}
