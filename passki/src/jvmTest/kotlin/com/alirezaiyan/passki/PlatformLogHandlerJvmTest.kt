package com.alirezaiyan.passki

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformLogHandlerJvmTest {
    @Test
    fun theDefaultHandlerWritesToStandardError() {
        val captured = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(captured, true))
        try {
            PasskiLogHandler.Platform.log(PasskiLogLevel.Error, "PassKi", "signIn failed: Misconfigured [AND-03]", null)
        } finally {
            System.setErr(original)
        }
        assertEquals("PassKi E signIn failed: Misconfigured [AND-03]", captured.toString().trim())
    }
}
