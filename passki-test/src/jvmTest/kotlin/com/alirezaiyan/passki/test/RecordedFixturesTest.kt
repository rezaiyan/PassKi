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

/**
 * Verifies real-device RegistrationResponseJSON fixtures (`fixtures/<platform>-<yyyymmdd>-registration.json`,
 * shaped `{"rpId": ..., "response": {...}}`) with webauthn4j. Origin and challenge come from the response's own
 * clientDataJSON.
 */
class RecordedFixturesTest {
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager()

    @Test
    fun recordedRegistrationsVerifyWithWebAuthn4j() {
        val files = File("src/jvmTest/resources/fixtures")
            .listFiles { file -> file.name.endsWith("-registration.json") }
            .orEmpty()
        files.forEach { file ->
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
        println("Verified ${files.size} recorded registration fixture(s)")
    }
}
