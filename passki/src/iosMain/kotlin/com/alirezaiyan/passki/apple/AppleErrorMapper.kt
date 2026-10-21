package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError

internal const val AASA_HINT: String =
    "Add the Associated Domains entitlement 'webcredentials:<rpId>' to your app target and serve " +
        "https://<rpId>/.well-known/apple-app-site-association with {\"webcredentials\":{\"apps\":[\"<TeamID>.<BundleID>\"]}}. " +
        "For local testing use 'webcredentials:<rpId>?mode=developer' and enable Associated Domains Development in " +
        "Settings > Developer."

private val BOILERPLATE = Regex("""^\s*The operation couldn['’]t be completed\.\s*""", RegexOption.IGNORE_CASE)

private val NOT_ASSOCIATED = Regex("""identifier (\S+) is not associated with domain (\S+?)[.,;:!)\s]*$""", RegexOption.IGNORE_CASE)

/** Removes Apple's generic "The operation couldn't be completed." prefix; keeps the message if nothing else remains. */
internal fun stripAppleBoilerplate(message: String): String =
    message.replaceFirst(BOILERPLATE, "").trim().ifEmpty { message }

internal fun concreteAasaHint(appId: String, domain: String): String =
    "Add the Associated Domains entitlement 'webcredentials:$domain' to the app target " +
        "('webcredentials:$domain?mode=developer' for local testing, with Associated Domains Development enabled " +
        "in Settings > Developer), and serve https://$domain/.well-known/apple-app-site-association containing " +
        "{\"webcredentials\":{\"apps\":[\"$appId\"]}}."

private fun associationError(message: String): PasskeyError.Misconfigured {
    val match = NOT_ASSOCIATED.find(message) ?: return PasskeyError.Misconfigured(message, AASA_HINT)
    val (appId, domain) = match.destructured
    return PasskeyError.Misconfigured("App $appId is not associated with $domain.", concreteAasaHint(appId, domain))
}

/** [presented] is true once the system asked for a presentation anchor, i.e. a sheet was (about to be) shown. */
internal class AppleAuthorizationException(val code: Long, message: String, val presented: Boolean) : Exception(message)

/**
 * Maps an ASAuthorizationError code. With [immediateOnly], iOS reports "nothing to show" as a cancel
 * (1001) without ever presenting a sheet; that case is [PasskeyError.NoCredentials], not a user cancel.
 */
internal fun appleErrorToPasskeyError(
    code: Long,
    message: String,
    immediateOnly: Boolean = false,
    presented: Boolean = true,
    cause: Throwable? = null,
): PasskeyError = appleErrorToPasskeyErrorInternal(code, stripAppleBoilerplate(message), immediateOnly, presented, cause)

private fun appleErrorToPasskeyErrorInternal(
    code: Long,
    message: String,
    immediateOnly: Boolean,
    presented: Boolean,
    cause: Throwable?,
): PasskeyError = when {
    message.contains("not associated with domain", ignoreCase = true) -> associationError(message)
    code == 1001L && immediateOnly && !presented -> PasskeyError.NoCredentials
    code == 1001L -> PasskeyError.Cancelled
    code == 1005L -> PasskeyError.NoCredentials
    code == 1006L -> PasskeyError.AlreadyRegistered
    code == 1010L -> PasskeyError.NotSupported(
        "This device is not configured for passkey creation (e.g. no passcode or iCloud Keychain off).",
    )
    else -> PasskeyError.Unknown(message, platformCode = "ASAuthorizationError.$code", cause = cause)
}

internal fun Throwable.toPasskeyError(immediateOnly: Boolean = false): PasskeyError = when (this) {
    is AppleAuthorizationException -> appleErrorToPasskeyError(code, message.orEmpty(), immediateOnly, presented, cause = this)
    is IllegalArgumentException -> PasskeyError.InvalidOptions(message ?: "Invalid request.")
    else -> PasskeyError.Unknown(message ?: this::class.simpleName.orEmpty(), platformCode = null, cause = this)
}
