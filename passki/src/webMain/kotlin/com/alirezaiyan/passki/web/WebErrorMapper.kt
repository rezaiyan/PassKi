package com.alirezaiyan.passki.web

import com.alirezaiyan.passki.PasskeyError

/** Which call failed; the same `DOMException` means different things for each. */
internal enum class WebOperation { Register, Upgrade, SignIn, ImmediateSignIn, Autofill, Signal }

internal const val RP_ID_HINT: String =
    "The RP ID must be the page's domain or a registrable suffix of it (a page on https://login.example.com can use " +
        "example.com, not other.com), and the page must be served over HTTPS or from localhost. To use one RP ID " +
        "across several domains, list them in https://<rpId>/.well-known/webauthn (related origins)."

private const val CONDITIONAL_DECLINED: String = "The browser declined conditional passkey creation"

/**
 * Maps a WebAuthn rejection (a `DOMException` [name] such as `NotAllowedError`) to a [PasskeyError].
 *
 * Browsers deliberately report a dismissed sheet, a timeout and "no passkey for this site" all as `NotAllowedError`,
 * so a modal request maps it to [PasskeyError.Cancelled]. With immediate mediation it means no passkey was available.
 */
internal fun webPasskeyError(name: String, message: String, operation: WebOperation): PasskeyError {
    val error = baseError(name, message.ifBlank { name }, operation)
    if (operation != WebOperation.Upgrade) return error
    // Conditional create is opportunistic: a decline is not an error to show (see Passkeys.upgrade).
    return when (error) {
        is PasskeyError.Cancelled, is PasskeyError.NotSupported, is PasskeyError.Unknown ->
            PasskeyError.NotSupported("$CONDITIONAL_DECLINED ($name).")
        else -> error
    }
}

private fun baseError(name: String, message: String, operation: WebOperation): PasskeyError = when (name) {
    "NotAllowedError" -> when (operation) {
        WebOperation.ImmediateSignIn -> PasskeyError.NoCredentials
        WebOperation.Signal -> PasskeyError.NotSupported(message)
        else -> PasskeyError.Cancelled
    }
    "InvalidStateError" -> when (operation) {
        WebOperation.Register, WebOperation.Upgrade -> PasskeyError.AlreadyRegistered
        else -> PasskeyError.Unknown(message, platformCode = name)
    }
    "AbortError" -> PasskeyError.Interrupted
    "SecurityError" -> PasskeyError.Misconfigured(message, RP_ID_HINT)
    // ConstraintError: the options require something (user verification, a discoverable credential) no authenticator offers.
    "NotSupportedError", "ConstraintError" -> PasskeyError.NotSupported(message)
    // Options were validated before the call, so a TypeError here means the browser rejects mediation "immediate".
    "TypeError" if operation == WebOperation.ImmediateSignIn -> PasskeyError.NotSupported(message)
    "TypeError", "SyntaxError", "DataError", "EncodingError", "InvalidCharacterError" ->
        PasskeyError.InvalidOptions(message)
    else -> PasskeyError.Unknown(message, platformCode = name)
}
