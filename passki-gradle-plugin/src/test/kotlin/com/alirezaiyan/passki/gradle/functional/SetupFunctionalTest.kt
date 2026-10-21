package com.alirezaiyan.passki.gradle.functional

import com.alirezaiyan.passki.gradle.WellKnownVerifier
import groovy.json.JsonSlurper
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SetupFunctionalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val noXcodebuild = "-Ppasski.xcodebuild=/nonexistent/xcodebuild"

    private fun project(apple: String = "teamId = \"ABCDE12345\"\n bundleId = \"com.example.app\""): Pair<AndroidTestProject, XcodeFixture> {
        val project = AndroidTestProject(tmp.root).apply { generateKeystore() }
        val xcode = XcodeFixture(tmp.root)
        project.writeBuild("passki {\n rpId = \"example.com\"\n apple {\n $apple\n }\n}")
        return project to xcode
    }

    @Test
    fun wiresEntitlementsThroughXcconfigAndWritesEverything() {
        val (project, xcode) = project()
        val pbxproj = File(xcode.project, "project.pbxproj").readText()

        val result = project.build("passkiSetup", noXcodebuild, "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, result.task(":passkiSetup")?.outcome)
        assertTrue(xcode.xcconfig.readText().endsWith("CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements\n"), xcode.xcconfig.readText())
        assertEquals(pbxproj, File(xcode.project, "project.pbxproj").readText(), "project.pbxproj must never be edited")
        val entitlements = xcode.entitlements.readText()
        assertTrue("<key>aps-environment</key>" in entitlements)
        assertEquals(
            listOf("webcredentials:example.com?mode=developer", "webcredentials:example.com"),
            WellKnownVerifier.associatedDomains(entitlements),
        )
        val build = File(tmp.root, "build/passki")
        assertTrue(File(build, "well-known/assetlinks.json").isFile)
        assertTrue("ABCDE12345.com.example.app" in File(build, "well-known/apple-app-site-association").readText())
        val config = JsonSlurper().parse(File(build, "server-config.json")) as Map<*, *>
        assertEquals("example.com", config["rpId"])
        val origins = config["origins"] as List<*>
        assertEquals("https://example.com", origins.first())
        assertTrue(origins.drop(1).all { it.toString().startsWith("android:apk-key-hash:") } && origins.size >= 2, origins.toString())
        assertEquals(
            setOf("simplewebauthn.ts", "webauthn4j.kt", "java-webauthn-server.kt", "py_webauthn.py"),
            File(build, "server-snippets").list()!!.toSet(),
        )
        assertTrue("next steps" in result.output && "https://example.com/.well-known/" in result.output, result.output)
    }

    @Test
    fun isIdempotentAndConfigurationCacheFriendly() {
        val (project, xcode) = project()
        project.build("passkiSetup", noXcodebuild, "--configuration-cache")
        val xcconfig = xcode.xcconfig.readText()
        val entitlements = xcode.entitlements.readText()

        val second = project.build("passkiSetup", noXcodebuild, "--configuration-cache")

        assertTrue("Reusing configuration cache" in second.output, second.output)
        assertEquals(xcconfig, xcode.xcconfig.readText())
        assertEquals(entitlements, xcode.entitlements.readText())
        assertTrue("already sets CODE_SIGN_ENTITLEMENTS" in second.output, second.output)
    }

    @Test
    fun discoversTeamAndBundleFromXcodebuild() {
        val (project, xcode) = project(apple = "")
        xcode.settings(mapOf("DEVELOPMENT_TEAM" to "ZZZZZ99999", "PRODUCT_BUNDLE_IDENTIFIER" to "com.example.found"))

        val result = project.build("passkiSetup", xcode.xcodebuildArg, "--configuration-cache")

        assertTrue("ZZZZZ99999.com.example.found" in File(tmp.root, "build/passki/well-known/apple-app-site-association").readText())
        assertTrue("read ZZZZZ99999.com.example.found from xcodebuild" in result.output, result.output)
    }

    @Test
    fun withoutXcconfigPrintsXcodeStepsInstead() {
        val (project, xcode) = project()
        xcode.xcconfig.delete()

        val result = project.build("passkiSetup", noXcodebuild)

        assertFalse(xcode.xcconfig.exists())
        assertTrue("In Xcode: select the iosApp target > Build Settings" in result.output, result.output)
        assertTrue("iosApp/iosApp.entitlements" in result.output)
    }

    @Test
    fun developerModeCanBeTurnedOff() {
        val (project, xcode) = project(apple = "teamId = \"ABCDE12345\"\n bundleId = \"com.example.app\"\n developerMode = false")
        xcode.entitlements.delete()

        project.build("passkiSetup", noXcodebuild)

        assertEquals(listOf("webcredentials:example.com"), WellKnownVerifier.associatedDomains(xcode.entitlements.readText()))
    }
}
