package com.alirezaiyan.passki.gradle.setup

import com.alirezaiyan.passki.gradle.WellKnownFiles
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Fetched
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SharedRpIdTest {
    private val fp = List(32) { "AA" }.joinToString(":")

    private fun ok(body: String) = Fetched(200, "application/json", body, redirected = false)

    @Test
    fun bareDomainsAreDetected() {
        assertTrue(SharedRpId.isBareDomain("example.com"))
        assertTrue(SharedRpId.isBareDomain("example.co.uk"))
        assertFalse(SharedRpId.isBareDomain("app.example.com"))
        assertFalse(SharedRpId.isBareDomain("lexicon.alirezaiyan.com"))
        assertFalse(SharedRpId.isBareDomain("a.b.example.co.uk"))
    }

    @Test
    fun otherAndroidAndAppleAppsAreListed() {
        val assetLinks = ok(WellKnownFiles.assetLinks(mapOf("com.example.app" to listOf(fp), "com.example.other" to listOf(fp))))
        val aasa = ok(WellKnownFiles.aasa(listOf("ABCDE12345.com.example.app", "ABCDE12345.com.example.tool")))

        val others = SharedRpId.otherApps(setOf("com.example.app"), setOf("ABCDE12345.com.example.app"), assetLinks, aasa)

        assertEquals(listOf("ABCDE12345.com.example.tool", "com.example.other"), others)
    }

    @Test
    fun onlyConfiguredAppsMeansNoOthers() {
        val assetLinks = ok(WellKnownFiles.assetLinks(mapOf("com.example.app" to listOf(fp))))
        assertEquals(emptyList(), SharedRpId.otherApps(setOf("com.example.app"), emptySet(), assetLinks, null))
    }

    @Test
    fun unreadableFilesCountAsNoApps() {
        val notFound = Fetched(404, "text/html", "Not Found", redirected = false)
        val page = ok("<!doctype html><html></html>")
        assertEquals(emptyList(), SharedRpId.otherApps(setOf("com.example.app"), emptySet(), notFound, page))
    }

    @Test
    fun warningExplainsWhyAndSuggestsASubdomain() {
        val text = SharedRpId.warning("example.com", listOf("com.example.other"))
        assertTrue("com.example.other" in text, text)
        assertTrue("orphans every passkey" in text, text)
        assertTrue("app.example.com" in text, text)
    }
}
