package com.alirezaiyan.passki.apple

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class NSDataInteropTest {
    @Test
    fun roundTripsBytes() {
        val bytes = byteArrayOf(0, 1, -1, 127, -128)
        assertContentEquals(bytes, bytes.toNSData().toByteArray())
    }

    @Test
    fun roundTripsEmpty() {
        assertContentEquals(ByteArray(0), ByteArray(0).toNSData().toByteArray())
    }

    @Test
    fun simulatorRunsAtLeastIos16() {
        assertTrue(iosAtLeast(16))
    }
}
