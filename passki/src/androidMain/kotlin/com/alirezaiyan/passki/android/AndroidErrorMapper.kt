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
import androidx.credentials.exceptions.domerrors.DataError
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

/** What the caller knew when the request failed; lets the mapper name the exact domain and package in hints. */
internal class AndroidErrorContext(val rpId: String? = null, val packageName: String? = null)

internal fun assetLinksHint(context: AndroidErrorContext): String {
    val rpId = context.rpId?.takeIf { it.isNotBlank() } ?: return ASSET_LINKS_HINT
    val pkg = context.packageName?.takeIf { it.isNotBlank() }?.let { "package $it" } ?: "this app's package"
    return "Serve https://$rpId/.well-known/assetlinks.json with relation \"delegate_permission/common.get_login_creds\" " +
        "listing $pkg with your signing SHA-256 (run ./gradlew passkiFingerprints, or " +
        "PasskeyDoctor.run(\"$rpId\")). Debug, release and Play App Signing keys differ; list each one."
}

private const val PROVIDER_HINT: String =
    "No credential provider is available. Make sure Google Play services is up to date and a provider " +
        "(Google Password Manager, 1Password, ...) is enabled in Settings > Passwords & accounts."

/** [conditional] is true for conditional create (`upgrade()`), where a provider declining is not a setup problem. */
internal fun Throwable.toPasskeyError(
    conditional: Boolean = false,
    context: AndroidErrorContext = AndroidErrorContext(),
): PasskeyError = when (this) {
    is CreateCredentialCancellationException, is GetCredentialCancellationException -> PasskeyError.Cancelled
    is NoCredentialException -> PasskeyError.NoCredentials
    is CreateCredentialInterruptedException, is GetCredentialInterruptedException -> PasskeyError.Interrupted
    is CreateCredentialProviderConfigurationException, is GetCredentialProviderConfigurationException ->
        PasskeyError.Misconfigured(messageOr("No credential provider configured."), PROVIDER_HINT)
    is CreateCredentialNoCreateOptionException ->
        if (conditional) {
            PasskeyError.NotSupported("The credential provider declined conditional passkey creation.")
        } else {
            PasskeyError.Misconfigured(messageOr("No provider can create this credential."), PROVIDER_HINT)
        }
    is SignalCredentialStateProviderConfigurationException ->
        PasskeyError.NotSupported("No credential provider on this device supports the Signal API.")
    is SignalCredentialSecurityException ->
        PasskeyError.Misconfigured(messageOr("Signal rejected for this RP ID."), assetLinksHint(context))
    is CreateCredentialUnsupportedException, is GetCredentialUnsupportedException ->
        PasskeyError.NotSupported(messageOr("Passkeys are not supported on this device."))
    is CreatePublicKeyCredentialDomException -> domError.toPasskeyError(messageOr(domError.type), this, context)
    is GetPublicKeyCredentialDomException -> domError.toPasskeyError(messageOr(domError.type), this, context)
    is IllegalArgumentException -> PasskeyError.InvalidOptions(messageOr("Invalid request."))
    is CreateCredentialException -> PasskeyError.Unknown(messageOr(type), platformCode = type, cause = this)
    is GetCredentialException -> PasskeyError.Unknown(messageOr(type), platformCode = type, cause = this)
    // Rate-limit and unknown signal exceptions land here; their `type` is restricted API, so report the class.
    else -> PasskeyError.Unknown(
        message ?: this::class.simpleName.orEmpty(),
        platformCode = this::class.simpleName,
        cause = this,
    )
}

private fun DomError.toPasskeyError(message: String, cause: Throwable, context: AndroidErrorContext): PasskeyError =
    when (this) {
        is InvalidStateError -> PasskeyError.AlreadyRegistered
        is NotAllowedError -> PasskeyError.Cancelled
        is SecurityError -> PasskeyError.Misconfigured(message, assetLinksHint(context))
        // Credential Manager reports an unverifiable asset-links association as a DataError, not a SecurityError.
        is DataError if message.contains("RP ID cannot be validated", ignoreCase = true) ->
            PasskeyError.Misconfigured(message, assetLinksHint(context))
        is NotSupportedError -> PasskeyError.NotSupported(message)
        else -> PasskeyError.Unknown(message, platformCode = type, cause = cause)
    }

private fun Throwable.messageOr(fallback: String): String = message?.takeIf { it.isNotBlank() } ?: fallback
