package com.alirezaiyan.passki.android

import com.alirezaiyan.passki.PassKi
import com.alirezaiyan.passki.PasskiLogHandler
import com.alirezaiyan.passki.PasskiLogLevel
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.internal.LoggingPasskeys
import com.alirezaiyan.passki.internal.ModalGate
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** `Passkeys()` on Android is the Credential Manager implementation wrapped in the logger. */
class AndroidLoggingTest {
    private val lines = mutableListOf<Pair<PasskiLogLevel, String>>()

    @AfterTest
    fun resetPassKi() {
        PassKi.logLevel = PasskiLogLevel.Error
        PassKi.logHandler = PasskiLogHandler.Platform
    }

    @Test
    fun passkeysLogsThroughThePassKiHandler() {
        val passkeys = Passkeys()
        assertIs<AndroidPasskeys>(assertIs<LoggingPasskeys>(passkeys).delegate)
    }

    @Test
    fun theDebugLogTracesACallOnOldAndroid() = runTest {
        PassKi.logLevel = PasskiLogLevel.Debug
        PassKi.logHandler = PasskiLogHandler { level, _, message, _ -> lines += level to message }

        LoggingPasskeys(AndroidPasskeys(sdkInt = { 27 }, gate = ModalGate())).signIn(REQUEST, SignInMode.ImmediateOnly)

        assertEquals(
            PasskiLogLevel.Debug to
                "signIn started: mode=ImmediateOnly, needs=immediateSignIn, rpId=example.com, options=<${REQUEST.length} chars>",
            lines.first(),
        )
        val (level, outcome) = lines.last()
        assertEquals(PasskiLogLevel.Warn, level)
        assertTrue(outcome.startsWith("signIn failed: NotSupported: "), outcome)
    }

    private companion object {
        const val REQUEST = """{"challenge":"AQID","rpId":"example.com"}"""
    }
}
