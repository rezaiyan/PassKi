package com.alirezaiyan.passki

/**
 * Entry point. Obtain with [Passkeys]; no platform handles needed.
 *
 * Every `optionsJson` parameter is the WebAuthn options JSON produced by your server,
 * optionally wrapped in `{"publicKey": ...}`.
 * Every returned `json` is ready to POST to your server's verifier.
 *
 * Implementing this interface is supported (e.g. for fakes; prefer passki-test's FakePasskeys).
 * New members will only ever be added with default implementations, so implementations stay
 * source- and binary-compatible.
 */
public interface Passkeys {
    /** What this device/OS supports, so UI can adapt before showing passkey buttons. */
    public suspend fun capabilities(): PasskeyCapabilities

    /**
     * Creates a passkey.
     * Typical errors: [PasskeyError.Cancelled], [PasskeyError.AlreadyRegistered], [PasskeyError.Misconfigured],
     * [PasskeyError.InvalidOptions], [PasskeyError.NotSupported].
     */
    public suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration>

    /**
     * Signs in with a passkey.
     * Typical errors: [PasskeyError.Cancelled], [PasskeyError.NoCredentials], [PasskeyError.Misconfigured],
     * [PasskeyError.InvalidOptions], [PasskeyError.NotSupported].
     */
    public suspend fun signIn(
        optionsJson: String,
        mode: SignInMode = SignInMode.Modal,
    ): PasskeyResult<PasskeyAssertion>

    /**
     * One system sheet offering passkeys and saved passwords. Requires [PasskeyCapabilities.unifiedPasswordSignIn].
     * Typical errors: [PasskeyError.Cancelled], [PasskeyError.NoCredentials], [PasskeyError.InvalidOptions],
     * [PasskeyError.NotSupported].
     */
    public suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential>

    /**
     * Silently creates a passkey for a user who just signed in with a password
     * (conditional create). The provider decides whether to create one; no sheet is shown.
     * Requires [PasskeyCapabilities.conditionalCreate].
     * Typical errors: [PasskeyError.NotSupported], [PasskeyError.AlreadyRegistered], [PasskeyError.InvalidOptions],
     * [PasskeyError.Misconfigured].
     */
    public suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration>

    /** Keeps credential providers in sync with the server (WebAuthn Signal API). */
    public val signals: PasskeySignals
}

/** Creates the [Passkeys] implementation for the current platform. */
public expect fun Passkeys(): Passkeys

/**
 * How [Passkeys.signIn] presents itself.
 *
 * New entries may be added in minor releases — keep an `else` branch in exhaustive `when`s.
 */
public enum class SignInMode {
    /** Standard sheet; shows hybrid (phone) / security-key options when no local passkey exists. */
    Modal,

    /** Only use passkeys already on this device; fails fast with [PasskeyError.NoCredentials] otherwise. */
    ImmediateOnly,
}

/**
 * Tells credential providers about server-side changes (WebAuthn Signal API). Requires [PasskeyCapabilities.signals].
 *
 * Implementing this interface is supported (e.g. for fakes; prefer passki-test's FakePasskeys).
 * New members will only ever be added with default implementations, so implementations stay
 * source- and binary-compatible.
 */
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

/**
 * What this device/OS supports. Instances are created by PassKi; the constructor is internal API
 * so fields can be added without breaking binaries.
 */
public class PasskeyCapabilities @InternalPasskiApi constructor(
    /** Passkeys can be used at all. When false, every operation fails with [PasskeyError.NotSupported]. */
    public val isSupported: Boolean,
    /** [SignInMode.ImmediateOnly] is available. */
    public val immediateSignIn: Boolean,
    /** [Passkeys.signInOrPassword] is available. */
    public val unifiedPasswordSignIn: Boolean,
    /** [Passkeys.upgrade] (conditional create) is available. */
    public val conditionalCreate: Boolean,
    /** [PasskeySignals] calls are available. */
    public val signals: Boolean,
) {
    override fun toString(): String =
        "PasskeyCapabilities(isSupported=$isSupported, immediateSignIn=$immediateSignIn, " +
            "unifiedPasswordSignIn=$unifiedPasswordSignIn, conditionalCreate=$conditionalCreate, signals=$signals)"
}

/**
 * Result of [Passkeys.register]. Send [json] to the server.
 * Instances are created by PassKi; the constructor is internal API so fields can be added without breaking binaries.
 */
public class PasskeyRegistration @InternalPasskiApi constructor(
    /** The WebAuthn registration response JSON, ready to POST to your server. */
    public val json: String,
    /** The new credential's id, base64url. */
    public val credentialId: String,
    /** Transports the authenticator reported (e.g. "internal", "hybrid"); may be empty. */
    public val transports: List<String>,
    /** "platform" or "cross-platform" when known, otherwise null. */
    public val authenticatorAttachment: String?,
) {
    override fun toString(): String = "PasskeyRegistration(credentialId=$credentialId, json=<${json.length} chars>)"
}

/**
 * Result of [Passkeys.signIn]. Send [json] to the server.
 * Instances are created by PassKi; the constructor is internal API so fields can be added without breaking binaries.
 */
public class PasskeyAssertion @InternalPasskiApi constructor(
    /** The WebAuthn assertion response JSON, ready to POST to your server. */
    public val json: String,
    /** The credential id used, base64url. */
    public val credentialId: String,
    /** The user handle (user.id) stored with the passkey, base64url, if any. */
    public val userHandle: String?,
) {
    override fun toString(): String = "PasskeyAssertion(credentialId=$credentialId, json=<${json.length} chars>)"
}

/**
 * What the user picked in [Passkeys.signInOrPassword].
 *
 * New subtypes may be added in minor releases — keep an `else` branch in exhaustive `when`s.
 */
public sealed interface SignInCredential {
    /** The user chose a passkey; send [assertion] to your server. */
    public class Passkey(public val assertion: PasskeyAssertion) : SignInCredential {
        override fun toString(): String = "SignInCredential.Passkey($assertion)"
    }

    /** The user chose a saved password. */
    public class Password(public val id: String, public val password: String) : SignInCredential {
        override fun toString(): String = "SignInCredential.Password(id=$id, password=***)"
    }
}
