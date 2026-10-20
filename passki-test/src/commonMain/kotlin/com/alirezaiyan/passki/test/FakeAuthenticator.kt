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
    /** WebAuthn user.name at registration. */
    public val userName: String,
    internal val privateKey: ECDSA.PrivateKey,
) {
    /** Signature counter; incremented on every successful sign-in. */
    public var signCount: Int = 0
        internal set

    override fun toString(): String = "FakeCredential(id=$id, rpId=$rpId, userName=$userName, signCount=$signCount)"
}

/**
 * In-memory platform authenticator producing cryptographically valid WebAuthn responses
 * (ES256, attestation "none"), so they pass real server verifiers.
 *
 * @property origin the web origin written into clientDataJSON, e.g. `https://example.com`.
 */
public class FakeAuthenticator(
    public val origin: String,
    private val random: Random = Random.Default,
) {
    private val crypto = CryptographyProvider.Default
    private val credentials = mutableListOf<FakeCredential>()

    /** Snapshot of the passkeys currently stored, oldest first. */
    public val storedCredentials: List<FakeCredential> get() = credentials.toList()

    /**
     * Creates a passkey for creation options [optionsJson] and returns RegistrationResponseJSON.
     * Throws [PasskeyException] with [PasskeyError.AlreadyRegistered] if an excluded credential is stored,
     * or [PasskeyError.InvalidOptions] for malformed options.
     */
    public suspend fun register(optionsJson: String): String {
        val options = WebAuthnOptions.parseCreation(optionsJson).model
        val rpId = options.rp.id ?: hostOf(origin)
        val excluded = options.excludeCredentials.map { it.id }.toSet()
        if (credentials.any { it.rpId == rpId && it.id in excluded }) {
            throw PasskeyException(PasskeyError.AlreadyRegistered)
        }

        val keyPair = crypto.get(ECDSA).keyPairGenerator(EC.Curve.P256).generateKey()
        // 0x04 || X || Y
        val rawPublicKey = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW.Uncompressed)
        val credentialId = random.nextBytes(16)

        val clientData = clientDataJson("webauthn.create", options.challenge)
        val authData = authenticatorData(rpId, FLAGS_REGISTER, signCount = 0) +
            attestedCredentialData(credentialId, rawPublicKey)
        val attestationObject = CborWriter()
            .mapHeader(3)
            .text("fmt").text("none")
            .text("attStmt").mapHeader(0)
            .text("authData").bytes(authData)
            .toByteArray()

        credentials += FakeCredential(
            id = Base64Url.encode(credentialId),
            rpId = rpId,
            userId = options.user.id,
            userName = options.user.name,
            privateKey = keyPair.privateKey,
        )
        return WebAuthnResponses.registration(
            credentialId = credentialId,
            clientDataJson = clientData,
            attestationObject = attestationObject,
            transports = listOf("internal", "hybrid"),
            attachment = "platform",
        )
    }

    /**
     * Signs request options [optionsJson] with the most recent matching passkey and returns
     * AuthenticationResponseJSON. Throws [PasskeyException] with [PasskeyError.NoCredentials] if none matches.
     */
    public suspend fun signIn(optionsJson: String): String {
        val options = WebAuthnOptions.parseRequest(optionsJson).model
        val rpId = options.rpId ?: hostOf(origin)
        val allowed = options.allowCredentials.map { it.id }.toSet()
        val credential = credentials.lastOrNull { it.rpId == rpId && (allowed.isEmpty() || it.id in allowed) }
            ?: throw PasskeyException(PasskeyError.NoCredentials)

        credential.signCount += 1
        val clientData = clientDataJson("webauthn.get", options.challenge)
        val authData = authenticatorData(rpId, FLAGS_ASSERT, credential.signCount)
        val signature = credential.privateKey
            .signatureGenerator(SHA256, ECDSA.SignatureFormat.DER)
            .generateSignature(authData + sha256(clientData))

        return WebAuthnResponses.authentication(
            credentialId = Base64Url.decode(credential.id),
            clientDataJson = clientData,
            authenticatorData = authData,
            signature = signature,
            userHandle = Base64Url.decode(credential.userId),
            attachment = "platform",
        )
    }

    /** Deletes the passkey with base64url [credentialId], if stored. */
    public fun remove(credentialId: String) {
        credentials.removeAll { it.id == credentialId }
    }

    /** Deletes every passkey of [userId] on [rpId] whose id is not in [credentialIds]. */
    public fun retainOnly(rpId: String, userId: String, credentialIds: List<String>) {
        credentials.removeAll { it.rpId == rpId && it.userId == userId && it.id !in credentialIds }
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

    private fun hostOf(origin: String): String =
        origin.substringAfter("://").substringBefore("/").substringBefore(":")

    private companion object {
        // UP | UV | BE | BS | AT
        const val FLAGS_REGISTER = 0x01 or 0x04 or 0x08 or 0x10 or 0x40

        // UP | UV | BE | BS
        const val FLAGS_ASSERT = 0x01 or 0x04 or 0x08 or 0x10
    }
}
