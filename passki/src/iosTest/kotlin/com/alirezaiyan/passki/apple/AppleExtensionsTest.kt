package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.LargeBlobSupport
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyExtensions
import com.alirezaiyan.passki.internal.ExtensionsJson
import com.alirezaiyan.passki.internal.WebAuthnOptions
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertionRequest
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement
import platform.Foundation.NSData
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleExtensionsTest {
    private val creation =
        """{"rp":{"id":"example.com","name":"Ex"},"user":{"id":"AQ","name":"a"},"challenge":"AQID"}"""
    private val request = """{"challenge":"AQID","rpId":"example.com"}"""

    private fun registration(json: String) = AppleRequests.registration(WebAuthnOptions.parseCreation(json).model, false)

    private fun assertion(json: String): ASAuthorizationPlatformPublicKeyCredentialAssertionRequest =
        AppleRequests.assertion(WebAuthnOptions.parseRequest(json).model)

    @Test
    fun missingOrMalformedRpIdIsInvalidOptions() {
        val noRpId = assertFailsWith<PasskeyException> { assertion("""{"challenge":"AQID"}""") }
        assertTrue("rpId is required" in assertIs<PasskeyError.InvalidOptions>(noRpId.error).message)
        val noRpIdOnCreation = assertFailsWith<PasskeyException> {
            registration("""{"rp":{"name":"Ex"},"user":{"id":"AQ","name":"a"},"challenge":"AQID"}""")
        }
        assertTrue("rp.id is required" in assertIs<PasskeyError.InvalidOptions>(noRpIdOnCreation.error).message)
        val withScheme = assertFailsWith<PasskeyException> {
            assertion("""{"challenge":"AQID","rpId":"https://example.com"}""")
        }
        assertTrue("bare domain" in assertIs<PasskeyError.InvalidOptions>(withScheme.error).message)
    }

    @Test
    fun noExtensionsLeaveRequestsUntouched() {
        if (iosAtLeast(18)) {
            assertNull(registration(creation).prf)
            assertNull(assertion(request).prf)
        }
        if (iosAtLeast(17)) {
            assertNull(registration(creation).largeBlob)
            assertNull(assertion(request).largeBlob)
        }
    }

    @Test
    fun registrationPrfChecksSupportWithoutSalts() {
        if (!iosAtLeast(18)) return
        val prf = assertNotNull(registration(PasskeyExtensions.withPrf(creation)).prf)
        assertTrue(prf.shouldCheckForSupport)
        assertNull(prf.inputValues)
    }

    @Test
    fun registrationPrfPassesSalts() {
        if (!iosAtLeast(18)) return
        // The request's `prf` getter drops inputValues, so check the mapped input itself...
        val ext = WebAuthnOptions.parseCreation(PasskeyExtensions.withPrf(creation, byteArrayOf(1), byteArrayOf(2)))
            .model.extensions
        val prf = AppleRequests.prfRegistrationInput(ExtensionsJson.prfInput(ext)!!)
        assertTrue(prf.shouldCheckForSupport) // iOS also reports support when evaluating at registration
        assertContentEquals(byteArrayOf(1), prf.inputValues!!.saltInput1.toByteArray())
        assertContentEquals(byteArrayOf(2), prf.inputValues!!.saltInput2!!.toByteArray())
        // ...and that the request carries one.
        assertNotNull(registration(PasskeyExtensions.withPrf(creation, byteArrayOf(1))).prf)
    }

    @Test
    fun registrationLargeBlobMapsSupportRequirement() {
        if (!iosAtLeast(17)) return
        assertEquals(
            ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirementRequired,
            registration(PasskeyExtensions.withLargeBlob(creation, LargeBlobSupport.Required)).largeBlob!!.supportRequirement,
        )
        assertEquals(
            ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirementPreferred,
            registration(PasskeyExtensions.withLargeBlob(creation)).largeBlob!!.supportRequirement,
        )
    }

    @Test
    fun assertionPrfPassesEvalAndEvalByCredential() {
        if (!iosAtLeast(18)) return
        val json = """{"challenge":"AQID","rpId":"example.com",""" +
            """"extensions":{"prf":{"eval":{"first":"AQ"},"evalByCredential":{"Bw":{"first":"Ag","second":"Aw"}}}}}"""
        val prf = assertNotNull(assertion(json).prf)
        assertContentEquals(byteArrayOf(1), prf.inputValues!!.saltInput1.toByteArray())
        assertNull(prf.inputValues!!.saltInput2)
        val perCredential = assertNotNull(prf.perCredentialInputValues)
        assertEquals(1, perCredential.size)
        val (key, values) = perCredential.entries.single()
        assertContentEquals(byteArrayOf(7), (key as NSData).toByteArray())
        val salts = values as platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFAssertionInputValues
        assertContentEquals(byteArrayOf(3), salts.saltInput2!!.toByteArray())
    }

    @Test
    fun assertionLargeBlobReadAndWrite() {
        if (!iosAtLeast(17)) return
        val read = assertNotNull(assertion(PasskeyExtensions.withLargeBlobRead(request)).largeBlob)
        assertEquals(
            ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationRead,
            read.operation,
        )
        val write = assertNotNull(assertion(PasskeyExtensions.withLargeBlobWrite(request, byteArrayOf(4, 5))).largeBlob)
        assertEquals(
            ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationWrite,
            write.operation,
        )
        assertContentEquals(byteArrayOf(4, 5), write.dataToWrite!!.toByteArray())
    }
}
