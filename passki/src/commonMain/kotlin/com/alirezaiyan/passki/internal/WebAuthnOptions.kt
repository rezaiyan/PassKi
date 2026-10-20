package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@InternalPasskiApi
public class NormalizedOptions<T>(
    /** Options JSON with any `{"publicKey": ...}` envelope removed; unknown fields preserved. */
    public val json: String,
    public val model: T,
)

@InternalPasskiApi
public object WebAuthnOptions {
    public fun parseCreation(json: String): NormalizedOptions<CreationOptionsJson> =
        parse(json, CreationOptionsJson.serializer()) { obj ->
            obj.withBinary("challenge", "challenge")
                .withObject("user") { it.withBinary("id", "user.id", maxBytes = MAX_USER_ID_BYTES) }
                .withDescriptors("excludeCredentials")
        }

    public fun parseRequest(json: String): NormalizedOptions<RequestOptionsJson> =
        parse(json, RequestOptionsJson.serializer()) { obj ->
            obj.withBinary("challenge", "challenge")
                .withDescriptors("allowCredentials")
        }

    private const val MAX_USER_ID_BYTES = 64

    private fun <T> parse(
        json: String,
        serializer: KSerializer<T>,
        canonicalize: (JsonObject) -> JsonObject,
    ): NormalizedOptions<T> {
        val element = try {
            PasskiJson.parseToJsonElement(json)
        } catch (e: IllegalArgumentException) {
            throw invalid("Options are not valid JSON: ${e.message}")
        }
        val root = element as? JsonObject ?: throw invalid("Options must be a JSON object.")
        val unwrapped = root["publicKey"] as? JsonObject ?: root
        try {
            PasskiJson.decodeFromJsonElement(serializer, unwrapped)
        } catch (e: IllegalArgumentException) {
            throw invalid("Options do not match the WebAuthn JSON format: ${e.message}")
        }
        val canonical = try {
            canonicalize(unwrapped)
        } catch (e: IllegalArgumentException) {
            throw invalid("A binary field is not base64url: ${e.message}")
        }
        val model = try {
            PasskiJson.decodeFromJsonElement(serializer, canonical)
        } catch (e: IllegalArgumentException) {
            throw invalid("Options do not match the WebAuthn JSON format: ${e.message}")
        }
        return NormalizedOptions(canonical.toString(), model)
    }

    /** Rewrites [key] (a string) as canonical unpadded base64url; fails if undecodable or empty. */
    private fun JsonObject.withBinary(key: String, field: String, maxBytes: Int = Int.MAX_VALUE): JsonObject {
        val value = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return this
        val bytes = try {
            Base64Url.decode(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$field: ${e.message}", e)
        }
        require(bytes.isNotEmpty()) { "$field must not be empty" }
        require(bytes.size <= maxBytes) { "$field must be at most $maxBytes bytes" }
        return JsonObject(LinkedHashMap(this).also { it[key] = JsonPrimitive(Base64Url.encode(bytes)) })
    }

    private fun JsonObject.withObject(key: String, transform: (JsonObject) -> JsonObject): JsonObject {
        val inner = this[key] as? JsonObject ?: return this
        return JsonObject(LinkedHashMap(this).also { it[key] = transform(inner) })
    }

    private fun JsonObject.withDescriptors(key: String): JsonObject {
        val array = this[key] as? JsonArray ?: return this
        val rewritten = array.map { item ->
            if (item is JsonObject) item.withBinary("id", "$key[].id") else item
        }
        return JsonObject(LinkedHashMap(this).also { it[key] = JsonArray(rewritten) })
    }

    private fun invalid(message: String) = PasskeyException(PasskeyError.InvalidOptions(message))
}
