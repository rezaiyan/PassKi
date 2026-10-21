package com.alirezaiyan.passki.internal.doctor

import com.alirezaiyan.passki.DoctorCheck
import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.internal.PasskiJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Checks `/.well-known/apple-app-site-association` for a `webcredentials` entry matching this app,
 * fetched both directly from the relying party and from Apple's CDN (which is what devices actually read).
 */
internal object AasaCheck {
    const val NAME: String = "apple-app-site-association"

    /** Apple's CDN refuses AASA files larger than this. */
    private const val APPLE_MAX_AASA_BYTES = 128 * 1024

    /**
     * @param direct `https://<rpId>/.well-known/apple-app-site-association`
     * @param cdn `https://app-site-association.cdn-apple.com/a/v1/<rpId>`
     */
    fun evaluate(direct: HttpResult, cdn: HttpResult, bundleId: String): DoctorCheck {
        val rpId = hostOf(direct.url)
        direct.fetchProblem()?.let { problem ->
            return fail(problem, "serve the file at ${direct.url} with HTTP 200, without redirects (passkiWellKnown generates it)")
        }
        val directJson = parse(direct.body.orEmpty()) as? JsonObject
            ?: return fail("${direct.url} is not a JSON object", "fix the JSON syntax; passkiWellKnown generates a valid file")

        val apps = (directJson["webcredentials"] as? JsonObject)?.get("apps").strings()
        val match = apps.firstOrNull { it.contains('.') && it.substringAfter('.') == bundleId }
        if (match == null) {
            val teamIds = apps.map { it.substringBefore('.') }.distinct()
            val found = if (apps.isEmpty()) "it lists no apps" else "found ${apps.joinToString()} (team ID ${teamIds.joinToString()})"
            val teamId = teamIds.singleOrNull() ?: "<TEAM_ID>"
            return fail(
                "webcredentials.apps has no entry for $bundleId; $found",
                "add \"$teamId.$bundleId\" to webcredentials.apps in ${direct.url}",
            )
        }
        val teamId = match.substringBefore('.')

        val size = direct.body.orEmpty().encodeToByteArray().size
        if (size > APPLE_MAX_AASA_BYTES) {
            return DoctorCheck(
                NAME,
                DoctorStatus.Warn,
                "lists $match (team ID $teamId), but the file is ${size / 1024} KB; Apple ignores AASA files over 128 KB",
                "shrink ${direct.url} below 128 KB (e.g. trim applinks paths)",
            )
        }

        val cdnHint = "Apple's CDN can lag up to 24h; while testing use webcredentials:$rpId?mode=developer and enable " +
            "Settings > Developer > Associated Domains Development on the device"
        // Only the webcredentials entry matters for passkeys; applinks or formatting differences are irrelevant here.
        val cdnFetchProblem = cdn.fetchProblem()
        val cdnJson = if (cdnFetchProblem == null) parse(cdn.body.orEmpty()) as? JsonObject else null
        if (cdnJson == null) {
            return DoctorCheck(
                NAME,
                DoctorStatus.Warn,
                "lists $match (team ID $teamId), but Apple's CDN copy is unavailable: " +
                    (cdnFetchProblem ?: "${cdn.url} is not a JSON object"),
                cdnHint,
            )
        }
        val cdnApps = (cdnJson["webcredentials"] as? JsonObject)?.get("apps").strings()
        if (match !in cdnApps) {
            return DoctorCheck(
                NAME,
                DoctorStatus.Warn,
                "lists $match (team ID $teamId), but Apple's CDN copy at ${cdn.url} doesn't yet (CDN lagging)",
                cdnHint,
            )
        }
        return DoctorCheck(NAME, DoctorStatus.Pass, "lists $match (team ID $teamId); Apple's CDN copy lists it too", fixHint = null)
    }

    private fun fail(message: String, fixHint: String) = DoctorCheck(NAME, DoctorStatus.Fail, message, fixHint)

    private fun parse(body: String): JsonElement? = try {
        PasskiJson.parseToJsonElement(body)
    } catch (_: SerializationException) {
        null
    }
}
