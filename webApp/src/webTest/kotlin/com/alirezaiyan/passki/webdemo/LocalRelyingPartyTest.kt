package com.alirezaiyan.passki.webdemo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalRelyingPartyTest {
    private val rp = LocalRelyingParty(rpId = "localhost", origin = "http://localhost:8080", random = Random(1))
    private val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

    /** A response as the browser would build it, for [challenge] from [origin]. */
    private fun response(
        type: String,
        challenge: String,
        origin: String = "http://localhost:8080",
        id: String = "Y3JlZA",
        userHandle: String? = null,
        flags: Int = 0x05,
    ): String {
        val clientData = codec.encode("""{"type":"$type","challenge":"$challenge","origin":"$origin"}""".encodeToByteArray())
        val authData = codec.encode(ByteArray(32) + byteArrayOf(flags.toByte(), 0, 0, 0, 7))
        val handle = userHandle?.let { ""","userHandle":"$it"""" }.orEmpty()
        return """{"id":"$id","rawId":"$id","type":"public-key","response":{"clientDataJSON":"$clientData","authenticatorData":"$authData"$handle}}"""
    }

    @Test
    fun registrationOptionsUseThePageDomainAndTheNameAsUserId() {
        val options = obj(rp.registrationOptions(" alice "))
        assertEquals("localhost", options.getValue("rp").jsonObject.str("id"))
        val user = options.getValue("user").jsonObject
        assertEquals("alice", user.str("name"))
        assertEquals("alice", rp.decodeName(user.str("id")))
        assertEquals(JsonArray(emptyList()), options["excludeCredentials"])
    }

    @Test
    fun aGoodRegistrationIsRememberedAndExcludedNextTime() {
        val challenge = obj(rp.registrationOptions("alice")).str("challenge")
        val report = rp.readRegistration(response("webauthn.create", challenge))
        assertTrue(report.ok, report.toString())
        assertTrue("sign count: 7" in report.toString())
        assertEquals(listOf("alice"), rp.credentials.map { it.username })

        val again = obj(rp.registrationOptions("alice"))
        assertEquals("Y3JlZA", (again.getValue("excludeCredentials") as JsonArray).single().jsonObject.str("id"))
        assertEquals(JsonArray(emptyList()), obj(rp.registrationOptions("bob"))["excludeCredentials"])
    }

    @Test
    fun wrongChallengeOriginOrTypeFails() {
        obj(rp.registrationOptions("alice"))
        assertFalse(rp.readRegistration(response("webauthn.create", "b3RoZXI")).ok)
        val challenge = obj(rp.registrationOptions("alice")).str("challenge")
        assertFalse(rp.readRegistration(response("webauthn.create", challenge, origin = "https://evil.example")).ok)
        val getChallenge = obj(rp.authenticationOptions()).str("challenge")
        assertFalse(rp.readRegistration(response("webauthn.get", getChallenge)).ok)
        assertTrue(rp.credentials.isEmpty())
    }

    @Test
    fun aChallengeIsGoodForOneResponse() {
        val challenge = obj(rp.authenticationOptions()).str("challenge")
        assertTrue(rp.readAssertion(response("webauthn.get", challenge)).ok)
        assertFalse(rp.readAssertion(response("webauthn.get", challenge)).ok)
    }

    @Test
    fun assertionNamesTheUserAndFlagsMissingPresence() {
        val challenge = obj(rp.authenticationOptions()).str("challenge")
        val handle = codec.encode("alice".encodeToByteArray())
        val report = rp.readAssertion(response("webauthn.get", challenge, userHandle = handle, flags = 0x00))
        assertTrue("user: alice" in report.toString())
        assertTrue("created before this page loaded" in report.toString())
        assertFalse(report.ok, "UP=0 must fail")
    }

    @Test
    fun garbageIsReportedNotThrown() {
        assertFalse(rp.readAssertion("not json").ok)
    }
}
