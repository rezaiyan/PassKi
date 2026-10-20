package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.internal.Base64Url
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class WebAuthn4jVerificationTest {
    private val origin = "https://passki.example"
    private val rpId = "passki.example"
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager()

    private fun serverProperty(challenge: ByteArray) =
        ServerProperty.builder().origin(Origin(origin)).rpId(rpId).challenge(DefaultChallenge(challenge)).build()

    @Test
    fun registrationAndAssertionVerifyWithRealServerLibrary() = runTest {
        val authenticator = FakeAuthenticator(origin)

        val registrationChallenge = Random.nextBytes(32)
        val registrationJson = authenticator.register(
            TestOptions.creation(rpId = rpId, challenge = Base64Url.encode(registrationChallenge)),
        )
        val registration = manager.parseRegistrationResponseJSON(registrationJson)
        manager.verify(
            registration,
            RegistrationParameters(serverProperty(registrationChallenge), null, true, true),
        )

        val record = CredentialRecordImpl(
            registration.attestationObject!!,
            registration.collectedClientData,
            registration.clientExtensions,
            registration.transports,
        )

        val assertionChallenge = Random.nextBytes(32)
        val assertionJson = authenticator.signIn(
            TestOptions.request(rpId = rpId, challenge = Base64Url.encode(assertionChallenge)),
        )
        val assertion = manager.parseAuthenticationResponseJSON(assertionJson)
        manager.verify(
            assertion,
            AuthenticationParameters(serverProperty(assertionChallenge), record, null, true, true),
        )
        assertEquals(1L, assertion.authenticatorData!!.signCount)
    }
}
