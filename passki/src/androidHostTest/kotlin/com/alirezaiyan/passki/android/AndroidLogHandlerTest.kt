package com.alirezaiyan.passki.android

import android.util.Log
import com.alirezaiyan.passki.AndroidLogHandler
import com.alirezaiyan.passki.PasskiLogLevel
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidLogHandlerTest {
    private val lines = mutableListOf<Triple<Int, String, String>>()
    private val handler = AndroidLogHandler { priority, tag, message -> lines += Triple(priority, tag, message) }

    @Test
    fun aSetupErrorGoesToLogcatAtErrorPriorityWithThePassKiTag() {
        handler.log(PasskiLogLevel.Error, "PassKi", "signIn failed: Misconfigured [AND-03]", null)
        assertEquals(listOf(Triple(Log.ERROR, "PassKi", "signIn failed: Misconfigured [AND-03]")), lines)
    }

    @Test
    fun eachLevelHasItsOwnPriority() {
        for (level in listOf(PasskiLogLevel.Warn, PasskiLogLevel.Info, PasskiLogLevel.Debug)) {
            handler.log(level, "PassKi", level.name, null)
        }
        assertEquals(listOf(Log.WARN, Log.INFO, Log.DEBUG), lines.map { it.first })
    }

    @Test
    fun inALocalUnitTestTheStubLogFallsBackToStandardError() {
        val captured = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(captured, true))
        try {
            AndroidLogHandler().log(PasskiLogLevel.Error, "PassKi", "signIn failed", null) // android.util.Log is a stub here
        } finally {
            System.setErr(original)
        }
        assertEquals("PassKi E signIn failed", captured.toString().trim())
    }

    @Test
    fun aThrowableIsAppendedAsAStackTrace() {
        handler.log(PasskiLogLevel.Error, "PassKi", "register failed", IllegalStateException("boom"))
        val message = lines.single().third
        assertTrue(message.startsWith("register failed\njava.lang.IllegalStateException: boom"), message)
    }
}
