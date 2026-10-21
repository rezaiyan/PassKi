package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.internal.doctor.AasaCheck
import com.alirezaiyan.passki.internal.doctor.HttpGetter
import com.alirezaiyan.passki.internal.doctor.HttpResult
import com.alirezaiyan.passki.internal.doctor.PLATFORM_CHECK
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosDoctorTest {
    private val bundleId = "com.example.app"
    private val directUrl = "https://example.com/.well-known/apple-app-site-association"
    private val cdnUrl = "https://app-site-association.cdn-apple.com/a/v1/example.com"
    private val validAasa = """{"webcredentials":{"apps":["ABCDE12345.$bundleId"]}}"""

    private fun facts(
        osMajor: Int = 17,
        bundleId: String? = this.bundleId,
        keyWindow: Boolean = true,
    ) = IosDoctorFacts(osMajor = osMajor, osVersion = "$osMajor.0", bundleId = bundleId, hasKeyWindow = keyWindow)

    private class RecordingGetter(private val respond: (String) -> HttpResult) : HttpGetter {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String): HttpResult {
            urls += url
            return respond(url)
        }
    }

    private fun ok(url: String, body: String) = HttpResult(url, 200, "application/json", body, redirectedTo = null, error = null)

    private fun missing(url: String) = HttpResult(url, 404, "text/html", "not found", redirectedTo = null, error = null)

    @Test
    fun healthySetupPassesAndSkipsEntitlements() = runTest {
        val getter = RecordingGetter { ok(it, validAasa) }
        val report = composeIosReport("example.com", facts(), getter)

        assertEquals(listOf(directUrl, cdnUrl), getter.urls)
        assertEquals(
            listOf(PLATFORM_CHECK, KEY_WINDOW_CHECK, ENTITLEMENT_CHECK, AasaCheck.NAME),
            report.checks.map { it.name },
        )
        assertEquals(
            listOf(DoctorStatus.Pass, DoctorStatus.Pass, DoctorStatus.Skipped, DoctorStatus.Pass),
            report.checks.map { it.status },
            report.toString(),
        )
        assertTrue(report.isHealthy)
        assertTrue("iOS 17" in report.checks[0].message)
        val entitlement = report.checks.single { it.name == ENTITLEMENT_CHECK }
        assertTrue("passkiVerify" in entitlement.fixHint.orEmpty())
        assertTrue("webcredentials:example.com" in entitlement.fixHint.orEmpty())
    }

    @Test
    fun aasaWithoutThisAppFails() = runTest {
        val other = """{"webcredentials":{"apps":["ABCDE12345.com.other.app"]}}"""
        val report = composeIosReport("example.com", facts(), RecordingGetter { ok(it, other) })

        val aasa = report.checks.single { it.name == AasaCheck.NAME }
        assertEquals(DoctorStatus.Fail, aasa.status)
        assertTrue(bundleId in aasa.message)
        assertTrue("ABCDE12345" in aasa.message)
        assertFalse(report.isHealthy)
    }

    @Test
    fun cdnLagWarns() = runTest {
        val getter = RecordingGetter { if (it == cdnUrl) missing(it) else ok(it, validAasa) }
        val aasa = composeIosReport("example.com", facts(), getter).checks.single { it.name == AasaCheck.NAME }

        assertEquals(DoctorStatus.Warn, aasa.status)
        assertTrue("mode=developer" in aasa.fixHint.orEmpty())
    }

    @Test
    fun redirectFailsAasa() = runTest {
        val getter = RecordingGetter {
            HttpResult(it, 301, null, null, redirectedTo = "https://www.example.com/.well-known/apple-app-site-association", error = null)
        }
        val aasa = composeIosReport("example.com", facts(), getter).checks.single { it.name == AasaCheck.NAME }

        assertEquals(DoctorStatus.Fail, aasa.status)
        assertTrue("redirect" in aasa.message)
    }

    @Test
    fun transportErrorFailsAasa() = runTest {
        val getter = RecordingGetter { HttpResult(it, -1, null, null, redirectedTo = null, error = "The Internet connection appears to be offline.") }
        val aasa = composeIosReport("example.com", facts(), getter).checks.single { it.name == AasaCheck.NAME }

        assertEquals(DoctorStatus.Fail, aasa.status)
        assertTrue("offline" in aasa.message)
    }

    @Test
    fun iosBelow16FailsAndStops() = runTest {
        val getter = RecordingGetter { ok(it, validAasa) }
        val report = composeIosReport("example.com", facts(osMajor = 15), getter)

        assertEquals(listOf(PLATFORM_CHECK), report.checks.map { it.name })
        assertEquals(DoctorStatus.Fail, report.checks.single().status)
        assertTrue("iOS 16" in report.checks.single().message)
        assertTrue(getter.urls.isEmpty())
    }

    @Test
    fun missingKeyWindowWarnsOnly() = runTest {
        val report = composeIosReport("example.com", facts(keyWindow = false), RecordingGetter { ok(it, validAasa) })

        val window = report.checks.single { it.name == KEY_WINDOW_CHECK }
        assertEquals(DoctorStatus.Warn, window.status)
        assertTrue(report.isHealthy)
    }

    @Test
    fun missingBundleIdFailsAndSkipsAasa() = runTest {
        val getter = RecordingGetter { ok(it, validAasa) }
        val report = composeIosReport("example.com", facts(bundleId = null), getter)

        val aasa = report.checks.single { it.name == AasaCheck.NAME }
        assertEquals(DoctorStatus.Fail, aasa.status)
        assertTrue(getter.urls.isEmpty())
    }
}
