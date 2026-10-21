package com.alirezaiyan.passki.gradle.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class XcconfigTest {
    private val template = "TEAM_ID=VFCFJC7Y5J\n\nPRODUCT_NAME=PassKi\nPRODUCT_BUNDLE_IDENTIFIER=com.example.app\n"

    @Test
    fun appendsMissingKeyKeepingEverythingElse() {
        val result = Xcconfig.set(template, "CODE_SIGN_ENTITLEMENTS", "iosApp/iosApp.entitlements")
        assertEquals(template + "CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements\n", result)
    }

    @Test
    fun addsTrailingNewlineBeforeAppending() {
        val result = Xcconfig.set("A=1", "B", "2")
        assertEquals("A=1\nB = 2\n", result)
    }

    @Test
    fun replacesExistingAssignmentInPlace() {
        val text = "A=1\nCODE_SIGN_ENTITLEMENTS=old/path.entitlements\nB=2\n"
        assertEquals(
            "A=1\nCODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements\nB=2\n",
            Xcconfig.set(text, "CODE_SIGN_ENTITLEMENTS", "iosApp/iosApp.entitlements"),
        )
    }

    @Test
    fun isIdempotent() {
        val once = Xcconfig.set(template, "CODE_SIGN_ENTITLEMENTS", "x.entitlements")
        assertEquals(once, Xcconfig.set(once, "CODE_SIGN_ENTITLEMENTS", "x.entitlements"))
    }

    @Test
    fun ignoresCommentsConditionalsAndPrefixes() {
        val text = "// CODE_SIGN_ENTITLEMENTS = commented\nCODE_SIGN_ENTITLEMENTS[sdk=iphoneos*] = cond\nMY_CODE_SIGN_ENTITLEMENTS = other\n"
        assertEquals(text + "CODE_SIGN_ENTITLEMENTS = a\n", Xcconfig.set(text, "CODE_SIGN_ENTITLEMENTS", "a"))
    }

    @Test
    fun removesDuplicateAssignmentsAfterTheFirst() {
        val text = "CODE_SIGN_ENTITLEMENTS = a\nX=1\nCODE_SIGN_ENTITLEMENTS = b\n"
        assertEquals("CODE_SIGN_ENTITLEMENTS = c\nX=1\n", Xcconfig.set(text, "CODE_SIGN_ENTITLEMENTS", "c"))
    }

    @Test
    fun readsValues() {
        assertEquals("VFCFJC7Y5J", Xcconfig.get(template, "TEAM_ID"))
        assertEquals("a b", Xcconfig.get("K = a b // note\n", "K"))
        assertNull(Xcconfig.get(template, "CODE_SIGN_ENTITLEMENTS"))
    }
}
