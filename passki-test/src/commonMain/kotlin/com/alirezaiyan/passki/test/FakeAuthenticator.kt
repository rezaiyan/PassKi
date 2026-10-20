package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.internal.Base64Url
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.WebAuthnResponses
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** A passkey stored by [FakeAuthenticator]. */
public class FakeCredential internal constructor(
    /** Credential id, base64url. */
    public val id: String,
    /** Relying party id the passkey is bound to. */
    public val rpId: String,
    /** WebAuthn user handle (user.id), base64url. */
    public val userId: String,
    /** WebAuthn user.name; set at registration, updated by the currentUserDetails signal. */
    userName: String,
    internal val privateKey: ECDSA.PrivateKey,
) {
    /** WebAuthn user.name; set at registration, updated by the currentUserDetails signal. */
    public var userName: String = userName
        internal set

    /** Signature counter; incremented on every successful sign-in. */
    public var signCount: Int = 0
        internal set

    override fun toString(): String = "FakeCredential(id=$id, rpId=$rpId, userName=$userName, signCount=$signCount)"
}

/**
 * In-memory platform authenticator producing cryptographically valid WebAuthn responses
 * (ES256, attestation "none"), so they pass real server verifiers.
 *
 * Like a platform authenticator it checks the options' RP ID against [origin]: an `http(s)` origin's host must equal
 * the RP ID or be a subdomain of it (otherwise [PasskeyError.Misconfigured]); for other origins (e.g. Android
 * `android:apk-key-hash:...`) the options must name the RP ID explicitly (otherwise [PasskeyError.InvalidOptions]).
 *
 * Safe to call from concurrent coroutines: every operation runs under one lock.
 *
 * @property origin the web origin written into clientDataJSON, e.g. `https://example.com`.
 */
public class FakeAuthenticator(
    public val origin: String,
    private val random: Random = Random.Default,
) {
    private val crypto = CryptographyProvider.Default
    private val lock = Mutex()

    // Copy-on-write, replaced only under [lock], so [storedCredentials] can read it without locking.
    private var credentials: List<FakeCredential> = emptyList()

    /** Snapshot of the passkeys currently stored, oldest first. */
    public val storedCredentials: List<FakeCredential> get() = credentials

    /**
     * Creates a passkey for creation options [optionsJson] and returns RegistrationResponseJSON.
     * An existing passkey for the same RP ID and user.id is replaced, as platform providers do.
     *
     * [conditional] mimics conditional create (passkey upgrade): authenticator data flags are BE | BS | AT with
     * neither UP nor UV set, so the server must verify with user presence and user verification not required.
     *
     * Throws [PasskeyException] with [PasskeyError.AlreadyRegistered] if an excluded credential is stored,
     * [PasskeyError.InvalidOptions] for malformed options, or [PasskeyError.Misconfigured] if the RP ID does not
     * match [origin].
     */
    public suspend fun register(optionsJson: String, conditional: Boolean = false): String =
        registerWithFlags(optionsJson, if (conditional) FLAGS_CONDITIONAL_REGISTER else FLAGS_REGISTER)

    internal suspend fun registerWithFlags(optionsJson: String, flags: Int): String = lock.withLock {
        val options = WebAuthnOptions.parseCreation(optionsJson).model
        val rpId = resolveRpId(options.rp.id)
        val excluded = options.excludeCredentials.map { it.id }.toSet()
        if (credentials.any { it.rpId == rpId && it.id in excluded }) {
            throw PasskeyException(PasskeyError.AlreadyRegistered)
        }

        val keyPair = crypto.get(ECDSA).keyPairGenerator(EC.Curve.P256).generateKey()
        // 0x04 || X || Y
        val rawPublicKey = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW.Uncompressed)
        val credentialId = random.nextBytes(16)

        val clientData = clientDataJson("webauthn.create", options.challenge)
        val authData = authenticatorData(rpId, flags, signCount = 0) +
            attestedCredentialData(credentialId, rawPublicKey)
        val attestationObject = CborWriter()
            .mapHeader(3)
            .text("fmt").text("none")
            .text("attStmt").mapHeader(0)
            .text("authData").bytes(authData)
            .toByteArray()

        credentials = credentials.filterNot { it.rpId == rpId && it.userId == options.user.id } + FakeCredential(
            id = Base64Url.encode(credentialId),
            rpId = rpId,
            userId = options.user.id,
            userName = options.user.name,
            privateKey = keyPair.privateKey,
        )
        WebAuthnResponses.registration(
            credentialId = credentialId,
            clientDataJson = clientData,
            attestationObject = attestationObject,
            transports = listOf("internal", "hybrid"),
            attachment = "platform",
        )
    }

    /**
     * Signs request options [optionsJson] with the most recent matching passkey and returns
     * AuthenticationResponseJSON. Throws [PasskeyException] with [PasskeyError.NoCredentials] if none matches,
     * [PasskeyError.InvalidOptions] for malformed options, or [PasskeyError.Misconfigured] if the RP ID does not
     * match [origin].
     */
    public suspend fun signIn(optionsJson: String): String = lock.withLock {
        val options = WebAuthnOptions.parseRequest(optionsJson).model
        val rpId = resolveRpId(options.rpId)
        val allowed = options.allowCredentials.map { it.id }.toSet()
        val credential = credentials.lastOrNull { it.rpId == rpId && (allowed.isEmpty() || it.id in allowed) }
            ?: throw PasskeyException(PasskeyError.NoCredentials)

        credential.signCount += 1
        val clientData = clientDataJson("webauthn.get", options.challenge)
        val authData = authenticatorData(rpId, FLAGS_ASSERT, credential.signCount)
        val signature = credential.privateKey
            .signatureGenerator(SHA256, ECDSA.SignatureFormat.DER)
            .generateSignature(authData + sha256(clientData))

        WebAuthnResponses.authentication(
            credentialId = Base64Url.decode(credential.id),
            clientDataJson = clientData,
            authenticatorData = authData,
            signature = signature,
            userHandle = Base64Url.decode(credential.userId),
            attachment = "platform",
        )
    }

    /**
     * Deletes the passkey with base64url [credentialId] on [rpId], if stored. Ids are compared by their bytes
     * (padding and standard-alphabet ids match). Throws [PasskeyException] with [PasskeyError.InvalidOptions]
     * for an undecodable id.
     */
    public suspend fun remove(rpId: String, credentialId: String) {
        val id = canonical(credentialId, "credentialId")
        lock.withLock { credentials = credentials.filterNot { it.rpId == rpId && it.id == id } }
    }

    /**
     * Deletes every passkey of [userId] on [rpId] whose id is not in [credentialIds]. Ids are compared by their
     * bytes. Throws [PasskeyException] with [PasskeyError.InvalidOptions] for an undecodable id.
     */
    public suspend fun retainOnly(rpId: String, userId: String, credentialIds: List<String>) {
        val user = canonical(userId, "userId")
        val keep = credentialIds.map { canonical(it, "credentialIds") }.toSet()
        lock.withLock {
            credentials = credentials.filterNot { it.rpId == rpId && it.userId == user && it.id !in keep }
        }
    }

    /** Sets [FakeCredential.userName] of [userId]'s passkeys on [rpId] to [name]. */
    internal suspend fun updateUserName(rpId: String, userId: String, name: String) {
        val user = canonical(userId, "userId")
        lock.withLock {
            credentials.filter { it.rpId == rpId && it.userId == user }.forEach { it.userName = name }
        }
    }

    private fun canonical(value: String, field: String): String = try {
        Base64Url.encode(Base64Url.decode(value))
    } catch (e: IllegalArgumentException) {
        throw PasskeyException(PasskeyError.InvalidOptions("$field must be base64url: ${e.message}"))
    }

    /** The effective RP ID, validated against [origin] the way browsers and platform authenticators do. */
    private fun resolveRpId(requested: String?): String {
        if (!origin.startsWith("https://") && !origin.startsWith("http://")) {
            return requested
                ?: throw PasskeyException(
                    PasskeyError.InvalidOptions("rpId is required when origin is not https (got $origin)"),
                )
        }
        val host = hostOf(origin)
        val rpId = requested ?: return host
        val h = host.lowercase()
        val r = rpId.lowercase()
        if (h != r && !h.endsWith(".$r")) {
            throw PasskeyException(
                PasskeyError.Misconfigured(
                    "RP ID $rpId is not a registrable suffix of origin host $host",
                    "Use an rpId equal to or a parent domain of the origin's host.",
                ),
            )
        }
        return rpId
    }

    private fun clientDataJson(type: String, challenge: String): ByteArray = buildJsonObject {
        put("type", type)
        put("challenge", Base64Url.encode(Base64Url.decode(challenge)))
        put("origin", origin)
        put("crossOrigin", false)
    }.toString().encodeToByteArray()

    private suspend fun authenticatorData(rpId: String, flags: Int, signCount: Int): ByteArray =
        sha256(rpId.encodeToByteArray()) + byteArrayOf(flags.toByte()) + signCount.toBigEndianBytes()

    private fun attestedCredentialData(credentialId: ByteArray, rawPublicKey: ByteArray): ByteArray {
        val x = rawPublicKey.copyOfRange(1, 33)
        val y = rawPublicKey.copyOfRange(33, 65)
        val coseKey = CborWriter()
            .mapHeader(5)
            .int(1).int(2) // kty: EC2
            .int(3).int(-7) // alg: ES256
            .int(-1).int(1) // crv: P-256
            .int(-2).bytes(x)
            .int(-3).bytes(y)
            .toByteArray()
        val aaguid = ByteArray(16)
        val length = byteArrayOf((credentialId.size shr 8).toByte(), credentialId.size.toByte())
        return aaguid + length + credentialId + coseKey
    }

    private suspend fun sha256(data: ByteArray): ByteArray = crypto.get(SHA256).hasher().hash(data)

    private fun Int.toBigEndianBytes(): ByteArray =
        byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), toByte())

    /** Host of an `http(s)` origin: no userinfo, no port, IPv6 brackets removed. */
    private fun hostOf(origin: String): String {
        val authority = origin.substringAfter("://").substringBefore("/").substringBefore("?").substringBefore("#")
            .substringAfterLast("@")
        return if (authority.startsWith("[")) {
            authority.substring(1).substringBefore("]")
        } else {
            authority.substringBefore(":")
        }
    }

    private companion object {
        // UP | UV | BE | BS | AT
        const val FLAGS_REGISTER = 0x01 or 0x04 or 0x08 or 0x10 or 0x40

        // BE | BS | AT: conditional create, no user presence or verification
        const val FLAGS_CONDITIONAL_REGISTER = 0x08 or 0x10 or 0x40

        // UP | UV | BE | BS
        const val FLAGS_ASSERT = 0x01 or 0x04 or 0x08 or 0x10
    }
}
