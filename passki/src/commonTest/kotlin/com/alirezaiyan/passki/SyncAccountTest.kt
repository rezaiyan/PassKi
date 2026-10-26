package com.alirezaiyan.passki

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** `syncAccount`'s default implementation, as an adopter's own [PasskeySignals] (with no override) gets it. */
class SyncAccountTest {
    private class HandWrittenSignals(
        private val accepted: PasskeyResult<SignalOutcome>,
        private val details: PasskeyResult<SignalOutcome>,
    ) : PasskeySignals {
        val calls = mutableListOf<String>()

        override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<SignalOutcome> =
            error("not used")

        override suspend fun acceptedCredentials(
            rpId: String,
            userHandle: String,
            credentialIds: List<String>,
        ): PasskeyResult<SignalOutcome> {
            calls += "accepted $userHandle $credentialIds"
            return accepted
        }

        override suspend fun currentUserDetails(
            rpId: String,
            userHandle: String,
            name: String,
            displayName: String,
        ): PasskeyResult<SignalOutcome> {
            calls += "details $userHandle $name"
            return details
        }
    }

    private val applied = PasskeyResult.Success(SignalOutcome.Applied)
    private val skipped = PasskeyResult.Success(SignalOutcome.Skipped("provider can't"))

    private suspend fun HandWrittenSignals.sync() = syncAccount("example.com", "AQID", listOf("BAUG"), "pat", "Pat")

    @Test
    fun bothAppliedIsApplied() = runTest {
        val signals = HandWrittenSignals(applied, applied)
        assertEquals(applied, signals.sync())
        assertEquals(listOf("accepted AQID [BAUG]", "details AQID pat"), signals.calls, "accepted credentials first")
    }

    @Test
    fun oneSkippedIsStillApplied() = runTest {
        assertEquals(applied, HandWrittenSignals(applied, skipped).sync())
        assertEquals(applied, HandWrittenSignals(skipped, applied).sync())
    }

    @Test
    fun bothSkippedIsSkippedWithTheFirstReason() = runTest {
        val other = PasskeyResult.Success(SignalOutcome.Skipped("other reason"))
        assertEquals(skipped, HandWrittenSignals(skipped, other).sync())
    }

    @Test
    fun aFailureStopsAtTheFirstSignal() = runTest {
        val failure = PasskeyResult.Failure(PasskeyError.Misconfigured("rejected", "fix it", "AND-03"))
        val signals = HandWrittenSignals(failure, applied)

        assertIs<PasskeyResult.Failure>(signals.sync())
        assertEquals(1, signals.calls.size, "the user-details signal isn't sent after a failure")
    }

    @Test
    fun aFailureOfTheSecondSignalIsReturned() = runTest {
        val failure = PasskeyResult.Failure(PasskeyError.InvalidOptions("userHandle must be base64url"))
        assertEquals(failure, HandWrittenSignals(applied, failure).sync())
    }
}
