package com.alirezaiyan.passki.android

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.DomError
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialSecurityException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialStateProviderConfigurationException
import com.alirezaiyan.passki.PasskeyError

internal const val ASSET_LINKS_HINT: String =
    "The RP ID is not associated with this app. Serve https://<rpId>/.well-known/assetlinks.json containing " +
        "relation \"delegate_permission/common.get_login_creds\" with this app's package name and SHA-256 " +
        "signing certificate fingerprint. Debug, release and Play App Signing keys differ; list each one."

private const val PROVIDER_HINT: String =
    "No credential provider is available. Make sure Google Play services is up to date and a provider " +
        "(Google Password Manager, 1Password, ...) is enabled in Settings > Passwords & accounts."

internal fun Throwable.toPasskeyError(): PasskeyError = when (this) {
    is CreateCredentialCancellationException, is GetCredentialCancellationException -> PasskeyError.Cancelled
    is NoCredentialException -> PasskeyError.NoCredentials
    is CreateCredentialInterruptedException, is GetCredentialInterruptedException -> PasskeyError.Interrupted
    is CreateCredentialProviderConfigurationException, is GetCredentialProviderConfigurationException ->
        PasskeyError.Misconfigured(messageOr("No credential provider configured."), PROVIDER_HINT)
    is CreateCredentialNoCreateOptionException ->
        PasskeyError.Misconfigured(messageOr("No provider can create this credential."), PROVIDER_HINT)
    is SignalCredentialStateProviderConfigurationException ->
        PasskeyError.Misconfigured(messageOr("No credential provider configured."), PROVIDER_HINT)
    is SignalCredentialSecurityException ->
        PasskeyError.Misconfigured(messageOr("Signal rejected for this RP ID."), ASSET_LINKS_HINT)
    is CreateCredentialUnsupportedException, is GetCredentialUnsupportedException ->
        PasskeyError.NotSupported(messageOr("Passkeys are not supported on this device."))
    is CreatePublicKeyCredentialDomException -> domError.toPasskeyError(messageOr(domError.type))
    is GetPublicKeyCredentialDomException -> domError.toPasskeyError(messageOr(domError.type))
    is IllegalArgumentException -> PasskeyError.InvalidOptions(messageOr("Invalid request."))
    is CreateCredentialException -> PasskeyError.Unknown(messageOr(type), platformCode = type)
    is GetCredentialException -> PasskeyError.Unknown(messageOr(type), platformCode = type)
    // Rate-limit and unknown signal exceptions land here; their `type` is restricted API, so report the class.
    else -> PasskeyError.Unknown(message ?: this::class.simpleName.orEmpty(), platformCode = this::class.simpleName)
}

private fun DomError.toPasskeyError(message: String): PasskeyError = when (this) {
    is InvalidStateError -> PasskeyError.AlreadyRegistered
    is NotAllowedError -> PasskeyError.Cancelled
    is SecurityError -> PasskeyError.Misconfigured(message, ASSET_LINKS_HINT)
    is NotSupportedError -> PasskeyError.NotSupported(message)
    else -> PasskeyError.Unknown(message, platformCode = type)
}

private fun Throwable.messageOr(fallback: String): String = message?.takeIf { it.isNotBlank() } ?: fallback
