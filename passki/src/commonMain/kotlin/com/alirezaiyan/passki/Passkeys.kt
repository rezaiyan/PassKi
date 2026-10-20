package com.alirezaiyan.passki

/**
 * Entry point. Obtain with [Passkeys]; no platform handles needed.
 *
 * Every `optionsJson` parameter is the WebAuthn options JSON produced by your server,
 * optionally wrapped in `{"publicKey": ...}`.
 * Every returned `json` is ready to POST to your server's verifier.
 */
public interface Passkeys {
    /** What this device/OS supports, so UI can adapt before showing passkey buttons. */
    public suspend fun capabilities(): PasskeyCapabilities

    /** Creates a passkey. */
    public suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration>

    /** Signs in with a passkey. */
    public suspend fun signIn(
        optionsJson: String,
        mode: SignInMode = SignInMode.Modal,
    ): PasskeyResult<PasskeyAssertion>

    /** One system sheet offering passkeys and saved passwords. */
    public suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential>

    /**
     * Silently creates a passkey for a user who just signed in with a password
     * (conditional create). The provider decides whether to create one; no sheet is shown.
     */
    public suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration>

    /** Keeps credential providers in sync with the server (WebAuthn Signal API). */
    public val signals: PasskeySignals
}

/** Creates the [Passkeys] implementation for the current platform. */
public expect fun Passkeys(): Passkeys

public enum class SignInMode {
    /** Standard sheet; shows hybrid (phone) / security-key options when no local passkey exists. */
    Modal,

    /** Only use passkeys already on this device; fails fast with [PasskeyError.NoCredentials] otherwise. */
    ImmediateOnly,
}

public interface PasskeySignals {
    /** The server doesn't recognise [credentialId]; providers may hide or delete it. */
    public suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit>

    /** The complete list of valid credential ids for [userId]; providers may remove others. */
    public suspend fun acceptedCredentials(
        rpId: String,
        userId: String,
        credentialIds: List<String>,
    ): PasskeyResult<Unit>

    /** The user's current name / display name, so providers can update saved passkeys. */
    public suspend fun currentUserDetails(
        rpId: String,
        userId: String,
        name: String,
        displayName: String,
    ): PasskeyResult<Unit>
}

public class PasskeyCapabilities(
    public val isSupported: Boolean,
    public val immediateSignIn: Boolean,
    public val unifiedPasswordSignIn: Boolean,
    public val conditionalCreate: Boolean,
    public val signals: Boolean,
) {
    override fun toString(): String =
        "PasskeyCapabilities(isSupported=$isSupported, immediateSignIn=$immediateSignIn, " +
            "unifiedPasswordSignIn=$unifiedPasswordSignIn, conditionalCreate=$conditionalCreate, signals=$signals)"
}

/** Result of [Passkeys.register]. Send [json] to the server. */
public class PasskeyRegistration(
    public val json: String,
    public val credentialId: String,
    public val transports: List<String>,
    public val authenticatorAttachment: String?,
) {
    override fun toString(): String = "PasskeyRegistration(credentialId=$credentialId, json=$json)"
}

/** Result of [Passkeys.signIn]. Send [json] to the server. */
public class PasskeyAssertion(
    public val json: String,
    public val credentialId: String,
    public val userHandle: String?,
) {
    override fun toString(): String = "PasskeyAssertion(credentialId=$credentialId, json=$json)"
}

public sealed interface SignInCredential {
    public class Passkey(public val assertion: PasskeyAssertion) : SignInCredential {
        override fun toString(): String = "SignInCredential.Passkey($assertion)"
    }

    public class Password(public val id: String, public val password: String) : SignInCredential {
        override fun toString(): String = "SignInCredential.Password(id=$id, password=***)"
    }
}
