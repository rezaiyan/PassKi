package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.LargeBlobSupport
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyExtensions
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.getOrThrow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeExtensionsTest {
    private val rpId = "passki.example"
    private val passkeys = FakePasskeys(origin = "https://$rpId")
    private val saltA = "salt-a".encodeToByteArray()
    private val saltB = "salt-b".encodeToByteArray()

    private fun request(vararg allow: String) = TestOptions.request(rpId, allowCredentialIds = allow.toList())

    @Test
    fun registrationReportsPrfEnabledWhenRequested() = runTest {
        val r = passkeys.register(PasskeyExtensions.withPrf(TestOptions.creation(rpId))).getOrThrow()
        assertEquals(true, r.prfEnabled)
        assertNull(r.prf)
        assertNull(passkeys.register(TestOptions.creation(rpId)).getOrThrow().prfEnabled)
    }

    @Test
    fun registrationEvaluatesPrfWhenSaltsGiven() = runTest {
        val r = passkeys.register(PasskeyExtensions.withPrf(TestOptions.creation(rpId), saltA)).getOrThrow()
        val a = passkeys.signIn(PasskeyExtensions.withPrf(request(r.credentialId), saltA)).getOrThrow()
        assertEquals(a.prf, r.prf)
    }

    @Test
    fun prfIsDeterministicPerCredentialAndSalt() = runTest {
        val id = passkeys.register(TestOptions.creation(rpId)).getOrThrow().credentialId
        val first = passkeys.signIn(PasskeyExtensions.withPrf(request(id), saltA, saltB)).getOrThrow().prf!!
        val again = passkeys.signIn(PasskeyExtensions.withPrf(request(id), saltA, saltB)).getOrThrow().prf!!
        assertEquals(first, again)
        assertEquals(32, first.first.size)
        assertNotEquals(first.first.toList(), first.second!!.toList())

        val other = passkeys.register(TestOptions.creation(rpId)).getOrThrow().credentialId
        val otherPrf = passkeys.signIn(PasskeyExtensions.withPrf(request(other), saltA)).getOrThrow().prf!!
        assertNotEquals(first.first.toList(), otherPrf.first.toList())
    }

    @Test
    fun prfUsesEvalByCredentialForTheChosenPasskey() = runTest {
        val id = passkeys.register(TestOptions.creation(rpId)).getOrThrow().credentialId
        val viaEval = passkeys.signIn(PasskeyExtensions.withPrf(request(id), saltB)).getOrThrow().prf
        val json = request(id).dropLast(1) +
            ""","extensions":{"prf":{"eval":{"first":"AA"},"evalByCredential":{"$id":{"first":"c2FsdC1i"}}}}}"""
        assertEquals(viaEval, passkeys.signIn(json).getOrThrow().prf)
    }

    @Test
    fun prfOmittedWithoutRequestOrSupport() = runTest {
        val id = passkeys.register(TestOptions.creation(rpId)).getOrThrow().credentialId
        assertNull(passkeys.signIn(request(id)).getOrThrow().prf)
        passkeys.authenticator.prfSupported = false
        assertNull(passkeys.signIn(PasskeyExtensions.withPrf(request(id), saltA)).getOrThrow().prf)
        assertEquals(false, passkeys.register(PasskeyExtensions.withPrf(TestOptions.creation(rpId))).getOrThrow().prfEnabled)
    }

    @Test
    fun largeBlobWriteThenRead() = runTest {
        val r = passkeys.register(PasskeyExtensions.withLargeBlob(TestOptions.creation(rpId))).getOrThrow()
        assertEquals(true, r.largeBlobSupported)

        val read0 = passkeys.signIn(PasskeyExtensions.withLargeBlobRead(request(r.credentialId))).getOrThrow()
        assertNull(read0.largeBlob)
        assertNull(read0.largeBlobWritten)

        val write = passkeys.signIn(PasskeyExtensions.withLargeBlobWrite(request(r.credentialId), byteArrayOf(1, 2))).getOrThrow()
        assertEquals(true, write.largeBlobWritten)
        assertNull(write.largeBlob)
        assertContentEquals(byteArrayOf(1, 2), passkeys.authenticator.storedCredentials.single().largeBlob)

        val read = passkeys.signIn(PasskeyExtensions.withLargeBlobRead(request(r.credentialId))).getOrThrow()
        assertContentEquals(byteArrayOf(1, 2), read.largeBlob)
    }

    @Test
    fun largeBlobNeedsSupportFromRegistration() = runTest {
        val id = passkeys.register(TestOptions.creation(rpId)).getOrThrow().credentialId
        val write = passkeys.signIn(PasskeyExtensions.withLargeBlobWrite(request(id), byteArrayOf(1))).getOrThrow()
        assertEquals(false, write.largeBlobWritten)
        assertNull(passkeys.authenticator.storedCredentials.single().largeBlob)
    }

    @Test
    fun largeBlobUnsupportedAuthenticator() = runTest {
        passkeys.authenticator.largeBlobSupported = false
        val r = passkeys.register(PasskeyExtensions.withLargeBlob(TestOptions.creation(rpId))).getOrThrow()
        assertEquals(false, r.largeBlobSupported)
        val required = passkeys.register(PasskeyExtensions.withLargeBlob(TestOptions.creation(rpId), LargeBlobSupport.Required))
        assertIs<PasskeyError.NotSupported>((required as PasskeyResult.Failure).error)
    }

    @Test
    fun registrationJsonCarriesPrfOutputsButToStringHidesThem() = runTest {
        val r = passkeys.register(PasskeyExtensions.withPrf(TestOptions.creation(rpId), saltA)).getOrThrow()
        assertTrue(r.json.contains("\"clientExtensionResults\":{\"prf\":{\"enabled\":true,\"results\":{\"first\":"))
        assertFalse(r.toString().contains("prf"))
    }
}
