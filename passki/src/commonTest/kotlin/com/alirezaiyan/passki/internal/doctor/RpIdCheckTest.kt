package com.alirezaiyan.passki.internal.doctor

import com.alirezaiyan.passki.DoctorStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RpIdCheckTest {
    @Test
    fun bareDomainHasNoProblem() {
        assertNull(rpIdCheck("example.com"))
        assertNull(rpIdCheck("login.example.co.uk"))
    }

    @Test
    fun schemeAndPathFailWithBareDomainSuggestion() {
        val check = assertNotNull(rpIdCheck("https://example.com/x"))
        assertEquals(DoctorStatus.Fail, check.status)
        assertEquals(RP_ID_CHECK, check.name)
        assertEquals("rpId must be a bare domain (got https://example.com/x); use example.com", check.message)
    }

    @Test
    fun portFails() {
        assertEquals(
            "rpId must be a bare domain (got example.com:8443); use example.com",
            assertNotNull(rpIdCheck("example.com:8443")).message,
        )
    }

    @Test
    fun trailingSlashAndQueryFail() {
        assertNotNull(rpIdCheck("example.com/"))
        assertNotNull(rpIdCheck("example.com?x=1"))
    }

    @Test
    fun blankFails() {
        assertEquals(DoctorStatus.Fail, assertNotNull(rpIdCheck(" ")).status)
    }
}
