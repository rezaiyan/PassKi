package com.alirezaiyan.passki.android

import com.alirezaiyan.passki.PasskeyError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidAutofillTest {
    @Test
    fun autofillNeedsApi35() {
        assertIs<PasskeyError.NotSupported>(autofillUnsupported(sdkInt = 34))
        assertNull(autofillUnsupported(sdkInt = 35))
        assertNull(autofillUnsupported(sdkInt = 36))
    }

    @Test
    fun capabilitiesReportAutofillFromApi35() {
        assertFalse(androidCapabilities(sdkInt = 34).autofill)
        assertTrue(androidCapabilities(sdkInt = 34).isSupported)
        assertTrue(androidCapabilities(sdkInt = 35).autofill)
    }

    @Test
    fun capabilitiesBelowApi28AreAllFalse() {
        val capabilities = androidCapabilities(sdkInt = 27)
        assertFalse(capabilities.isSupported)
        assertFalse(capabilities.immediateSignIn)
        assertFalse(capabilities.unifiedPasswordSignIn)
        assertFalse(capabilities.conditionalCreate)
        assertFalse(capabilities.signals)
        assertFalse(capabilities.autofill)
    }

    @Test
    fun noFocusedFieldIsMisconfiguredWithHint() {
        val error = NO_FOCUSED_FIELD
        assertEquals("Autofill needs a focused text field", error.message)
        assertTrue("PasskeyUsernameField" in error.fixHint, error.fixHint)
    }

    @Test
    fun explicitAnchorWinsAndPrefersItsFocusedDescendant() {
        val windows = listOf(WindowFocus(hasWindowFocus = true, focused = "dialog-field"))
        assertEquals("anchor-child", chooseAutofillTarget("anchor", "anchor-child", windows, "activity-field"))
        assertEquals("anchor", chooseAutofillTarget("anchor", null, windows, "activity-field"))
    }

    @Test
    fun focusedWindowWinsOverTheActivityWindow() {
        // A Compose Dialog or ModalBottomSheet is its own window: the activity's currentFocus is a stale view behind it.
        val windows = listOf(
            WindowFocus(hasWindowFocus = false, focused = "activity-field"),
            WindowFocus(hasWindowFocus = true, focused = "dialog-field"),
        )
        assertEquals("dialog-field", chooseAutofillTarget(null, null, windows, "activity-field"))
    }

    @Test
    fun fallsBackToActivityFocusWhenNoWindowHasFocus() {
        val windows = listOf(WindowFocus<String>(hasWindowFocus = false, focused = null))
        assertEquals("activity-field", chooseAutofillTarget(null, null, windows, "activity-field"))
        assertNull(chooseAutofillTarget<String>(null, null, emptyList(), null))
    }

    @Test
    fun focusedWindowWithoutFocusedViewFallsBack() {
        val windows = listOf(WindowFocus<String>(hasWindowFocus = true, focused = null))
        assertEquals("activity-field", chooseAutofillTarget(null, null, windows, "activity-field"))
    }
}
