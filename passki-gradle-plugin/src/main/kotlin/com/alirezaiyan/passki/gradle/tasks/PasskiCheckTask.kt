package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.WellKnownVerifier
import com.alirezaiyan.passki.gradle.check.AndroidChecks
import com.alirezaiyan.passki.gradle.check.Catalog
import com.alirezaiyan.passki.gradle.check.CheckIds
import com.alirezaiyan.passki.gradle.check.CheckReport
import com.alirezaiyan.passki.gradle.check.CheckResult
import com.alirezaiyan.passki.gradle.check.Http
import com.alirezaiyan.passki.gradle.check.IosChecks
import com.alirezaiyan.passki.gradle.check.ResultFactory
import com.alirezaiyan.passki.gradle.check.ServerChecks
import com.alirezaiyan.passki.gradle.check.XcodeProbe
import com.alirezaiyan.passki.gradle.setup.ServerConfig
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Runs every setup check that can run from the build machine (catalog IDs AND-01..05, IOS-01..06, SRV-01..03),
 * prints a checklist, writes `build/reports/passki/check.json` and `index.html`, and fails on any FAIL
 * (or WARN with `-Ppasski.check.warningsAsErrors=true`). Network-dependent: never wired into `check`.
 */
@DisableCachingByDefault(because = "Checks remote state")
public abstract class PasskiCheckTask : PasskiAppleTask() {
    @get:Input
    @get:Optional
    public abstract val serverConfigUrl: Property<String>

    /** Overrides `https://<rpId>` (Gradle property `passki.check.baseUrl`, falls back to `passki.verify.baseUrl`); for tests. */
    @get:Input
    @get:Optional
    public abstract val baseUrl: Property<String>

    /** Google Digital Asset Links API base (Gradle property `passki.check.googleDalUrl`). */
    @get:Input
    public abstract val googleDalUrl: Property<String>

    /** Apple AASA CDN base (Gradle property `passki.check.appleCdnUrl`). */
    @get:Input
    public abstract val appleCdnUrl: Property<String>

    @get:Input
    public abstract val warningsAsErrors: Property<Boolean>

    @get:OutputDirectory
    public abstract val reportDir: DirectoryProperty

    @TaskAction
    public fun check() {
        val rp = requireRpId()
        val apple = Apple()
        val byPackage = fingerprintsByPackage(resolveVariants())
        val appIds = apple.appIds
        val firstPackage = byPackage.keys.sorted().firstOrNull()
        val placeholders = buildMap {
            put("rpId", rp)
            firstPackage?.let { put("package", it) }
            byPackage[firstPackage]?.firstOrNull()?.let { put("fingerprint", it) }
            apple.teamId?.let { put("teamId", it) }
            apple.bundleId?.let { put("bundleId", it) }
            apple.entitlementsSetting?.let { put("path", it) }
        }
        val f = ResultFactory(Catalog.bundled, placeholders)
        val http = Http()
        val base = (baseUrl.orNull ?: "https://$rp").trimEnd('/')
        val expected = WellKnownVerifier.Expected(rp, byPackage, appIds)
        val results = mutableListOf<CheckResult>()

        // Android
        results += AndroidChecks.wellKnown(f, expected, http.get("$base/.well-known/${PasskiWellKnownTask.ASSET_LINKS}"))
        val dalUrl = googleDalUrl.get().trimEnd('/') +
            "/v1/statements:list?source.web.site=https://$rp&relation=delegate_permission/common.get_login_creds"
        results += AndroidChecks.dal(f, expected, http.get(dalUrl))
        results += AndroidChecks.playSigning(f, variants.get(), extraFingerprints.get())

        // iOS
        if (!apple.configured) {
            val reason = "Apple not configured (passki { apple { teamId; bundleId } } or an Xcode project)"
            listOf(CheckIds.IOS_01, CheckIds.IOS_02, CheckIds.IOS_03, CheckIds.IOS_04, CheckIds.IOS_05, CheckIds.IOS_06).forEach { results += f.skip(it, reason) }
        } else {
            if (appIds.isEmpty()) {
                val reason = "Team ID / bundle ID unknown (set them in passki { apple { } } or make xcodebuild available)"
                listOf(CheckIds.IOS_01, CheckIds.IOS_02, CheckIds.IOS_03).forEach { results += f.skip(it, reason) }
            } else {
                results += IosChecks.wellKnown(f, expected, http.get("$base/.well-known/${PasskiWellKnownTask.AASA}"))
                results += IosChecks.cdn(f, appIds, http.get("${appleCdnUrl.get().trimEnd('/')}/a/v1/$rp"))
            }
            results += IosChecks.entitlements(f, rp, apple.entitlements)
            val probe = apple.probe
            results += IosChecks.wiring(f, rp, probe)
            val built = (probe as? XcodeProbe.Found)?.let { apple.xcode.builtApp(it.settings, apple.entitlements) }
            results += if (probe is XcodeProbe.Unavailable) f.skip(CheckIds.IOS_06, probe.reason) else IosChecks.builtApp(f, rp, built)
        }

        // Server
        val origins = ServerConfig.of(rp, byPackage, appIds).origins
        results += ServerChecks.evaluate(f, rp, origins, serverConfigUrl.orNull?.let(http::get))

        val report = CheckReport(rp, results, Catalog.bundled)
        val dir = reportDir.get().asFile.apply { mkdirs() }
        File(dir, "check.json").writeText(report.json())
        val html = File(dir, "index.html").apply { writeText(report.html()) }
        logger.lifecycle(report.console())
        logger.lifecycle("\npasski: report at ${html.toURI()}")

        val failing = report.failing(warningsAsErrors.get())
        if (failing.isNotEmpty()) {
            throw GradleException(
                "passkiCheck: ${failing.size} check(s) failed: ${failing.joinToString { it.id }}. See the checklist above or ${html.toURI()}",
            )
        }
    }
}
