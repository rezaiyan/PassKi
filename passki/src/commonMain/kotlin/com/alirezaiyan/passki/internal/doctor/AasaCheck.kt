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

        val cdnProblem = cdn.fetchProblem()
        val cdnDiffers = cdnProblem == null && parse(cdn.body.orEmpty()) != directJson
        if (cdnProblem != null || cdnDiffers) {
            return DoctorCheck(
                NAME,
                DoctorStatus.Warn,
                if (cdnProblem != null) {
                    "lists $match (team ID $teamId), but Apple's CDN copy is unavailable: $cdnProblem"
                } else {
                    "lists $match (team ID $teamId), but Apple's CDN copy at ${cdn.url} differs"
                },
                "Apple's CDN can lag up to 24h; use webcredentials:$rpId?mode=developer while testing",
            )
        }
        return DoctorCheck(NAME, DoctorStatus.Pass, "lists $match (team ID $teamId); Apple's CDN copy matches", fixHint = null)
    }

    private fun fail(message: String, fixHint: String) = DoctorCheck(NAME, DoctorStatus.Fail, message, fixHint)

    private fun parse(body: String): JsonElement? = try {
        PasskiJson.parseToJsonElement(body)
    } catch (_: SerializationException) {
        null
    }
}
