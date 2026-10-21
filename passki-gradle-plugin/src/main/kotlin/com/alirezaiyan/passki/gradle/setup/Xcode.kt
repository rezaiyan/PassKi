package com.alirezaiyan.passki.gradle.setup

import com.alirezaiyan.passki.gradle.check.BuiltApp
import com.alirezaiyan.passki.gradle.check.XcodeProbe
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File

internal data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String)

/** Runs a command at execution time; null when the binary can't be started (e.g. no Xcode on Linux CI). */
internal fun interface CommandRunner {
    fun run(args: List<String>): CommandResult?
}

internal class ExecCommandRunner(private val exec: ExecOperations) : CommandRunner {
    override fun run(args: List<String>): CommandResult? {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        return try {
            val result = exec.exec { spec ->
                spec.commandLine(args)
                spec.standardOutput = out
                spec.errorOutput = err
                spec.isIgnoreExitValue = true
            }
            CommandResult(result.exitValue, out.toString(Charsets.UTF_8.name()), err.toString(Charsets.UTF_8.name()))
        } catch (e: Exception) {
            null
        }
    }
}

/** Finds the Xcode project and asks `xcodebuild` / `codesign` about it. Never edits project.pbxproj. */
internal class Xcode(private val runner: CommandRunner, private val xcodebuild: String, private val codesign: String) {
    fun probe(project: File?, scheme: String?): XcodeProbe {
        if (project == null) {
            return XcodeProbe.Unavailable("no Xcode project found; set passki { apple { xcodeProject = file(\"…/iosApp.xcodeproj\") } }")
        }
        val args = listOf(xcodebuild, "-project", project.absolutePath, "-scheme", scheme ?: project.name.removeSuffix(".xcodeproj"), "-showBuildSettings", "-json")
        val result = runner.run(args) ?: return XcodeProbe.Unavailable("xcodebuild is not available on this machine")
        if (result.exitCode != 0) {
            val why = (result.stderr.lineSequence().firstOrNull { "error" in it } ?: result.stderr.lineSequence().firstOrNull()).orEmpty().trim()
            return XcodeProbe.Unavailable("xcodebuild -showBuildSettings failed (exit ${result.exitCode}): $why")
        }
        return XcodeSettings.parse(result.stdout)?.let { XcodeProbe.Found(it) }
            ?: XcodeProbe.Unavailable("could not parse xcodebuild -showBuildSettings -json output")
    }

    /**
     * The newest `<FULL_PRODUCT_NAME>` under the project's `BUILD_DIR` (DerivedData), with its entitlements:
     * the Simulator's `<App>.app-Simulated.xcent` for simulator builds, `codesign -d --entitlements -` otherwise.
     * Null when the project was never built.
     */
    fun builtApp(settings: XcodeSettings, entitlementsFile: File?): BuiltApp? {
        val buildDir = settings["BUILD_DIR"]?.let(::File) ?: return null
        val product = settings["FULL_PRODUCT_NAME"] ?: return null
        val app = buildDir.listFiles().orEmpty().map { File(it, product) }.filter { it.isDirectory }.maxByOrNull { modified(it) } ?: return null
        val configDir = app.parentFile.name
        val stale = entitlementsFile?.takeIf { it.isFile }?.let { it.lastModified() > modified(app) } ?: false
        if (configDir.endsWith("iphonesimulator")) {
            val xcent = settings["OBJROOT"]?.let { obj ->
                File(obj, "${settings["PROJECT_NAME"]}.build/$configDir/${settings["TARGET_NAME"]}.build/$product-Simulated.xcent")
            }
            if (xcent != null && xcent.isFile) return BuiltApp(app.path, xcent.name, xcent.readText(), stale)
        }
        val signed = runner.run(listOf(codesign, "-d", "--entitlements", "-", "--xml", app.absolutePath))
        val xml = signed?.takeIf { it.exitCode == 0 }?.stdout?.let { out -> out.indexOf("<?xml").takeIf { it >= 0 }?.let { out.substring(it) } ?: out.takeIf { "<plist" in it } }
        return BuiltApp(app.path, "codesign", xml, stale)
    }

    private fun modified(app: File): Long = maxOf(app.lastModified(), File(app, app.name.removeSuffix(".app")).lastModified())

    companion object {
        // First match wins, per root: <root>/iosApp/X.xcodeproj, <root>/X.xcodeproj, then <root>/<dir>/X.xcodeproj.
        fun locate(roots: List<File>): File? = roots.asSequence().flatMap { root ->
            sequenceOf(File(root, "iosApp"), root).flatMap { dir -> projectsIn(dir) } +
                root.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.asSequence().flatMap { projectsIn(it) }
        }.firstOrNull()

        private fun projectsIn(dir: File): Sequence<File> =
            dir.listFiles().orEmpty().filter { it.isDirectory && it.name.endsWith(".xcodeproj") }.sortedBy { it.name }.asSequence()

        /** The KMP template's base configuration: `Configuration/Config.xcconfig` next to the project. */
        fun defaultXcconfig(project: File): File? = File(project.parentFile, "Configuration/Config.xcconfig").takeIf { it.isFile }

        /** `<srcroot>/<name>/<name>.entitlements` (the KMP template layout). */
        fun defaultEntitlements(project: File): File {
            val name = project.name.removeSuffix(".xcodeproj")
            return File(project.parentFile, "$name/$name.entitlements")
        }
    }
}
