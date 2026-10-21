package com.alirezaiyan.passki.apple

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosAutofillTest {
    @Test
    fun cancelCodeDuringAutofillIsADismissal() {
        assertTrue(isAutoFillDismissal(AppleAuthorizationException(1001, "canceled", presented = false)))
    }

    @Test
    fun otherErrorsEndAutofill() {
        assertFalse(isAutoFillDismissal(AppleAuthorizationException(1004, "not associated", presented = false)))
        assertFalse(isAutoFillDismissal(IllegalStateException("x")))
    }

    @Test
    fun capabilitiesReportAutofillFromIos16() = runTest {
        assertEquals(iosAtLeast(16), IosPasskeys(anchor = { null }).capabilities().autofill)
    }
}
