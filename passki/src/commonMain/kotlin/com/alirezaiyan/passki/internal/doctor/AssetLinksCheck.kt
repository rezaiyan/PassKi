package com.alirezaiyan.passki.internal.doctor

import com.alirezaiyan.passki.DoctorCheck
import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.internal.Base64Url
import com.alirezaiyan.passki.internal.PasskiJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Checks a fetched `/.well-known/assetlinks.json` against this app's package and signing certificates. */
internal object AssetLinksCheck {
    const val NAME: String = "assetlinks.json"
    const val LOGIN_CREDS_RELATION: String = "delegate_permission/common.get_login_creds"

    /**
     * @param certFingerprints SHA-256 signing certificate fingerprints, colon hex (`AB:CD:...`), any case.
     */
    fun evaluate(result: HttpResult, packageName: String, certFingerprints: List<String>): DoctorCheck {
        result.fetchProblem()?.let { problem ->
            return fail(problem, "serve the file at ${result.url} with HTTP 200, without redirects (passkiWellKnown generates it)")
        }
        val statements = parseStatements(result.body.orEmpty())
            ?: return fail("${result.url} is not a JSON array of statements", "fix the JSON syntax; passkiWellKnown generates a valid file")

        val loginStatements = statements.filter { LOGIN_CREDS_RELATION in it.relations }
        if (loginStatements.isEmpty()) {
            return fail(
                "no statement grants $LOGIN_CREDS_RELATION",
                addStatementHint(result.url, packageName, certFingerprints),
            )
        }
        val ours = loginStatements.filter { it.namespace == "android_app" && it.packageName == packageName }
        if (ours.isEmpty()) {
            return fail(
                "no $LOGIN_CREDS_RELATION statement for package $packageName",
                addStatementHint(result.url, packageName, certFingerprints),
            )
        }
        if (certFingerprints.isEmpty()) {
            return fail("no signing certificate found for $packageName", fixHint = null)
        }
        val listed = ours.flatMapTo(mutableSetOf()) { it.fingerprints.map(String::uppercase) }
        val missing = certFingerprints.filter { it.uppercase() !in listed }
        if (missing.isNotEmpty()) {
            return fail(
                "${plural(missing.size, "fingerprint")} ${missing.joinToString()} missing for $packageName",
                addStatementHint(result.url, packageName, missing),
            )
        }
        val contentType = result.contentType
        if (contentType == null || !contentType.substringBefore(';').trim().equals("application/json", ignoreCase = true)) {
            return DoctorCheck(
                NAME,
                DoctorStatus.Warn,
                "served with Content-Type ${contentType ?: "(none)"}, expected application/json",
                fixHint = "configure your server to send Content-Type: application/json for ${result.url}",
            )
        }
        return DoctorCheck(
            NAME,
            DoctorStatus.Pass,
            "$packageName delegated with ${plural(certFingerprints.size, "fingerprint")}",
            fixHint = null,
        )
    }

    private fun fail(message: String, fixHint: String?) = DoctorCheck(NAME, DoctorStatus.Fail, message, fixHint)

    private fun addStatementHint(url: String, packageName: String, fingerprints: List<String>): String =
        "add this statement to $url:\n" + PasskiJson.encodeToString(JsonElement.serializer(), statement(packageName, fingerprints))

    private fun statement(packageName: String, fingerprints: List<String>): JsonObject = buildJsonObject {
        putJsonArray("relation") { add(JsonPrimitive(LOGIN_CREDS_RELATION)) }
        putJsonObject("target") {
            put("namespace", "android_app")
            put("package_name", packageName)
            putJsonArray("sha256_cert_fingerprints") { fingerprints.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private class Statement(
        val relations: List<String>,
        val namespace: String?,
        val packageName: String?,
        val fingerprints: List<String>,
    )

    /** Null when [body] isn't a JSON array. Malformed entries inside the array are skipped. */
    private fun parseStatements(body: String): List<Statement>? {
        val root = try {
            PasskiJson.parseToJsonElement(body)
        } catch (_: SerializationException) {
            return null
        }
        if (root !is JsonArray) return null
        return root.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val target = obj["target"] as? JsonObject
            Statement(
                relations = obj["relation"].strings(),
                namespace = (target?.get("namespace") as? JsonPrimitive)?.contentOrNullIfString(),
                packageName = (target?.get("package_name") as? JsonPrimitive)?.contentOrNullIfString(),
                fingerprints = target?.get("sha256_cert_fingerprints").strings(),
            )
        }
    }
}

/**
 * The WebAuthn origin Android reports for an app signed with [fingerprint]
 * (SHA-256, colon hex, any case): `android:apk-key-hash:<base64url of the 32 bytes>`.
 */
internal fun androidOrigin(fingerprint: String): String {
    val parts = fingerprint.split(':')
    require(parts.size == SHA256_BYTES && parts.all { part -> part.length == 2 && part.all { it in HEX_DIGITS } }) {
        "Not a SHA-256 fingerprint: $fingerprint"
    }
    val bytes = ByteArray(parts.size) { i -> parts[i].toInt(16).toByte() }
    return "android:apk-key-hash:" + Base64Url.encode(bytes)
}

private const val SHA256_BYTES = 32
private const val HEX_DIGITS = "0123456789abcdefABCDEF"

internal fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

internal fun JsonElement?.strings(): List<String> =
    (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullIfString() }.orEmpty()

internal fun JsonPrimitive.contentOrNullIfString(): String? = if (isString) content else null
