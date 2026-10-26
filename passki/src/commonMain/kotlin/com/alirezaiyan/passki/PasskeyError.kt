package com.alirezaiyan.passki

/**
 * Every way a passkey operation can fail, across all platforms.
 * [fixHint] is non-null when a developer can fix the cause (configuration, options). [checkId] names the setup check
 * from the PassKi check catalog that covers the cause, the same ID `PasskeyDoctor`, `./gradlew passkiCheck` and the web
 * verifier report, and [docsUrl] links its docs page.
 *
 * Open: new subtypes may be added in minor releases; keep an `else` branch in exhaustive `when`s.
 */
public sealed class PasskeyError {
    /** Human-readable description, suitable for logs. */
    public abstract val message: String

    /** What a developer should change to fix this error, or null when it is not actionable. */
    public open val fixHint: String? get() = null

    /**
     * The catalog check that covers this error (e.g. `AND-03`, `IOS-02`, `CFG-01`), or null when no setup check does.
     * Run `PasskeyDoctor` or `./gradlew passkiCheck` to see which part of that check fails.
     */
    public open val checkId: String? get() = null

    /** The docs page for [checkId]: what the check means and how to fix it. Null when [checkId] is null. */
    public val docsUrl: String? get() = checkId?.let { CHECK_DOCS_BASE + it }

    /**
     * What kind of failure this is, for deciding what to show. The set of categories is closed, so a `when` over it
     * needs no `else`; a new error type always joins an existing category.
     * ```kotlin
     * when (error.category) {
     *     PasskeyErrorCategory.UserCancelled -> Unit                    // nothing to show
     *     PasskeyErrorCategory.NoCredential -> showPasswordForm()
     *     PasskeyErrorCategory.AlreadyExists -> showMessage("You already have a passkey on this device.")
     *     PasskeyErrorCategory.Unsupported -> hidePasskeyButton()
     *     PasskeyErrorCategory.Setup, PasskeyErrorCategory.Unexpected -> showError("Passkeys aren't working right now.")
     * }
     * ```
     */
    public val category: PasskeyErrorCategory
        get() = when (this) {
            Cancelled, Interrupted -> PasskeyErrorCategory.UserCancelled
            NoCredentials -> PasskeyErrorCategory.NoCredential
            AlreadyRegistered -> PasskeyErrorCategory.AlreadyExists
            is NotSupported -> PasskeyErrorCategory.Unsupported
            is InvalidOptions, is Misconfigured -> PasskeyErrorCategory.Setup
            is Unknown -> PasskeyErrorCategory.Unexpected
        }

    /** True when the user ended the request or it was interrupted ([Cancelled], [Interrupted]): show no error. */
    public val isUserCancellation: Boolean get() = category == PasskeyErrorCategory.UserCancelled

    /** The user dismissed the passkey sheet. Usually not worth showing an error for. */
    public data object Cancelled : PasskeyError() {
        override val message: String = "The user cancelled the passkey request."
    }

    /** No matching passkey is available on this device. Offer another sign-in method. */
    public data object NoCredentials : PasskeyError() {
        override val message: String = "No passkey for this account is available on this device."
    }

    /**
     * A passkey from the options' `excludeCredentials` already exists on this device.
     * Not reported below iOS 17.4, which ignores `excludeCredentials`.
     */
    public data object AlreadyRegistered : PasskeyError() {
        override val message: String =
            "A passkey for this account already exists on this device (excludeCredentials matched)."
    }

    /**
     * The request didn't run to the end, and nothing is wrong with it: the system interrupted it (e.g. the app went
     * to the background), or another PassKi passkey request was still showing (only one runs at a time). Safe to
     * retry; don't show it to users as a failure.
     */
    public data object Interrupted : PasskeyError() {
        override val message: String =
            "The passkey request was interrupted, or another passkey request is in progress. Retry it."
    }

    /** The device, OS or platform does not support the requested operation. */
    public class NotSupported(override val message: String) : PasskeyError() {
        override fun equals(other: Any?): Boolean = other is NotSupported && message == other.message
        override fun hashCode(): Int = message.hashCode()
        override fun toString(): String = "NotSupported(message=$message)"
    }

    /** The options JSON passed in was malformed or invalid. See [fixHint]; [checkId] is `CFG-01` for a bad RP ID. */
    public class InvalidOptions internal constructor(
        override val message: String,
        override val checkId: String?,
        override val fixHint: String,
    ) : PasskeyError() {
        public constructor(message: String, checkId: String?) : this(message, checkId, INVALID_OPTIONS_HINT)

        /** An options error that no setup check covers. */
        public constructor(message: String) : this(message, null)

        override fun equals(other: Any?): Boolean =
            other is InvalidOptions && message == other.message && checkId == other.checkId

        override fun hashCode(): Int = 31 * message.hashCode() + (checkId?.hashCode() ?: 0)
        override fun toString(): String =
            "InvalidOptions(message=$message" + checkId?.let { ", checkId=$it, docsUrl=$docsUrl" }.orEmpty() + ")"
    }

    /** The app or its environment is set up incorrectly (e.g. missing asset links). See [fixHint] and [docsUrl]. */
    public class Misconfigured(
        override val message: String,
        override val fixHint: String,
        override val checkId: String?,
    ) : PasskeyError() {
        /** A setup problem that no catalog check covers. */
        public constructor(message: String, fixHint: String) : this(message, fixHint, null)

        override fun equals(other: Any?): Boolean =
            other is Misconfigured && message == other.message && fixHint == other.fixHint && checkId == other.checkId

        override fun hashCode(): Int = 31 * (31 * message.hashCode() + fixHint.hashCode()) + (checkId?.hashCode() ?: 0)
        override fun toString(): String =
            "Misconfigured(message=$message, fixHint=$fixHint" + checkId?.let { ", checkId=$it, docsUrl=$docsUrl" }.orEmpty() + ")"
    }

    /** Any failure PassKi could not classify. */
    public class Unknown(
        override val message: String,
        /** The raw platform error code or type name, if the platform provided one. */
        public val platformCode: String?,
        /**
         * The original platform throwable, for logging and crash reporting. Not part of [equals] / [hashCode]
         * because Throwables have no value equality.
         */
        public val cause: Throwable? = null,
    ) : PasskeyError() {
        override fun equals(other: Any?): Boolean =
            other is Unknown && message == other.message && platformCode == other.platformCode

        override fun hashCode(): Int = 31 * message.hashCode() + (platformCode?.hashCode() ?: 0)
        override fun toString(): String =
            "Unknown(message=$message, platformCode=$platformCode, cause=${cause?.let { it::class.simpleName }})"
    }
}

/**
 * The kind of a [PasskeyError], from [PasskeyError.category].
 *
 * Closed: there are exactly these six categories, in every release; a `when` over it needs no `else` branch.
 */
public enum class PasskeyErrorCategory {
    /** The user closed the sheet or the request was interrupted ([PasskeyError.Cancelled], [PasskeyError.Interrupted]). */
    UserCancelled,

    /** No passkey for this account on this device ([PasskeyError.NoCredentials]): offer another sign-in method. */
    NoCredential,

    /** The device already has a passkey for this account ([PasskeyError.AlreadyRegistered]). */
    AlreadyExists,

    /** The device, OS or credential provider can't do this ([PasskeyError.NotSupported]). */
    Unsupported,

    /**
     * A developer must fix the app, its server or its options ([PasskeyError.Misconfigured],
     * [PasskeyError.InvalidOptions]).
     */
    Setup,

    /** Anything else ([PasskeyError.Unknown]). */
    Unexpected,
}

/** Where each catalog check's docs page lives (the same pages as `docs/checks/<ID>.md` in the repository). */
private const val CHECK_DOCS_BASE = "https://passki.alirezaiyan.com/checks/"

/**
 * Thrown by [getOrThrow]. The message includes [PasskeyError.fixHint] and [PasskeyError.docsUrl] when there are ones.
 */
public class PasskeyException(public val error: PasskeyError) :
    Exception(
        error.message +
            error.fixHint?.let { " Fix: $it" }.orEmpty() +
            error.docsUrl?.let { " Docs: $it" }.orEmpty(),
    )

private const val INVALID_OPTIONS_HINT: String =
    "Pass the server's WebAuthn options JSON unchanged " +
        "(PublicKeyCredentialCreationOptionsJSON / PublicKeyCredentialRequestOptionsJSON). " +
        "Binary fields (challenge, user.id, credential ids) must be base64url."
