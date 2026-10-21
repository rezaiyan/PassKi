package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoctorReportTest {
    private val pass = DoctorCheck("Platform support", DoctorStatus.Pass, "Android 14 (API 34)", fixHint = null)
    private val warn = DoctorCheck("INTERNET permission", DoctorStatus.Warn, "not granted", fixHint = "add it to the manifest")
    private val fail = DoctorCheck(
        "assetlinks.json",
        DoctorStatus.Fail,
        "fingerprint C9:64 missing",
        fixHint = "add it to https://example.com/.well-known/assetlinks.json",
    )
    private val skipped = DoctorCheck("Entitlements", DoctorStatus.Skipped, "can't be read at runtime", fixHint = null)

    @Test
    fun healthyWhenNoCheckFails() {
        assertTrue(DoctorReport("example.com", listOf(pass, warn, skipped)).isHealthy)
        assertTrue(DoctorReport("example.com", emptyList()).isHealthy)
    }

    @Test
    fun unhealthyWhenAnyCheckFails() {
        assertFalse(DoctorReport("example.com", listOf(pass, fail)).isHealthy)
    }

    @Test
    fun toStringListsEveryCheckWithMarkersAndFixHints() {
        val report = DoctorReport("example.com", listOf(pass, fail, warn, skipped))
        assertEquals(
            """
            PassKi doctor for example.com: 1 failure, 1 warning
            [PASS] Platform support - Android 14 (API 34)
            [FAIL] assetlinks.json - fingerprint C9:64 missing
                   fix: add it to https://example.com/.well-known/assetlinks.json
            [WARN] INTERNET permission - not granted
                   fix: add it to the manifest
            [SKIP] Entitlements - can't be read at runtime
            """.trimIndent(),
            report.toString(),
        )
    }

    @Test
    fun toStringPluralisesCounts() {
        val report = DoctorReport("example.com", listOf(fail, fail, warn, warn, pass))
        assertTrue(report.toString().startsWith("PassKi doctor for example.com: 2 failures, 2 warnings\n"))
    }

    @Test
    fun toStringIndentsMultiLineFixHints() {
        val check = DoctorCheck("x", DoctorStatus.Fail, "bad", fixHint = "line one\n{\"a\":1}")
        val lines = DoctorReport("example.com", listOf(check)).toString().lines()
        assertEquals("       fix: line one", lines[2])
        assertEquals("            {\"a\":1}", lines[3])
    }

    @Test
    fun checkToStringIncludesCatalogIdWhenPresent() {
        val check = DoctorCheck("assetlinks.json", DoctorStatus.Pass, "delegated", fixHint = null, id = "AND-01")
        assertEquals("AND-01", check.id)
        assertEquals("[PASS] AND-01 assetlinks.json - delegated", check.toString())
    }

    @Test
    fun checkWithoutIdHasNullId() {
        assertEquals(null, warn.id)
    }

    @Test
    fun checkToStringIsReadable() {
        assertEquals("[WARN] INTERNET permission - not granted", warn.toString())
    }

    @Test
    fun failuresAndWarningsListMatchingChecksInOrder() {
        val secondWarn = DoctorCheck("CDN", DoctorStatus.Warn, "lagging", fixHint = null)
        val report = DoctorReport("example.com", listOf(pass, warn, fail, skipped, secondWarn))
        assertEquals(listOf(fail), report.failures)
        assertEquals(listOf(warn, secondWarn), report.warnings)
    }

    @Test
    fun healthyReportCanStillCarryWarnings() {
        val report = DoctorReport("example.com", listOf(pass, warn))
        assertTrue(report.isHealthy)
        assertTrue(report.failures.isEmpty())
        assertEquals(listOf(warn), report.warnings)
    }
}
