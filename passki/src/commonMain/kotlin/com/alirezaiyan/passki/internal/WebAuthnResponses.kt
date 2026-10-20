package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyRegistration
import kotlinx.serialization.json.JsonObject

/** Builds W3C RegistrationResponseJSON / AuthenticationResponseJSON from raw authenticator bytes. */
@InternalPasskiApi
public object WebAuthnResponses {
    public fun registration(
        credentialId: ByteArray,
        clientDataJson: ByteArray,
        attestationObject: ByteArray,
        transports: List<String>,
        attachment: String?,
        extensions: JsonObject = JsonObject(emptyMap()),
    ): String {
        val id = Base64Url.encode(credentialId)
        val model = RegistrationResponseJson(
            id = id,
            rawId = id,
            response = AttestationResponseJson(
                clientDataJSON = Base64Url.encode(clientDataJson),
                attestationObject = Base64Url.encode(attestationObject),
                transports = transports,
            ),
            authenticatorAttachment = attachment,
            clientExtensionResults = extensions,
        )
        return PasskiJson.encodeToString(RegistrationResponseJson.serializer(), model)
    }

    public fun authentication(
        credentialId: ByteArray,
        clientDataJson: ByteArray,
        authenticatorData: ByteArray,
        signature: ByteArray,
        userHandle: ByteArray?,
        attachment: String?,
        extensions: JsonObject = JsonObject(emptyMap()),
    ): String {
        val id = Base64Url.encode(credentialId)
        val model = AuthenticationResponseJson(
            id = id,
            rawId = id,
            response = AssertionResponseJson(
                clientDataJSON = Base64Url.encode(clientDataJson),
                authenticatorData = Base64Url.encode(authenticatorData),
                signature = Base64Url.encode(signature),
                userHandle = userHandle?.let(Base64Url::encode),
            ),
            authenticatorAttachment = attachment,
            clientExtensionResults = extensions,
        )
        return PasskiJson.encodeToString(AuthenticationResponseJson.serializer(), model)
    }
}

@InternalPasskiApi
public fun parseRegistration(json: String): PasskeyRegistration {
    val model = try {
        PasskiJson.decodeFromString(RegistrationResponseJson.serializer(), json)
    } catch (e: IllegalArgumentException) {
        throw PasskeyException(PasskeyError.Unknown("Platform returned malformed registration JSON: ${e.message}", null))
    }
    requireBase64Url(model.id)
    return PasskeyRegistration(
        json = json,
        credentialId = model.id,
        transports = model.response.transports,
        authenticatorAttachment = model.authenticatorAttachment,
    )
}

@InternalPasskiApi
public fun parseAssertion(json: String): PasskeyAssertion {
    val model = try {
        PasskiJson.decodeFromString(AuthenticationResponseJson.serializer(), json)
    } catch (e: IllegalArgumentException) {
        throw PasskeyException(PasskeyError.Unknown("Platform returned malformed assertion JSON: ${e.message}", null))
    }
    requireBase64Url(model.id)
    model.response.userHandle?.let(::requireBase64Url)
    return PasskeyAssertion(json = json, credentialId = model.id, userHandle = model.response.userHandle)
}

private fun requireBase64Url(value: String) {
    try {
        Base64Url.decode(value)
    } catch (e: IllegalArgumentException) {
        throw PasskeyException(PasskeyError.Unknown("Platform returned a non-base64url credential id", null))
    }
}
