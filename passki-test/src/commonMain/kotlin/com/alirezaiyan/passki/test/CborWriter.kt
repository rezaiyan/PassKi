package com.alirezaiyan.passki.test

/** Minimal canonical CBOR encoder: just what WebAuthn attestation objects and COSE keys need. */
internal class CborWriter {
    private val out = ArrayList<Byte>()

    fun int(value: Long): CborWriter = apply { if (value >= 0) head(0, value) else head(1, -1 - value) }

    fun int(value: Int): CborWriter = int(value.toLong())

    fun bytes(value: ByteArray): CborWriter = apply {
        head(2, value.size.toLong())
        value.forEach { out += it }
    }

    fun text(value: String): CborWriter = apply {
        val encoded = value.encodeToByteArray()
        head(3, encoded.size.toLong())
        encoded.forEach { out += it }
    }

    fun mapHeader(size: Int): CborWriter = apply { head(5, size.toLong()) }

    fun toByteArray(): ByteArray = out.toByteArray()

    /** Writes a major type + argument; [value] is non-negative (for negative ints it is `-1 - n`). */
    private fun head(major: Int, value: Long) {
        val m = major shl 5
        when {
            value < 24 -> out += (m or value.toInt()).toByte()
            value < 0x100 -> {
                out += (m or 24).toByte()
                out += value.toByte()
            }
            value < 0x10000 -> {
                out += (m or 25).toByte()
                out += (value shr 8).toByte()
                out += value.toByte()
            }
            value < 0x1_0000_0000L -> {
                out += (m or 26).toByte()
                for (shift in 24 downTo 0 step 8) out += (value shr shift).toByte()
            }
            else -> {
                out += (m or 27).toByte()
                for (shift in 56 downTo 0 step 8) out += (value shr shift).toByte()
            }
        }
    }
}
