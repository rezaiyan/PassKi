package com.alirezaiyan.passki.gradle

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor

/** Reads app identity and per-variant signing from the finalized AGP DSL. */
internal object AndroidSigning {
    /** `defaultConfig.applicationId`, falling back to `namespace` (AGP's own default). */
    fun applicationId(dsl: ApplicationExtension): String? = dsl.defaultConfig.applicationId ?: dsl.namespace

    /**
     * One [VariantSpec] per build type × flavor combination (one flavor per dimension, in
     * `flavorDimensions` order), mirroring AGP:
     * - package: first flavor `applicationId` (dimension order) or the base package, plus the
     *   defaultConfig, flavor and build type `applicationIdSuffix`es, dot-joined;
     * - signing: build type → flavors (dimension order) → defaultConfig; debuggable build types without
     *   an explicit config fall back to AGP's `debug` signing config (`~/.android/debug.keystore`).
     *   Configs without a store file yield a `null` keystore.
     */
    fun variants(dsl: ApplicationExtension): List<VariantSpec> {
        val defaultConfig = dsl.defaultConfig
        val debugConfig = dsl.signingConfigs.findByName("debug")
        return dsl.buildTypes.flatMap { buildType ->
            val fallback = if (buildType.isDebuggable) debugConfig else null
            flavorCombinations(dsl).map { flavors ->
                val label = (flavors.map { it.name } + buildType.name).joinToString("/")
                val config = buildType.signingConfig
                    ?: flavors.firstNotNullOfOrNull { it.signingConfig }
                    ?: defaultConfig.signingConfig
                    ?: fallback
                VariantSpec(
                    label = label,
                    applicationId = flavors.firstNotNullOfOrNull { it.applicationId },
                    applicationIdSuffix = VariantSpec.combineSuffixes(
                        defaultConfig.applicationIdSuffix,
                        *flavors.map { it.applicationIdSuffix }.toTypedArray(),
                        buildType.applicationIdSuffix,
                    ),
                    debuggable = buildType.isDebuggable,
                    keystore = config?.toSpec(label),
                )
            }
        }
    }

    /** Cartesian product of flavors across dimensions; a single empty combination without flavors. */
    private fun flavorCombinations(dsl: ApplicationExtension): List<List<ApplicationProductFlavor>> {
        val flavors = dsl.productFlavors.toList()
        if (flavors.isEmpty()) return listOf(emptyList())
        val dimensions = dsl.flavorDimensions.ifEmpty { listOf(null) }
        val byDimension = dimensions.map { dimension ->
            flavors.filter { dimensions.size == 1 || it.dimension == dimension }
        }.filter { it.isNotEmpty() }
        return byDimension.fold(listOf(emptyList())) { acc, group -> acc.flatMap { prefix -> group.map { prefix + it } } }
    }

    private fun ApkSigningConfig.toSpec(label: String): KeystoreSpec? {
        val file = storeFile ?: return null
        return KeystoreSpec(label, file.absolutePath, storePassword, keyAlias, storeType)
    }
}
