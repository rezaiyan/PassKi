package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.WellKnownFiles
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Generates `assetlinks.json` and, when Apple is configured, `apple-app-site-association` into
 * [outputDir] (default `build/passki/well-known`). Upload both to `https://<rpId>/.well-known/`.
 */
@CacheableTask
public abstract class PasskiWellKnownTask : PasskiSigningTask() {
    /** `<TeamID>.<bundleId>` entries; empty when `passki { apple { } }` is not fully configured. */
    @get:Input
    public abstract val appleAppIds: ListProperty<String>

    @get:OutputDirectory
    public abstract val outputDir: DirectoryProperty

    @TaskAction
    public fun generate() {
        val dir = outputDir.get().asFile
        File(dir, ASSET_LINKS).writeText(WellKnownFiles.assetLinks(fingerprintsByPackage(resolveVariants())))
        logger.lifecycle("passki: wrote ${File(dir, ASSET_LINKS)}")

        val aasa = File(dir, AASA)
        val appIds = appleAppIds.get()
        if (appIds.isEmpty()) {
            aasa.delete()
            logger.lifecycle(
                "passki: $AASA not generated — set passki { apple { teamId = \"…\"; bundleId = \"…\" } } to generate it",
            )
        } else {
            aasa.writeText(WellKnownFiles.aasa(appIds))
            logger.lifecycle("passki: wrote $aasa")
        }
    }

    internal companion object {
        const val ASSET_LINKS = "assetlinks.json"
        const val AASA = "apple-app-site-association"
    }
}
