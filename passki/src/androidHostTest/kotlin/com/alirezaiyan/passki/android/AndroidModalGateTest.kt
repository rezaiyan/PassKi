package com.alirezaiyan.passki.android

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.internal.ModalGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * While another modal request holds the gate, every modal call fails with Interrupted before it builds a Credential
 * Manager request (host tests have no real `android.os.Bundle`, so reaching that point would throw). The gate itself
 * (release, cancellation, a request after the first) is covered by ModalGateTest in commonTest.
 */
class AndroidModalGateTest {
    private val gate = ModalGate()
    private val passkeys = AndroidPasskeys(sdkInt = { 34 }, gate = gate)

    private fun assertInterrupted(result: PasskeyResult<*>) {
        val failure = assertIs<PasskeyResult.Failure>(result)
        assertEquals(PasskeyError.Interrupted, failure.error)
    }

    @Test
    fun everyModalCallFailsWhileAnotherRequestRuns() = runTest {
        val end = assertNotNull(gate.tryBegin())

        assertInterrupted(passkeys.register(CREATION))
        assertInterrupted(passkeys.upgrade(CREATION))
        assertInterrupted(passkeys.signIn(REQUEST, SignInMode.Modal))
        assertInterrupted(passkeys.signIn(REQUEST, SignInMode.ImmediateOnly))
        assertInterrupted(passkeys.signInOrPassword(REQUEST))
        end()
    }

    @Test
    fun invalidOptionsAreReportedEvenWhileAnotherRequestRuns() = runTest {
        val end = assertNotNull(gate.tryBegin())

        val failure = assertIs<PasskeyResult.Failure>(passkeys.register("""{"rp":{"name":"x"}}"""))
        assertIs<PasskeyError.InvalidOptions>(failure.error)
        end()
    }

    @Test
    fun oldAndroidIsNotSupportedBeforeTheGateIsChecked() = runTest {
        val end = assertNotNull(gate.tryBegin())

        val failure = assertIs<PasskeyResult.Failure>(AndroidPasskeys(sdkInt = { 27 }, gate = gate).register(CREATION))
        assertIs<PasskeyError.NotSupported>(failure.error)
        end()
    }

    private companion object {
        const val CREATION =
            """{"rp":{"id":"example.com","name":"Example"},"user":{"id":"AQID","name":"pat","displayName":"Pat"},""" +
                """"challenge":"AQID","pubKeyCredParams":[{"type":"public-key","alg":-7}]}"""
        const val REQUEST = """{"challenge":"AQID","rpId":"example.com"}"""
    }
}
