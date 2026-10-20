package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.getOrThrow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}
