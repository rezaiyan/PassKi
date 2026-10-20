package com.alirezaiyan.passki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DemoRelyingPartyJvmTest {
    @Test
    fun creationOptions_containsRpId() {
        val json = DemoRelyingParty.creationOptions()
        assertTrue(json.contains("\"rp\":{\"id\":\"alirezaiyan.com\""), json)
        assertEquals("alirezaiyan.com", DemoRelyingParty.RP_ID)
    }

    @Test
    fun register_onJvm_failsWithNotSupported(): Unit = runBlocking {
        val result = Passkeys().register(DemoRelyingParty.creationOptions())
        val failure = assertIs<PasskeyResult.Failure>(result)
        assertIs<PasskeyError.NotSupported>(failure.error)
        Unit
    }
}
