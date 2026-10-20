package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun exceptionMessageIncludesFixHint() {
        val e = PasskeyException(PasskeyError.Misconfigured("broken", "do X"))
        assertTrue(e.message!!.contains("broken"))
        assertTrue(e.message!!.contains("Fix: do X"))
        assertEquals("The user cancelled the passkey request.", PasskeyException(PasskeyError.Cancelled).message)
    }

    @Test
    fun valueErrorsHaveStructuralEqualityAndToString() {
        assertEquals(PasskeyError.NotSupported("a"), PasskeyError.NotSupported("a"))
        assertEquals(PasskeyError.NotSupported("a").hashCode(), PasskeyError.NotSupported("a").hashCode())
        assertNotEquals<PasskeyError>(PasskeyError.NotSupported("a"), PasskeyError.InvalidOptions("a"))
        assertEquals(PasskeyError.Unknown("m", "c"), PasskeyError.Unknown("m", "c"))
        assertNotEquals(PasskeyError.Unknown("m", "c"), PasskeyError.Unknown("m", null))
        assertEquals(PasskeyError.Misconfigured("m", "h"), PasskeyError.Misconfigured("m", "h"))
        assertEquals("NotSupported(message=a)", PasskeyError.NotSupported("a").toString())
    }

    @Test
    fun registrationToStringOmitsJsonBody() {
        val r = PasskeyRegistration("{\"secret\":1}", "id", emptyList(), null)
        assertEquals("PasskeyRegistration(credentialId=id, json=<12 chars>)", r.toString())
        assertEquals("PasskeyAssertion(credentialId=id, json=<2 chars>)", PasskeyAssertion("{}", "id", null).toString())
    }
}
