package com.alirezaiyan.passki.gradle.functional

import com.alirezaiyan.passki.gradle.Fingerprints
import com.alirezaiyan.passki.gradle.WellKnownFiles
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** passkiCheck against a local HttpServer standing in for the RP, Google DAL, Apple's CDN and the WebAuthn server. */
internal class CheckFunctionalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val appId = "ABCDE12345.com.example.app"
    private val served = mutableMapOf<String, String>()
    private lateinit var server: HttpServer

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val body = served[exchange.requestURI.path]?.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (body == null) 404 else 200, body?.size?.toLong() ?: -1)
            body?.let { exchange.responseBody.write(it) }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private val url get() = "http://127.0.0.1:${server.address.port}"

    private fun setUp(): Pair<AndroidTestProject, XcodeFixture> {
        val project = AndroidTestProject(tmp.root).apply { generateKeystore() }
        val xcode = XcodeFixture(tmp.root)
        project.writeBuild(
            """
            passki {
                rpId = "example.com"
                apple { teamId = "ABCDE12345"; bundleId = "com.example.app" }
                server { configUrl = "$url/server-config" }
            }
            """.trimIndent(),
        )
        val fingerprints = listOfNotNull(
            Fingerprints.sha256(project.keystore, "secret123", "key0", "PKCS12"),
            AndroidTestProject.debugKeystore?.let { Fingerprints.sha256(it, "android", "androiddebugkey", null) },
        )
        served["/.well-known/assetlinks.json"] = WellKnownFiles.assetLinks(mapOf("com.example.app" to fingerprints))
        served["/.well-known/apple-app-site-association"] = WellKnownFiles.aasa(listOf(appId))
        served["/a/v1/example.com"] = WellKnownFiles.aasa(listOf(appId))
        served["/v1/statements:list"] = JsonOutput.toJson(
            mapOf(
                "statements" to fingerprints.map { fp ->
                    mapOf("target" to mapOf("androidApp" to mapOf("packageName" to "com.example.app", "certificate" to mapOf("sha256Fingerprint" to fp))))
                },
            ),
        )
        served["/server-config"] = JsonOutput.toJson(
            mapOf("rpId" to "example.com", "origins" to listOf("https://example.com") + fingerprints.map(Fingerprints::androidOrigin)),
        )
        // Entitlements list the domain; xcodebuild resolves CODE_SIGN_ENTITLEMENTS to them; a Simulator build carries them.
        xcode.entitlements.writeText(xcode.entitlements.readText().replace("?mode=developer", ""))
        xcode.settings(mapOf("CODE_SIGN_ENTITLEMENTS" to "iosApp/iosApp.entitlements") + xcode.simulatorBuild("webcredentials:example.com"))
        return project to xcode
    }

    private fun args(xcode: XcodeFixture, vararg extra: String) = arrayOf(
        "passkiCheck",
        "-Ppasski.check.baseUrl=$url",
        "-Ppasski.check.googleDalUrl=$url",
        "-Ppasski.check.appleCdnUrl=$url",
        xcode.xcodebuildArg,
        "--configuration-cache",
        *extra,
    )

    private fun statuses(): Map<String, String> {
        val json = JsonSlurper().parse(File(tmp.root, "build/reports/passki/check.json")) as Map<*, *>
        return (json["checks"] as List<*>).associate { (it as Map<*, *>)["id"].toString() to it["status"].toString() }
    }

    @Test
    fun everythingGreenPasses() {
        val (project, xcode) = setUp()

        val result: BuildResult = project.build(*args(xcode))

        assertEquals(TaskOutcome.SUCCESS, result.task(":passkiCheck")?.outcome)
        val statuses = statuses()
        val expectedPass = listOf("AND-01", "AND-02", "AND-03", "AND-04", "AND-05", "IOS-01", "IOS-02", "IOS-03", "IOS-04", "IOS-05", "IOS-06", "SRV-01", "SRV-02")
        assertEquals(expectedPass.associateWith { "PASS" }, statuses.filterKeys { it in expectedPass }, result.output)
        assertEquals("SKIP", statuses["SRV-03"])
        assertTrue("[PASS] IOS-05  Entitlements wired into the Xcode build" in result.output, result.output)
        assertTrue(File(tmp.root, "build/reports/passki/index.html").readText().contains("IOS-05"))
    }

    @Test
    fun missingCodeSignEntitlementsFailsIos05() {
        val (project, xcode) = setUp()
        xcode.settings()

        val result = project.buildAndFail(*args(xcode))

        assertEquals(TaskOutcome.FAILED, result.task(":passkiCheck")?.outcome)
        assertTrue("[FAIL] IOS-05" in result.output, result.output)
        assertTrue("fix: Set CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements" in result.output, result.output)
        assertEquals("FAIL", statuses()["IOS-05"])
        assertTrue("passkiCheck: " in result.output && "IOS-05" in result.output)
    }

    @Test
    fun warningsFailOnlyWithWarningsAsErrors() {
        val (project, xcode) = setUp()
        served["/v1/statements:list"] = "{}"

        val lenient = project.build(*args(xcode))
        assertTrue("[WARN] AND-04" in lenient.output, lenient.output)

        val strict = project.buildAndFail(*args(xcode, "-Ppasski.check.warningsAsErrors=true"))
        assertTrue("AND-04" in strict.output)
    }

    @Test
    fun noXcodebuildSkipsIos05And06() {
        val (project, xcode) = setUp()

        project.build("passkiCheck", "-Ppasski.check.baseUrl=$url", "-Ppasski.check.googleDalUrl=$url", "-Ppasski.check.appleCdnUrl=$url", "-Ppasski.xcodebuild=/nonexistent/xcodebuild")

        val statuses = statuses()
        assertEquals("SKIP", statuses["IOS-05"])
        assertEquals("SKIP", statuses["IOS-06"])
        assertEquals("PASS", statuses["IOS-04"], xcode.entitlements.path)
    }
}
