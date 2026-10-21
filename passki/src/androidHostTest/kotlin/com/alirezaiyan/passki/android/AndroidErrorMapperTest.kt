package com.alirezaiyan.passki.android

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.DataError
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialRateLimitExceededException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialSecurityException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialStateProviderConfigurationException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialUnknownException
import com.alirezaiyan.passki.PasskeyError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidErrorMapperTest {
    @Test
    fun createCancellationIsCancelled() {
        assertEquals(PasskeyError.Cancelled, CreateCredentialCancellationException("x").toPasskeyError())
    }

    @Test
    fun getCancellationIsCancelled() {
        assertEquals(PasskeyError.Cancelled, GetCredentialCancellationException("x").toPasskeyError())
    }

    @Test
    fun noCredentialIsNoCredentials() {
        assertEquals(PasskeyError.NoCredentials, NoCredentialException("x").toPasskeyError())
    }

    @Test
    fun interruptedIsInterrupted() {
        assertEquals(PasskeyError.Interrupted, CreateCredentialInterruptedException("x").toPasskeyError())
    }

    @Test
    fun invalidStateDomErrorIsAlreadyRegistered() {
        val error = CreatePublicKeyCredentialDomException(InvalidStateError(), "exists").toPasskeyError()
        assertEquals(PasskeyError.AlreadyRegistered, error)
    }

    @Test
    fun notAllowedDomErrorIsCancelled() {
        val error = GetPublicKeyCredentialDomException(NotAllowedError(), "dismissed").toPasskeyError()
        assertEquals(PasskeyError.Cancelled, error)
    }

    @Test
    fun securityDomErrorIsMisconfiguredWithAssetLinksHint() {
        val error = GetPublicKeyCredentialDomException(SecurityError(), "rp mismatch").toPasskeyError()
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertTrue("assetlinks.json" in misconfigured.fixHint)
    }

    @Test
    fun providerConfigurationIsMisconfigured() {
        val error = CreateCredentialProviderConfigurationException("no provider").toPasskeyError()
        assertIs<PasskeyError.Misconfigured>(error)
    }

    @Test
    fun unsupportedIsNotSupported() {
        assertIs<PasskeyError.NotSupported>(GetCredentialUnsupportedException("old").toPasskeyError())
    }

    @Test
    fun unknownCreateExceptionKeepsMessageAndType() {
        val error = assertIs<PasskeyError.Unknown>(CreateCredentialUnknownException("boom").toPasskeyError())
        assertEquals("boom", error.message)
        assertTrue(error.platformCode!!.isNotEmpty())
    }

    @Test
    fun illegalArgumentIsInvalidOptions() {
        assertIs<PasskeyError.InvalidOptions>(IllegalArgumentException("bad json").toPasskeyError())
    }

    @Test
    fun signalProviderConfigurationIsNotSupported() {
        val error = SignalCredentialStateProviderConfigurationException("no provider").toPasskeyError()
        val notSupported = assertIs<PasskeyError.NotSupported>(error)
        assertEquals("No credential provider on this device supports the Signal API.", notSupported.message)
    }

    @Test
    fun signalSecurityIsMisconfiguredWithAssetLinksHint() {
        val error = SignalCredentialSecurityException("not allowed").toPasskeyError()
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertEquals("not allowed", misconfigured.message)
        assertTrue("assetlinks.json" in misconfigured.fixHint)
    }

    @Test
    fun noCreateOptionFromRegisterIsMisconfiguredWithProviderHint() {
        val error = CreateCredentialNoCreateOptionException("no option").toPasskeyError()
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertTrue("credential provider" in misconfigured.fixHint)
    }

    @Test
    fun noCreateOptionFromUpgradeIsNotSupported() {
        val error = CreateCredentialNoCreateOptionException("no option").toPasskeyError(conditional = true)
        val notSupported = assertIs<PasskeyError.NotSupported>(error)
        assertEquals("The credential provider declined conditional passkey creation.", notSupported.message)
    }

    @Test
    fun signalRateLimitStaysUnknownWithType() {
        val error = assertIs<PasskeyError.Unknown>(SignalCredentialRateLimitExceededException(1000L, "slow").toPasskeyError())
        assertEquals("SignalCredentialRateLimitExceededException", error.platformCode)
    }

    @Test
    fun signalUnknownStaysUnknownWithType() {
        val error = assertIs<PasskeyError.Unknown>(SignalCredentialUnknownException("boom").toPasskeyError())
        assertEquals("boom", error.message)
        assertEquals("SignalCredentialUnknownException", error.platformCode)
    }

    @Test
    fun unknownKeepsOriginalCause() {
        val original = CreateCredentialUnknownException("boom")
        assertSame(original, assertIs<PasskeyError.Unknown>(original.toPasskeyError()).cause)
        val other = RuntimeException("x")
        assertSame(other, assertIs<PasskeyError.Unknown>(other.toPasskeyError()).cause)
    }

    @Test
    fun rpIdCannotBeValidatedDataErrorIsMisconfigured() {
        val error = CreatePublicKeyCredentialDomException(DataError(), "RP ID cannot be validated.").toPasskeyError()
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertEquals("RP ID cannot be validated.", misconfigured.message)
        assertEquals(ASSET_LINKS_HINT, misconfigured.fixHint)
    }

    @Test
    fun rpIdValidationMatchIsCaseInsensitive() {
        val error = GetPublicKeyCredentialDomException(DataError(), "rp id CANNOT be validated").toPasskeyError()
        assertIs<PasskeyError.Misconfigured>(error)
    }

    @Test
    fun otherDataErrorsStayUnknown() {
        val error = CreatePublicKeyCredentialDomException(DataError(), "bad data").toPasskeyError()
        assertIs<PasskeyError.Unknown>(error)
    }

    @Test
    fun rpIdValidationHintIsConcreteWithContext() {
        val error = CreatePublicKeyCredentialDomException(DataError(), "RP ID cannot be validated.")
            .toPasskeyError(context = AndroidErrorContext(rpId = "alirezaiyan.com", packageName = "com.alirezaiyan.passki"))
        val hint = assertIs<PasskeyError.Misconfigured>(error).fixHint
        assertTrue("https://alirezaiyan.com/.well-known/assetlinks.json" in hint, hint)
        assertTrue("package com.alirezaiyan.passki" in hint, hint)
        assertTrue("./gradlew passkiFingerprints" in hint, hint)
        assertTrue("PasskeyDoctor.run(\"alirezaiyan.com\")" in hint, hint)
        assertFalse("<rpId>" in hint, hint)
    }

    @Test
    fun rpIdOnlyContextStillProducesConcreteUrl() {
        val error = GetPublicKeyCredentialDomException(SecurityError(), "rp mismatch")
            .toPasskeyError(context = AndroidErrorContext(rpId = "example.com", packageName = null))
        val hint = assertIs<PasskeyError.Misconfigured>(error).fixHint
        assertTrue("https://example.com/.well-known/assetlinks.json" in hint, hint)
        assertTrue("this app's package" in hint, hint)
    }

    @Test
    fun signalSecurityUsesContextHint() {
        val error = SignalCredentialSecurityException("not allowed")
            .toPasskeyError(context = AndroidErrorContext(rpId = "example.com", packageName = "com.example"))
        assertTrue("https://example.com/.well-known/assetlinks.json" in assertIs<PasskeyError.Misconfigured>(error).fixHint)
    }
}
