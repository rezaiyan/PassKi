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
    }

    @Test
    fun everyOperationFailsWithNotSupported() = runTest {
        listOf(
            passkeys.register("{}"),
            passkeys.signIn("{}"),
            passkeys.signInOrPassword("{}"),
            passkeys.upgrade("{}"),
            passkeys.signals.unknownCredential("example.com", "AQID"),
        ).forEach { result ->
            val failure = assertIs<PasskeyResult.Failure>(result)
            assertIs<PasskeyError.NotSupported>(failure.error)
        }
    }
}
