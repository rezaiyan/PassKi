package com.alirezaiyan.passki

import com.alirezaiyan.passki.demo.PasskiDemoServer
import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class PasskeysJvmTest {
    @Test
    fun register_onJvm_failsWithNotSupported(): Unit = runBlocking {
        val options = """{"rp":{"id":"${PasskiDemoServer.RP_ID}","name":"PassKi demo"},"user":{"id":"AAAA","name":"demo","displayName":"demo"},"challenge":"AAAA","pubKeyCredParams":[{"type":"public-key","alg":-7}]}"""
        val failure = assertIs<PasskeyResult.Failure>(Passkeys().register(options))
        assertIs<PasskeyError.NotSupported>(failure.error)
        Unit
    }
}
