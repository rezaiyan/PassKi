package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PasskeyExtensions
import com.alirezaiyan.passki.getOrThrow
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
import kotlin.test.assertFails

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

    @Test
    fun responsesWithPrfAndLargeBlobOutputsVerify() = runTest {
        val authenticator = FakeAuthenticator(origin)
        val regChallenge = Random.nextBytes(32)
        val creation = PasskeyExtensions.withLargeBlob(
            PasskeyExtensions.withPrf(TestOptions.creation(rpId, challenge = Base64Url.encode(regChallenge)), byteArrayOf(1)),
        )
        val registration = manager.parseRegistrationResponseJSON(authenticator.register(creation))
        manager.verify(registration, RegistrationParameters(serverProperty(regChallenge), null, true, true))

        val assertChallenge = Random.nextBytes(32)
        val request = PasskeyExtensions.withLargeBlobWrite(
            PasskeyExtensions.withPrf(TestOptions.request(rpId, challenge = Base64Url.encode(assertChallenge)), byteArrayOf(1)),
            byteArrayOf(9),
        )
        val assertion = manager.parseAuthenticationResponseJSON(authenticator.signIn(request))
        val record = CredentialRecordImpl(
            registration.attestationObject!!,
            registration.collectedClientData,
            registration.clientExtensions,
            registration.transports,
        )
        manager.verify(assertion, AuthenticationParameters(serverProperty(assertChallenge), record, null, true, true))
    }

    private fun recordOf(registrationJson: String) = manager.parseRegistrationResponseJSON(registrationJson).let {
        CredentialRecordImpl(it.attestationObject!!, it.collectedClientData, it.clientExtensions, it.transports)
    }

    @Test
    fun upgradeEmitsConditionalCreateFlags() = runTest {
        val passkeys = FakePasskeys(origin)
        val challenge = Random.nextBytes(32)
        val json = passkeys.upgrade(TestOptions.creation(rpId = rpId, challenge = Base64Url.encode(challenge)))
            .getOrThrow().json
        val registration = manager.parseRegistrationResponseJSON(json)
        // BE | BS | AT, no UP, no UV.
        assertEquals(0x58.toByte(), registration.attestationObject!!.authenticatorData.flags)

        // Servers must accept conditional create with UP and UV not required ...
        manager.verify(registration, RegistrationParameters(serverProperty(challenge), null, false, false))
        // ... a server still requiring user presence rejects it.
        assertFails {
            manager.verify(
                manager.parseRegistrationResponseJSON(json),
                RegistrationParameters(serverProperty(challenge), null, false, true),
            )
        }
    }

    @Test
    fun conditionalRegisterOnAuthenticatorUsesConditionalFlags() = runTest {
        val json = FakeAuthenticator(origin).register(TestOptions.creation(rpId), conditional = true)
        assertEquals(0x58.toByte(), manager.parseRegistrationResponseJSON(json).attestationObject!!.authenticatorData.flags)
    }

    @Test
    fun allowCredentialsPicksAllowedCredentialAndCounterAdvances() = runTest {
        val authenticator = FakeAuthenticator(origin)
        val firstJson = authenticator.register(TestOptions.creation(rpId = rpId, userId = "AAAA"))
        authenticator.register(TestOptions.creation(rpId = rpId, userId = "BBBB"))
        val first = manager.parseRegistrationResponseJSON(firstJson)
        val firstId = Base64Url.encode(first.attestationObject!!.authenticatorData.attestedCredentialData!!.credentialId)
        val record = recordOf(firstJson)

        repeat(2) { index ->
            val challenge = Random.nextBytes(32)
            val assertionJson = authenticator.signIn(
                TestOptions.request(rpId = rpId, challenge = Base64Url.encode(challenge), allowCredentialIds = listOf(firstId)),
            )
            val assertion = manager.parseAuthenticationResponseJSON(assertionJson)
            assertEquals(firstId, Base64Url.encode(assertion.credentialId))
            manager.verify(assertion, AuthenticationParameters(serverProperty(challenge), record, null, true, true))
            val signCount = assertion.authenticatorData!!.signCount
            assertEquals(index + 1L, signCount)
            record.counter = signCount
        }
    }
}
