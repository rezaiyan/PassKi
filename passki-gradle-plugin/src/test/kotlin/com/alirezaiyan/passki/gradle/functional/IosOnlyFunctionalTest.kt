package com.alirezaiyan.passki.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The plugin in a project without `com.android.application` (iOS-only app, or applied to the wrong module). */
internal class IosOnlyFunctionalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val noXcodebuild = "-Ppasski.xcodebuild=/nonexistent/xcodebuild"

    private fun write(passki: String) {
        File(tmp.root, "settings.gradle.kts").writeText("rootProject.name = \"ios-only\"\n")
        File(tmp.root, "build.gradle.kts").writeText("plugins {\n    id(\"com.alirezaiyan.passki\")\n}\n\n$passki\n")
    }

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(tmp.root)
        .withPluginClasspath()
        .withArguments(*args, "--stacktrace")
        .forwardOutput()

    private fun build(vararg args: String): BuildResult = runner(*args).build()

    @Test
    fun setupWorksWithoutAnAndroidAppAndSaysWhy() {
        val xcode = XcodeFixture(tmp.root)
        write("passki {\n    rpId = \"example.com\"\n    apple { teamId = \"ABCDE12345\"; bundleId = \"com.example.app\" }\n}")

        val result = build("passkiSetup", noXcodebuild, "--configuration-cache")

        assertTrue("iOS-only mode: the root project has no Android app (com.android.application)" in result.output, result.output)
        assertTrue("apply id(\"com.alirezaiyan.passki\") in that module instead" in result.output, result.output)
        val wellKnown = File(tmp.root, "build/passki/well-known")
        assertFalse(File(wellKnown, "assetlinks.json").exists())
        assertTrue("ABCDE12345.com.example.app" in File(wellKnown, "apple-app-site-association").readText())
        assertTrue("CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements" in xcode.xcconfig.readText())
        assertTrue("Upload build/passki/well-known/apple-app-site-association to https://example.com/.well-known/" in result.output, result.output)
    }

    @Test
    fun androidOnlyTasksAreNotRegistered() {
        write("passki { rpId = \"example.com\" }")

        val result = build("tasks", "--group", "passki")

        assertTrue("passkiSetup" in result.output && "passkiCheck" in result.output, result.output)
        assertFalse("passkiFingerprints" in result.output, result.output)
    }

    @Test
    fun failsClearlyWhenThereIsNothingToSetUp() {
        write("passki { rpId = \"example.com\" }")

        val result = runner("passkiSetup", noXcodebuild).buildAndFail()

        assertTrue("passkiSetup has nothing to set up" in result.output, result.output)
        assertTrue("set passki { apple { teamId; bundleId } }" in result.output, result.output)
    }

    @Test
    fun checkSkipsAndroidChecksWithTheReason() {
        XcodeFixture(tmp.root)
        write("passki {\n    rpId = \"example.com\"\n    apple { teamId = \"ABCDE12345\"; bundleId = \"com.example.app\" }\n}")

        // Unreachable hosts: every network check fails, which is fine; this test is about the Android skips.
        val result = runner(
            "passkiCheck", noXcodebuild,
            "-Ppasski.check.baseUrl=http://127.0.0.1:9",
            "-Ppasski.check.appleCdnUrl=http://127.0.0.1:9",
        ).buildAndFail()

        val report = File(tmp.root, "build/reports/passki/check.json").readText()
        listOf("AND-01", "AND-02", "AND-03", "AND-04", "AND-05").forEach { id ->
            assertTrue(Regex("\\[SKIP\\] $id .*iOS-only mode").containsMatchIn(result.output), "$id\n${result.output}")
        }
        assertTrue("\"IOS-01\"" in report)
        assertEquals(1, Regex("passki: iOS-only mode").findAll(result.output).count(), result.output)
    }
}
