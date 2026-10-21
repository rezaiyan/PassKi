package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasskeyErrorTest {
    @Test
    fun unknownEqualityIgnoresCause() {
        val a = PasskeyError.Unknown("m", "c", RuntimeException("a"))
        val b = PasskeyError.Unknown("m", "c", IllegalStateException("b"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun unknownToStringShowsCauseClassOnly() {
        val error = PasskeyError.Unknown("m", "c", IllegalStateException("secret"))
        assertTrue("cause=IllegalStateException" in error.toString())
        assertTrue("secret" !in error.toString())
    }

    @Test
    fun unknownCauseDefaultsToNull() {
        assertEquals(null, PasskeyError.Unknown("m", "c").cause)
    }
}
