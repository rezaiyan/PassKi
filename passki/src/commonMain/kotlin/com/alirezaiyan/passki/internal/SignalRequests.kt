package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** JSON bodies defined by the WebAuthn Signal API (signalUnknownCredential & co). */
@InternalPasskiApi
public object SignalRequests {
    public fun unknownCredential(rpId: String, credentialId: String): String {
        val credentialId = canonical(credentialId, "credentialId")
        return buildJsonObject {
            put("rpId", rpId)
            put("credentialId", credentialId)
        }.toString()
    }

    public fun acceptedCredentials(rpId: String, userId: String, credentialIds: List<String>): String {
        val userId = canonical(userId, "userId")
        val credentialIds = credentialIds.map { canonical(it, "credentialIds") }
        return buildJsonObject {
            put("rpId", rpId)
            put("userId", userId)
            putJsonArray("allAcceptedCredentialIds") { credentialIds.forEach { add(it) } }
        }.toString()
    }

    public fun currentUserDetails(rpId: String, userId: String, name: String, displayName: String): String {
        val userId = canonical(userId, "userId")
        return buildJsonObject {
            put("rpId", rpId)
            put("userId", userId)
            put("name", name)
            put("displayName", displayName)
        }.toString()
    }

    private fun canonical(value: String, field: String): String {
        try {
            return Base64Url.encode(Base64Url.decode(value))
        } catch (e: IllegalArgumentException) {
            throw PasskeyException(PasskeyError.InvalidOptions("$field must be base64url: ${e.message}"))
        }
    }
}
