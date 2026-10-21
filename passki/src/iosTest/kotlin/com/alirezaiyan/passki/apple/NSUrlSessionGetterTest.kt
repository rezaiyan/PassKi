package com.alirezaiyan.passki.apple

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class NSUrlSessionGetterTest {
    /** `.invalid` never resolves (RFC 2606), so this exercises the session bridge without real network traffic. */
    @Test
    fun unresolvableHostBecomesTransportError() = runTest(timeout = 30.seconds) {
        val url = "https://passki-doctor.invalid/.well-known/apple-app-site-association"
        val result = NSUrlSessionGetter.get(url)

        assertEquals(url, result.url)
        assertEquals(-1, result.status)
        assertNotNull(result.error)
    }

    @Test
    fun redirectLocationIsReportedFor3xx() {
        assertEquals("https://b.com/x", redirectTarget("https://a.com/x", 301, location = "https://b.com/x", finalUrl = "https://a.com/x"))
    }

    @Test
    fun followedRedirectIsReportedViaFinalUrl() {
        assertEquals(
            "https://www.example.com/.well-known/apple-app-site-association",
            redirectTarget(
                "https://example.com/.well-known/apple-app-site-association",
                200,
                location = null,
                finalUrl = "https://www.example.com/.well-known/apple-app-site-association",
            ),
        )
    }

    @Test
    fun sameFinalUrlIsNoRedirect() {
        assertNull(redirectTarget("https://a.com/x", 200, location = null, finalUrl = "https://a.com/x"))
        assertNull(redirectTarget("https://a.com/x", 200, location = null, finalUrl = null))
    }
}
