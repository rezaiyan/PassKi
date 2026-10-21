package com.alirezaiyan.passki.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

internal class SigningGroupsTest {
    private val fpDebug = List(32) { "DD" }.joinToString(":")
    private val fpRelease = List(32) { "EE" }.joinToString(":")
    private val fpExtra = List(32) { "AB" }.joinToString(":")

    private fun variant(
        label: String,
        suffix: String = "",
        debuggable: Boolean = false,
        fingerprint: String? = null,
        applicationId: String? = null,
    ): ResolvedVariant {
        val keystore = KeystoreSpec(label, "/k/$label.jks", "pw", "key", null)
        val spec = VariantSpec(label, applicationId, suffix, debuggable, keystore)
        val resolved = fingerprint?.let { ResolvedKeystore.Ok(keystore, it) } ?: ResolvedKeystore.Skipped(keystore, "missing")
        return ResolvedVariant(spec, spec.packageName("com.example.app"), resolved)
    }

    @Test
    fun combineSuffixesDotPrefixesEachPart() {
        assertEquals("", VariantSpec.combineSuffixes(null, "", null))
        assertEquals(".debug", VariantSpec.combineSuffixes(null, "debug"))
        assertEquals(".free.debug", VariantSpec.combineSuffixes(".free", null, ".debug"))
        assertEquals(".a.b.c", VariantSpec.combineSuffixes("a", ".b", "c"))
    }

    @Test
    fun packageNameUsesFlavorApplicationIdAndSuffix() {
        val spec = VariantSpec("x/debug", "com.other", ".debug", true, null)

        assertEquals("com.other.debug", spec.packageName("com.example.app"))
        assertEquals("com.example.app.debug", spec.copy(applicationId = null).packageName("com.example.app"))
    }

    @Test
    fun suffixedDebugGetsOnlyDebugKeyAndReleaseGetsReleasePlusExtras() {
        val variants = listOf(
            variant("debug", suffix = ".debug", debuggable = true, fingerprint = fpDebug),
            variant("release", fingerprint = fpRelease),
        )

        assertEquals(
            mapOf(
                "com.example.app" to listOf(fpExtra, fpRelease),
                "com.example.app.debug" to listOf(fpDebug),
            ),
            SigningGroups.group("com.example.app", variants, listOf(fpExtra)),
        )
    }

    @Test
    fun samePackageMergesFingerprintsOfAllVariants() {
        val variants = listOf(
            variant("debug", debuggable = true, fingerprint = fpDebug),
            variant("release", fingerprint = fpRelease),
        )

        assertEquals(
            mapOf("com.example.app" to listOf(fpDebug, fpRelease)),
            SigningGroups.group("com.example.app", variants, emptyList()),
        )
    }

    @Test
    fun releaseWithoutKeystoreStillGetsExtras() {
        val variants = listOf(variant("release"))

        assertEquals(mapOf("com.example.app" to listOf(fpExtra)), SigningGroups.group("com.example.app", variants, listOf(fpExtra)))
    }

    @Test
    fun packagesWithoutFingerprintsAreOmittedAndReported() {
        val variants = listOf(
            variant("debug", suffix = ".debug", debuggable = true),
            variant("release", fingerprint = fpRelease),
        )

        val grouped = SigningGroups.group("com.example.app", variants, emptyList())

        assertEquals(mapOf("com.example.app" to listOf(fpRelease)), grouped)
        assertEquals(mapOf("com.example.app.debug" to listOf("debug")), SigningGroups.packagesWithoutFingerprints(variants, grouped))
    }

    @Test
    fun extrasGoEverywhereWhenAllVariantsAreDebuggable() {
        val variants = listOf(variant("debug", debuggable = true, fingerprint = fpDebug))

        assertEquals(
            mapOf("com.example.app" to listOf(fpExtra, fpDebug)),
            SigningGroups.group("com.example.app", variants, listOf(fpExtra)),
        )
    }

    @Test
    fun noVariantsAttachesExtrasToBasePackage() {
        assertEquals(mapOf("com.example.app" to listOf(fpExtra)), SigningGroups.group("com.example.app", emptyList(), listOf(fpExtra)))
        assertEquals(emptyMap(), SigningGroups.group("com.example.app", emptyList(), emptyList()))
    }
}
