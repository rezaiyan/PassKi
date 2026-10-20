package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError

internal const val AASA_HINT: String =
    "Add the Associated Domains entitlement 'webcredentials:<rpId>' to your app target and serve " +
        "https://<rpId>/.well-known/apple-app-site-association with {\"webcredentials\":{\"apps\":[\"<TeamID>.<BundleID>\"]}}. " +
        "For local testing use 'webcredentials:<rpId>?mode=developer' and enable Associated Domains Development in " +
        "Settings > Developer."

internal class AppleAuthorizationException(val code: Long, message: String) : Exception(message)

internal fun appleErrorToPasskeyError(code: Long, message: String): PasskeyError = when {
    message.contains("not associated with domain", ignoreCase = true) -> PasskeyError.Misconfigured(message, AASA_HINT)
    code == 1001L -> PasskeyError.Cancelled
    code == 1005L -> PasskeyError.NoCredentials
    code == 1006L -> PasskeyError.AlreadyRegistered
    else -> PasskeyError.Unknown(message, platformCode = "ASAuthorizationError.$code")
}

internal fun Throwable.toPasskeyError(): PasskeyError = when (this) {
    is AppleAuthorizationException -> appleErrorToPasskeyError(code, message.orEmpty())
    is IllegalArgumentException -> PasskeyError.InvalidOptions(message ?: "Invalid request.")
    else -> PasskeyError.Unknown(message ?: this::class.simpleName.orEmpty(), platformCode = null)
}
