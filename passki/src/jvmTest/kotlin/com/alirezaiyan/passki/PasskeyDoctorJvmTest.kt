package com.alirezaiyan.passki

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasskeyDoctorJvmTest {
    @Test
    fun reportsSingleSkippedCheck() = runTest {
        val report = PasskeyDoctor.run("example.com")
        assertEquals("example.com", report.rpId)
        val check = report.checks.single()
        assertEquals(DoctorStatus.Skipped, check.status)
        assertTrue("Android and iOS" in check.message)
        assertTrue(report.isHealthy)
    }
}
