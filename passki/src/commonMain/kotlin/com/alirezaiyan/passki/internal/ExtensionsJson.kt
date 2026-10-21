package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PrfResults
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * WebAuthn L3 `prf` and `largeBlob` extensions: reads inputs from options `extensions` and builds / reads
 * `clientExtensionResults`. Platform glue (iOS, fakes) maps these to authenticator calls.
 */
@InternalPasskiApi
public object ExtensionsJson {
    /** A PRF evaluation: salts on input, results on output. */
    public class PrfValues(public val first: ByteArray, public val second: ByteArray?)

    /** `extensions.prf` input. Neither [eval] nor [evalByCredential] set means "is PRF supported?". */
    public class PrfInput(public val eval: PrfValues?, public val evalByCredential: Map<String, PrfValues>)

    /** `extensions.largeBlob` assertion input. */
    public sealed interface LargeBlobAssertion {
        public object Read : LargeBlobAssertion
        public class Write(public val blob: ByteArray) : LargeBlobAssertion
    }

    // --- inputs ---

    /** `extensions.prf`, or null when absent. Malformed salts throw [PasskeyError.InvalidOptions]. */
    public fun prfInput(extensions: JsonObject?): PrfInput? {
        val prf = extensions?.get("prf") as? JsonObject ?: return null
        val eval = prf["eval"]?.let { prfValues(it, "extensions.prf.eval") }
        val byCredential = (prf["evalByCredential"] as? JsonObject).orEmpty().mapValues { (id, value) ->
            prfValues(value, "extensions.prf.evalByCredential.$id")
        }
        return PrfInput(eval, byCredential)
    }

    /** `extensions.largeBlob.support` for registration ("required" or "preferred"), or null when absent. */
    public fun largeBlobSupport(extensions: JsonObject?): String? {
        val largeBlob = extensions?.get("largeBlob") as? JsonObject ?: return null
        return if (largeBlob.string("support") == "required") "required" else "preferred"
    }

    /** `extensions.largeBlob` read / write for an assertion, or null when absent or neither is requested. */
    public fun largeBlobAssertion(extensions: JsonObject?): LargeBlobAssertion? {
        val largeBlob = extensions?.get("largeBlob") as? JsonObject ?: return null
        largeBlob["write"]?.let { return LargeBlobAssertion.Write(binary(it, "extensions.largeBlob.write")) }
        return if ((largeBlob["read"] as? JsonPrimitive)?.booleanOrNull == true) LargeBlobAssertion.Read else null
    }

    // --- outputs ---

    /** Registration `clientExtensionResults`; null arguments are omitted. */
    public fun registrationOutputs(prfEnabled: Boolean?, prf: PrfValues?, largeBlobSupported: Boolean?): JsonObject =
        buildJsonObject {
            if (prfEnabled != null || prf != null) {
                put(
                    "prf",
                    buildJsonObject {
                        prfEnabled?.let { put("enabled", it) }
                        prf?.let { put("results", it.toJson()) }
                    },
                )
            }
            largeBlobSupported?.let { put("largeBlob", buildJsonObject { put("supported", it) }) }
        }

    /** Assertion `clientExtensionResults`; null arguments are omitted. */
    public fun assertionOutputs(prf: PrfValues?, largeBlob: ByteArray?, largeBlobWritten: Boolean?): JsonObject =
        buildJsonObject {
            prf?.let { put("prf", buildJsonObject { put("results", it.toJson()) }) }
            if (largeBlob != null || largeBlobWritten != null) {
                put(
                    "largeBlob",
                    buildJsonObject {
                        largeBlob?.let { put("blob", Base64Url.encode(it)) }
                        largeBlobWritten?.let { put("written", it) }
                    },
                )
            }
        }

    // --- typed results (lenient: malformed outputs read as absent) ---

    internal fun prfEnabled(results: JsonObject): Boolean? = results.obj("prf")?.boolean("enabled")

    internal fun prfResults(results: JsonObject): PrfResults? {
        val values = results.obj("prf")?.obj("results") ?: return null
        val first = values.bytes("first") ?: return null
        val second = if ("second" in values) values.bytes("second") ?: return null else null
        return PrfResults(first, second)
    }

    internal fun largeBlobSupported(results: JsonObject): Boolean? = results.obj("largeBlob")?.boolean("supported")

    internal fun largeBlob(results: JsonObject): ByteArray? = results.obj("largeBlob")?.bytes("blob")

    internal fun largeBlobWritten(results: JsonObject): Boolean? = results.obj("largeBlob")?.boolean("written")

    // --- helpers ---

    private fun PrfValues.toJson(): JsonObject = buildJsonObject {
        put("first", Base64Url.encode(first))
        second?.let { put("second", Base64Url.encode(it)) }
    }

    private fun prfValues(element: JsonElement, field: String): PrfValues {
        val obj = element as? JsonObject ?: throw invalid("$field must be an object")
        val first = obj["first"] ?: throw invalid("$field.first is required")
        return PrfValues(binary(first, "$field.first"), obj["second"]?.let { binary(it, "$field.second") })
    }

    private fun binary(element: JsonElement, field: String): ByteArray {
        val value = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw invalid("$field must be a base64url string")
        return try {
            Base64Url.decode(value)
        } catch (e: IllegalArgumentException) {
            throw invalid("$field is not base64url: ${e.message}")
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.bytes(key: String): ByteArray? = string(key)?.let {
        try {
            Base64Url.decode(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun invalid(message: String) = PasskeyException(PasskeyError.InvalidOptions(message))
}
