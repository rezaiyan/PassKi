package com.alirezaiyan.passki.apple

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorizationArbiterTest {
    @Test
    fun autoFillStartsAtOnceWhenNoModalRuns() = runTest {
        val arbiter = AuthorizationArbiter()
        val wait = async { arbiter.awaitNoModal() }
        runCurrent()
        assertTrue(wait.isCompleted)
    }

    @Test
    fun autoFillWaitsUntilTheModalRequestEnds() = runTest {
        val arbiter = AuthorizationArbiter()
        val endModal = arbiter.beginModal()
        val wait = async { arbiter.awaitNoModal() }
        runCurrent()
        assertFalse(wait.isCompleted)

        endModal()
        runCurrent()
        assertTrue(wait.isCompleted)
    }

    @Test
    fun autoFillWaitsForEveryModalRequest() = runTest {
        val arbiter = AuthorizationArbiter()
        val endFirst = arbiter.beginModal()
        val endSecond = arbiter.beginModal()
        val wait = async { arbiter.awaitNoModal() }

        endFirst()
        runCurrent()
        assertFalse(wait.isCompleted)

        endSecond()
        runCurrent()
        assertTrue(wait.isCompleted)
    }

    @Test
    fun endingAModalRequestTwiceCountsOnce() = runTest {
        val arbiter = AuthorizationArbiter()
        val endFirst = arbiter.beginModal()
        arbiter.beginModal()
        endFirst()
        endFirst()
        val wait = async { arbiter.awaitNoModal() }
        runCurrent()
        assertFalse(wait.isCompleted)
        wait.cancel()
    }

    @Test
    fun aModalRequestCancelsPendingAutoFillRequests() {
        val arbiter = AuthorizationArbiter()
        var cancelled = 0
        arbiter.beginAutoFill { cancelled++ }
        arbiter.beginAutoFill { cancelled++ }

        arbiter.beginModal()

        assertEquals(2, cancelled)
    }

    @Test
    fun finishedAutoFillRequestsAreNotCancelled() {
        val arbiter = AuthorizationArbiter()
        var cancelled = false
        val endAutoFill = arbiter.beginAutoFill { cancelled = true }
        endAutoFill()

        arbiter.beginModal()

        assertFalse(cancelled)
    }

    @Test
    fun anAutoFillRequestCancelledByAModalIsCancelledOnce() {
        val arbiter = AuthorizationArbiter()
        var cancelled = 0
        arbiter.beginAutoFill { cancelled++ }

        arbiter.beginModal()()
        arbiter.beginModal()

        assertEquals(1, cancelled)
    }
}
