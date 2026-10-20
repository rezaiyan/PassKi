package com.alirezaiyan.passki

/**
 * Every way a passkey operation can fail, across all platforms.
 * [fixHint] is non-null when a developer can fix the cause (configuration, options).
 */
public sealed class PasskeyError {
    public abstract val message: String
    public open val fixHint: String? get() = null

    public data object Cancelled : PasskeyError() {
        override val message: String = "The user cancelled the passkey request."
    }

    public data object NoCredentials : PasskeyError() {
        override val message: String = "No passkey for this account is available on this device."
    }

    public data object AlreadyRegistered : PasskeyError() {
        override val message: String =
            "A passkey for this account already exists on this device (excludeCredentials matched)."
    }

    public data object Interrupted : PasskeyError() {
        override val message: String = "The passkey request was interrupted. Retry it."
    }

    public data class NotSupported(override val message: String) : PasskeyError()

    public data class InvalidOptions(override val message: String) : PasskeyError() {
        override val fixHint: String =
            "Pass the server's WebAuthn options JSON unchanged " +
                "(PublicKeyCredentialCreationOptionsJSON / PublicKeyCredentialRequestOptionsJSON). " +
                "Binary fields (challenge, user.id, credential ids) must be base64url."
    }

    public data class Misconfigured(
        override val message: String,
        override val fixHint: String,
    ) : PasskeyError()

    public data class Unknown(
        override val message: String,
        val platformCode: String?,
    ) : PasskeyError()
}

/** Thrown by [getOrThrow] and used internally to short-circuit with a typed error. */
public class PasskeyException(public val error: PasskeyError) : Exception(error.message)
