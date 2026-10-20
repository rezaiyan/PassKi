package com.alirezaiyan.passki.android

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
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
import kotlin.test.assertIs
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
    fun signalProviderConfigurationIsMisconfiguredWithProviderHint() {
        val error = SignalCredentialStateProviderConfigurationException("no provider").toPasskeyError()
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertEquals("no provider", misconfigured.message)
        assertTrue("credential provider" in misconfigured.fixHint)
    }

    @Test
    fun signalSecurityIsMisconfiguredWithAssetLinksHint() {
        val error = SignalCredentialSecurityException("not allowed").toPasskeyError()
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertEquals("not allowed", misconfigured.message)
        assertTrue("assetlinks.json" in misconfigured.fixHint)
    }

    @Test
    fun noCreateOptionIsMisconfiguredWithProviderHint() {
        val error = CreateCredentialNoCreateOptionException("no option").toPasskeyError()
        val misconfigured = assertIs<PasskeyError.Misconfigured>(error)
        assertTrue("credential provider" in misconfigured.fixHint)
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
}
