package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PasskeyResultTest {
    @Test
    fun onSuccessRunsOnlyForSuccess() {
        var seen: String? = null
        PasskeyResult.Success("a")
            .onSuccess { seen = it }
            .onFailure { error("must not run") }
        assertEquals("a", seen)
    }

    @Test
    fun onFailureRunsOnlyForFailure() {
        var seen: PasskeyError? = null
        val result: PasskeyResult<String> = PasskeyResult.Failure(PasskeyError.Cancelled)
        result.onSuccess { error("must not run") }.onFailure { seen = it }
        assertEquals(PasskeyError.Cancelled, seen)
    }

    @Test
    fun getOrNullReturnsValueOrNull() {
        assertEquals(1, PasskeyResult.Success(1).getOrNull())
        assertNull(PasskeyResult.Failure(PasskeyError.NoCredentials).getOrNull())
    }

    @Test
    fun getOrThrowWrapsErrorInPasskeyException() {
        val e = assertFailsWith<PasskeyException> {
            PasskeyResult.Failure(PasskeyError.NoCredentials).getOrThrow()
        }
        assertEquals(PasskeyError.NoCredentials, e.error)
    }

    @Test
    fun mapTransformsSuccessAndKeepsFailure() {
        assertEquals(PasskeyResult.Success(2), PasskeyResult.Success(1).map { it + 1 })
        val failure: PasskeyResult<Int> = PasskeyResult.Failure(PasskeyError.Interrupted)
        assertEquals(failure, failure.map { it + 1 })
    }

    @Test
    fun onlyActionableErrorsExposeFixHint() {
        assertEquals("do X", PasskeyError.Misconfigured("broken", "do X").fixHint)
        assertNull(PasskeyError.Cancelled.fixHint)
    }
}
