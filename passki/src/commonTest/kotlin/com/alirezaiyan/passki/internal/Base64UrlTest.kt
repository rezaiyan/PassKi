package com.alirezaiyan.passki.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base64UrlTest {
    private val bytes = byteArrayOf(1, 2, 3, -1)

    @Test
    fun encodesUrlSafeWithoutPadding() {
        assertEquals("AQID_w", Base64Url.encode(bytes))
    }

    @Test
    fun decodesUnpaddedBase64Url() {
        assertContentEquals(bytes, Base64Url.decode("AQID_w"))
    }

    @Test
    fun decodesStandardPaddedBase64Leniently() {
        assertContentEquals(bytes, Base64Url.decode("AQID/w=="))
    }

    @Test
    fun rejectsInvalidCharacters() {
        assertFailsWith<IllegalArgumentException> { Base64Url.decode("***") }
    }

    @Test
    fun roundTripsEmptyAndShortLengths() {
        assertEquals("", Base64Url.encode(ByteArray(0)))
        assertContentEquals(ByteArray(0), Base64Url.decode(""))
        for (n in 1..5) {
            val input = ByteArray(n) { (it * 61 + 250).toByte() }
            assertContentEquals(input, Base64Url.decode(Base64Url.encode(input)))
        }
    }

    @Test
    fun decodesMixedAlphabets() {
        val input = byteArrayOf(-5, -1, -65, 62, 63) // forces both '+'/'-' and '/'/'_' symbols
        val url = Base64Url.encode(input)
        val mixed = url.replaceFirst('-', '+').replaceFirst('_', '/')
        assertContentEquals(input, Base64Url.decode(mixed))
        assertContentEquals(input, Base64Url.decode(url.replace('-', '+').replace('_', '/')))
    }

    @Test
    fun decodesOverPaddedInput() {
        val input = byteArrayOf(1, 2, 3, 4)
        assertContentEquals(input, Base64Url.decode(Base64Url.encode(input) + "===="))
    }
}
