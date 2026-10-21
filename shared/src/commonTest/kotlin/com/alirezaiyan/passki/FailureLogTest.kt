package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals

class FailureLogTest {
    @Test
    fun misconfiguredPrintsMessageAndFixHintOnce() {
        val line = failureLog("Register", PasskeyError.Misconfigured("App X is not associated with d.com.", "Serve the file."))
        assertEquals("Register ✗ App X is not associated with d.com.\nfix: Serve the file.", line)
    }

    @Test
    fun errorWithoutHintPrintsMessageOnly() {
        assertEquals("Sign in ✗ ${PasskeyError.Cancelled.message}", failureLog("Sign in", PasskeyError.Cancelled))
    }
}
