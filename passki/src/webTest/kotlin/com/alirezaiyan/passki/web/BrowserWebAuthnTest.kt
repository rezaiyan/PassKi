package com.alirezaiyan.passki.web

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The JS glue in a real (headless) browser: no authenticator is needed for these calls. */
class BrowserWebAuthnTest {
    @Test
    fun capabilitiesComeBackAsJson() = runTest {
        val caps = Json.parseToJsonElement(BrowserWebAuthn.call("capabilities", "{}", null)).jsonObject

        assertTrue(caps.getValue("webauthn").jsonPrimitive.boolean, caps.toString())
    }

    @Test
    fun unknownOperationFailsWithTheBrowsersErrorName() = runTest {
        val e = assertFailsWith<WebAuthnException> { BrowserWebAuthn.call("noSuchMethod", "{}", null) }

        assertEquals("NotSupportedError", e.name)
    }
}
