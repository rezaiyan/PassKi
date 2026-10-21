package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.Base64Url
import com.alirezaiyan.passki.internal.PasskiJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Adds typed WebAuthn extensions to the options JSON your server produced, before you pass it to
 * [Passkeys.register] or [Passkeys.signIn]. Read the outputs from [PasskeyRegistration] / [PasskeyAssertion].
 *
 * Usually your server adds extensions itself; use these helpers when the extension inputs are produced on the
 * device (e.g. a PRF salt for client-side encryption that the server must never see).
 *
 * Every helper returns new JSON: other extensions and fields are preserved, as is a `{"publicKey": ...}`
 * envelope. They throw [IllegalArgumentException] if the JSON is not an options object, or is the wrong kind
 * (creation options have `rp` / `user`; request options do not).
 *
 * Extensions are best-effort, as in browsers: a provider or OS that does not support one ignores it, and its
 * output is then `null`. PRF needs iOS 18+ (Android: provider-dependent, Google Password Manager supports it);
 * largeBlob needs iOS 17+.
 */
public object PasskeyExtensions {
    /**
     * Requests the `prf` extension evaluated with salts [first] and optional [second].
     *
     * On request options the results arrive in [PasskeyAssertion.prf]: the same passkey and salt always give the
     * same 32 bytes, so they can derive an encryption key. On creation options, providers that can evaluate PRF
     * at registration return [PasskeyRegistration.prf]; others only report [PasskeyRegistration.prfEnabled].
     *
     * Salts are WebAuthn PRF inputs: the platform hashes them with the "WebAuthn PRF" context before they reach
     * the authenticator. Any `evalByCredential` already in the options is kept.
     */
    public fun withPrf(optionsJson: String, first: ByteArray, second: ByteArray? = null): String =
        edit(optionsJson, expectCreation = null) { extensions ->
            val prf = extensions["prf"] as? JsonObject ?: JsonObject(emptyMap())
            val eval = buildJsonObject {
                put("first", Base64Url.encode(first))
                second?.let { put("second", Base64Url.encode(it)) }
            }
            extensions.with("prf", prf.with("eval", eval))
        }

    /**
     * Asks, at registration, whether the new passkey supports PRF; the answer is [PasskeyRegistration.prfEnabled].
     * Existing `prf` inputs (e.g. from [withPrf]) are kept.
     */
    public fun withPrf(creationOptionsJson: String): String =
        edit(creationOptionsJson, expectCreation = true) { extensions ->
            extensions.with("prf", extensions["prf"] as? JsonObject ?: JsonObject(emptyMap()))
        }

    /**
     * Asks the authenticator to create the passkey with large-blob storage; whether it did is
     * [PasskeyRegistration.largeBlobSupported]. With [LargeBlobSupport.Required], authenticators without
     * large-blob storage are not offered.
     */
    public fun withLargeBlob(
        creationOptionsJson: String,
        support: LargeBlobSupport = LargeBlobSupport.Preferred,
    ): String = edit(creationOptionsJson, expectCreation = true) { extensions ->
        extensions.with("largeBlob", buildJsonObject { put("support", support.webAuthnValue) })
    }

    /** Reads the passkey's large blob while signing in; the bytes arrive in [PasskeyAssertion.largeBlob]. */
    public fun withLargeBlobRead(requestOptionsJson: String): String =
        edit(requestOptionsJson, expectCreation = false) { extensions ->
            extensions.with("largeBlob", buildJsonObject { put("read", true) })
        }

    /**
     * Stores [blob] in the passkey's large blob while signing in, replacing any previous blob; success is
     * [PasskeyAssertion.largeBlobWritten]. Replaces a [withLargeBlobRead] request (read and write are exclusive).
     *
     * Platforms require the options to name the passkey in `allowCredentials` for a write.
     */
    public fun withLargeBlobWrite(requestOptionsJson: String, blob: ByteArray): String =
        edit(requestOptionsJson, expectCreation = false) { extensions ->
            extensions.with("largeBlob", buildJsonObject { put("write", Base64Url.encode(blob)) })
        }

    /**
     * Parses [json], applies [transform] to its `extensions` object (inside any `publicKey` envelope) and
     * re-serialises. [expectCreation] null accepts both creation and request options.
     */
    private fun edit(json: String, expectCreation: Boolean?, transform: (JsonObject) -> JsonObject): String {
        val root = try {
            PasskiJson.parseToJsonElement(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Options are not valid JSON: ${e.message}", e)
        } as? JsonObject ?: throw IllegalArgumentException("Options must be a JSON object.")
        val envelope = root["publicKey"] as? JsonObject
        val options = envelope ?: root
        val isCreation = "rp" in options || "user" in options
        if (expectCreation != null && expectCreation != isCreation) {
            val expected = if (expectCreation) "creation (registration)" else "request (sign-in)"
            throw IllegalArgumentException("This extension needs $expected options.")
        }
        val extensions = when (val current = options["extensions"]) {
            null -> JsonObject(emptyMap())
            is JsonObject -> current
            else -> throw IllegalArgumentException("Options field `extensions` must be an object.")
        }
        val edited = options.with("extensions", transform(extensions))
        return (if (envelope != null) root.with("publicKey", edited) else edited).toString()
    }

    private fun JsonObject.with(key: String, value: JsonElement): JsonObject =
        JsonObject(LinkedHashMap(this).also { it[key] = value })
}

/**
 * Whether a new passkey must have large-blob storage ([PasskeyExtensions.withLargeBlob]).
 *
 * New entries may be added in minor releases — keep an `else` branch in exhaustive `when`s.
 */
public enum class LargeBlobSupport(internal val webAuthnValue: String) {
    /** Only offer authenticators with large-blob storage. */
    Required("required"),

    /** Use large-blob storage when the authenticator has it. */
    Preferred("preferred"),
}

/**
 * Outputs of the WebAuthn `prf` extension ([PasskeyExtensions.withPrf]): one 32-byte result per salt.
 *
 * These are secrets: [toString] prints only their sizes. Compared by content.
 * Instances are created by PassKi; the constructor is internal API so fields can be added without breaking binaries.
 */
public class PrfResults @InternalPasskiApi constructor(
    /** The PRF output for the first salt. */
    public val first: ByteArray,
    /** The PRF output for the second salt, if one was given. */
    public val second: ByteArray?,
) {
    override fun equals(other: Any?): Boolean =
        other is PrfResults && first.contentEquals(other.first) && second.contentEquals(other.second)

    override fun hashCode(): Int = 31 * first.contentHashCode() + second.contentHashCode()

    override fun toString(): String =
        "PrfResults(first=<${first.size} bytes>, second=${second?.let { "<${it.size} bytes>" } ?: "null"})"
}
