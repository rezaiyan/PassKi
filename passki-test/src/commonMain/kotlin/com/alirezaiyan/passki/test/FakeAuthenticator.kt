package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.internal.Base64Url
import com.alirezaiyan.passki.internal.ExtensionsJson
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.WebAuthnResponses
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.HMAC
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
    /** Created with large-blob storage (the `largeBlob` extension was requested and supported). */
    internal val largeBlobEnabled: Boolean = false,
) {
    /** WebAuthn user.name; set at registration, updated by the currentUserDetails signal. */
    public var userName: String = userName
        internal set

    /** The large blob last written with the `largeBlob` extension, or null. */
    public var largeBlob: ByteArray? = null
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
 * Extensions: `prf` and `largeBlob` are supported (toggle with [prfSupported] / [largeBlobSupported]); outputs
 * appear in `clientExtensionResults` as on real platforms.
 * PRF is deterministic and **test-only**: `HMAC-SHA256(secret, SHA-256("WebAuthn PRF" || 0x00 || salt))`, mirroring
 * the WebAuthn salt hashing, where `secret` is derived from the credential id (so it is predictable; never use
 * this as real key material). The same passkey and salt always give the same 32 bytes; different salts or
 * passkeys give different ones. Large blobs are kept in memory per passkey ([FakeCredential.largeBlob]).
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

    /**
     * Whether the `prf` extension is supported. When false, registration reports `prf.enabled = false` and
     * sign-in returns no PRF results. Default true. Set from test code, not concurrently with operations.
     */
    public var prfSupported: Boolean = true

    /**
     * Whether new passkeys can get large-blob storage. When false, registration reports
     * `largeBlob.supported = false`, or fails with [PasskeyError.NotSupported] if support is `required`.
     * Default true. Set from test code, not concurrently with operations.
     */
    public var largeBlobSupported: Boolean = true

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

        val prfInput = ExtensionsJson.prfInput(options.extensions)
        val largeBlobSupport = ExtensionsJson.largeBlobSupport(options.extensions)
        if (largeBlobSupport == "required" && !largeBlobSupported) {
            throw PasskeyException(PasskeyError.NotSupported("FakeAuthenticator: largeBlobSupported = false"))
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
            largeBlobEnabled = largeBlobSupport != null && largeBlobSupported,
        )
        WebAuthnResponses.registration(
            credentialId = credentialId,
            clientDataJson = clientData,
            attestationObject = attestationObject,
            transports = listOf("internal", "hybrid"),
            attachment = "platform",
            extensions = ExtensionsJson.registrationOutputs(
                prfEnabled = prfInput?.let { prfSupported },
                prf = prfInput?.eval?.takeIf { prfSupported }?.let { prf(credentialId, it) },
                largeBlobSupported = largeBlobSupport?.let { largeBlobSupported },
            ),
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

        val credentialId = Base64Url.decode(credential.id)
        val prfInput = ExtensionsJson.prfInput(options.extensions)?.takeIf { prfSupported }
        val prfSalts = prfInput?.let { input ->
            input.evalByCredential.entries.firstOrNull { (id, _) -> sameId(id, credential.id) }?.value ?: input.eval
        }
        val largeBlob = ExtensionsJson.largeBlobAssertion(options.extensions)
        if (largeBlob is ExtensionsJson.LargeBlobAssertion.Write && credential.largeBlobEnabled) {
            credential.largeBlob = largeBlob.blob.copyOf()
        }

        WebAuthnResponses.authentication(
            credentialId = credentialId,
            clientDataJson = clientData,
            authenticatorData = authData,
            signature = signature,
            userHandle = Base64Url.decode(credential.userId),
            attachment = "platform",
            extensions = ExtensionsJson.assertionOutputs(
                prf = prfSalts?.let { prf(credentialId, it) },
                largeBlob = if (largeBlob == ExtensionsJson.LargeBlobAssertion.Read) credential.largeBlob else null,
                largeBlobWritten = (largeBlob as? ExtensionsJson.LargeBlobAssertion.Write)?.let { credential.largeBlobEnabled },
            ),
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

    /** Test-only PRF; see the class KDoc. */
    private suspend fun prf(credentialId: ByteArray, salts: ExtensionsJson.PrfValues): ExtensionsJson.PrfValues {
        val secret = sha256("passki-test PRF secret".encodeToByteArray() + credentialId)
        val key = crypto.get(HMAC).keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, secret)
        val generator = key.signatureGenerator()
        suspend fun eval(salt: ByteArray) =
            generator.generateSignature(sha256(PRF_CONTEXT + byteArrayOf(0) + salt))
        return ExtensionsJson.PrfValues(eval(salts.first), salts.second?.let { eval(it) })
    }

    private fun sameId(a: String, b: String): Boolean = try {
        Base64Url.decode(a).contentEquals(Base64Url.decode(b))
    } catch (e: IllegalArgumentException) {
        false
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
                    // Same catalog check as the real implementation's RP ID errors.
                    PasskeyError.InvalidOptions("rpId is required when origin is not https (got $origin)", "CFG-01"),
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
        val PRF_CONTEXT = "WebAuthn PRF".encodeToByteArray()

        // UP | UV | BE | BS | AT
        const val FLAGS_REGISTER = 0x01 or 0x04 or 0x08 or 0x10 or 0x40

        // BE | BS | AT: conditional create, no user presence or verification
        const val FLAGS_CONDITIONAL_REGISTER = 0x08 or 0x10 or 0x40

        // UP | UV | BE | BS
        const val FLAGS_ASSERT = 0x01 or 0x04 or 0x08 or 0x10
    }
}
