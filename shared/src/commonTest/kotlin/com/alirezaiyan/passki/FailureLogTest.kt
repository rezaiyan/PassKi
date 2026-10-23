package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals

class FailureLogTest {
    @Test
    fun misconfiguredPrintsTypeMessageAndFixHintOnce() {
        val line = failureLog("Register", PasskeyError.Misconfigured("App X is not associated with d.com.", "Serve the file."))
        assertEquals("Register ✗ [Misconfigured] App X is not associated with d.com.\nfix: Serve the file.", line)
    }

    @Test
    fun errorWithACatalogCheckShowsTheIdAndDocsLink() {
        val line = failureLog("Register", PasskeyError.Misconfigured("App X is not associated with d.com.", "Serve the file.", "IOS-02"))
        assertEquals(
            "Register ✗ [Misconfigured IOS-02] App X is not associated with d.com.\nfix: Serve the file.\n" +
                "docs: https://passki.alirezaiyan.com/checks/IOS-02",
            line,
        )
    }

    @Test
    fun errorWithoutHintPrintsTypeAndMessageOnly() {
        assertEquals(
            "Sign in ✗ [Cancelled] ${PasskeyError.Cancelled.message}",
            failureLog("Sign in", PasskeyError.Cancelled),
        )
    }
}
