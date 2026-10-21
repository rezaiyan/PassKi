package com.alirezaiyan.passki.gradle

import java.io.Serializable

/**
 * One Android variant (build type, or flavor(s) × build type) captured from the DSL at configuration time:
 * how its package name is derived and which keystore signs it.
 *
 * Plain serializable value so tasks stay configuration-cache compatible.
 */
public data class VariantSpec(
    /** e.g. `release` or `free/debug`. */
    val label: String,
    /** Flavor `applicationId` override; `null` uses the base package. */
    val applicationId: String?,
    /** Combined defaultConfig + flavor + build type `applicationIdSuffix`, dot-prefixed (or empty). */
    val applicationIdSuffix: String,
    /** Debuggable variants never receive `extraFingerprints` (those are for release/Play signing). */
    val debuggable: Boolean,
    /** Signing keystore, or `null` when the variant has no usable signing config. */
    val keystore: KeystoreSpec?,
) : Serializable {
    /** Tracked identity: everything but the keystore password and file. */
    val identity: String
        get() = "$label|${applicationId.orEmpty()}|$applicationIdSuffix|$debuggable|${keystore?.identity.orEmpty()}"

    /** The package this variant produces when the base package is [basePackage]. */
    public fun packageName(basePackage: String): String = (applicationId ?: basePackage) + applicationIdSuffix

    internal companion object {
        private const val serialVersionUID: Long = 1L

        /** Joins suffixes the way AGP does: each non-empty part is separated by exactly one leading dot. */
        fun combineSuffixes(vararg suffixes: String?): String = suffixes
            .filterNot { it.isNullOrEmpty() }
            .joinToString("") { if (it!!.startsWith(".")) it else ".$it" }
    }
}

/** A [VariantSpec] with its resolved package and keystore. */
internal data class ResolvedVariant(val spec: VariantSpec, val packageName: String, val keystore: ResolvedKeystore?)

/** Groups signing fingerprints per produced package for `assetlinks.json`. */
internal object SigningGroups {
    /**
     * Every distinct package → sorted, distinct fingerprints of the variants producing it.
     * [extras] go to packages of non-debuggable variants (to all packages if every variant is debuggable).
     * With no variants at all, [extras] are attached to [basePackage]. Packages left without any
     * fingerprint are omitted (see [packagesWithoutFingerprints]).
     */
    fun group(basePackage: String, variants: List<ResolvedVariant>, extras: List<String>): Map<String, List<String>> {
        if (variants.isEmpty()) {
            return if (extras.isEmpty()) emptyMap() else mapOf(basePackage to extras.distinct().sorted())
        }
        val extrasEverywhere = variants.all { it.spec.debuggable }
        val byPackage = sortedMapOf<String, MutableSet<String>>()
        variants.forEach { variant ->
            val set = byPackage.getOrPut(variant.packageName) { sortedSetOf() }
            (variant.keystore as? ResolvedKeystore.Ok)?.let { set += it.sha256 }
            if (extrasEverywhere || !variant.spec.debuggable) set += extras
        }
        return byPackage.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() }
    }

    /** Packages produced by [variants] that [group] had to omit, with the variant labels producing them. */
    fun packagesWithoutFingerprints(variants: List<ResolvedVariant>, grouped: Map<String, List<String>>): Map<String, List<String>> =
        variants.filter { it.packageName !in grouped }.groupBy({ it.packageName }, { it.spec.label }).toSortedMap()
}
