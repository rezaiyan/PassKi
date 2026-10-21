package com.alirezaiyan.passki.gradle.check

import com.alirezaiyan.passki.gradle.VariantSpec
import com.alirezaiyan.passki.gradle.WellKnownVerifier
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Expected
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Fetched
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Problem
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Severity
import groovy.json.JsonSlurper

/** One GET: either a [response] or a transport [error]. */
internal data class Fetch(val url: String, val response: Fetched?, val error: String?)

/** Turns verifier [Problem]s of one catalog ID into a result: PASS when there are none. */
internal fun ResultFactory.fromProblems(id: String, problems: List<Problem>, passMessage: String, extra: Map<String, String> = emptyMap()): CheckResult {
    val mine = problems.filter { it.id == id }
    if (mine.isEmpty()) return pass(id, passMessage)
    return problem(
        id,
        mine.joinToString("; ") { it.message },
        hint = mine.map { it.fix }.distinct().joinToString("\n"),
        warning = mine.all { it.severity == Severity.WARNING },
        extra = extra,
    )
}

/** AND-01..05 from the build machine. */
internal object AndroidChecks {
    fun wellKnown(f: ResultFactory, expected: Expected, assetLinks: Fetch): List<CheckResult> {
        val response = assetLinks.response
        if (response == null) {
            return listOf(
                f.problem(CheckIds.AND_01, "could not fetch ${assetLinks.url}: ${assetLinks.error}"),
                f.skip(CheckIds.AND_02, "blocked by AND-01"),
                f.skip(CheckIds.AND_03, "blocked by AND-01"),
            )
        }
        val problems = WellKnownVerifier.verify(expected.copy(appleAppIds = emptyList()), response, null, null)
        val and01 = f.fromProblems(CheckIds.AND_01, problems, "${assetLinks.url} served (HTTP 200, JSON, no redirect)")
        if (and01.status == CheckStatus.FAIL) {
            return listOf(and01, f.skip(CheckIds.AND_02, "blocked by AND-01"), f.skip(CheckIds.AND_03, "blocked by AND-01"))
        }
        val packages = expected.fingerprintsByPackage.keys.sorted()
        val and02 = f.fromProblems(CheckIds.AND_02, problems, "get_login_creds granted to ${packages.joinToString()}")
        val and03 = if (and02.status != CheckStatus.PASS && problems.none { it.id == CheckIds.AND_03 }) {
            f.skip(CheckIds.AND_03, "blocked by AND-02")
        } else {
            val missing = problems.filter { it.id == CheckIds.AND_03 }.flatMap { p -> FINGERPRINT.findAll(p.message).map { it.value }.toList() }
            val count = expected.fingerprintsByPackage.values.sumOf { it.size }
            f.fromProblems(
                CheckIds.AND_03,
                problems,
                "$count fingerprint(s) listed for ${packages.joinToString()}",
                extra = if (missing.isEmpty()) emptyMap() else mapOf("fingerprint" to missing.joinToString(", ")),
            )
        }
        return listOf(and01, and02, and03)
    }

    /** AND-04: Google's Digital Asset Links API lists every expected package + fingerprint. */
    fun dal(f: ResultFactory, expected: Expected, dal: Fetch): CheckResult {
        val response = dal.response
        if (response == null || response.status != 200) {
            return f.problem(CheckIds.AND_04, "could not query ${dal.url}: ${dal.error ?: "HTTP ${response?.status}"}")
        }
        val statements = try {
            ((JsonSlurper().parseText(response.body) as? Map<*, *>)?.get("statements") as? List<*>).orEmpty()
        } catch (e: Exception) {
            return f.problem(CheckIds.AND_04, "Digital Asset Links API returned invalid JSON")
        }
        val seen = statements.mapNotNull { statement ->
            val app = (((statement as? Map<*, *>)?.get("target") as? Map<*, *>)?.get("androidApp") as? Map<*, *>) ?: return@mapNotNull null
            val fingerprint = (app["certificate"] as? Map<*, *>)?.get("sha256Fingerprint")?.toString() ?: return@mapNotNull null
            app["packageName"].toString() to fingerprint.uppercase()
        }.toSet()
        val missing = expected.fingerprintsByPackage.flatMap { (pkg, fps) -> fps.filter { (pkg to it.uppercase()) !in seen }.map { "$pkg $it" } }
        return if (missing.isEmpty()) {
            f.pass(CheckIds.AND_04, "Google lists every package and fingerprint (${seen.size} statement(s))")
        } else {
            f.problem(CheckIds.AND_04, "Google's Digital Asset Links API doesn't list: ${missing.joinToString("; ")}")
        }
    }

    /** AND-05: a release variant without local signing and without `extraFingerprints` is likely Play-signed with an unlisted key. */
    fun playSigning(f: ResultFactory, variants: List<VariantSpec>, extraFingerprints: List<String>): CheckResult {
        val release = variants.filterNot { it.debuggable }
        if (release.isEmpty()) return f.skip(CheckIds.AND_05, "no release (non-debuggable) variants")
        if (extraFingerprints.isNotEmpty()) {
            return f.pass(CheckIds.AND_05, "${extraFingerprints.size} extra fingerprint(s) configured for release packages")
        }
        val unsigned = release.filter { it.keystore == null }.map { it.label }
        return if (unsigned.isEmpty()) {
            f.pass(CheckIds.AND_05, "release signed locally (${release.joinToString { it.label }}); add the Play app signing key via extraFingerprints if Play re-signs")
        } else {
            f.problem(CheckIds.AND_05, "release variant(s) ${unsigned.joinToString()} have no signing config and no passki.android.extraFingerprints; Play App Signing key likely missing")
        }
    }

    private val FINGERPRINT = Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")
}
