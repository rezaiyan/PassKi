package com.alirezaiyan.passki

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

class UnsupportedPasskeysTest {
    private val passkeys = Passkeys()

    @Test
    fun capabilitiesReportUnsupported() = runTest {
        assertFalse(passkeys.capabilities().isSupported)
        assertFalse(passkeys.capabilities().autofill)
    }

    @Test
    fun everyOperationFailsWithNotSupported() = runTest {
        listOf(
            passkeys.register("{}"),
            passkeys.signIn("{}"),
            passkeys.signIn("{}", SignInMode.Autofill),
            passkeys.signInOrPassword("{}"),
            passkeys.upgrade("{}"),
            passkeys.signals.unknownCredential("example.com", "AQID"),
            passkeys.signals.acceptedCredentials("example.com", "AQID", listOf("AQID")),
            passkeys.signals.currentUserDetails("example.com", "AQID", "a", "A"),
        ).forEach { result ->
            val failure = assertIs<PasskeyResult.Failure>(result)
            assertIs<PasskeyError.NotSupported>(failure.error)
        }
    }
}
