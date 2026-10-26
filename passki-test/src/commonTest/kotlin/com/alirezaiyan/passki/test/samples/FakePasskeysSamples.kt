package com.alirezaiyan.passki.test.samples

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.getOrThrow
import com.alirezaiyan.passki.test.FakePasskeys
import com.alirezaiyan.passki.test.TestOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Samples linked from the KDoc with @sample; they run as tests.
class FakePasskeysSamples {
    @Test
    fun registerThenSignIn() = runTest {
        val device = FakePasskeys(origin = "https://example.com") // your server's allowed origin
        val created = device.register(TestOptions.creation("example.com")).getOrThrow()
        // send created.json to your server's verifier here
        val assertion = device.signIn(TestOptions.request("example.com")).getOrThrow()
        assertEquals(created.credentialId, assertion.credentialId)
    }

    @Test
    fun simulateTheUserClosingTheSheet() = runTest {
        val device = FakePasskeys(origin = "https://example.com")
        device.failNextWith(PasskeyError.Cancelled)
        assertEquals(PasskeyResult.Failure(PasskeyError.Cancelled), device.signIn(TestOptions.request("example.com")))
    }
}
