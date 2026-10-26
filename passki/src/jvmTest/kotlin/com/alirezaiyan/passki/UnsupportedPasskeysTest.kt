package com.alirezaiyan.passki

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UnsupportedPasskeysTest {
    private val passkeys = Passkeys()

    @Test
    fun capabilitiesReportUnsupported() = runTest {
        assertFalse(passkeys.capabilities().isSupported)
        assertFalse(passkeys.capabilities().autofill)
    }

    @Test
    fun everyCeremonyFailsWithNotSupported() = runTest {
        listOf(
            passkeys.register("{}"),
            passkeys.signIn("{}"),
            passkeys.signIn("{}", SignInMode.Autofill),
            passkeys.signInOrPassword("{}"),
            passkeys.upgrade("{}"),
        ).forEach { result ->
            val failure = assertIs<PasskeyResult.Failure>(result)
            assertIs<PasskeyError.NotSupported>(failure.error)
        }
    }

    @Test
    fun everySignalIsSkippedNamingThePlatform() = runTest {
        listOf(
            passkeys.signals.unknownCredential("example.com", "AQID"),
            passkeys.signals.acceptedCredentials("example.com", "AQID", listOf("AQID")),
            passkeys.signals.currentUserDetails("example.com", "AQID", "a", "A"),
            passkeys.signals.syncAccount("example.com", "AQID", listOf("AQID"), "a", "A"),
        ).forEach { result ->
            val skipped = assertIs<SignalOutcome.Skipped>(assertIs<PasskeyResult.Success<SignalOutcome>>(result).value)
            assertTrue("JVM desktop" in skipped.reason, skipped.reason)
        }
    }
}
