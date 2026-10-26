package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals

/** In a real (headless) browser: the default handler writes to the matching `console` method. */
class PlatformLogHandlerJsTest {
    @Test
    fun theDefaultHandlerWritesToTheConsole() {
        js(
            "globalThis.__passkiConsole = []; globalThis.__passkiError = console.error; " +
                "console.error = function (m) { globalThis.__passkiConsole.push(m); }",
        )
        try {
            PasskiLogHandler.Platform.log(PasskiLogLevel.Error, "PassKi", "signIn failed: Misconfigured [AND-03]", null)
        } finally {
            js("console.error = globalThis.__passkiError")
        }
        val lines = js("globalThis.__passkiConsole")
        assertEquals(1, lines.length as Int)
        assertEquals("[PassKi] signIn failed: Misconfigured [AND-03]", lines[0] as String)
    }
}
