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
import com.alirezaiyan.passki.internal.doctor.CheckIds

internal const val ASSET_LINKS_HINT: String =
    "The RP ID is not associated with this app. Serve https://<rpId>/.well-known/assetlinks.json containing " +
        "relation \"delegate_permission/common.get_login_creds\" with this app's package name and SHA-256 " +
        "signing certificate fingerprint. Debug, release and Play App Signing keys differ; list each one."

/**
 * What the caller knew when the request failed; lets the mapper name the exact domain, package and signing keys in
 * hints. [signingFingerprints] are the app's current signing certificates (colon hex), empty when they couldn't be
 * read. The values are gathered by the caller so the mapper stays pure and host-testable.
 */
internal class AndroidErrorContext(
    val rpId: String? = null,
    val packageName: String? = null,
    val signingFingerprints: List<String> = emptyList(),
    val isEmulator: Boolean = false,
)

internal const val EMULATOR_NOTE: String =
    "On an emulator: some emulator images fail RP ID validation even when assetlinks.json is correct. If " +
        "PasskeyDoctor passes and the fingerprint above is listed, try a real device or another emulator image."

internal fun assetLinksHint(context: AndroidErrorContext): String {
    val rpId = context.rpId?.takeIf { it.isNotBlank() }
    val base = if (rpId == null) {
        ASSET_LINKS_HINT
    } else {
        val pkg = context.packageName?.takeIf { it.isNotBlank() }?.let { "package $it" } ?: "this app's package"
        "Serve https://$rpId/.well-known/assetlinks.json with relation \"delegate_permission/common.get_login_creds\" " +
            "listing $pkg with your signing SHA-256 (run ./gradlew passkiFingerprints, or " +
            "PasskeyDoctor.run(\"$rpId\")). Debug, release and Play App Signing keys differ; list each one."
    }
    val evidence = signingEvidence(context.signingFingerprints)
    val emulator = if (context.isEmulator) " $EMULATOR_NOTE" else ""
    return "$base $evidence$emulator"
}

/** "This app is signed with SHA-256 A (origin X) and B (origin Y)." — the values to copy into assetlinks.json. */
private fun signingEvidence(fingerprints: List<String>): String {
    if (fingerprints.isEmpty()) {
        return "PassKi couldn't read this app's signing certificate here; ./gradlew passkiFingerprints and " +
            "PasskeyDoctor show it."
    }
    val keys = fingerprints.joinToString(" and ") { "$it (origin ${androidOrigin(it)})" }
    return "This app is signed with SHA-256 $keys."
}

private const val PROVIDER_HINT: String =
    "No credential provider is available. Make sure Google Play services is up to date and a provider " +
        "(Google Password Manager, 1Password, ...) is enabled in Settings > Passwords & accounts."

private const val CONDITIONAL_DECLINED: String = "The credential provider declined conditional passkey creation"

/**
 * [conditional] is true for conditional create (`upgrade()`), where a provider declining is not a setup problem:
 * any NotSupported or Unknown outcome is reported as [PasskeyError.NotSupported] with a short platform detail
 * (e.g. Play services' raw "API is not supported: 17: " becomes "status 17").
 */
internal fun Throwable.toPasskeyError(
    conditional: Boolean = false,
    context: AndroidErrorContext = AndroidErrorContext(),
): PasskeyError {
    val error = toBasePasskeyError(conditional, context)
    if (!conditional || this is CreateCredentialNoCreateOptionException) return error
    return when (error) {
        is PasskeyError.NotSupported, is PasskeyError.Unknown -> {
            val detail = conditionalDeclineDetail(message)
            PasskeyError.NotSupported(if (detail == null) "$CONDITIONAL_DECLINED." else "$CONDITIONAL_DECLINED ($detail).")
        }
        else -> error
    }
}

private val STATUS_CODE = Regex("""(?:^|:)\s*(\d+)\s*(?::|$)""")

/** A short, human detail from a provider message: "status N" when a numeric status is embedded, else the trimmed text. */
internal fun conditionalDeclineDetail(message: String?): String? {
    val text = message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    STATUS_CODE.find(text)?.let { return "status ${it.groupValues[1]}" }
    if (text.startsWith("android.credentials.") || text.startsWith("androidx.credentials.")) return null
    return text.take(120)
}

private fun Throwable.toBasePasskeyError(conditional: Boolean, context: AndroidErrorContext): PasskeyError = when (this) {
    is CreateCredentialCancellationException, is GetCredentialCancellationException -> PasskeyError.Cancelled
    is NoCredentialException -> PasskeyError.NoCredentials
    is CreateCredentialInterruptedException, is GetCredentialInterruptedException -> PasskeyError.Interrupted
    is CreateCredentialProviderConfigurationException, is GetCredentialProviderConfigurationException ->
        PasskeyError.Misconfigured(messageOr("No credential provider configured."), PROVIDER_HINT, CheckIds.DEV_01)
    is CreateCredentialNoCreateOptionException ->
        if (conditional) {
            PasskeyError.NotSupported("$CONDITIONAL_DECLINED.")
        } else {
            PasskeyError.Misconfigured(messageOr("No provider can create this credential."), PROVIDER_HINT, CheckIds.DEV_01)
        }
    is SignalCredentialStateProviderConfigurationException ->
        PasskeyError.NotSupported("No credential provider on this device supports the Signal API.")
    is SignalCredentialSecurityException ->
        PasskeyError.Misconfigured(messageOr("Signal rejected for this RP ID."), assetLinksHint(context), CheckIds.AND_03)
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
        is SecurityError -> PasskeyError.Misconfigured(message, assetLinksHint(context), CheckIds.AND_03)
        // Credential Manager reports an unverifiable asset-links association as a DataError, not a SecurityError.
        is DataError if message.contains("RP ID cannot be validated", ignoreCase = true) ->
            PasskeyError.Misconfigured(message, assetLinksHint(context), CheckIds.AND_03)
        is NotSupportedError -> PasskeyError.NotSupported(message)
        else -> PasskeyError.Unknown(message, platformCode = type, cause = cause)
    }

private fun Throwable.messageOr(fallback: String): String = message?.takeIf { it.isNotBlank() } ?: fallback
