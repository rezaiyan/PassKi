package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
}
