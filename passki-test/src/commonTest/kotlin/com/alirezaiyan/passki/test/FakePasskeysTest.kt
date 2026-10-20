package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.getOrThrow
import com.alirezaiyan.passki.internal.Base64Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakePasskeysTest {
    private val rpId = "passki.example"
    private val passkeys = FakePasskeys(origin = "https://$rpId")

    @Test
    fun registerThenSignInReturnsSameCredential() = runTest {
        val registration = passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        val assertion = passkeys.signIn(TestOptions.request(rpId)).getOrThrow()
        assertEquals(registration.credentialId, assertion.credentialId)
    }

    @Test
    fun signInWithoutPasskeyIsNoCredentials() = runTest {
        assertEquals(PasskeyResult.Failure(PasskeyError.NoCredentials), passkeys.signIn(TestOptions.request(rpId)))
    }

    @Test
    fun excludedCredentialIsAlreadyRegistered() = runTest {
        val first = passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        val second = passkeys.register(TestOptions.creation(rpId, excludeCredentialIds = listOf(first.credentialId)))
        assertEquals(PasskeyResult.Failure(PasskeyError.AlreadyRegistered), second)
    }

    @Test
    fun failNextWithInjectsOneError() = runTest {
        passkeys.failNextWith(PasskeyError.Cancelled)
        assertEquals(PasskeyResult.Failure(PasskeyError.Cancelled), passkeys.register(TestOptions.creation(rpId)))
        assertIs<PasskeyResult.Success<*>>(passkeys.register(TestOptions.creation(rpId)))
    }

    @Test
    fun unknownCredentialSignalRemovesPasskey() = runTest {
        val registration = passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        passkeys.signals.unknownCredential(rpId, registration.credentialId).getOrThrow()
        assertEquals(PasskeyResult.Failure(PasskeyError.NoCredentials), passkeys.signIn(TestOptions.request(rpId)))
        assertEquals(listOf<FakeSignal>(FakeSignal.UnknownCredential(rpId, registration.credentialId)), passkeys.receivedSignals)
    }

    @Test
    fun signInOrPasswordFallsBackToSavedPassword() = runTest {
        passkeys.savedPasswords["alice"] = "hunter2"
        val credential = assertIs<SignInCredential.Password>(passkeys.signInOrPassword(TestOptions.request(rpId)).getOrThrow())
        assertEquals("alice", credential.id)
        assertEquals("hunter2", credential.password)
    }

    @Test
    fun signInOrPasswordPrefersPasskey() = runTest {
        passkeys.savedPasswords["alice"] = "hunter2"
        passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        assertIs<SignInCredential.Passkey>(passkeys.signInOrPassword(TestOptions.request(rpId)).getOrThrow())
    }

    // --- capabilities ---

    private fun notSupported(result: PasskeyResult<*>, flag: String) {
        val failure = assertIs<PasskeyResult.Failure>(result)
        val error = assertIs<PasskeyError.NotSupported>(failure.error)
        assertTrue("(FakePasskeys: $flag = false)" in error.message, error.message)
    }

    @Test
    fun capabilitiesDefaultToAllSupported() = runTest {
        val capabilities = passkeys.capabilities()
        assertTrue(capabilities.isSupported)
        assertTrue(capabilities.immediateSignIn)
        assertTrue(capabilities.unifiedPasswordSignIn)
        assertTrue(capabilities.conditionalCreate)
        assertTrue(capabilities.signals)
    }

    @Test
    fun capabilitiesReflectFlags() = runTest {
        passkeys.immediateSignIn = false
        passkeys.signalsSupported = false
        val capabilities = passkeys.capabilities()
        assertTrue(capabilities.isSupported)
        assertFalse(capabilities.immediateSignIn)
        assertTrue(capabilities.unifiedPasswordSignIn)
        assertTrue(capabilities.conditionalCreate)
        assertFalse(capabilities.signals)
    }

    @Test
    fun isSupportedFalseFailsEveryOperation() = runTest {
        passkeys.isSupported = false
        assertFalse(passkeys.capabilities().isSupported)
        notSupported(passkeys.register(TestOptions.creation(rpId)), "isSupported")
        notSupported(passkeys.upgrade(TestOptions.creation(rpId)), "isSupported")
        notSupported(passkeys.signIn(TestOptions.request(rpId)), "isSupported")
        notSupported(passkeys.signIn(TestOptions.request(rpId), SignInMode.ImmediateOnly), "isSupported")
        notSupported(passkeys.signInOrPassword(TestOptions.request(rpId)), "isSupported")
        notSupported(passkeys.signals.unknownCredential(rpId, "AAAA"), "isSupported")
        notSupported(passkeys.signals.acceptedCredentials(rpId, "AAAA", emptyList()), "isSupported")
        notSupported(passkeys.signals.currentUserDetails(rpId, "AAAA", "a", "A"), "isSupported")
        assertTrue(passkeys.authenticator.storedCredentials.isEmpty())
    }

    @Test
    fun conditionalCreateFalseFailsUpgradeOnly() = runTest {
        passkeys.conditionalCreate = false
        notSupported(passkeys.upgrade(TestOptions.creation(rpId)), "conditionalCreate")
        assertIs<PasskeyResult.Success<*>>(passkeys.register(TestOptions.creation(rpId)))
    }

    @Test
    fun upgradeCreatesPasskeyByDefault() = runTest {
        val registration = passkeys.upgrade(TestOptions.creation(rpId)).getOrThrow()
        assertEquals(registration.credentialId, passkeys.authenticator.storedCredentials.single().id)
    }

    @Test
    fun unifiedPasswordSignInFalseFailsSignInOrPassword() = runTest {
        passkeys.unifiedPasswordSignIn = false
        passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        notSupported(passkeys.signInOrPassword(TestOptions.request(rpId)), "unifiedPasswordSignIn")
        assertIs<PasskeyResult.Success<*>>(passkeys.signIn(TestOptions.request(rpId)))
    }

    @Test
    fun immediateSignInFalseFailsImmediateModeOnly() = runTest {
        passkeys.immediateSignIn = false
        passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        notSupported(passkeys.signIn(TestOptions.request(rpId), SignInMode.ImmediateOnly), "immediateSignIn")
        assertIs<PasskeyResult.Success<*>>(passkeys.signIn(TestOptions.request(rpId), SignInMode.Modal))
    }

    @Test
    fun signalsSupportedFalseFailsEverySignal() = runTest {
        passkeys.signalsSupported = false
        val registration = passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        notSupported(passkeys.signals.unknownCredential(rpId, registration.credentialId), "signalsSupported")
        notSupported(passkeys.signals.acceptedCredentials(rpId, "AAAA", emptyList()), "signalsSupported")
        notSupported(passkeys.signals.currentUserDetails(rpId, "AAAA", "a", "A"), "signalsSupported")
        assertTrue(passkeys.receivedSignals.isEmpty())
        assertEquals(1, passkeys.authenticator.storedCredentials.size)
    }

    @Test
    fun queuedErrorTakesPrecedenceOverCapabilityFlags() = runTest {
        passkeys.isSupported = false
        passkeys.failNextWith(PasskeyError.Cancelled)
        assertEquals(PasskeyResult.Failure(PasskeyError.Cancelled), passkeys.register(TestOptions.creation(rpId)))
        notSupported(passkeys.register(TestOptions.creation(rpId)), "isSupported")
    }

    // --- signals ---

    /** Same bytes as [id], but standard alphabet with `=` padding. */
    private fun padded(id: String): String {
        val std = id.replace('-', '+').replace('_', '/')
        return std + "=".repeat((4 - std.length % 4) % 4)
    }

    private val userId = "AAECAwQFBgcICQoLDA0ODw" // 16 bytes

    @Test
    fun acceptedCredentialsComparesCanonicalIds() = runTest {
        val registration = passkeys.register(TestOptions.creation(rpId, userId = userId)).getOrThrow()
        passkeys.signals.acceptedCredentials(rpId, userId, listOf(padded(registration.credentialId))).getOrThrow()
        assertEquals(registration.credentialId, passkeys.authenticator.storedCredentials.single().id)
        passkeys.signals.acceptedCredentials(rpId, padded(userId), listOf(padded(registration.credentialId))).getOrThrow()
        assertEquals(registration.credentialId, passkeys.authenticator.storedCredentials.single().id)
        assertIs<PasskeyResult.Success<*>>(passkeys.signIn(TestOptions.request(rpId)))
    }

    @Test
    fun acceptedCredentialsRemovesOnlyThatUsersOtherPasskeys() = runTest {
        passkeys.register(TestOptions.creation(rpId, userId = userId)).getOrThrow()
        val bob = passkeys.register(TestOptions.creation(rpId, userId = "BBBB")).getOrThrow()
        passkeys.signals.acceptedCredentials(rpId, padded(userId), emptyList()).getOrThrow()
        assertEquals(bob.credentialId, passkeys.authenticator.storedCredentials.single().id)
    }

    @Test
    fun unknownCredentialWithPaddedIdRemovesPasskey() = runTest {
        val registration = passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        passkeys.signals.unknownCredential(rpId, padded(registration.credentialId)).getOrThrow()
        assertTrue(passkeys.authenticator.storedCredentials.isEmpty())
    }

    @Test
    fun unknownCredentialForOtherRpIdKeepsPasskey() = runTest {
        val registration = passkeys.register(TestOptions.creation(rpId)).getOrThrow()
        passkeys.signals.unknownCredential("other.example", registration.credentialId).getOrThrow()
        assertEquals(1, passkeys.authenticator.storedCredentials.size)
    }

    @Test
    fun malformedSignalIdsAreInvalidOptions() = runTest {
        val registration = passkeys.register(TestOptions.creation(rpId, userId = userId)).getOrThrow()
        val results = listOf(
            passkeys.signals.unknownCredential(rpId, "!!!"),
            passkeys.signals.acceptedCredentials(rpId, userId, listOf("!!!")),
            passkeys.signals.acceptedCredentials(rpId, "!!!", listOf(registration.credentialId)),
            passkeys.signals.currentUserDetails(rpId, "!!!", "bob", "Bob"),
        )
        results.forEach { assertIs<PasskeyError.InvalidOptions>(assertIs<PasskeyResult.Failure>(it).error) }
        assertEquals(1, passkeys.authenticator.storedCredentials.size)
        assertTrue(passkeys.receivedSignals.isEmpty())
    }

    @Test
    fun currentUserDetailsUpdatesUserName() = runTest {
        passkeys.register(TestOptions.creation(rpId, userId = userId, userName = "alice")).getOrThrow()
        passkeys.signals.currentUserDetails(rpId, padded(userId), "bob", "Bob").getOrThrow()
        assertEquals("bob", passkeys.authenticator.storedCredentials.single().userName)
        passkeys.signals.currentUserDetails("other.example", userId, "carol", "Carol").getOrThrow()
        assertEquals("bob", passkeys.authenticator.storedCredentials.single().userName)
    }

    // --- authenticator semantics ---

    @Test
    fun reRegisteringSameUserReplacesPasskey() = runTest {
        passkeys.register(TestOptions.creation(rpId, userId = userId)).getOrThrow()
        val second = passkeys.register(TestOptions.creation(rpId, userId = padded(userId))).getOrThrow()
        passkeys.register(TestOptions.creation(rpId, userId = "BBBB")).getOrThrow()
        val stored = passkeys.authenticator.storedCredentials
        assertEquals(2, stored.size)
        assertEquals(second.credentialId, stored.single { it.userId == userId }.id)
    }

    @Test
    fun concurrentRegistrationsAreAllStored() = runTest {
        // Distinct user ids, built up front: a shared user id would (correctly) replace the earlier passkey.
        val options = (0 until 64).map { i ->
            TestOptions.creation(rpId, userId = Base64Url.encode(byteArrayOf(i.toByte(), 0x7F)), userName = "user$i")
        }
        withContext(Dispatchers.Default) {
            options.map { async { passkeys.register(it).getOrThrow() } }.awaitAll()
        }
        assertEquals(64, passkeys.authenticator.storedCredentials.size)
    }

    // --- origin / rpId ---

    private val creationWithoutRpId =
        """{"rp":{"name":"x"},"user":{"id":"AAAA","name":"a","displayName":"A"},"challenge":"AAAA",""" +
            """"pubKeyCredParams":[{"type":"public-key","alg":-7}]}"""
    private val requestWithoutRpId = """{"challenge":"AAAA"}"""

    @Test
    fun nonHttpsOriginRequiresRpId() = runTest {
        val origin = "android:apk-key-hash:abc"
        val fake = FakePasskeys(origin = origin)
        val expected = PasskeyResult.Failure(
            PasskeyError.InvalidOptions("rpId is required when origin is not https (got $origin)"),
        )
        assertEquals(expected, fake.register(creationWithoutRpId))
        assertEquals(expected, fake.signIn(requestWithoutRpId))
    }

    @Test
    fun nonHttpsOriginWithExplicitRpIdWorks() = runTest {
        val fake = FakePasskeys(origin = "android:apk-key-hash:abc")
        fake.register(TestOptions.creation(rpId)).getOrThrow()
        fake.signIn(TestOptions.request(rpId)).getOrThrow()
    }

    @Test
    fun rpIdMustBeRegistrableSuffixOfOriginHost() = runTest {
        val expected = PasskeyResult.Failure(
            PasskeyError.Misconfigured(
                "RP ID other.example is not a registrable suffix of origin host passki.example",
                "Use an rpId equal to or a parent domain of the origin's host.",
            ),
        )
        assertEquals(expected, passkeys.register(TestOptions.creation("other.example")))
        assertEquals(expected, passkeys.signIn(TestOptions.request("other.example")))

        val lookalike = FakePasskeys(origin = "https://notpasski.example")
        assertIs<PasskeyError.Misconfigured>(
            assertIs<PasskeyResult.Failure>(lookalike.register(TestOptions.creation(rpId))).error,
        )
    }

    @Test
    fun subdomainOriginWithPortAcceptsParentRpId() = runTest {
        val fake = FakePasskeys(origin = "https://login.$rpId:8443")
        fake.register(TestOptions.creation(rpId)).getOrThrow()
        fake.signIn(TestOptions.request(rpId)).getOrThrow()
        assertEquals(rpId, fake.authenticator.storedCredentials.single().rpId)
    }

    @Test
    fun httpOriginWithoutRpIdDefaultsToHost() = runTest {
        val fake = FakePasskeys(origin = "http://localhost:8080")
        fake.register(creationWithoutRpId).getOrThrow()
        fake.signIn(requestWithoutRpId).getOrThrow()
        assertEquals("localhost", fake.authenticator.storedCredentials.single().rpId)
    }

    @Test
    fun ipv6OriginHostIsParsed() = runTest {
        val fake = FakePasskeys(origin = "http://[::1]:8080")
        fake.register(creationWithoutRpId).getOrThrow()
        assertEquals("::1", fake.authenticator.storedCredentials.single().rpId)
        fake.signIn(TestOptions.request("::1")).getOrThrow()
    }
}
