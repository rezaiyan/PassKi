package com.alirezaiyan.passki

import kotlin.test.Test

class PlatformLogHandlerIosTest {
    // NSLog output can't be read back in a test; this checks the call itself, including format characters in the
    // message (escaped, since the line is NSLog's format).
    @Test
    fun theDefaultHandlerWritesToNSLog() {
        PasskiLogHandler.Platform.log(PasskiLogLevel.Error, "PassKi", "signIn failed: 100% %@ %d", IllegalStateException())
    }
}
