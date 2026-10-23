package com.alirezaiyan.passki.gradle

import java.net.URI

/** Validates `passki { rpId }`: apps have no origin, so the RP ID must be a bare, lowercase domain. */
internal object RpId {
    private val DOMAIN = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]([a-z0-9-]{0,61}[a-z0-9])?$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** Returns [value] when it is a bare domain; otherwise throws with the corrected value when one can be derived. */
    fun validate(value: String): String {
        if (DOMAIN.matches(value) && !IPV4.matches(value)) return value
        val suggestion = suggest(value)
        if (suggestion != null && suggestion != value) {
            throw PasskiConfigException("passki { rpId = \"$value\" }: the RP ID is a bare domain: use \"$suggestion\"")
        }
        throw PasskiConfigException(
            "passki { rpId = \"$value\" }: the RP ID must be a bare domain you serve /.well-known/ files from, e.g. \"example.com\"",
        )
    }

    private fun suggest(value: String): String? {
        val trimmed = value.trim()
        val host = if ("://" in trimmed) {
            runCatching { URI(trimmed).host }.getOrNull()
        } else {
            trimmed.substringBefore('/').substringBefore(':')
        } ?: return null
        val candidate = host.lowercase().removeSuffix(".")
        return candidate.takeIf { DOMAIN.matches(it) && !IPV4.matches(it) }
    }
}
