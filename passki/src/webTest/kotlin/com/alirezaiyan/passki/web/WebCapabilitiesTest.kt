package com.alirezaiyan.passki.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebCapabilitiesTest {
    @Test
    fun unsupportedBrowserSupportsNothing() {
        val caps = WebCapabilities.parse("""{"supported":false,"client":{"conditionalGet":true}}""")
        assertFalse(caps.isSupported)
        assertFalse(caps.autofill)
        assertFalse(caps.signals)
    }

    @Test
    fun malformedSnapshotIsUnsupported() {
        assertFalse(WebCapabilities.parse("not json").isSupported)
    }

    @Test
    fun clientCapabilitiesWin() {
        val caps = WebCapabilities.parse(
            """{"supported":true,"conditionalMediation":true,
               "signalUnknownCredential":true,"signalAllAcceptedCredentials":true,"signalCurrentUserDetails":true,
               "immediateMediation":true,"client":{"conditionalGet":false,"conditionalCreate":true}}""",
        )
        assertTrue(caps.isSupported)
        assertFalse(caps.autofill)
        assertTrue(caps.conditionalCreate)
        assertTrue(caps.immediateSignIn)
        assertTrue(caps.signals)
        assertFalse(caps.unifiedPasswordSignIn)
    }

    @Test
    fun immediateSignInNeedsTheProbeNotJustTheClientFlag() {
        val caps = WebCapabilities.parse("""{"supported":true,"immediateMediation":false,"client":{"immediateGet":true}}""")
        assertFalse(caps.immediateSignIn)
    }

    @Test
    fun olderBrowsersFallBack() {
        val caps = WebCapabilities.parse("""{"supported":true,"conditionalMediation":true,"signalUnknownCredential":true,"client":{}}""")
        assertTrue(caps.autofill)
        assertFalse(caps.conditionalCreate)
        assertFalse(caps.immediateSignIn)
        assertEquals(false, caps.signals)
    }
}
