package com.alirezaiyan.passki.webdemo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.random.Random

/**
 * A stand-in for your WebAuthn server, running inside the page so the web sample works on any domain, `localhost`
 * included. The hosted demo server can't be used from a browser: its RP ID is its own domain, and browsers only
 * accept an RP ID that matches the page's.
 *
 * It creates options the way a server would and reads back what a server reads from the response. **It is not
 * verification:** it doesn't check the signature or the attestation, so it proves nothing. Your server must verify
 * every response with a WebAuthn library (SimpleWebAuthn, webauthn4j, java-webauthn-server, py_webauthn...).
 */
class LocalRelyingParty(
    /** The page's domain (`window.location.hostname`): the only RP ID the browser accepts here. */
    val rpId: String,
    /** The page's origin (`window.location.origin`): what `clientDataJSON.origin` must be. */
    val origin: String,
    private val random: Random = Random.Default,
) {
    /** A passkey created in this session; the page forgets them on reload, the browser keeps the passkeys. */
    class Credential(val id: String, val userHandle: String, val username: String)

    var credentials: List<Credential> = emptyList()
        private set

    /** The challenge this RP issued last, and for registrations the user it was for. Each is good for one response. */
    private class Pending(val kind: String, val challenge: String, val userHandle: String?)

    private var pending: Pending? = null

    /**
     * Creation options for [username]. `excludeCredentials` lists the passkeys this session created for the same
     * user, so creating one again fails with `AlreadyRegistered`.
     */
    fun registrationOptions(username: String): String {
        val name = username.trim().ifEmpty { "demo user" }
        // A real server uses a random user ID and stores the name next to it; here the ID *is* the name, so the page
        // can greet whoever signs in, even after a reload.
        val userHandle = b64(name.encodeToByteArray().take(MAX_USER_ID_BYTES).toByteArray())
        return buildJsonObject {
            putJsonObject("rp") {
                put("id", rpId)
                put("name", "PassKi web demo")
            }
            putJsonObject("user") {
                put("id", userHandle)
                put("name", name)
                put("displayName", name)
            }
            put("challenge", newChallenge(CREATE, userHandle))
            putJsonArray("pubKeyCredParams") {
                listOf(-7, -257).forEach { alg -> addJsonObject { put("type", "public-key"); put("alg", alg) } }
            }
            put("timeout", TIMEOUT_MS)
            put("attestation", "none")
            putJsonObject("authenticatorSelection") {
                put("residentKey", "required")
                put("userVerification", "preferred")
            }
            putJsonArray("excludeCredentials") {
                credentials.filter { it.userHandle == userHandle }.forEach { addJsonObject { put("type", "public-key"); put("id", it.id) } }
            }
        }.toString()
    }

    /** Request options with an empty `allowCredentials`: any passkey for [rpId] may answer (discoverable sign-in). */
    fun authenticationOptions(): String = buildJsonObject {
        put("challenge", newChallenge(GET, userHandle = null))
        put("rpId", rpId)
        put("timeout", TIMEOUT_MS)
        put("userVerification", "preferred")
        putJsonArray("allowCredentials") {}
    }.toString()

    /** Reads a registration response like a server would, remembers the passkey, and reports what it found. */
    fun readRegistration(responseJson: String): Report {
        val response = parse(responseJson)
        val userHandle = pending?.takeIf { it.kind == CREATE }?.userHandle
        val checks = clientDataChecks(response, CREATE)
        val id = response.string("id")
        if (id != null && userHandle != null && checks.all { it.ok }) {
            credentials = credentials.filterNot { it.id == id } + Credential(id, userHandle, decodeName(userHandle))
        }
        return Report(checks + flagLines(response), listOfNotNull(id?.let { "credential id: $it" }))
    }

    /** Reads an assertion like a server would and reports who signed in. */
    fun readAssertion(responseJson: String): Report {
        val response = parse(responseJson)
        val checks = clientDataChecks(response, GET)
        val id = response.string("id")
        val userHandle = (response["response"] as? JsonObject)?.string("userHandle")
        val known = credentials.any { it.id == id }
        val facts = listOfNotNull(
            userHandle?.let { "user: ${decodeName(it)}" },
            id?.let { "credential id: $it" + if (known) "" else " (created before this page loaded)" },
        )
        return Report(checks + flagLines(response), facts)
    }

    /** The name a user handle from [registrationOptions] stands for. */
    fun decodeName(userHandle: String): String =
        runCatching { b64Decode(userHandle).decodeToString() }.getOrDefault(userHandle)

    /** One line per check; [ok] is false for a check that failed. */
    class Check(val ok: Boolean, val text: String)

    class Report(val checks: List<Check>, val facts: List<String>) {
        val ok: Boolean get() = checks.all { it.ok }
        override fun toString(): String = (facts + checks.map { (if (it.ok) "✓ " else "✗ ") + it.text }).joinToString("\n")
    }

    private fun newChallenge(kind: String, userHandle: String?): String {
        // Demo only: a server makes the challenge with a cryptographic RNG and keeps it until the response arrives.
        val challenge = b64(random.nextBytes(32))
        pending = Pending(kind, challenge, userHandle)
        return challenge
    }

    private fun clientDataChecks(response: JsonObject, kind: String): List<Check> {
        val expected = pending
        pending = null
        val clientDataB64 = (response["response"] as? JsonObject)?.string("clientDataJSON")
            ?: return listOf(Check(false, "no clientDataJSON in the response"))
        val clientData = runCatching { Json.parseToJsonElement(b64Decode(clientDataB64).decodeToString()).jsonObject }.getOrNull()
            ?: return listOf(Check(false, "clientDataJSON is not JSON"))
        val type = clientData.string("type")
        val challenge = clientData.string("challenge")
        val origin = clientData.string("origin")
        return listOf(
            Check(type == kind, "type is $type"),
            Check(expected != null && expected.kind == kind && expected.challenge == challenge, "challenge is the one this RP issued"),
            Check(origin == this.origin, "origin is $origin"),
        )
    }

    /** Flags from `authenticatorData` (the browser includes it in both responses): user present, verified, synced. */
    private fun flagLines(response: JsonObject): List<Check> {
        val data = (response["response"] as? JsonObject)?.string("authenticatorData")?.let { runCatching { b64Decode(it) }.getOrNull() }
        if (data == null || data.size < 37) return emptyList()
        val flags = data[32].toInt()
        val signCount = (0..3).fold(0L) { acc, i -> (acc shl 8) or (data[33 + i].toLong() and 0xFF) }
        return listOf(
            Check(flags and 0x01 != 0, "user present (UP)"),
            Check(true, "user verified (UV): ${flags and 0x04 != 0}"),
            Check(true, "backup eligible: ${flags and 0x08 != 0}, backed up: ${flags and 0x10 != 0}"),
            Check(true, "sign count: $signCount"),
        )
    }

    private fun parse(json: String): JsonObject =
        runCatching { Json.parseToJsonElement(json).jsonObject }.getOrDefault(JsonObject(emptyMap()))

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val CREATE = "webauthn.create"
        const val GET = "webauthn.get"
        const val TIMEOUT_MS = 60_000
        const val MAX_USER_ID_BYTES = 64
        val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        fun b64(bytes: ByteArray): String = codec.encode(bytes)
        fun b64Decode(value: String): ByteArray = codec.decode(value)
    }
}
