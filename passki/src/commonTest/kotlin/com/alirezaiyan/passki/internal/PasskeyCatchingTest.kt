package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PasskeyCatchingTest {
    private val mapped = PasskeyError.Unknown("mapped", null)

    @Test
    fun wrapsValueInSuccess() {
        assertEquals(PasskeyResult.Success(1), passkeyCatching({ mapped }) { 1 })
    }

    @Test
    fun passkeyExceptionKeepsItsOwnError() {
        val result = passkeyCatching<Int>({ mapped }) { throw PasskeyException(PasskeyError.Cancelled) }
        assertEquals(PasskeyResult.Failure(PasskeyError.Cancelled), result)
    }

    @Test
    fun otherThrowablesGoThroughMapper() {
        val result = passkeyCatching<Int>({ mapped }) { throw IllegalStateException("boom") }
        assertEquals(PasskeyResult.Failure(mapped), result)
    }

    @Test
    fun cancellationIsRethrown() {
        assertFailsWith<CancellationException> {
            passkeyCatching<Int>({ mapped }) { throw CancellationException("stop") }
        }
    }

    @Test
    fun errorsAreRethrown() {
        assertFailsWith<Error> {
            passkeyCatching<Int>({ mapped }) { throw Error("fatal") }
        }
    }
}
