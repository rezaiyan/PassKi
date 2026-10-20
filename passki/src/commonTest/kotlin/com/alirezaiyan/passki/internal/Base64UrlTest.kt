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
}
