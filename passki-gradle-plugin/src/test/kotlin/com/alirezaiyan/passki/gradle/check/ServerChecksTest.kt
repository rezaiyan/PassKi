package com.alirezaiyan.passki.gradle.check

import com.alirezaiyan.passki.gradle.WellKnownVerifier.Fetched
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ServerChecksTest {
    private val origins = listOf("https://example.com", "android:apk-key-hash:abc")
    private val f = ResultFactory(Catalog.bundled, mapOf("rpId" to "example.com"))
    private val url = "https://example.com/passki/server-config"

    private fun ok(body: String) = Fetch(url, Fetched(200, "application/json", body, false), null)

    private fun run(fetch: Fetch?) = ServerChecks.evaluate(f, "example.com", origins, fetch).associate { it.id to it }

    private fun b64(size: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(size) { it.toByte() })

    @Test
    fun notConfiguredSkipsAll() {
        assertEquals(setOf(CheckStatus.SKIP), run(null).values.map { it.status }.toSet())
        assertEquals(setOf("SRV-01", "SRV-02", "SRV-03"), run(null).keys)
    }

    @Test
    fun configContractPasses() {
        val results = run(ok("""{"rpId":"example.com","origins":["https://example.com","android:apk-key-hash:abc","https://other"]}"""))
        assertEquals(CheckStatus.PASS, results["SRV-01"]!!.status)
        assertEquals(CheckStatus.PASS, results["SRV-02"]!!.status)
        assertEquals(CheckStatus.SKIP, results["SRV-03"]!!.status)
    }

    @Test
    fun wrongRpIdAndMissingOriginFail() {
        val results = run(ok("""{"rpId":"www.example.com","origins":["https://example.com"]}"""))
        assertEquals(CheckStatus.FAIL, results["SRV-01"]!!.status)
        assertTrue("www.example.com" in results["SRV-01"]!!.message)
        assertEquals(CheckStatus.FAIL, results["SRV-02"]!!.status)
        assertTrue("android:apk-key-hash:abc" in results["SRV-02"]!!.message)
    }

    @Test
    fun unreachableFailsSrv01() {
        val results = run(Fetch(url, null, "refused"))
        assertEquals(CheckStatus.FAIL, results["SRV-01"]!!.status)
        assertEquals(CheckStatus.SKIP, results["SRV-02"]!!.status)
        assertEquals(CheckStatus.FAIL, run(ok("<html>"))["SRV-01"]!!.status)
    }

    @Test
    fun registrationOptionsAreValidated() {
        val good = """{"rp":{"id":"example.com","name":"Ex"},"challenge":"${b64(32)}","user":{"id":"${b64(16)}","name":"a"}}"""
        val results = run(ok(good))
        assertEquals(CheckStatus.PASS, results["SRV-01"]!!.status)
        assertEquals(CheckStatus.SKIP, results["SRV-02"]!!.status)
        assertEquals(CheckStatus.PASS, results["SRV-03"]!!.status, results["SRV-03"]!!.message)
        assertEquals(CheckStatus.PASS, run(ok("""{"publicKey":$good}"""))["SRV-03"]!!.status)
    }

    @Test
    fun badRegistrationOptionsFailSrv03() {
        val shortChallenge = """{"rp":{"id":"example.com"},"challenge":"${b64(8)}","user":{"id":"${b64(16)}"}}"""
        assertEquals(CheckStatus.FAIL, run(ok(shortChallenge))["SRV-03"]!!.status)
        val longUser = """{"rp":{"id":"example.com"},"challenge":"${b64(32)}","user":{"id":"${b64(65)}"}}"""
        assertEquals(CheckStatus.FAIL, run(ok(longUser))["SRV-03"]!!.status)
        val noRpId = """{"rp":{},"challenge":"${b64(32)}","user":{"id":"${b64(16)}"}}"""
        assertEquals(CheckStatus.FAIL, run(ok(noRpId))["SRV-03"]!!.status)
    }
}
