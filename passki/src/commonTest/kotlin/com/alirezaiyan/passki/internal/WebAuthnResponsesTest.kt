package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class WebAuthnResponsesTest {
    private val clientData = "{}".encodeToByteArray() // base64url "e30"

    private fun registrationJson() = WebAuthnResponses.registration(
        credentialId = byteArrayOf(1, 2, 3),
        clientDataJson = clientData,
        attestationObject = byteArrayOf(9),
        transports = listOf("internal"),
        attachment = "platform",
    )

    @Test
    fun registrationJsonHasWebAuthnShape() {
        val obj = PasskiJson.parseToJsonElement(registrationJson()).jsonObject
        assertEquals("AQID", obj["id"]!!.jsonPrimitive.content)
        assertEquals("AQID", obj["rawId"]!!.jsonPrimitive.content)
        assertEquals("public-key", obj["type"]!!.jsonPrimitive.content)
        assertEquals("platform", obj["authenticatorAttachment"]!!.jsonPrimitive.content)
        assertEquals(JsonObject(emptyMap()), obj["clientExtensionResults"])
        val response = obj["response"]!!.jsonObject
        assertEquals("e30", response["clientDataJSON"]!!.jsonPrimitive.content)
        assertEquals("CQ", response["attestationObject"]!!.jsonPrimitive.content)
        assertEquals(listOf("internal"), response["transports"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun authenticationJsonOmitsNulls() {
        val json = WebAuthnResponses.authentication(
            credentialId = byteArrayOf(1, 2, 3),
            clientDataJson = clientData,
            authenticatorData = byteArrayOf(1),
            signature = byteArrayOf(2),
            userHandle = null,
            attachment = null,
        )
        val obj = PasskiJson.parseToJsonElement(json).jsonObject
        assertFalse("authenticatorAttachment" in obj)
        assertFalse("userHandle" in obj["response"]!!.jsonObject)
    }

    @Test
    fun parseRegistrationExposesTypedFields() {
        val json = registrationJson()
        val registration = parseRegistration(json)
        assertEquals(json, registration.json)
        assertEquals("AQID", registration.credentialId)
        assertEquals(listOf("internal"), registration.transports)
        assertEquals("platform", registration.authenticatorAttachment)
    }

    @Test
    fun parseAssertionExposesUserHandle() {
        val json = WebAuthnResponses.authentication(
            credentialId = byteArrayOf(1, 2, 3),
            clientDataJson = clientData,
            authenticatorData = byteArrayOf(1),
            signature = byteArrayOf(2),
            userHandle = byteArrayOf(7),
            attachment = "platform",
        )
        val assertion = parseAssertion(json)
        assertEquals("AQID", assertion.credentialId)
        assertEquals("Bw", assertion.userHandle)
    }

    @Test
    fun malformedPlatformJsonBecomesUnknown() {
        val e = assertFailsWith<PasskeyException> { parseRegistration("""{"id":"x"}""") }
        assertIs<PasskeyError.Unknown>(e.error)
    }
}
