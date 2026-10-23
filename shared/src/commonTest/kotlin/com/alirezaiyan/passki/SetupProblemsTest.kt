package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetupProblemsTest {
    private fun row(id: String?, status: DoctorStatus) = CheckRow(id, "name", status, "message", null)

    @Test
    fun noSummaryBeforeTheCheckRanOrWhenNothingFails() {
        assertNull(setupProblems(null))
        assertNull(setupProblems(listOf(row("AND-03", DoctorStatus.Pass), row("DEV-01", DoctorStatus.Warn), row("SRV-02", DoctorStatus.Skipped))))
    }

    @Test
    fun failuresAreCountedAndNamedByCheckId() {
        assertEquals("Setup: 1 problem (AND-03)", setupProblems(listOf(row("AND-03", DoctorStatus.Fail), row("DEV-01", DoctorStatus.Warn))))
        assertEquals(
            "Setup: 3 problems (AND-03, SRV-02, Android origin)",
            setupProblems(listOf(row("AND-03", DoctorStatus.Fail), row("SRV-02", DoctorStatus.Fail), CheckRow(null, "Android origin", DoctorStatus.Fail, "m", null))),
        )
    }
}
