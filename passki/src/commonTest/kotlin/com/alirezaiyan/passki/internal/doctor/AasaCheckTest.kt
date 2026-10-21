package com.alirezaiyan.passki.internal.doctor

import com.alirezaiyan.passki.DoctorStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AasaCheckTest {
    private val directUrl = "https://example.com/.well-known/apple-app-site-association"
    private val cdnUrl = "https://app-site-association.cdn-apple.com/a/v1/example.com"
    private val bundleId = "com.example.app"
    private val body = """{"webcredentials":{"apps":["ABCDE12345.com.example.app"]},"applinks":{"details":[]}}"""

    private fun ok(url: String, body: String) =
        HttpResult(url = url, status = 200, contentType = "application/json", body = body, redirectedTo = null, error = null)

    private fun evaluate(direct: HttpResult, cdn: HttpResult = ok(cdnUrl, body)) = AasaCheck.evaluate(direct, cdn, bundleId)

    @Test
    fun passesAndNamesTeamIdWhenDirectAndCdnAgree() {
        val check = evaluate(ok(directUrl, body))
        assertEquals(DoctorStatus.Pass, check.status)
        assertEquals("apple-app-site-association", check.name)
        assertTrue("ABCDE12345" in check.message)
        assertNull(check.fixHint)
    }

    @Test
    fun cdnComparisonIgnoresFormatting() {
        val pretty = """
            {
              "applinks": { "details": [] },
              "webcredentials": { "apps": [ "ABCDE12345.com.example.app" ] }
            }
        """.trimIndent()
        assertEquals(DoctorStatus.Pass, evaluate(ok(directUrl, body), ok(cdnUrl, pretty)).status)
    }

    @Test
    fun failsWhenDirectFileIsMissing() {
        val direct = HttpResult(directUrl, status = 404, contentType = null, body = null, redirectedTo = null, error = null)
        val check = evaluate(direct)
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("404" in check.message)
        assertTrue(directUrl in check.fixHint!!)
    }

    @Test
    fun failsWhenDirectFetchErrored() {
        val direct = HttpResult(directUrl, status = -1, contentType = null, body = null, redirectedTo = null, error = "offline")
        val check = evaluate(direct)
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("offline" in check.message)
    }

    @Test
    fun failsWhenDirectFileRedirects() {
        val direct = HttpResult(
            directUrl, status = 302, contentType = null, body = null,
            redirectedTo = "https://www.example.com/.well-known/apple-app-site-association", error = null,
        )
        val check = evaluate(direct)
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("redirect" in check.message)
    }

    @Test
    fun failsWhenDirectFileIsNotJson() {
        val check = evaluate(ok(directUrl, "<html>"))
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("JSON" in check.message)
    }

    @Test
    fun failsWhenWebcredentialsSectionIsAbsent() {
        val check = evaluate(ok(directUrl, """{"applinks":{"details":[]}}"""))
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("webcredentials" in check.message)
        assertTrue(bundleId in check.fixHint!!)
    }

    @Test
    fun failsWhenBundleIdIsNotListedAndNamesTeamIdFound() {
        val check = evaluate(ok(directUrl, """{"webcredentials":{"apps":["ZZZZZ99999.com.other.app"]}}"""))
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue(bundleId in check.message)
        assertTrue("ZZZZZ99999" in check.message)
        assertTrue("ZZZZZ99999.$bundleId" in check.fixHint!!)
    }

    @Test
    fun bundleIdMatchIsExactAfterTeamId() {
        val check = evaluate(ok(directUrl, """{"webcredentials":{"apps":["ABCDE12345.com.example.app.extra"]}}"""))
        assertEquals(DoctorStatus.Fail, check.status)
    }

    @Test
    fun warnsWhenCdnCopyDiffers() {
        val stale = """{"webcredentials":{"apps":[]}}"""
        val check = evaluate(ok(directUrl, body), ok(cdnUrl, stale))
        assertEquals(DoctorStatus.Warn, check.status)
        assertTrue("CDN" in check.message)
        val hint = assertNotNull(check.fixHint)
        assertTrue("24h" in hint)
        assertTrue("webcredentials:example.com?mode=developer" in hint)
    }

    @Test
    fun warnsWhenCdnCopyIsMissing() {
        val cdn = HttpResult(cdnUrl, status = 404, contentType = null, body = null, redirectedTo = null, error = null)
        val check = evaluate(ok(directUrl, body), cdn)
        assertEquals(DoctorStatus.Warn, check.status)
        assertTrue("webcredentials:example.com?mode=developer" in check.fixHint!!)
    }

    @Test
    fun warnsWhenCdnFetchErrored() {
        val cdn = HttpResult(cdnUrl, status = -1, contentType = null, body = null, redirectedTo = null, error = "offline")
        assertEquals(DoctorStatus.Warn, evaluate(ok(directUrl, body), cdn).status)
    }

    @Test
    fun directFailureWinsOverCdnWarning() {
        val cdn = HttpResult(cdnUrl, status = 404, contentType = null, body = null, redirectedTo = null, error = null)
        val direct = HttpResult(directUrl, status = 404, contentType = null, body = null, redirectedTo = null, error = null)
        assertEquals(DoctorStatus.Fail, evaluate(direct, cdn).status)
    }
}
