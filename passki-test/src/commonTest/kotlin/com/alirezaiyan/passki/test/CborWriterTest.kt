package com.alirezaiyan.passki.test

import kotlin.test.Test
import kotlin.test.assertContentEquals

class CborWriterTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun smallPositiveInt() = assertContentEquals(bytes(0x01), CborWriter().int(1).toByteArray())

    @Test
    fun negativeInts() {
        assertContentEquals(bytes(0x20), CborWriter().int(-1).toByteArray())
        assertContentEquals(bytes(0x26), CborWriter().int(-7).toByteArray())
    }

    @Test
    fun oneByteLengthInt() = assertContentEquals(bytes(0x18, 0xFF), CborWriter().int(255).toByteArray())

    @Test
    fun twoByteLengthInt() = assertContentEquals(bytes(0x19, 0x01, 0x00), CborWriter().int(256).toByteArray())

    @Test
    fun textString() = assertContentEquals(bytes(0x63, 'f'.code, 'm'.code, 't'.code), CborWriter().text("fmt").toByteArray())

    @Test
    fun byteStringWithOneByteLength() {
        val encoded = CborWriter().bytes(ByteArray(32)).toByteArray()
        assertContentEquals(bytes(0x58, 0x20), encoded.copyOfRange(0, 2))
        assertContentEquals(ByteArray(32), encoded.copyOfRange(2, 34))
    }

    @Test
    fun mapHeader() {
        assertContentEquals(bytes(0xA5), CborWriter().mapHeader(5).toByteArray())
        assertContentEquals(bytes(0xA0), CborWriter().mapHeader(0).toByteArray())
    }
}
