package com.alirezaiyan.passki.internal.doctor

import com.alirezaiyan.passki.DoctorCheck
import com.alirezaiyan.passki.DoctorReport
import com.alirezaiyan.passki.DoctorStatus

/**
 * Outcome of one HTTP GET, as gathered by a platform [HttpGetter]. Redirects must not be followed.
 *
 * @property status HTTP status code, or -1 when no response arrived (see [error]).
 * @property redirectedTo `Location` of a redirect response, or the final URL when the platform followed it anyway.
 * @property error transport failure (DNS, TLS, timeout...), null when a response arrived.
 * @property truncated true when the body exceeded [MAX_BODY_BYTES] and [body] holds only the first part.
 */
internal class HttpResult(
    val url: String,
    val status: Int,
    val contentType: String?,
    val body: String?,
    val redirectedTo: String?,
    val error: String?,
    val truncated: Boolean = false,
)

/** Largest body a platform [HttpGetter] reads; bigger files are cut off and flagged [HttpResult.truncated]. */
internal const val MAX_BODY_BYTES: Int = 1 shl 20

/** Platform HTTP GET used by the doctor; faked in tests. Implementations never throw, they return [HttpResult.error]. */
internal fun interface HttpGetter {
    suspend fun get(url: String): HttpResult
}

/**
 * Why [this] result can't be used as a well-known file, or null when it's a 200 with a body.
 * Well-known files must be served directly: neither Android nor Apple follow redirects for them.
 */
internal fun HttpResult.fetchProblem(): String? = when {
    error != null -> "could not fetch $url: $error"
    redirectedTo != null || status in 300..399 -> "$url redirects to ${redirectedTo ?: "another location"} (redirects are not followed)"
    status != 200 -> "$url returned HTTP $status"
    truncated -> "$url is larger than 1 MiB; the doctor only reads the first 1 MiB"
    body.isNullOrBlank() -> "$url is empty"
    else -> null
}

/** The host part of an `https://host/...` URL. */
internal fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?')

/** A report with one [DoctorStatus.Skipped] platform check, for platforms the doctor doesn't cover (yet). */
internal fun skippedDoctorReport(rpId: String, reason: String): DoctorReport =
    DoctorReport(
        rpId = rpId,
        checks = listOf(DoctorCheck(PLATFORM_CHECK, DoctorStatus.Skipped, reason, fixHint = null, id = CheckIds.DEV_03)),
    )

internal const val PLATFORM_CHECK: String = "Platform support"

internal const val RP_ID_CHECK: String = "Relying party ID"

/**
 * A [DoctorStatus.Fail] check when [rpId] isn't a bare domain (scheme, path, port, query...), else null.
 * The doctor doesn't normalise silently: the same value is likely passed to the WebAuthn options too.
 */
internal fun rpIdCheck(rpId: String): DoctorCheck? {
    val trimmed = rpId.trim()
    if (trimmed.isEmpty()) {
        return DoctorCheck(RP_ID_CHECK, DoctorStatus.Fail, "rpId is empty", fixHint = "pass your domain, e.g. example.com", id = CheckIds.CFG_01)
    }
    val bare = trimmed == rpId && trimmed.none { it in "/:?#@ " }
    if (bare) return null
    val suggestion = hostOf(trimmed).substringBefore('#').substringAfter('@').substringBefore(':').lowercase()
    val use = if (suggestion.isEmpty()) "" else "; use $suggestion"
    return DoctorCheck(
        RP_ID_CHECK,
        DoctorStatus.Fail,
        "rpId must be a bare domain (got $rpId)$use",
        fixHint = "pass the domain only: no scheme, port, path or query (the same value as the WebAuthn rp.id)",
        id = CheckIds.CFG_01,
    )
}
