package com.alirezaiyan.passki.test

import com.alirezaiyan.passki.internal.Base64Url
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/** Builds server-style WebAuthn options JSON for tests. Not cryptographically random; tests only. */
public object TestOptions {
    /**
     * PublicKeyCredentialCreationOptionsJSON for [rpId], requesting a discoverable ES256/RS256 credential.
     * [challenge] and [userId] are base64url; the defaults are random (32 and 16 bytes).
     */
    public fun creation(
        rpId: String,
        challenge: String = randomBase64Url(32),
        userId: String = randomBase64Url(16),
        userName: String = "alice@example.com",
        displayName: String = "Alice",
        excludeCredentialIds: List<String> = emptyList(),
    ): String = buildJsonObject {
        putJsonObject("rp") {
            put("id", rpId)
            put("name", rpId)
        }
        putJsonObject("user") {
            put("id", userId)
            put("name", userName)
            put("displayName", displayName)
        }
        put("challenge", challenge)
        putJsonArray("pubKeyCredParams") {
            addJsonObject {
                put("type", "public-key")
                put("alg", -7)
            }
            addJsonObject {
                put("type", "public-key")
                put("alg", -257)
            }
        }
        putJsonArray("excludeCredentials") {
            excludeCredentialIds.forEach { id ->
                addJsonObject {
                    put("id", id)
                    put("type", "public-key")
                }
            }
        }
        putJsonObject("authenticatorSelection") {
            put("residentKey", "required")
            put("userVerification", "preferred")
        }
        put("attestation", "none")
    }.toString()

    /**
     * PublicKeyCredentialRequestOptionsJSON for [rpId]. [challenge] is base64url (random 32 bytes by default);
     * an empty [allowCredentialIds] means any discoverable credential.
     */
    public fun request(
        rpId: String,
        challenge: String = randomBase64Url(32),
        allowCredentialIds: List<String> = emptyList(),
    ): String = buildJsonObject {
        put("challenge", challenge)
        put("rpId", rpId)
        put("userVerification", "preferred")
        putJsonArray("allowCredentials") {
            allowCredentialIds.forEach { id ->
                addJsonObject {
                    put("id", id)
                    put("type", "public-key")
                }
            }
        }
    }.toString()

    private fun randomBase64Url(size: Int): String = Base64Url.encode(Random.nextBytes(size))
}
