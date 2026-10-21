package com.alirezaiyan.passki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs

class PasskeysJvmTest {
    @Test
    fun register_onJvm_failsWithNotSupported(): Unit = runBlocking {
        val options = """{"rp":{"id":"${DemoRelyingParty.RP_ID}","name":"PassKi demo"},"user":{"id":"AAAA","name":"demo","displayName":"demo"},"challenge":"AAAA","pubKeyCredParams":[{"type":"public-key","alg":-7}]}"""
        val failure = assertIs<PasskeyResult.Failure>(Passkeys().register(options))
        assertIs<PasskeyError.NotSupported>(failure.error)
        Unit
    }
}
