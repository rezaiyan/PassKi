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
 */
internal class HttpResult(
    val url: String,
    val status: Int,
    val contentType: String?,
    val body: String?,
    val redirectedTo: String?,
    val error: String?,
)

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
    body.isNullOrBlank() -> "$url is empty"
    else -> null
}

/** The host part of an `https://host/...` URL. */
internal fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?')

/** A report with one [DoctorStatus.Skipped] platform check, for platforms the doctor doesn't cover (yet). */
internal fun skippedDoctorReport(rpId: String, reason: String): DoctorReport =
    DoctorReport(
        rpId = rpId,
        checks = listOf(DoctorCheck(PLATFORM_CHECK, DoctorStatus.Skipped, reason, fixHint = null)),
    )

internal const val PLATFORM_CHECK: String = "Platform support"
