package com.alirezaiyan.passki

/**
 * Entry point. Obtain with [Passkeys]; no platform handles needed.
 *
 * Every `optionsJson` parameter is the WebAuthn options JSON produced by your server,
 * optionally wrapped in `{"publicKey": ...}`. It must name the RP ID (`rp.id` in creation options, `rpId` in
 * request options) as a bare domain such as `example.com`: browsers derive it from the page's origin, but apps have
 * no origin, so a missing or malformed RP ID fails with [PasskeyError.InvalidOptions].
 * Every returned `json` is ready to POST to your server's verifier.
 *
 * **Threads and cancellation.**
 * - Every call is main-safe: call it from any dispatcher, including `Dispatchers.Main`. PassKi moves UI work to the
 *   main thread itself and never blocks the calling thread; the result comes back in the calling coroutine.
 * - Cancelling the calling coroutine (e.g. the screen's scope) withdraws the request: PassKi asks the platform to
 *   close the sheet (Android credential providers may keep theirs open), the call ends with `CancellationException`,
 *   and a passkey picked afterwards is discarded. That's different from [PasskeyError.Cancelled], which means the
 *   user closed the sheet.
 * - One sheet at a time: [register], [signIn] (except [SignInMode.Autofill]), [signInOrPassword] and [upgrade] fail
 *   at once with [PasskeyError.Interrupted] while another of these calls is still pending, for example after a double
 *   tap. Autofill sign-in waits for the pending call to finish instead.
 *
 * Implementing this interface is supported (e.g. for fakes; prefer passki-test's FakePasskeys).
 * New members will only ever be added with default implementations, so implementations stay
 * source- and binary-compatible.
 */
public interface Passkeys {
    /**
     * What this device/OS supports, so UI can adapt before showing passkey buttons.
     *
     * Cheap enough to call before every sign-in: on Android and iOS it only reads the OS version; on the web it asks
     * the browser (no UI, no network), and the one slower probe, for immediate sign-in, runs once per page.
     */
    public suspend fun capabilities(): PasskeyCapabilities

    /**
     * Creates a passkey.
     * Typical errors: [PasskeyError.Cancelled], [PasskeyError.AlreadyRegistered], [PasskeyError.Misconfigured],
     * [PasskeyError.InvalidOptions], [PasskeyError.NotSupported].
     *
     * `excludeCredentials` needs iOS 17.4+. Older iOS versions ignore it, so they never report
     * [PasskeyError.AlreadyRegistered] and may save a second passkey for the same account; the server still
     * receives the new credential and can reject or replace the duplicate.
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
     *
     * Conditional creation is opportunistic: providers may decline (e.g. iOS requires a recent password-autofill
     * sign-in). A decline is reported as [PasskeyError.NotSupported] on both platforms: on Android when the
     * credential provider rejects conditional create (Play services may answer with a status such as 17), on iOS
     * when the system declines silently without showing UI. Treat any failure as silent; never show an error to
     * the user.
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
 * Open: new entries may be added in minor releases; keep an `else` branch in exhaustive `when`s.
 */
public enum class SignInMode {
    /** Standard sheet; shows hybrid (phone) / security-key options when no local passkey exists. */
    Modal,

    /** Only use passkeys already on this device; fails fast with [PasskeyError.NoCredentials] otherwise. */
    ImmediateOnly,

    /**
     * Offers passkeys as suggestions in the keyboard / QuickType bar of the focused username field
     * (WebAuthn "conditional mediation"). Requires [PasskeyCapabilities.autofill]: Android 15 (API 35)+, iOS 16+.
     *
     * [Passkeys.signIn] suspends until the user picks a passkey suggestion. Dismissing the suggestion or the
     * sheet does not complete the call; it keeps waiting. Cancel the calling coroutine to withdraw the offer,
     * e.g. when the field loses focus or leaves composition. On Android, after a dismissal the suggestion is
     * offered again once you cancel and call again (e.g. on the next focus of the field).
     *
     * A username text field must be focused when you call it: use `PasskeyUsernameField` from passki-compose,
     * or focus your own username field (content type username) first. Without one, Android fails with
     * [PasskeyError.Misconfigured].
     */
    Autofill,
}

/**
 * Tells credential providers about server-side changes (WebAuthn Signal API). Requires [PasskeyCapabilities.signals].
 *
 * Every signal returns [SignalOutcome.Applied] when the provider took it, or [SignalOutcome.Skipped] when this OS or
 * provider can't apply it. A skipped signal is normal: never show it to users. A [PasskeyResult.Failure] means
 * something you can fix or should log ([PasskeyError.Misconfigured], [PasskeyError.InvalidOptions],
 * [PasskeyError.Unknown]), so `signals.syncAccount(...).onFailure { log(it) }` is enough.
 *
 * `userHandle` is the WebAuthn user handle: the base64url `user.id` your server put in the creation options, also
 * returned as [PasskeyAssertion.userHandle]. Never pass your account id or an email address. Credential IDs are the
 * base64url [PasskeyRegistration.credentialId] / [PasskeyAssertion.credentialId].
 *
 * Signals show no sheet. Like every [Passkeys] call they are main-safe, and cancelling the calling coroutine ends the
 * call with `CancellationException`; they don't take part in the one-sheet-at-a-time rule.
 *
 * Implementing this interface is supported (e.g. for fakes; prefer passki-test's FakePasskeys).
 * New members will only ever be added with default implementations, so implementations stay
 * source- and binary-compatible.
 */
public interface PasskeySignals {
    /** The server doesn't recognise [credentialId]; providers may hide or delete it. */
    public suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<SignalOutcome>

    /** The complete list of valid credential IDs for [userHandle]; providers may remove others. */
    public suspend fun acceptedCredentials(
        rpId: String,
        userHandle: String,
        credentialIds: List<String>,
    ): PasskeyResult<SignalOutcome>

    /** The user's current name / display name, so providers can update saved passkeys. */
    public suspend fun currentUserDetails(
        rpId: String,
        userHandle: String,
        name: String,
        displayName: String,
    ): PasskeyResult<SignalOutcome>

    /**
     * Tells the provider everything it should know about one account: its complete list of passkeys
     * ([acceptedCredentials]) and its current label ([currentUserDetails]). Call it whenever you load the account's
     * passkeys, for example when the passkey settings screen opens.
     *
     * Returns [SignalOutcome.Applied] if the provider took at least one of the two signals, [SignalOutcome.Skipped]
     * if it could take neither, and the first [PasskeyResult.Failure] otherwise (the remaining signal isn't sent).
     */
    public suspend fun syncAccount(
        rpId: String,
        userHandle: String,
        credentialIds: List<String>,
        name: String,
        displayName: String,
    ): PasskeyResult<SignalOutcome> {
        val accepted = when (val result = acceptedCredentials(rpId, userHandle, credentialIds)) {
            is PasskeyResult.Failure -> return result
            is PasskeyResult.Success -> result.value
        }
        val details = when (val result = currentUserDetails(rpId, userHandle, name, displayName)) {
            is PasskeyResult.Failure -> return result
            is PasskeyResult.Success -> result.value
        }
        return PasskeyResult.Success(
            if (accepted == SignalOutcome.Applied || details == SignalOutcome.Applied) SignalOutcome.Applied else accepted,
        )
    }
}

/**
 * What a [PasskeySignals] call did.
 *
 * Closed: a signal is either applied or skipped; a `when` over it needs no `else` branch.
 */
public sealed interface SignalOutcome {
    /** The credential provider took the signal. */
    public data object Applied : SignalOutcome

    /** This OS or credential provider can't apply the signal; [reason] says why. Nothing to show the user. */
    public class Skipped(public val reason: String) : SignalOutcome {
        override fun equals(other: Any?): Boolean = other is Skipped && reason == other.reason
        override fun hashCode(): Int = reason.hashCode()
        override fun toString(): String = "Skipped(reason=$reason)"
    }
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
    /**
     * [Passkeys.upgrade] (conditional create) is available.
     * On Android this reflects OS support; the user's credential provider may still decline (operation then returns NotSupported).
     */
    public val conditionalCreate: Boolean,
    /**
     * [PasskeySignals] calls are available.
     * On Android this reflects OS support; the user's credential provider may still decline (operation then returns NotSupported).
     */
    public val signals: Boolean,
    /** [SignInMode.Autofill] (passkey suggestions in the username field's keyboard) is available. */
    public val autofill: Boolean,
) {
    override fun toString(): String =
        "PasskeyCapabilities(isSupported=$isSupported, immediateSignIn=$immediateSignIn, " +
            "unifiedPasswordSignIn=$unifiedPasswordSignIn, conditionalCreate=$conditionalCreate, signals=$signals, " +
            "autofill=$autofill)"
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
    /**
     * Whether the new passkey supports the `prf` extension, when it was requested
     * ([PasskeyExtensions.withPrf]); null if not requested or the platform did not answer.
     */
    public val prfEnabled: Boolean? = null,
    /** PRF results, when salts were given at registration and the provider evaluated them; otherwise null. */
    public val prf: PrfResults? = null,
    /**
     * Whether the new passkey has large-blob storage, when it was requested ([PasskeyExtensions.withLargeBlob]);
     * null if not requested or the platform did not answer.
     */
    public val largeBlobSupported: Boolean? = null,
    /**
     * The RP ID this ceremony ran for: the options' RP ID, or on the web the page's host when the options name none.
     * Pass it to [PasskeySignals] calls instead of parsing the options again.
     */
    public val rpId: String,
) {
    override fun toString(): String =
        "PasskeyRegistration(credentialId=$credentialId, rpId=$rpId, json=<${json.length} chars>)"
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
    /** PRF results for the salts from [PasskeyExtensions.withPrf]; null if not requested or not supported. */
    public val prf: PrfResults? = null,
    /**
     * The passkey's large blob read with [PasskeyExtensions.withLargeBlobRead]; null if not requested, not
     * supported, or none is stored. Treat it as a secret.
     */
    public val largeBlob: ByteArray? = null,
    /**
     * Whether the blob from [PasskeyExtensions.withLargeBlobWrite] was stored; null if no write was requested or
     * the platform did not answer.
     */
    public val largeBlobWritten: Boolean? = null,
    /**
     * The RP ID this ceremony ran for: the options' RP ID, or on the web the page's host when the options name none.
     * Pass it to [PasskeySignals] calls instead of parsing the options again.
     */
    public val rpId: String,
) {
    override fun toString(): String = "PasskeyAssertion(credentialId=$credentialId, rpId=$rpId, json=<${json.length} chars>)"
}

/**
 * What the user picked in [Passkeys.signInOrPassword].
 *
 * Open: new subtypes may be added in minor releases; keep an `else` branch in exhaustive `when`s.
 */
public sealed interface SignInCredential {
    /**
     * The user chose a passkey; send [assertion] to your server.
     * Instances are created by PassKi; the constructor is internal API so fields can be added without breaking binaries.
     */
    public class Passkey @InternalPasskiApi constructor(public val assertion: PasskeyAssertion) : SignInCredential {
        override fun toString(): String = "SignInCredential.Passkey($assertion)"
    }

    /**
     * The user chose a saved password.
     * Instances are created by PassKi; the constructor is internal API so fields can be added without breaking binaries.
     */
    public class Password @InternalPasskiApi constructor(public val id: String, public val password: String) : SignInCredential {
        override fun toString(): String = "SignInCredential.Password(id=$id, password=***)"
    }
}
