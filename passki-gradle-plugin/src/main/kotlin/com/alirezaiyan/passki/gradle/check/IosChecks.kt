package com.alirezaiyan.passki.gradle.check

import com.alirezaiyan.passki.gradle.WellKnownVerifier
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Expected
import com.alirezaiyan.passki.gradle.setup.XcodeSettings
import groovy.json.JsonSlurper
import java.io.File

/** Result of asking `xcodebuild` for the app target's build settings. */
internal sealed class XcodeProbe {
    data class Found(val settings: XcodeSettings) : XcodeProbe()

    data class Unavailable(val reason: String) : XcodeProbe()
}

/**
 * The last built app: where it is, where its entitlements were read from ([source]: codesign or the
 * Simulator's `-Simulated.xcent`), the entitlements XML (null if unreadable), and whether it predates the
 * entitlements file.
 */
internal data class BuiltApp(val path: String, val source: String, val entitlements: String?, val stale: Boolean)

/** IOS-01..06 from the build machine. */
internal object IosChecks {
    fun wellKnown(f: ResultFactory, expected: Expected, aasa: Fetch): List<CheckResult> {
        val response = aasa.response
            ?: return listOf(f.problem(CheckIds.IOS_01, "could not fetch ${aasa.url}: ${aasa.error}"), f.skip(CheckIds.IOS_02, "blocked by IOS-01"))
        val problems = WellKnownVerifier.verify(expected.copy(fingerprintsByPackage = emptyMap()), null, response, null)
        val ios01 = f.fromProblems(CheckIds.IOS_01, problems, "${aasa.url} served (HTTP 200, JSON, no redirect)")
        if (ios01.status == CheckStatus.FAIL) return listOf(ios01, f.skip(CheckIds.IOS_02, "blocked by IOS-01"))
        return listOf(ios01, f.fromProblems(CheckIds.IOS_02, problems, "webcredentials.apps lists ${expected.appleAppIds.joinToString()}"))
    }

    /** IOS-03: Apple's CDN copy lists every app ID. */
    fun cdn(f: ResultFactory, appIds: List<String>, cdn: Fetch): CheckResult {
        val response = cdn.response
        if (response == null || response.status != 200) {
            return f.problem(CheckIds.IOS_03, "Apple's CDN has no copy at ${cdn.url} (${cdn.error ?: "HTTP ${response?.status}"})")
        }
        val apps = try {
            (((JsonSlurper().parseText(response.body) as? Map<*, *>)?.get("webcredentials") as? Map<*, *>)?.get("apps") as? List<*>).orEmpty()
        } catch (e: Exception) {
            emptyList<Any>()
        }
        val missing = appIds.filter { it !in apps }
        return if (missing.isEmpty()) {
            f.pass(CheckIds.IOS_03, "Apple's CDN lists ${appIds.joinToString()}")
        } else {
            f.problem(CheckIds.IOS_03, "Apple's CDN copy doesn't list ${missing.joinToString()} yet")
        }
    }

    /** IOS-04: the configured (or discovered) entitlements file lists webcredentials:<rpId>. */
    fun entitlements(f: ResultFactory, rpId: String, file: File?): CheckResult {
        if (file == null) return f.skip(CheckIds.IOS_04, "no entitlements file configured (passki { apple { entitlementsFile } }) or found via xcodebuild")
        if (!file.isFile) return f.problem(CheckIds.IOS_04, "entitlements file ${file.path} does not exist", hint = "Run ./gradlew passkiSetup to create it")
        val domains = try {
            WellKnownVerifier.associatedDomains(file.readText())
        } catch (e: Exception) {
            return f.problem(CheckIds.IOS_04, "${file.path} is not a readable plist: ${e.message}")
        }
        return if (WellKnownVerifier.coversWebcredentials(domains, rpId)) {
            f.pass(CheckIds.IOS_04, "${file.name} lists ${domains.filter { it.startsWith("webcredentials:$rpId") }.joinToString()}")
        } else {
            f.problem(CheckIds.IOS_04, "${file.path} does not list webcredentials:$rpId (found: ${domains.ifEmpty { listOf("none") }.joinToString()})")
        }
    }

    /** IOS-05: `CODE_SIGN_ENTITLEMENTS`, as Xcode resolves it, points at a file listing webcredentials:<rpId>. */
    fun wiring(f: ResultFactory, rpId: String, probe: XcodeProbe): CheckResult {
        val settings = when (probe) {
            is XcodeProbe.Unavailable -> return f.skip(CheckIds.IOS_05, probe.reason)
            is XcodeProbe.Found -> probe.settings
        }
        val target = settings.target ?: "the app target"
        val file = settings.entitlementsFile()
            ?: return f.problem(
                CheckIds.IOS_05,
                "CODE_SIGN_ENTITLEMENTS is not set for $target, so Xcode signs the app without associated domains",
                hint = "Run ./gradlew passkiSetup to write it into the base xcconfig",
            )
        if (!file.isFile) {
            return f.problem(CheckIds.IOS_05, "CODE_SIGN_ENTITLEMENTS = ${settings["CODE_SIGN_ENTITLEMENTS"]} but ${file.path} does not exist")
        }
        val domains = try {
            WellKnownVerifier.associatedDomains(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
        return if (WellKnownVerifier.coversWebcredentials(domains, rpId)) {
            f.pass(CheckIds.IOS_05, "$target: CODE_SIGN_ENTITLEMENTS = ${settings["CODE_SIGN_ENTITLEMENTS"]} (lists webcredentials:$rpId)")
        } else {
            f.problem(CheckIds.IOS_05, "$target: CODE_SIGN_ENTITLEMENTS = ${settings["CODE_SIGN_ENTITLEMENTS"]}, which does not list webcredentials:$rpId")
        }
    }

    /** IOS-06: the last built app carries webcredentials:<rpId>. */
    fun builtApp(f: ResultFactory, rpId: String, app: BuiltApp?): CheckResult {
        if (app == null) return f.skip(CheckIds.IOS_06, "no built app found in the project's DerivedData; build it in Xcode and re-run")
        val entitlements = app.entitlements
            ?: return f.problem(CheckIds.IOS_06, "could not read entitlements of ${app.path} (${app.source})")
        val domains = try {
            WellKnownVerifier.associatedDomains(entitlements)
        } catch (e: Exception) {
            emptyList()
        }
        if (!WellKnownVerifier.coversWebcredentials(domains, rpId)) {
            return f.problem(CheckIds.IOS_06, "${app.path} was signed without webcredentials:$rpId (read from ${app.source})")
        }
        if (app.stale) {
            return f.problem(CheckIds.IOS_06, "${app.path} carries webcredentials:$rpId but was built before the entitlements file last changed", hint = "Rebuild the app", warning = true)
        }
        return f.pass(CheckIds.IOS_06, "${File(app.path).name} carries webcredentials:$rpId (${app.source})")
    }
}
