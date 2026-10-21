package com.alirezaiyan.passki.gradle.functional

import com.alirezaiyan.passki.gradle.Fingerprints
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class WellKnownFunctionalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val extra = List(32) { "AB" }.joinToString(":")

    private fun project(apple: String): AndroidTestProject = AndroidTestProject(tmp.root).apply {
        generateKeystore()
        writeBuild(
            """
            passki {
                rpId = "example.com"
                android { extraFingerprints.add("${extra.replace(":", "").lowercase()}") }
                $apple
            }
            """.trimIndent(),
        )
    }

    private val outputDir get() = File(tmp.root, "build/passki/well-known")

    @Test
    fun generatesAssetLinksAndAasaAndIsUpToDateOnRerun() {
        val project = project("""apple { teamId = "ABCDE12345"; bundleId = "com.example.app" }""")
        val release = Fingerprints.sha256(project.keystore, "secret123", "key0", "PKCS12")

        val first = project.build("passkiWellKnown", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, first.task(":passkiWellKnown")?.outcome)
        val assetLinks = File(outputDir, "assetlinks.json").readText()
        assertTrue(assetLinks.contains("\"com.example.app\""), assetLinks)
        assertTrue(assetLinks.contains(release), assetLinks)
        assertTrue(assetLinks.contains(extra), "extra fingerprint must be normalized: $assetLinks")
        AndroidTestProject.debugKeystore?.let { debug ->
            val debugFingerprint = Fingerprints.sha256(debug, "android", "androiddebugkey", null)
            assertTrue(assetLinks.contains(debugFingerprint), assetLinks)
        }
        val aasa = File(outputDir, "apple-app-site-association").readText()
        assertTrue(aasa.contains("\"ABCDE12345.com.example.app\""), aasa)

        val second = project.build("passkiWellKnown", "--configuration-cache")

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":passkiWellKnown")?.outcome)
        assertTrue(second.output.contains("Reusing configuration cache"), second.output)
    }

    @Test
    fun skipsAasaWhenAppleIsNotConfigured() {
        val project = project(apple = "")

        val result = project.build("passkiWellKnown", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, result.task(":passkiWellKnown")?.outcome)
        assertTrue(File(outputDir, "assetlinks.json").isFile)
        assertFalse(File(outputDir, "apple-app-site-association").exists())
        assertTrue(result.output.contains("apple-app-site-association not generated"), result.output)
    }

    @Test
    fun missingKeystoreIsSkippedWithWarning() {
        val project = project(apple = "")
        project.keystore.delete()

        val result = project.build("passkiWellKnown", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, result.task(":passkiWellKnown")?.outcome)
        assertTrue(result.output.contains("keystore not found"), result.output)
        assertTrue(File(outputDir, "assetlinks.json").readText().contains(extra))
    }

    @Test
    fun fingerprintsTaskPrintsTableAndAndroidOrigins() {
        val project = project(apple = "")
        val release = Fingerprints.sha256(project.keystore, "secret123", "key0", "PKCS12")
        val releaseBytes = release.split(":").map { it.toInt(16).toByte() }.toByteArray()
        val origin = "android:apk-key-hash:" + Base64.getUrlEncoder().withoutPadding().encodeToString(releaseBytes)

        val result = project.build("passkiFingerprints", "--configuration-cache")

        assertTrue(result.output.contains("release"), result.output)
        assertTrue(result.output.contains(release), result.output)
        assertTrue(result.output.contains(extra), result.output)
        assertTrue(result.output.contains(origin), result.output)
    }
}
