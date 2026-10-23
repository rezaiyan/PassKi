package com.alirezaiyan.passki.demo

import kotlinx.serialization.Serializable

/** What the demo server answers after verifying a registration or an assertion. Every field comes from the server. */
@Serializable
public data class DemoVerdict(
    /** True when the signature, challenge, origin and RP ID all checked out. */
    val verified: Boolean,
    /** The credential the server verified, base64url. */
    val credentialId: String,
    /** The authenticator's signature counter after this response (often 0 for synced passkeys). */
    val signCount: Long,
    /** Whether the authenticator verified the user (biometrics or screen lock). */
    val userVerified: Boolean,
    /** Whether the passkey may be synced to other devices. */
    val backupEligible: Boolean = false,
    /** Whether the passkey is currently synced. */
    val backedUp: Boolean = false,
    /** The authenticator model ID, when the server could read it. */
    val aaguid: String? = null,
    /** The demo user's handle (`user.id`), base64url. */
    val userHandle: String,
    /** The origin in the response, e.g. `android:apk-key-hash:…` on Android or `https://<rpId>` on iOS. */
    val origin: String,
    /** The RP ID the server checked. */
    val rpId: String,
) {
    override fun toString(): String =
        (if (verified) "verified by server ✓" else "NOT verified by server ✗") +
            " signCount $signCount userVerified $userVerified backedUp $backedUp" +
            "\n  credentialId $credentialId\n  userHandle $userHandle\n  origin $origin" +
            aaguid?.let { "\n  aaguid $it" }.orEmpty()
}

/** `GET /api/demo/server-config`: the RP ID and the origins the demo server accepts. */
@Serializable
public data class DemoServerConfig(
    /** The RP ID the server verifies against. */
    val rpId: String,
    /** Every accepted origin: `https://<rpId>` plus one `android:apk-key-hash:` per known signing key. */
    val origins: List<String>,
)

/** The demo user this app last registered or signed in as. The demo server keeps it for 24 hours. */
public data class DemoAccount(
    /** The user handle (`user.id`), base64url. */
    val userHandle: String,
    /** The user's passkeys this client has seen verified, oldest first. */
    val credentialIds: List<String>,
)

/** The demo server answered with an error ([status] and its message), or could not be reached ([status] null). */
public class DemoServerException(
    /** The HTTP status, or null when the server could not be reached. */
    public val status: Int?,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
