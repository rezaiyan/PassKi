package com.alirezaiyan.passki.gradle.check

import com.alirezaiyan.passki.gradle.KeystoreSpec
import com.alirezaiyan.passki.gradle.VariantSpec
import com.alirezaiyan.passki.gradle.WellKnownFiles
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Expected
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Fetched
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class AndroidChecksTest {
    private val fp = List(32) { "AB" }.joinToString(":")
    private val fp2 = List(32) { "CD" }.joinToString(":")
    private val expected = Expected("example.com", mapOf("com.example.app" to listOf(fp)), emptyList())
    private val f = ResultFactory(Catalog.bundled, mapOf("rpId" to "example.com", "package" to "com.example.app", "fingerprint" to fp))
    private val url = "https://example.com/.well-known/assetlinks.json"

    private fun ok(body: String) = Fetch(url, Fetched(200, "application/json", body, redirected = false), error = null)

    private fun statuses(results: List<CheckResult>) = results.associate { it.id to it.status }

    @Test
    fun goodFilePassesAnd01To03() {
        val results = AndroidChecks.wellKnown(f, expected, ok(WellKnownFiles.assetLinks(expected.fingerprintsByPackage)))
        assertEquals(mapOf("AND-01" to CheckStatus.PASS, "AND-02" to CheckStatus.PASS, "AND-03" to CheckStatus.PASS), statuses(results))
    }

    @Test
    fun unreachableFileFailsAnd01AndSkipsTheRest() {
        val results = AndroidChecks.wellKnown(f, expected, Fetch(url, null, "connection refused"))
        assertEquals(mapOf("AND-01" to CheckStatus.FAIL, "AND-02" to CheckStatus.SKIP, "AND-03" to CheckStatus.SKIP), statuses(results))
        val and01 = results.first()
        assertTrue("connection refused" in and01.message)
        assertTrue(and01.fix!!.contains("https://example.com/.well-known/assetlinks.json"), and01.fix)
    }

    @Test
    fun notFoundFailsAnd01() {
        val results = AndroidChecks.wellKnown(f, expected, Fetch(url, Fetched(404, null, "", false), null))
        assertEquals(CheckStatus.FAIL, statuses(results)["AND-01"])
        assertEquals(CheckStatus.SKIP, statuses(results)["AND-02"])
    }

    @Test
    fun missingPackageFailsAnd02() {
        val results = AndroidChecks.wellKnown(f, expected, ok(WellKnownFiles.assetLinks(mapOf("com.other" to listOf(fp)))))
        assertEquals(mapOf("AND-01" to CheckStatus.PASS, "AND-02" to CheckStatus.FAIL, "AND-03" to CheckStatus.SKIP), statuses(results))
    }

    @Test
    fun missingFingerprintFailsAnd03WithFingerprintInFix() {
        val results = AndroidChecks.wellKnown(f, expected, ok(WellKnownFiles.assetLinks(mapOf("com.example.app" to listOf(fp2)))))
        val and03 = results.single { it.id == "AND-03" }
        assertEquals(CheckStatus.FAIL, and03.status)
        assertTrue(fp in and03.message)
        assertTrue(fp in and03.fix!!, and03.fix)
    }

    private fun dalBody(pkg: String, fingerprint: String) = """
        {"statements":[{"source":{"web":{"site":"https://example.com."}},"relation":"delegate_permission/common.get_login_creds",
         "target":{"androidApp":{"packageName":"$pkg","certificate":{"sha256Fingerprint":"$fingerprint"}}}}],"maxAge":"600s"}
    """.trimIndent()

    @Test
    fun dalAgreesPasses() {
        assertEquals(CheckStatus.PASS, AndroidChecks.dal(f, expected, ok(dalBody("com.example.app", fp))).status)
    }

    @Test
    fun dalMissingFingerprintWarns() {
        val result = AndroidChecks.dal(f, expected, ok(dalBody("com.example.app", fp2)))
        assertEquals(CheckStatus.WARN, result.status)
        assertTrue(fp in result.message)
    }

    @Test
    fun dalUnreachableWarns() {
        assertEquals(CheckStatus.WARN, AndroidChecks.dal(f, expected, Fetch("x", null, "timeout")).status)
    }

    private fun variant(debuggable: Boolean, signed: Boolean) =
        VariantSpec(if (debuggable) "debug" else "release", null, "", debuggable, if (signed) KeystoreSpec("x", "/k", "p", "a", null) else null)

    @Test
    fun unsignedReleaseWithoutExtrasWarnsAnd05() {
        assertEquals(CheckStatus.WARN, AndroidChecks.playSigning(f, listOf(variant(true, true), variant(false, false)), emptyList()).status)
    }

    @Test
    fun extrasOrSignedReleasePassAnd05() {
        assertEquals(CheckStatus.PASS, AndroidChecks.playSigning(f, listOf(variant(false, false)), listOf(fp)).status)
        assertEquals(CheckStatus.PASS, AndroidChecks.playSigning(f, listOf(variant(false, true)), emptyList()).status)
    }

    @Test
    fun onlyDebugVariantsSkipAnd05() {
        assertEquals(CheckStatus.SKIP, AndroidChecks.playSigning(f, listOf(variant(true, true)), emptyList()).status)
    }
}
