package com.alirezaiyan.passki.apple

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
}
