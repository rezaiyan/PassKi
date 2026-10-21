package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
            // A test runner has no associated domains, so the updater may succeed or fail — it must never be NotSupported.
            if (result is PasskeyResult.Failure) assertIs<PasskeyError.Unknown>(result.error)
        } else {
            assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(result).error)
        }
    }
}
