package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

@InternalPasskiApi
public class NormalizedOptions<T>(
    /** Options JSON with any `{"publicKey": ...}` envelope removed; unknown fields preserved. */
    public val json: String,
    public val model: T,
)

@InternalPasskiApi
public object WebAuthnOptions {
    public fun parseCreation(json: String): NormalizedOptions<CreationOptionsJson> =
        parse(json, CreationOptionsJson.serializer()) { options ->
            Base64Url.decode(options.challenge)
            Base64Url.decode(options.user.id)
            options.excludeCredentials.forEach { Base64Url.decode(it.id) }
        }

    public fun parseRequest(json: String): NormalizedOptions<RequestOptionsJson> =
        parse(json, RequestOptionsJson.serializer()) { options ->
            Base64Url.decode(options.challenge)
            options.allowCredentials.forEach { Base64Url.decode(it.id) }
        }

    private fun <T> parse(json: String, serializer: KSerializer<T>, validate: (T) -> Unit): NormalizedOptions<T> {
        val element = try {
            PasskiJson.parseToJsonElement(json)
        } catch (e: IllegalArgumentException) {
            throw invalid("Options are not valid JSON: ${e.message}")
        }
        val root = element as? JsonObject ?: throw invalid("Options must be a JSON object.")
        val unwrapped = root["publicKey"] as? JsonObject ?: root
        val model = try {
            PasskiJson.decodeFromJsonElement(serializer, unwrapped)
        } catch (e: IllegalArgumentException) {
            throw invalid("Options do not match the WebAuthn JSON format: ${e.message}")
        }
        try {
            validate(model)
        } catch (e: IllegalArgumentException) {
            throw invalid("A binary field is not base64url: ${e.message}")
        }
        return NormalizedOptions(unwrapped.toString(), model)
    }

    private fun invalid(message: String) = PasskeyException(PasskeyError.InvalidOptions(message))
}
