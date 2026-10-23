package com.alirezaiyan.passki.gradle.setup

import groovy.json.JsonSlurper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DemoRegistrationTest {
    private val fp = "59:E5:92:32:4E:6C:E6:FA:5F:E2:F0:8A:39:18:13:2D:A5:1D:DE:5D:F9:BD:11:F0:5C:E8:23:57:88:E2:06:C0"

    @Test
    fun oneRequestPerPackageAndPerAppleAppId() {
        val requests = DemoRegistration.requests(
            "https://demo.example/",
            mapOf("com.example.app" to listOf(fp), "com.example.app.debug" to listOf(fp)),
            listOf("ABCDE12345.com.example.app"),
        )

        assertEquals(
            listOf(
                "https://demo.example/api/demo/android-apps",
                "https://demo.example/api/demo/android-apps",
                "https://demo.example/api/demo/ios-apps",
            ),
            requests.map { it.url },
        )
        assertEquals(mapOf("packageName" to "com.example.app", "fingerprints" to listOf(fp)), JsonSlurper().parseText(requests[0].body))
        assertEquals(mapOf("teamId" to "ABCDE12345", "bundleId" to "com.example.app"), JsonSlurper().parseText(requests[2].body))
        assertEquals("Android com.example.app", requests[0].label)
        assertEquals("iOS ABCDE12345.com.example.app", requests[2].label)
    }

    @Test
    fun successNamesTheExpiry() {
        val outcome = DemoRegistration.outcome(201, """{"appId":"ABCDE12345.com.example.app","expiresAt":"2026-10-30T10:00:00.000Z"}""", null)
        assertTrue(outcome.ok)
        assertEquals("registered until 2026-10-30T10:00:00.000Z", outcome.message)
    }

    @Test
    fun alreadyRegisteredIsFine() {
        val android = DemoRegistration.outcome(200, """{"expiresAt":"2026-10-30T10:00:00.000Z","created":0}""", null)
        assertTrue(android.ok)
        assertEquals("already registered until 2026-10-30T10:00:00.000Z", android.message)

        val ios = DemoRegistration.outcome(409, """{"error":"ABCDE12345.com.example.app is already registered until 2026-10-30T10:00:00.000Z"}""", null)
        assertTrue(ios.ok)
        assertEquals("ABCDE12345.com.example.app is already registered until 2026-10-30T10:00:00.000Z", ios.message)
    }

    @Test
    fun errorsAreReportedWithTheServerMessage() {
        val limited = DemoRegistration.outcome(429, """{"error":"limit is 5 registrations per day"}""", null)
        assertFalse(limited.ok)
        assertEquals("HTTP 429: limit is 5 registrations per day", limited.message)

        val down = DemoRegistration.outcome(null, null, "Connection refused")
        assertFalse(down.ok)
        assertEquals("not sent: Connection refused", down.message)

        val html = DemoRegistration.outcome(502, "<html>Bad gateway</html>", null)
        assertEquals("HTTP 502: <html>Bad gateway</html>", html.message)
    }
}
