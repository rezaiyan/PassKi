package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PassKi
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskiLogHandler
import com.alirezaiyan.passki.PasskiLogLevel
import com.alirezaiyan.passki.SignInCredential
import com.alirezaiyan.passki.getOrThrow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakePasskeysLoggingTest {
    private val rpId = "passki.example"
    private val passkeys = FakePasskeys(origin = "https://$rpId")
    private val lines = mutableListOf<Pair<PasskiLogLevel, String>>()

    @BeforeTest
    fun captureLogs() {
        PassKi.logHandler = PasskiLogHandler { level, _, message, _ -> lines += level to message }
    }

    @AfterTest
    fun resetPassKi() {
        PassKi.logLevel = PasskiLogLevel.Error
        PassKi.logHandler = PasskiLogHandler.Platform
    }

    @Test
    fun aMisconfiguredSignInIsLoggedAtTheDefaultLevel() = runTest {
        passkeys.failNextWith(PasskeyError.Misconfigured("Digital Asset Links don't list this app.", "Add the app.", "AND-03"))

        passkeys.signIn(TestOptions.request(rpId))

        val (level, message) = lines.single()
        assertEquals(PasskiLogLevel.Error, level)
        assertTrue(message.startsWith("signIn failed: Misconfigured [AND-03]"), message)
        assertTrue("https://passki.alirezaiyan.com/checks/AND-03" in message, message)
    }

    @Test
    fun cancelledAndNoCredentialsAreNotLoggedAtTheDefaultLevel() = runTest {
        passkeys.failNextWith(PasskeyError.Cancelled)
        passkeys.signIn(TestOptions.request(rpId))
        passkeys.signIn(TestOptions.request(rpId)) // NoCredentials: nothing registered

        assertEquals(emptyList(), lines)
    }
}

/**
 * No secrets in logs: a full register + sign-in trace at Debug never contains the challenge, the user handle or any
 * options or response JSON, and a password sign-in never contains the password. The security model page points here.
 */
class PasskiLogRedactionTest {
    private val rpId = "passki.example"
    private val passkeys = FakePasskeys(origin = "https://$rpId")
    private val output = StringBuilder()

    @BeforeTest
    fun traceEverything() {
        PassKi.logLevel = PasskiLogLevel.Debug
        PassKi.logHandler = PasskiLogHandler { _, tag, message, throwable ->
            output.append(tag).append(' ').append(message).append('\n')
            throwable?.let { output.append(it.stackTraceToString()).append('\n') }
        }
    }

    @AfterTest
    fun resetPassKi() {
        PassKi.logLevel = PasskiLogLevel.Error
        PassKi.logHandler = PasskiLogHandler.Platform
    }

    @Test
    fun aDebugTraceOfRegisterAndSignInHoldsNoSecrets() = runTest {
        val creation = TestOptions.creation(rpId, challenge = CREATE_CHALLENGE, userId = USER_HANDLE)
        val request = TestOptions.request(rpId, challenge = GET_CHALLENGE)

        val registration = passkeys.register(creation).getOrThrow()
        val assertion = passkeys.signIn(request).getOrThrow()
        passkeys.signals.syncAccount(rpId, USER_HANDLE, listOf(registration.credentialId), "alice", "Alice")
        passkeys.register("""{"challenge":"$GET_CHALLENGE","rp":{"id":"$rpId"},}""") // invalid JSON, logged at Error

        val log = output.toString()
        for (event in listOf("register started", "signIn succeeded", "signals.acceptedCredentials succeeded")) {
            assertTrue(event in log, log)
        }
        assertTrue("register failed: InvalidOptions" in log, log)
        for (secret in listOf(CREATE_CHALLENGE, GET_CHALLENGE, USER_HANDLE)) {
            assertFalse(secret in log, "the log contains $secret:\n$log")
        }
        for (json in listOf(creation, request, registration.json, assertion.json)) {
            assertFalse(json in log, "the log contains JSON:\n$log")
        }
        assertFalse(registration.credentialId in log, "full credential ID in the log:\n$log")
        assertTrue(registration.credentialId.take(8) + "…" in log, log)
    }

    @Test
    fun aPasswordSignInLogsTheUsernameNeverThePassword() = runTest {
        passkeys.savedPasswords["alice@example.com"] = "correct horse battery staple"

        val credential = passkeys.signInOrPassword(TestOptions.request(rpId)).getOrThrow()

        assertIs<SignInCredential.Password>(credential)
        val log = output.toString()
        assertTrue("signInOrPassword succeeded: password for alice@example.com" in log, log)
        assertFalse("correct horse" in log, log)
    }

    private companion object {
        const val CREATE_CHALLENGE = "Y3JlYXRlLWNoYWxsZW5nZS1zZWNyZXQ"
        const val GET_CHALLENGE = "Z2V0LWNoYWxsZW5nZS1zZWNyZXQ"
        const val USER_HANDLE = "dXNlci1oYW5kbGUtc2VjcmV0"
    }
}
