package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppleErrorMapperTest {
    @Test
    fun canceledIsCancelled() {
        assertEquals(PasskeyError.Cancelled, appleErrorToPasskeyError(1001, "The operation couldn't be completed."))
    }

    @Test
    fun notInteractiveIsNoCredentials() {
        assertEquals(PasskeyError.NoCredentials, appleErrorToPasskeyError(1005, "not interactive"))
    }

    @Test
    fun matchedExcludedCredentialIsAlreadyRegistered() {
        assertEquals(PasskeyError.AlreadyRegistered, appleErrorToPasskeyError(1006, "excluded"))
    }

    @Test
    fun domainAssociationFailureIsMisconfiguredWithAasaHint() {
        val error = appleErrorToPasskeyError(
            1004,
            "Application with identifier ABCDE12345.com.example is not associated with domain example.com",
        )
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertTrue("apple-app-site-association" in misconfigured.fixHint)
        assertEquals("IOS-02", misconfigured.checkId)
    }

    @Test
    fun otherFailuresAreUnknownWithCode() {
        val error = assertIs<PasskeyError.Unknown>(appleErrorToPasskeyError(1004, "failed"))
        assertEquals("ASAuthorizationError.1004", error.platformCode)
    }

    @Test
    fun authorizationExceptionGoesThroughMapper() {
        assertEquals(PasskeyError.Cancelled, AppleAuthorizationException(1001, "x", presented = true).toPasskeyError())
    }

    @Test
    fun deviceNotConfiguredForPasskeyCreationIsNotSupported() {
        val error = assertIs<PasskeyError.NotSupported>(appleErrorToPasskeyError(1010, "not configured"))
        assertTrue("passcode" in error.message)
    }

    @Test
    fun immediateOnlyCancelWithoutSheetIsNoCredentials() {
        assertEquals(
            PasskeyError.NoCredentials,
            appleErrorToPasskeyError(1001, "canceled", immediateOnly = true, presented = false),
        )
    }

    @Test
    fun immediateOnlyCancelAfterSheetIsCancelled() {
        assertEquals(
            PasskeyError.Cancelled,
            appleErrorToPasskeyError(1001, "canceled", immediateOnly = true, presented = true),
        )
    }

    @Test
    fun nonImmediateCancelWithoutSheetIsCancelled() {
        assertEquals(
            PasskeyError.Cancelled,
            appleErrorToPasskeyError(1001, "canceled", immediateOnly = false, presented = false),
        )
    }

    @Test
    fun authorizationExceptionCarriesImmediateOnlyAndPresented() {
        val error = AppleAuthorizationException(1001, "x", presented = false).toPasskeyError(immediateOnly = true)
        assertEquals(PasskeyError.NoCredentials, error)
    }

    @Test
    fun unknownKeepsOriginalCause() {
        val original = RuntimeException("boom")
        assertSame(original, assertIs<PasskeyError.Unknown>(original.toPasskeyError()).cause)
    }

    @Test
    fun unknownAuthorizationErrorKeepsCause() {
        val original = AppleAuthorizationException(1004, "failed", presented = true)
        assertSame(original, assertIs<PasskeyError.Unknown>(original.toPasskeyError()).cause)
    }

    @Test
    fun realSimulatorAssociationErrorProducesConcreteAasaHint() {
        val error = appleErrorToPasskeyError(
            1004,
            "The operation couldn\u2019t be completed. Application with identifier " +
                "VFCFJC7Y5J.com.alirezaiyan.passki.PassKi is not associated with domain alirezaiyan.com",
        )
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertEquals(
            "App VFCFJC7Y5J.com.alirezaiyan.passki.PassKi is not associated with alirezaiyan.com.",
            misconfigured.message,
        )
        val hint = misconfigured.fixHint
        assertTrue("webcredentials:alirezaiyan.com" in hint, hint)
        assertTrue("webcredentials:alirezaiyan.com?mode=developer" in hint, hint)
        assertTrue("https://alirezaiyan.com/.well-known/apple-app-site-association" in hint, hint)
        assertTrue(
            "{\"webcredentials\":{\"apps\":[\"VFCFJC7Y5J.com.alirezaiyan.passki.PassKi\"]}}" in hint,
            hint,
        )
        assertFalse("<" in hint, hint)
    }

    @Test
    fun associationErrorToleratesStraightApostropheAndTrailingPeriod() {
        val error = appleErrorToPasskeyError(
            1004,
            "The operation couldn't be completed. Application with identifier T.com.example is not associated " +
                "with domain example.com.",
        )
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertEquals("App T.com.example is not associated with example.com.", misconfigured.message)
        assertTrue("https://example.com/.well-known/apple-app-site-association" in misconfigured.fixHint)
    }

    @Test
    fun unparseableAssociationErrorFallsBackToGenericHint() {
        val error = appleErrorToPasskeyError(1004, "The app is not associated with domain")
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertEquals(AASA_HINT, misconfigured.fixHint)
        assertEquals("IOS-02", misconfigured.checkId)
    }

    @Test
    fun operationCouldNotBeCompletedBoilerplateIsStripped() {
        val error = assertIs<PasskeyError.Unknown>(
            appleErrorToPasskeyError(1004, "The operation couldn\u2019t be completed. Something specific broke."),
        )
        assertEquals("Something specific broke.", error.message)
    }

    @Test
    fun boilerplateOnlyMessageIsKept() {
        val error = assertIs<PasskeyError.Unknown>(
            appleErrorToPasskeyError(1004, "The operation couldn't be completed."),
        )
        assertEquals("The operation couldn't be completed.", error.message)
    }

    @Test
    fun conditionalCreateCancelIsNotSupportedDecline() {
        val error = appleErrorToPasskeyError(1001, "canceled", conditional = true)
        assertEquals(
            PasskeyError.NotSupported(
                "iOS declined conditional passkey creation; it requires a recent password AutoFill sign-in for this account.",
            ),
            error,
        )
    }

    @Test
    fun authorizationExceptionCarriesConditional() {
        val error = AppleAuthorizationException(1001, "x", presented = false).toPasskeyError(conditional = true)
        assertTrue(error is PasskeyError.NotSupported)
    }

    @Test
    fun conditionalCreateMatchedExcludedIsStillAlreadyRegistered() {
        assertEquals(PasskeyError.AlreadyRegistered, appleErrorToPasskeyError(1006, "x", conditional = true))
    }
}
