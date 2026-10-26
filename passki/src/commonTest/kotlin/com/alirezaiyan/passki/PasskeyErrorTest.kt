package com.alirezaiyan.passki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasskeyErrorTest {
    @Test
    fun unknownEqualityIgnoresCause() {
        val a = PasskeyError.Unknown("m", "c", RuntimeException("a"))
        val b = PasskeyError.Unknown("m", "c", IllegalStateException("b"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun unknownToStringShowsCauseClassOnly() {
        val error = PasskeyError.Unknown("m", "c", IllegalStateException("secret"))
        assertTrue("cause=IllegalStateException" in error.toString())
        assertTrue("secret" !in error.toString())
    }

    @Test
    fun unknownCauseDefaultsToNull() {
        assertEquals(null, PasskeyError.Unknown("m", "c").cause)
    }

    @Test
    fun misconfiguredNamesItsCatalogCheckAndDocsPage() {
        val error = PasskeyError.Misconfigured("m", "h", checkId = "AND-03")
        assertEquals("AND-03", error.checkId)
        assertEquals("https://passki.alirezaiyan.com/checks/AND-03", error.docsUrl)
        assertEquals(
            "Misconfigured(message=m, fixHint=h, checkId=AND-03, docsUrl=https://passki.alirezaiyan.com/checks/AND-03)",
            error.toString(),
        )
    }

    @Test
    fun checkIdIsOptionalAndPartOfEquality() {
        val plain = PasskeyError.Misconfigured("m", "h")
        assertNull(plain.checkId)
        assertNull(plain.docsUrl)
        assertEquals("Misconfigured(message=m, fixHint=h)", plain.toString())
        assertNotEquals<PasskeyError>(plain, PasskeyError.Misconfigured("m", "h", checkId = "AND-03"))
        assertNull(PasskeyError.Cancelled.checkId)
        assertNull(PasskeyError.Cancelled.docsUrl)
    }

    @Test
    fun invalidOptionsCanNameACheckToo() {
        val error = PasskeyError.InvalidOptions("rpId must be a bare domain", checkId = "CFG-01")
        assertEquals("CFG-01", error.checkId)
        assertEquals(
            "InvalidOptions(message=rpId must be a bare domain, checkId=CFG-01, " +
                "docsUrl=https://passki.alirezaiyan.com/checks/CFG-01)",
            error.toString(),
        )
        assertEquals("InvalidOptions(message=x)", PasskeyError.InvalidOptions("x").toString())
        assertNotEquals<PasskeyError>(PasskeyError.InvalidOptions("x"), PasskeyError.InvalidOptions("x", checkId = "CFG-01"))
    }

    @Test
    fun exceptionMessageLinksTheDocsPage() {
        val e = PasskeyException(PasskeyError.Misconfigured("broken", "do X", checkId = "IOS-02"))
        assertEquals("broken Fix: do X Docs: https://passki.alirezaiyan.com/checks/IOS-02", e.message)
    }

    @Test
    fun everyErrorHasItsCategory() {
        val expected = mapOf(
            PasskeyError.Cancelled to PasskeyErrorCategory.UserCancelled,
            PasskeyError.Interrupted to PasskeyErrorCategory.UserCancelled,
            PasskeyError.NoCredentials to PasskeyErrorCategory.NoCredential,
            PasskeyError.AlreadyRegistered to PasskeyErrorCategory.AlreadyExists,
            PasskeyError.NotSupported("old OS") to PasskeyErrorCategory.Unsupported,
            PasskeyError.Misconfigured("m", "h", "AND-03") to PasskeyErrorCategory.Setup,
            PasskeyError.InvalidOptions("bad") to PasskeyErrorCategory.Setup,
            PasskeyError.Unknown("?", null) to PasskeyErrorCategory.Unexpected,
        )
        for ((error, category) in expected) assertEquals(category, error.category, error.toString())
    }

    @Test
    fun onlyCancelledAndInterruptedAreUserCancellations() {
        assertTrue(PasskeyError.Cancelled.isUserCancellation)
        assertTrue(PasskeyError.Interrupted.isUserCancellation)
        assertFalse(PasskeyError.Misconfigured("m", "h").isUserCancellation)
        assertFalse(PasskeyError.NoCredentials.isUserCancellation)
    }

    @Test
    fun errorsWithoutADocsPagePrintAsBefore() {
        assertEquals("Cancelled", PasskeyError.Cancelled.toString())
        assertEquals("NotSupported(message=old OS)", PasskeyError.NotSupported("old OS").toString())
    }
}
