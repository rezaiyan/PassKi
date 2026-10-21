package com.alirezaiyan.passki.gradle.check

import groovy.json.JsonSlurper
import java.util.Base64

/**
 * SRV-01..03 against `passki { server { configUrl } }`. The URL answers a GET with either
 *
 * - the server config contract `{"rpId": "example.com", "origins": ["https://example.com", "android:apk-key-hash:..."]}`
 *   (SRV-01, SRV-02), or
 * - WebAuthn registration options `{rp, challenge, user}` (optionally wrapped in `{"publicKey": ...}`) (SRV-01, SRV-03).
 */
internal object ServerChecks {
    private const val MIN_CHALLENGE_BYTES = 16
    private const val MAX_USER_ID_BYTES = 64

    fun evaluate(f: ResultFactory, rpId: String, expectedOrigins: List<String>, fetch: Fetch?): List<CheckResult> {
        if (fetch == null) {
            val reason = "set passki { server { configUrl = \"https://…\" } } to check your server"
            return listOf(f.skip(CheckIds.SRV_01, reason), f.skip(CheckIds.SRV_02, reason), f.skip(CheckIds.SRV_03, reason))
        }
        val response = fetch.response
        val json = when {
            response == null -> null
            response.status != 200 -> null
            else -> try {
                JsonSlurper().parseText(response.body) as? Map<*, *>
            } catch (e: Exception) {
                null
            }
        }
        if (json == null) {
            val why = fetch.error ?: if (response?.status != 200) "HTTP ${response?.status}" else "not a JSON object"
            return listOf(
                f.problem(CheckIds.SRV_01, "could not read ${fetch.url}: $why", hint = "Serve {\"rpId\": …, \"origins\": […]} at ${fetch.url}"),
                f.skip(CheckIds.SRV_02, "blocked by SRV-01"),
                f.skip(CheckIds.SRV_03, "blocked by SRV-01"),
            )
        }
        return if (json.containsKey("rpId")) config(f, rpId, expectedOrigins, json) else options(f, rpId, (json["publicKey"] as? Map<*, *>) ?: json)
    }

    private fun rpIdResult(f: ResultFactory, rpId: String, served: String?): CheckResult = when (served) {
        rpId -> f.pass(CheckIds.SRV_01, "server RP ID is $rpId")
        null -> f.problem(CheckIds.SRV_01, "server reports no RP ID")
        else -> f.problem(CheckIds.SRV_01, "server RP ID is $served, app uses $rpId")
    }

    private fun config(f: ResultFactory, rpId: String, expectedOrigins: List<String>, json: Map<*, *>): List<CheckResult> {
        val origins = (json["origins"] as? List<*>).orEmpty().map { it.toString() }
        val missing = expectedOrigins.filter { it !in origins }
        val srv02 = if (missing.isEmpty()) {
            f.pass(CheckIds.SRV_02, "server allows all ${expectedOrigins.size} expected origin(s)")
        } else {
            f.problem(
                CheckIds.SRV_02,
                "server does not allow ${missing.joinToString()}",
                extra = missing.firstOrNull { it.startsWith(APK_KEY_HASH) }?.let { mapOf("hash" to it.removePrefix(APK_KEY_HASH)) }.orEmpty(),
            )
        }
        return listOf(
            rpIdResult(f, rpId, json["rpId"]?.toString()),
            srv02,
            f.skip(CheckIds.SRV_03, "configUrl serves the config contract; point it at your registration-options endpoint to validate options"),
        )
    }

    private fun options(f: ResultFactory, rpId: String, options: Map<*, *>): List<CheckResult> {
        val rp = (options["rp"] as? Map<*, *>)?.get("id")?.toString()
        val problems = buildList {
            if (rp.isNullOrBlank()) add("rp.id is missing")
            val challenge = decode(options["challenge"])
            if (challenge == null) add("challenge is not base64url") else if (challenge.size < MIN_CHALLENGE_BYTES) add("challenge is ${challenge.size} bytes (< $MIN_CHALLENGE_BYTES)")
            val userId = decode((options["user"] as? Map<*, *>)?.get("id"))
            if (userId == null) add("user.id is missing or not base64url") else if (userId.size !in 1..MAX_USER_ID_BYTES) add("user.id is ${userId.size} bytes (must be 1-$MAX_USER_ID_BYTES)")
        }
        val srv03 = if (problems.isEmpty()) {
            f.pass(CheckIds.SRV_03, "registration options are valid WebAuthn")
        } else {
            f.problem(CheckIds.SRV_03, problems.joinToString("; "))
        }
        return listOf(
            rpIdResult(f, rpId, rp?.takeIf { it.isNotBlank() }),
            f.skip(CheckIds.SRV_02, "registration options don't list allowed origins; serve the config contract to check them"),
            srv03,
        )
    }

    private fun decode(value: Any?): ByteArray? {
        val text = value as? String ?: return null
        return try {
            Base64.getUrlDecoder().decode(text.trimEnd('='))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private const val APK_KEY_HASH = "android:apk-key-hash:"
}
