package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.PasskiConfigException
import com.alirezaiyan.passki.gradle.WellKnownFiles
import com.alirezaiyan.passki.gradle.setup.DemoRegistration
import com.alirezaiyan.passki.gradle.setup.Entitlements
import com.alirezaiyan.passki.gradle.setup.FileChanges
import com.alirezaiyan.passki.gradle.setup.ServerConfig
import com.alirezaiyan.passki.gradle.setup.Xcconfig
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * One-shot, idempotent setup driven by `passki { }`:
 *
 * 1. writes `assetlinks.json` / `apple-app-site-association` into `build/passki/well-known` (with your own `rpId`),
 *    or registers the app with the demo relying party (with `useDemoServer()`);
 * 2. merges `webcredentials:<rpId>` (and `?mode=developer` when `apple.developerMode`) into the entitlements file;
 * 3. wires it into the build with `CODE_SIGN_ENTITLEMENTS` in the base `.xcconfig` (never touches project.pbxproj;
 *    prints the Xcode steps when there's no xcconfig);
 * 4. writes `build/passki/server-config.json` and verifier snippets into `build/passki/server-snippets/`;
 * 5. prints a summary: every file created / changed / unchanged (with a diff for your own files), every request
 *    sent, and the remaining manual steps.
 *
 * `--preview` prints the same summary and writes or sends nothing. Team ID and bundle ID are read from
 * `xcodebuild -showBuildSettings` when not set in the DSL. Without an Android app module, Android is skipped
 * (iOS-only mode) and the task says so.
 */
@DisableCachingByDefault(because = "Edits files in the Xcode project")
public abstract class PasskiSetupTask : PasskiAppleTask() {
    @get:Input
    public abstract val developerMode: Property<Boolean>

    /** Preview: print what would be written and sent, change nothing. */
    @get:Input
    @get:Option(option = "preview", description = "Print what passkiSetup would write and send, without changing anything.")
    public abstract val preview: Property<Boolean>

    /** `build/passki`: well-known/, server-config.json, server-snippets/. Untracked (shared with passkiWellKnown). */
    @get:Internal
    public abstract val outputDir: DirectoryProperty

    /** The build's root directory; summary paths are shown relative to it. */
    @get:Internal
    public abstract val rootDirectory: DirectoryProperty

    init {
        preview.convention(false)
    }

    @TaskAction
    public fun setup() {
        val rp = requireRpId()
        val isPreview = preview.get()
        val apple = Apple()
        val out = outputDir.get().asFile
        val changes = FileChanges(isPreview, rootDirectory.get().asFile)
        val steps = mutableListOf<String>()
        val byPackage = androidFingerprints()

        val appIds = apple.appIds
        if (apple.discovered && appIds.isNotEmpty()) {
            logger.lifecycle("passki: read ${appIds.joinToString()} from xcodebuild -showBuildSettings (scheme ${apple.schemeName})")
        }
        if (appIds.isEmpty()) {
            logger.lifecycle("passki: no Apple Team ID / bundle ID (passki { apple { } } or xcodebuild), so iOS is not registered or listed")
        }
        if (byPackage.isEmpty() && !apple.configured) {
            throw PasskiConfigException(
                "passkiSetup has nothing to set up: ${iosOnlyReason()} No Apple app found either: set passki { apple { teamId; bundleId } }.",
            )
        }

        // 1. Well-known files: generated for your domain, or the app registered with the demo server.
        val failed = if (demoMode()) registerWithDemo(byPackage, appIds, changes, steps) else writeWellKnown(rp, out, byPackage, appIds, changes, steps)

        // 2 + 3. iOS entitlements and their wiring.
        if (apple.configured) setupIos(rp, apple, changes, steps)

        // 4. Server config + snippets (the demo server is already configured).
        if (!demoMode()) {
            val server = ServerConfig.of(rp, byPackage, appIds)
            changes.write(File(out, "server-config.json"), server.json(), showDiff = false)
            server.snippets().forEach { (name, text) -> changes.write(File(out, "server-snippets/$name"), text, showDiff = false) }
            steps += "Configure your WebAuthn server with RP ID $rp and these origins: ${server.origins.joinToString()} " +
                "(${changes.relative(File(out, "server-config.json"))}; snippets in ${changes.relative(File(out, "server-snippets"))}/)."
        }

        // 5. Summary and next steps.
        steps += when {
            !isPreview -> "Run ./gradlew passkiCheck to verify everything end to end."
            changes.hasChanges() -> "Run ./gradlew passkiSetup without --preview to apply this."
            else -> "Nothing would change: everything is already set up. Run ./gradlew passkiCheck to verify it end to end."
        }
        logger.lifecycle("\n" + changes.summary(steps))
        if (failed.isNotEmpty()) {
            throw GradleException("passkiSetup: demo registration failed for ${failed.joinToString()}. See the summary above.")
        }
    }

    private fun writeWellKnown(
        rp: String,
        out: File,
        byPackage: Map<String, List<String>>,
        appIds: List<String>,
        changes: FileChanges,
        steps: MutableList<String>,
    ): List<String> {
        val wellKnown = File(out, "well-known")
        val files = buildList {
            val assetLinks = File(wellKnown, PasskiWellKnownTask.ASSET_LINKS)
            if (byPackage.isEmpty()) changes.delete(assetLinks) else add(assetLinks.also { changes.write(it, WellKnownFiles.assetLinks(byPackage), showDiff = false) })
            val aasa = File(wellKnown, PasskiWellKnownTask.AASA)
            if (appIds.isEmpty()) changes.delete(aasa) else add(aasa.also { changes.write(it, WellKnownFiles.aasa(appIds), showDiff = false) })
        }
        if (files.isNotEmpty()) {
            steps += "Upload ${files.joinToString(" and ") { changes.relative(it) }} to https://$rp/.well-known/ " +
                "(HTTPS, HTTP 200, Content-Type: application/json, no redirects)."
        }
        return emptyList()
    }

    /** Registers each Android package and Apple app ID with the demo server; returns the labels that failed. */
    private fun registerWithDemo(
        byPackage: Map<String, List<String>>,
        appIds: List<String>,
        changes: FileChanges,
        steps: MutableList<String>,
    ): List<String> {
        val base = demoServerUrl.get()
        logger.lifecycle("passki: demo server mode: registering this app with $base for 7 days (public data only, shown below)")
        val failed = mutableListOf<String>()
        DemoRegistration.requests(base, byPackage, appIds).forEach { request ->
            val outcome = if (preview.get()) DemoRegistration.Outcome(true, "not sent (preview)") else DemoRegistration.send(request)
            changes.sent("POST ${request.url} ${request.body}", outcome.message)
            if (!outcome.ok) failed += request.label
        }
        if (byPackage.isNotEmpty()) {
            steps += "Android: Google caches asset links, so a new app can take a few minutes before passkeys work; passkiCheck AND-04 shows when Google sees it."
        }
        steps += "In the app, use PasskiDemoServer (com.alirezaiyan.passki:passki-demo) for options and verification. Registrations expire on the date shown above; run passkiSetup again after that to register again."
        return failed
    }

    private fun setupIos(rp: String, apple: Apple, changes: FileChanges, steps: MutableList<String>) {
        val entitlements = apple.entitlements
        if (entitlements == null) {
            logger.warn("passki: no entitlements file: set passki { apple { entitlementsFile = file(\"…/App.entitlements\") } }")
            return
        }
        val before = entitlements.takeIf { it.isFile }?.readText()
        val merged = Entitlements.merge(before, Entitlements.domains(rp, developerMode.get()))
        if (before != null && before == merged) logger.lifecycle("passki: ${entitlements.path} already lists webcredentials:$rp")
        changes.write(entitlements, merged)

        val setting = apple.entitlementsSetting ?: entitlements.absolutePath
        val xcconfig = apple.xcconfigFile
        if (xcconfig == null || !xcconfig.isFile) {
            val target = apple.schemeName ?: "your app"
            logger.warn("passki: no base .xcconfig found; wire the entitlements in Xcode instead (see next steps)")
            steps += "In Xcode: select the $target target > Build Settings > search \"Code Signing Entitlements\" > " +
                "set it to $setting for all configurations (or set passki { apple { xcconfig = file(\"…/Config.xcconfig\") } } and re-run)."
        } else {
            val text = xcconfig.readText()
            val updated = Xcconfig.set(text, CODE_SIGN_ENTITLEMENTS, setting)
            if (updated == text) logger.lifecycle("passki: ${xcconfig.path} already sets $CODE_SIGN_ENTITLEMENTS = $setting")
            changes.write(xcconfig, updated)
            pbxprojOverride(apple.project)?.let { value ->
                logger.warn("passki: project.pbxproj sets $CODE_SIGN_ENTITLEMENTS = $value on a target, which overrides the xcconfig")
                steps += "Remove the target-level Code Signing Entitlements value ($value) in Xcode so the xcconfig value applies."
            }
        }
        steps += "Rebuild the iOS app (Xcode) so the signed app carries webcredentials:$rp."
    }

    /** A target-level CODE_SIGN_ENTITLEMENTS in project.pbxproj (read only, never edited). */
    private fun pbxprojOverride(project: File?): String? {
        val pbxproj = project?.let { File(it, "project.pbxproj") }?.takeIf { it.isFile } ?: return null
        return Regex("CODE_SIGN_ENTITLEMENTS = ([^;]+);").find(pbxproj.readText())?.groupValues?.get(1)?.trim()
    }

    private companion object {
        const val CODE_SIGN_ENTITLEMENTS = "CODE_SIGN_ENTITLEMENTS"
    }
}
