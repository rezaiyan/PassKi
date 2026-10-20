package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.internal.Base64Url
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Verifies real-device RegistrationResponseJSON fixtures (`fixtures/<platform>-<yyyymmdd>-registration.json`,
 * shaped `{"rpId": ..., "response": {...}}`) with webauthn4j. Origin and challenge come from the response's own
 * clientDataJSON. Fixtures are loaded from the test classpath (`src/jvmTest/resources/fixtures`).
 */
class RecordedFixturesTest {
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager()

    @Test
    fun recordedRegistrationsVerifyWithWebAuthn4j() {
        val dir = assertNotNull(
            javaClass.getResource("/fixtures"),
            "Fixture directory /fixtures not found on the test classpath (expected src/jvmTest/resources/fixtures).",
        )
        val files = File(dir.toURI())
            .listFiles { file -> file.name.endsWith("-registration.json") }
            .orEmpty()
            .sortedBy { it.name }
        files.forEach { file ->
            try {
                verify(file)
            } catch (e: Exception) {
                throw AssertionError("${file.name}: ${e.message}", e)
            } catch (e: AssertionError) {
                throw AssertionError("${file.name}: ${e.message}", e)
            }
        }
        println("Verified ${files.size} recorded registration fixture(s)")
    }

    private fun verify(file: File) {
        val fixture = Json.parseToJsonElement(file.readText()).jsonObject
        val rpId = fixture["rpId"]!!.jsonPrimitive.content
        val response = fixture["response"]!!.jsonObject
        val clientData = Json.parseToJsonElement(
            Base64Url.decode(response["response"]!!.jsonObject["clientDataJSON"]!!.jsonPrimitive.content)
                .decodeToString(),
        ).jsonObject
        val origin = clientData["origin"]!!.jsonPrimitive.content
        val challenge = Base64Url.decode(clientData["challenge"]!!.jsonPrimitive.content)

        val serverProperty = ServerProperty.builder()
            .origin(Origin(origin))
            .rpId(rpId)
            .challenge(DefaultChallenge(challenge))
            .build()
        val data = manager.parseRegistrationResponseJSON(response.toString())
        manager.verify(data, RegistrationParameters(serverProperty, null, false, true))
    }
}
