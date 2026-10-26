package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.internal.ModalGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorizationArbiterTest {
    // Each test gets its own gate; the production arbiter shares the process-wide one.
    private fun arbiter() = AuthorizationArbiter(ModalGate())

    @Test
    fun autoFillStartsAtOnceWhenNoModalRuns() = runTest {
        val arbiter = arbiter()
        val wait = async { arbiter.awaitNoModal() }
        runCurrent()
        assertTrue(wait.isCompleted)
    }

    @Test
    fun autoFillWaitsUntilTheModalRequestEnds() = runTest {
        val arbiter = arbiter()
        val endModal = assertNotNull(arbiter.beginModal())
        val wait = async { arbiter.awaitNoModal() }
        runCurrent()
        assertFalse(wait.isCompleted)

        endModal()
        runCurrent()
        assertTrue(wait.isCompleted)
    }

    @Test
    fun aSecondModalRequestIsRefusedWhileTheFirstRuns() {
        val arbiter = arbiter()
        val endFirst = assertNotNull(arbiter.beginModal())
        assertNull(arbiter.beginModal())

        endFirst()
        assertNotNull(arbiter.beginModal(), "a new modal request runs once the first ended")
    }

    @Test
    fun aRefusedModalRequestCancelsNoAutoFillRequest() {
        val arbiter = arbiter()
        val endModal = assertNotNull(arbiter.beginModal())
        var cancelled = false
        arbiter.beginAutoFill { cancelled = true }

        assertNull(arbiter.beginModal())

        assertFalse(cancelled)
        endModal()
    }

    @Test
    fun endingAModalRequestTwiceDoesNotEndTheNextOne() = runTest {
        val arbiter = arbiter()
        val endFirst = assertNotNull(arbiter.beginModal())
        endFirst()
        assertNotNull(arbiter.beginModal())
        endFirst()
        val wait = async { arbiter.awaitNoModal() }
        runCurrent()
        assertFalse(wait.isCompleted)
        wait.cancel()
    }

    @Test
    fun aModalRequestCancelsPendingAutoFillRequests() {
        val arbiter = arbiter()
        var cancelled = 0
        arbiter.beginAutoFill { cancelled++ }
        arbiter.beginAutoFill { cancelled++ }

        arbiter.beginModal()

        assertEquals(2, cancelled)
    }

    @Test
    fun finishedAutoFillRequestsAreNotCancelled() {
        val arbiter = arbiter()
        var cancelled = false
        val endAutoFill = arbiter.beginAutoFill { cancelled = true }
        endAutoFill()

        arbiter.beginModal()

        assertFalse(cancelled)
    }

    @Test
    fun anAutoFillRequestCancelledByAModalIsCancelledOnce() {
        val arbiter = arbiter()
        var cancelled = 0
        arbiter.beginAutoFill { cancelled++ }

        arbiter.beginModal()!!()
        arbiter.beginModal()

        assertEquals(1, cancelled)
    }
}
