package com.alirezaiyan.passki.gradle.check

import groovy.json.JsonSlurper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class CheckReportTest {
    private val results = listOf(
        CheckResult("AND-01", CheckStatus.PASS, "served at https://example.com/.well-known/assetlinks.json"),
        CheckResult("AND-04", CheckStatus.WARN, "Google doesn't list it yet", "Wait for the cache"),
        CheckResult("IOS-05", CheckStatus.FAIL, "CODE_SIGN_ENTITLEMENTS is not set", "Set CODE_SIGN_ENTITLEMENTS = a.entitlements\nsecond line"),
        CheckResult("IOS-06", CheckStatus.SKIP, "no build <found>"),
    )
    private val report = CheckReport("example.com", results, Catalog.bundled)

    @Test
    fun consoleListsMarkerIdTitleMessageAndFix() {
        val lines = report.console().lines()
        assertEquals("PassKi check for example.com: 1 passed, 1 warning, 1 failed, 1 skipped", lines[0])
        assertEquals("[PASS] AND-01  assetlinks.json reachable - served at https://example.com/.well-known/assetlinks.json", lines[1])
        assertEquals("[WARN] AND-04  Google Digital Asset Links agrees - Google doesn't list it yet", lines[2])
        assertEquals("               fix: Wait for the cache", lines[3])
        assertTrue(lines[4].startsWith("               docs: ") && lines[4].endsWith("AND-04.md"), lines[4])
        assertEquals("[FAIL] IOS-05  Entitlements wired into the Xcode build - CODE_SIGN_ENTITLEMENTS is not set", lines[5])
        assertEquals("                    second line", lines[7])
        assertTrue(lines.any { it.startsWith("[SKIP] IOS-06") })
    }

    @Test
    fun jsonHasSummaryAndEveryCheck() {
        val json = JsonSlurper().parseText(report.json()) as Map<*, *>
        assertEquals("example.com", json["rpId"])
        assertEquals(mapOf("pass" to 1, "warn" to 1, "fail" to 1, "skip" to 1), json["summary"])
        val checks = json["checks"] as List<*>
        val first = checks.first() as Map<*, *>
        assertEquals("AND-01", first["id"])
        assertEquals("PASS", first["status"])
        assertEquals("assetlinks.json reachable", first["title"])
        assertEquals("android", first["platform"])
        assertTrue((first["docs"] as String).endsWith("AND-01.md"))
    }

    @Test
    fun htmlIsStaticAndEscaped() {
        val html = report.html()
        assertTrue(html.startsWith("<!doctype html>"))
        assertFalse("<script" in html)
        assertTrue("no build &lt;found&gt;" in html)
        assertTrue("IOS-05" in html && "Entitlements wired into the Xcode build" in html)
    }

    @Test
    fun failingRespectsWarningsAsErrors() {
        assertEquals(listOf("IOS-05"), report.failing(warningsAsErrors = false).map { it.id })
        assertEquals(listOf("AND-04", "IOS-05"), report.failing(warningsAsErrors = true).map { it.id })
    }
}
