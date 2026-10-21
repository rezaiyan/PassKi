package com.alirezaiyan.passki.gradle

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension

/** Reads app identity and signing keystores from the finalized AGP DSL. */
internal object AndroidSigning {
    /** `defaultConfig.applicationId`, falling back to `namespace` (AGP's own default). */
    fun applicationId(dsl: ApplicationExtension): String? = dsl.defaultConfig.applicationId ?: dsl.namespace

    /**
     * One [KeystoreSpec] per build type (or per flavor × build type), using AGP's precedence:
     * build type → product flavor → defaultConfig. Debuggable build types without an explicit config
     * fall back to AGP's `debug` signing config (`~/.android/debug.keystore`). Configs without a
     * store file are ignored.
     */
    fun keystores(dsl: ApplicationExtension): List<KeystoreSpec> {
        val defaultConfig = dsl.defaultConfig.signingConfig
        val debugConfig = dsl.signingConfigs.findByName("debug")
        val flavors = dsl.productFlavors.toList()
        val pairs = dsl.buildTypes.flatMap { buildType ->
            val fallback = if (buildType.isDebuggable) debugConfig else null
            if (flavors.isEmpty()) {
                listOf(buildType.name to (buildType.signingConfig ?: defaultConfig ?: fallback))
            } else {
                flavors.map { flavor ->
                    "${flavor.name}/${buildType.name}" to
                        (buildType.signingConfig ?: flavor.signingConfig ?: defaultConfig ?: fallback)
                }
            }
        }
        return pairs.mapNotNull { (label, config) -> config?.toSpec(label) }
    }

    private fun ApkSigningConfig.toSpec(label: String): KeystoreSpec? {
        val file = storeFile ?: return null
        return KeystoreSpec(label, file.absolutePath, storePassword, keyAlias, storeType)
    }
}
