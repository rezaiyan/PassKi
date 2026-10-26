package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ModalGateTest {
    @Test
    fun secondBeginFailsWhileTheFirstIsHeld() {
        val gate = ModalGate()
        val end = assertNotNull(gate.tryBegin())
        assertNull(gate.tryBegin())

        end()
        assertNotNull(gate.tryBegin(), "a new request runs once the first ended")
    }

    @Test
    fun endIsIdempotent() {
        val gate = ModalGate()
        val endFirst = assertNotNull(gate.tryBegin())
        endFirst()
        val endSecond = assertNotNull(gate.tryBegin())

        endFirst() // a late second call must not release the second request
        assertNull(gate.tryBegin())
        endSecond()
    }

    @Test
    fun awaitIdleWaitsForTheModalRequest() = runTest {
        val gate = ModalGate()
        val end = assertNotNull(gate.tryBegin())
        val wait = async { gate.awaitIdle() }
        runCurrent()
        assertFalse(wait.isCompleted)

        end()
        runCurrent()
        assertTrue(wait.isCompleted)
    }

    @Test
    fun exclusiveFailsWithInterruptedWhileAnotherRequestRuns() = runTest {
        val gate = ModalGate()
        val sheet = CompletableDeferred<String>()
        val first = async { gate.exclusive { sheet.await() } }
        runCurrent()

        val error = assertFailsWith<PasskeyException> { gate.exclusive { "second" } }
        assertEquals(PasskeyError.Interrupted, error.error)
        assertTrue("another passkey request is in progress" in error.error.message, error.error.message)

        sheet.complete("first")
        assertEquals("first", first.await(), "the pending request is unaffected")
        assertEquals("third", gate.exclusive { "third" })
    }

    @Test
    fun cancellingTheCallerReleasesTheGate() = runTest {
        val gate = ModalGate()
        val sheet = CompletableDeferred<String>()
        val first = async { gate.exclusive { sheet.await() } }
        runCurrent()

        first.cancel()
        runCurrent()
        assertFailsWith<CancellationException> { first.await() }
        sheet.complete("late") // a result after cancellation goes nowhere
        assertEquals("next", gate.exclusive { "next" })
    }

    @Test
    fun aFailingRequestReleasesTheGate() = runTest {
        val gate = ModalGate()
        assertFailsWith<IllegalStateException> { gate.exclusive { error("platform failed") } }
        assertEquals("next", gate.exclusive { "next" })
    }
}
