package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.WellKnownFiles
import com.alirezaiyan.passki.gradle.setup.Entitlements
import com.alirezaiyan.passki.gradle.setup.ServerConfig
import com.alirezaiyan.passki.gradle.setup.Xcconfig
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * One-shot, idempotent setup driven by `passki { }`:
 *
 * 1. writes `assetlinks.json` / `apple-app-site-association` into `build/passki/well-known`;
 * 2. merges `webcredentials:<rpId>` (and `?mode=developer` when `apple.developerMode`) into the entitlements file;
 * 3. wires it into the build with `CODE_SIGN_ENTITLEMENTS` in the base `.xcconfig` (never touches project.pbxproj;
 *    prints the Xcode steps when there's no xcconfig);
 * 4. writes `build/passki/server-config.json` and verifier snippets into `build/passki/server-snippets/`;
 * 5. prints the remaining manual steps.
 *
 * Team ID and bundle ID are read from `xcodebuild -showBuildSettings` when not set in the DSL.
 */
@DisableCachingByDefault(because = "Edits files in the Xcode project")
public abstract class PasskiSetupTask : PasskiAppleTask() {
    @get:Input
    public abstract val developerMode: Property<Boolean>

    /** `build/passki`: well-known/, server-config.json, server-snippets/. Untracked (shared with passkiWellKnown). */
    @get:Internal
    public abstract val outputDir: DirectoryProperty

    @TaskAction
    public fun setup() {
        val rp = requireRpId()
        val apple = Apple()
        val out = outputDir.get().asFile
        val steps = mutableListOf<String>()
        val byPackage = fingerprintsByPackage(resolveVariants())

        // 1. Well-known files.
        val wellKnown = File(out, "well-known").apply { mkdirs() }
        File(wellKnown, PasskiWellKnownTask.ASSET_LINKS).writeText(WellKnownFiles.assetLinks(byPackage))
        val aasa = File(wellKnown, PasskiWellKnownTask.AASA)
        val appIds = apple.appIds
        if (apple.discovered && appIds.isNotEmpty()) {
            logger.lifecycle("passki: read ${appIds.joinToString()} from xcodebuild -showBuildSettings (scheme ${apple.schemeName})")
        }
        val files = if (appIds.isEmpty()) {
            aasa.delete()
            logger.lifecycle("passki: no Apple Team ID / bundle ID (DSL or xcodebuild); apple-app-site-association not generated")
            listOf(PasskiWellKnownTask.ASSET_LINKS)
        } else {
            aasa.writeText(WellKnownFiles.aasa(appIds))
            listOf(PasskiWellKnownTask.ASSET_LINKS, PasskiWellKnownTask.AASA)
        }
        logger.lifecycle("passki: wrote ${files.joinToString { File(wellKnown, it).path }}")
        steps += "Upload ${files.joinToString(" and ") { "build/passki/well-known/$it" }} to https://$rp/.well-known/ " +
            "(HTTPS, HTTP 200, Content-Type: application/json, no redirects)."

        // 2 + 3. iOS entitlements and their wiring.
        if (appIds.isNotEmpty() || apple.project != null || entitlementsFile.isPresent) {
            setupIos(rp, apple, steps)
        }

        // 4. Server config + snippets.
        val server = ServerConfig.of(rp, byPackage, appIds)
        File(out, "server-config.json").writeText(server.json())
        val snippets = File(out, "server-snippets").apply { mkdirs() }
        server.snippets().forEach { (name, text) -> File(snippets, name).writeText(text) }
        logger.lifecycle("passki: wrote ${File(out, "server-config.json").path} and ${server.snippets().size} snippets in ${snippets.path}")
        steps += "Configure your WebAuthn server with RP ID $rp and these origins: ${server.origins.joinToString()} " +
            "(build/passki/server-config.json; snippets in build/passki/server-snippets/)."

        // 5. Next steps.
        steps += "Run ./gradlew passkiCheck to verify everything end to end."
        logger.lifecycle(steps.mapIndexed { i, step -> "  ${i + 1}. $step" }.joinToString("\n", prefix = "\npasski setup: next steps\n"))
    }

    private fun setupIos(rp: String, apple: Apple, steps: MutableList<String>) {
        val entitlements = apple.entitlements
        if (entitlements == null) {
            logger.warn("passki: no entitlements file: set passki { apple { entitlementsFile = file(\"…/App.entitlements\") } }")
            return
        }
        val before = entitlements.takeIf { it.isFile }?.readText()
        val merged = Entitlements.merge(before, Entitlements.domains(rp, developerMode.get()))
        when {
            before == null -> {
                entitlements.parentFile?.mkdirs()
                entitlements.writeText(merged)
                logger.lifecycle("passki: created ${entitlements.path}")
            }
            before != merged -> {
                entitlements.writeText(merged)
                logger.lifecycle("passki: added webcredentials:$rp to ${entitlements.path}")
            }
            else -> logger.lifecycle("passki: ${entitlements.path} already lists webcredentials:$rp")
        }

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
            if (updated != text) {
                xcconfig.writeText(updated)
                logger.lifecycle("passki: set $CODE_SIGN_ENTITLEMENTS = $setting in ${xcconfig.path}")
            } else {
                logger.lifecycle("passki: ${xcconfig.path} already sets $CODE_SIGN_ENTITLEMENTS = $setting")
            }
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
