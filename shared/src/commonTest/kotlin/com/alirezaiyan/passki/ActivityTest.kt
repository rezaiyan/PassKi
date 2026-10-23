package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityTest {
    @Test
    fun outcomeFollowsTheLogMarks() {
        assertEquals(Outcome.Ok, DemoController.outcomeOf("Register ✓ prfEnabled=true"))
        assertEquals(Outcome.Failed, DemoController.outcomeOf("Sign in ✗ [Cancelled] cancelled"))
        // Passed on the device but rejected by the server is a failure.
        assertEquals(Outcome.Failed, DemoController.outcomeOf("Register ✓ on device, ✗ by server: HTTP 400"))
        assertEquals(Outcome.Info, DemoController.outcomeOf("Signal unknown: register or sign in first"))
    }

    @Test
    fun splitsTitleDetailAndFixHint() {
        val entry = failureLog("Register", PasskeyError.Misconfigured("App X is not associated with d.com.", "Serve the file."))
        val activity = Activity(1, "Register", DemoController.outcomeOf(entry), entry)
        assertEquals("Register ✗ [Misconfigured] App X is not associated with d.com.", activity.title)
        assertEquals("Serve the file.", activity.fixHint)
        assertEquals("", activity.detail)
    }

    @Test
    fun readsTheCheckIdOfACatalogFailure() {
        val entry = failureLog("Register", PasskeyError.Misconfigured("App X is not associated with d.com.", "Serve the file.", "IOS-02"))
        assertEquals("IOS-02", Activity(1, "Register", Outcome.Failed, entry).checkId)

        val plain = failureLog("Sign in", PasskeyError.Cancelled)
        assertNull(Activity(2, "Sign in", Outcome.Failed, plain).checkId)
    }

    @Test
    fun checkDocsUrlPointsAtTheCheckPage() {
        assertEquals("https://passki.alirezaiyan.com/checks/AND-03", checkDocsUrl("AND-03"))
    }

    @Test
    fun keepsVerdictLinesAsDetail() {
        val activity = Activity(2, "Sign in", Outcome.Ok, "Sign in ✓\nverified by server ✓ signCount 3\n  credentialId abc")
        assertEquals("Sign in ✓", activity.title)
        assertEquals("verified by server ✓ signCount 3\n  credentialId abc", activity.detail)
        assertNull(activity.fixHint)
    }
}
