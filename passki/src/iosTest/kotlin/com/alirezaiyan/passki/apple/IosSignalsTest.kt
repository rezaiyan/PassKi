package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosSignalsTest {
    @Test
    fun okStatusIsSuccess() {
        assertEquals(PasskeyResult.Success(Unit), signalResult(SignalStatus.OK, null))
    }

    @Test
    fun notAvailableStatusIsNotSupported() {
        val error = assertIs<PasskeyResult.Failure>(signalResult(SignalStatus.NOT_AVAILABLE, "Requires iOS 26.")).error
        assertEquals(PasskeyError.NotSupported("Requires iOS 26."), error)
    }

    @Test
    fun errorStatusIsUnknownWithMessage() {
        val error = assertIs<PasskeyResult.Failure>(signalResult(SignalStatus.ERROR, "ASDomain 1: boom")).error
        assertIs<PasskeyError.Unknown>(error)
        assertEquals("ASDomain 1: boom", error.message)
    }

    @Test
    fun notAssociatedErrorIsMisconfiguredWithConcreteHint() {
        val message = "com.apple.AuthenticationServices.AuthorizationError 1004: Application with identifier " +
            "ABCDE12345.com.example.app is not associated with domain example.com"
        val error = assertIs<PasskeyResult.Failure>(signalResult(SignalStatus.ERROR, message)).error
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertEquals("App ABCDE12345.com.example.app is not associated with example.com.", misconfigured.message)
        assertEquals(concreteAasaHint("ABCDE12345.com.example.app", "example.com"), misconfigured.fixHint)
    }

    @Test
    fun error1004WithoutDetailsIsMisconfiguredWithGenericHint() {
        val message = "com.apple.AuthenticationServices.AuthorizationError 1004: The operation couldn’t be completed. " +
            "(com.apple.AuthenticationServices.AuthorizationError error 1004.)"
        val error = assertIs<PasskeyResult.Failure>(signalResult(SignalStatus.ERROR, message)).error
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertEquals(NOT_ASSOCIATED_SIGNAL_MESSAGE, misconfigured.message)
        assertEquals(AASA_HINT, misconfigured.fixHint)
    }

    @Test
    fun otherAuthorizationErrorStaysUnknown() {
        val message = "com.apple.AuthenticationServices.AuthorizationError 1000: failed"
        val error = assertIs<PasskeyResult.Failure>(signalResult(SignalStatus.ERROR, message)).error
        assertIs<PasskeyError.Unknown>(error)
    }

    @Test
    fun invalidBase64IsInvalidOptionsWithoutCallingTheBridge() = runTest {
        val result = IosSignals.unknownCredential("example.com", "not base64 !!")
        assertIs<PasskeyError.InvalidOptions>(assertIs<PasskeyResult.Failure>(result).error)
    }

    @Test
    fun bridgeCallsBackThroughTheLinkedSwiftLibrary() = runTest {
        // Exercises the real Swift static library: below iOS 26 it reports NOT_AVAILABLE, on 26+ the updater runs.
        val result = IosSignals.unknownCredential("example.com", "AAEC")
        println("PASSKI_BRIDGE_TEST $result")
        if (iosAtLeast(26)) {
            // A test runner has no associated domains, so the updater may succeed or fail (1004 → Misconfigured);
            // it must never be NotSupported.
            if (result is PasskeyResult.Failure) {
                assertTrue(result.error is PasskeyError.Misconfigured || result.error is PasskeyError.Unknown, "${result.error}")
            }
        } else {
            assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(result).error)
        }
    }
}
