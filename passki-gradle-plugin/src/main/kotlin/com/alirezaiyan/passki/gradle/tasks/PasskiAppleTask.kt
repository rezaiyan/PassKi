package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.PasskiConfigException
import com.alirezaiyan.passki.gradle.check.XcodeProbe
import com.alirezaiyan.passki.gradle.setup.ExecCommandRunner
import com.alirezaiyan.passki.gradle.setup.Xcode
import com.alirezaiyan.passki.gradle.setup.XcodeSettings
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Shared inputs of passkiSetup / passkiCheck: the relying party, the Apple app identity (DSL first, then
 * `xcodebuild -showBuildSettings`) and the Xcode project. Files are [Internal]: these tasks always run.
 */
@DisableCachingByDefault(because = "Base type; subclasses decide")
public abstract class PasskiAppleTask : PasskiSigningTask() {
    @get:Input
    @get:Optional
    public abstract val rpId: Property<String>

    @get:Input
    @get:Optional
    public abstract val teamId: Property<String>

    @get:Input
    @get:Optional
    public abstract val bundleId: Property<String>

    @get:Internal
    public abstract val entitlementsFile: RegularFileProperty

    @get:Internal
    public abstract val xcodeProject: DirectoryProperty

    @get:Input
    @get:Optional
    public abstract val scheme: Property<String>

    @get:Internal
    public abstract val xcconfig: RegularFileProperty

    /** Where to look for an `.xcodeproj` when [xcodeProject] is unset (project dir, root dir). */
    @get:Internal
    public abstract val searchRoots: ConfigurableFileCollection

    /** `xcodebuild` binary (Gradle property `passki.xcodebuild`); a test fake or a missing binary are fine. */
    @get:Input
    public abstract val xcodebuild: Property<String>

    /** `codesign` binary (Gradle property `passki.codesign`). */
    @get:Input
    public abstract val codesign: Property<String>

    @get:Inject
    protected abstract val execOperations: ExecOperations

    init {
        outputs.upToDateWhen { false }
    }

    internal fun requireRpId(): String = rpId.orNull?.takeIf { it.isNotBlank() }
        ?: throw PasskiConfigException("$name needs passki { rpId = \"example.com\" }")

    /** Apple context resolved at execution time; `xcodebuild` runs at most once, and only when needed. */
    internal inner class Apple {
        val xcode = Xcode(ExecCommandRunner(execOperations), xcodebuild.get(), codesign.get())
        val project: File? = xcodeProject.orNull?.asFile ?: Xcode.locate(searchRoots.files.toList())
        val schemeName: String? = scheme.orNull ?: project?.name?.removeSuffix(".xcodeproj")
        val probe: XcodeProbe by lazy { xcode.probe(project, schemeName) }
        val settings: XcodeSettings? get() = (probe as? XcodeProbe.Found)?.settings

        val teamId: String? by lazy { this@PasskiAppleTask.teamId.orNull ?: settings?.get("DEVELOPMENT_TEAM") }
        val bundleId: String? by lazy { this@PasskiAppleTask.bundleId.orNull ?: settings?.get("PRODUCT_BUNDLE_IDENTIFIER") }

        /** Whether the IDs came from xcodebuild rather than the DSL. */
        val discovered: Boolean get() = (!this@PasskiAppleTask.teamId.isPresent || !this@PasskiAppleTask.bundleId.isPresent) && settings != null

        val appIds: List<String> by lazy {
            val team = teamId
            val bundle = bundleId
            if (team != null && bundle != null) listOf("$team.$bundle") else emptyList()
        }

        /** DSL file, else what Xcode's CODE_SIGN_ENTITLEMENTS names, else the template default when an Xcode project exists. */
        val entitlements: File? by lazy {
            entitlementsFile.orNull?.asFile
                ?: (if (project != null) settings?.entitlementsFile() else null)
                ?: project?.let(Xcode::defaultEntitlements)
        }

        val xcconfigFile: File? by lazy { xcconfig.orNull?.asFile ?: project?.let(Xcode::defaultXcconfig) }

        /** SRCROOT: build-setting paths are relative to it. */
        val srcRoot: File? get() = project?.parentFile ?: settings?.projectDir ?: xcconfigFile?.parentFile?.parentFile

        /** [entitlements] as `CODE_SIGN_ENTITLEMENTS` should spell it (relative to [srcRoot]). */
        val entitlementsSetting: String? by lazy {
            val file = entitlements ?: return@lazy null
            val root = srcRoot ?: return@lazy file.absolutePath
            file.absoluteFile.relativeTo(root.absoluteFile).invariantSeparatorsPath
        }

        val configured: Boolean get() = appIds.isNotEmpty() || project != null || entitlementsFile.isPresent
    }
}
