package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.PasskiConfigException
import com.alirezaiyan.passki.gradle.ResolvedKeystore
import com.alirezaiyan.passki.gradle.ResolvedVariant
import com.alirezaiyan.passki.gradle.SigningGroups
import com.alirezaiyan.passki.gradle.VariantSpec
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
import org.gradle.work.DisableCachingByDefault

/**
 * Shared inputs of the passki tasks: the Android base package and per-variant signing.
 *
 * Keystore passwords are deliberately [Internal]: up-to-date checks track the keystore files
 * (content only) plus the variants' password-free identity, never secrets.
 */
@DisableCachingByDefault(because = "Base type; subclasses decide")
public abstract class PasskiSigningTask : DefaultTask() {
    /** Base Android package name (applicationId); variant suffixes and flavor overrides apply on top. */
    @get:Input
    @get:Optional
    public abstract val packageName: Property<String>

    /** Variants captured from the Android DSL, keystore passwords included; not tracked as an input. */
    @get:Internal
    public abstract val variants: ListProperty<VariantSpec>

    /** Tracked, password-free identity of [variants]. */
    @get:Input
    public abstract val variantIdentities: ListProperty<String>

    /** Keystore files referenced by [variants]; missing files are allowed. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val keystoreFiles: ConfigurableFileCollection

    /** Already-normalized extra fingerprints (e.g. Play App Signing), for non-debuggable variants' packages. */
    @get:Input
    public abstract val extraFingerprints: ListProperty<String>

    init {
        variantIdentities.set(variants.map { specs -> specs.map { it.identity } })
        keystoreFiles.from(variants.map { specs -> specs.mapNotNull { it.keystore?.path } })
    }

    internal fun requirePackageName(): String = packageName.orNull
        ?: throw PasskiConfigException(
            "No Android package name: set android.defaultConfig.applicationId or passki { android { packageName = \"…\" } }",
        )

    /** Resolves every variant's package and keystore, logging a warning for each skipped keystore (once per file). */
    internal fun resolveVariants(): List<ResolvedVariant> {
        val base = requirePackageName()
        val byPath = mutableMapOf<String, ResolvedKeystore>()
        return variants.get().map { spec ->
            val keystore = spec.keystore?.let { ks ->
                byPath.getOrPut(ks.path) {
                    ResolvedKeystore.of(ks).also {
                        if (it is ResolvedKeystore.Skipped) logger.warn("passki: skipping '${ks.label}' — ${it.reason}")
                    }
                }
            }
            ResolvedVariant(spec, spec.packageName(base), keystore)
        }
    }

    /** Package → fingerprints for `assetlinks.json`; warns about packages left without any, fails if none remain. */
    internal fun fingerprintsByPackage(resolved: List<ResolvedVariant>): Map<String, List<String>> {
        val grouped = SigningGroups.group(requirePackageName(), resolved, extraFingerprints.get())
        SigningGroups.packagesWithoutFingerprints(resolved, grouped).forEach { (pkg, labels) ->
            logger.warn("passki: no signing fingerprint for package $pkg (${labels.joinToString(", ")}) — left out of assetlinks.json")
        }
        if (grouped.isEmpty()) {
            throw PasskiConfigException(
                "No signing fingerprint available: no readable keystore and no passki { android { extraFingerprints } }",
            )
        }
        return grouped
    }
}
