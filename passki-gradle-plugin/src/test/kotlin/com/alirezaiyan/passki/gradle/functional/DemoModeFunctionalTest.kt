package com.alirezaiyan.passki.gradle.functional

import com.alirezaiyan.passki.gradle.WellKnownVerifier
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonSlurper
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `passki { useDemoServer() }`: passkiSetup registers the app with a local stand-in for the demo relying party. */
internal class DemoModeFunctionalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val requests: MutableList<Pair<String, Map<*, *>>> = Collections.synchronizedList(mutableListOf())
    private var iosStatus = 201
    private lateinit var server: HttpServer
    private val noXcodebuild = "-Ppasski.xcodebuild=/nonexistent/xcodebuild"

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/api/demo/") { exchange ->
            val body = JsonSlurper().parse(exchange.requestBody) as Map<*, *>
            requests += exchange.requestURI.path to body
            val (status, answer) = when (exchange.requestURI.path) {
                "/api/demo/ios-apps" -> if (iosStatus == 409) {
                    409 to """{"error":"ABCDE12345.com.example.app is already registered until 2026-10-30T00:00:00.000Z"}"""
                } else {
                    201 to """{"appId":"ABCDE12345.com.example.app","expiresAt":"2026-10-30T00:00:00.000Z"}"""
                }
                else -> 201 to """{"packageName":"${body["packageName"]}","expiresAt":"2026-10-30T00:00:00.000Z","created":1}"""
            }
            val bytes = answer.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private val demoUrl get() = "-Ppasski.demo.url=http://127.0.0.1:${server.address.port}"

    private fun project(): Pair<AndroidTestProject, XcodeFixture> {
        val project = AndroidTestProject(tmp.root).apply { generateKeystore() }
        val xcode = XcodeFixture(tmp.root)
        project.writeBuild("passki {\n useDemoServer()\n apple { teamId = \"ABCDE12345\"; bundleId = \"com.example.app\" }\n}")
        return project to xcode
    }

    @Test
    fun registersAndroidAndIosAndShowsWhatItSent() {
        val (project, xcode) = project()

        val result = project.build("passkiSetup", noXcodebuild, demoUrl, "--configuration-cache")

        val android = requests.filter { it.first == "/api/demo/android-apps" }.map { it.second }
        assertEquals(listOf("com.example.app"), android.map { it["packageName"] })
        assertTrue((android.single()["fingerprints"] as List<*>).isNotEmpty())
        assertEquals(listOf(mapOf("teamId" to "ABCDE12345", "bundleId" to "com.example.app")), requests.filter { it.first == "/api/demo/ios-apps" }.map { it.second })

        assertTrue("POST http://127.0.0.1:${server.address.port}/api/demo/android-apps {\"packageName\":\"com.example.app\"" in result.output, result.output)
        assertTrue("→ registered until 2026-10-30T00:00:00.000Z" in result.output, result.output)
        assertTrue("PasskiDemoServer" in result.output, result.output)
        assertFalse("Upload" in result.output, result.output)
        assertFalse(File(tmp.root, "build/passki/well-known").exists(), "the demo server hosts the well-known files")
        assertFalse(File(tmp.root, "build/passki/server-config.json").exists(), "the demo server is already configured")
        assertEquals(
            listOf("webcredentials:passki.alirezaiyan.com", "webcredentials:passki.alirezaiyan.com?mode=developer").toSet(),
            WellKnownVerifier.associatedDomains(xcode.entitlements.readText()).filter { "passki.alirezaiyan.com" in it }.toSet(),
        )
    }

    @Test
    fun rerunWithAnAlreadyRegisteredAppSucceeds() {
        val (project, _) = project()
        iosStatus = 409

        val result = project.build("passkiSetup", noXcodebuild, demoUrl)

        assertTrue("→ ABCDE12345.com.example.app is already registered until" in result.output, result.output)
    }

    @Test
    fun dryRunSendsNothing() {
        val (project, _) = project()

        val result = project.build("passkiSetup", "--preview", noXcodebuild, demoUrl)

        assertTrue(requests.isEmpty(), requests.toString())
        assertTrue("would send" in result.output && "→ not sent (preview)" in result.output, result.output)
    }

    @Test
    fun failsWhenTheDemoServerIsUnreachable() {
        val (project, _) = project()

        val result = project.buildAndFail("passkiSetup", noXcodebuild, "-Ppasski.demo.url=http://127.0.0.1:9")

        assertTrue("not sent:" in result.output, result.output)
        assertTrue("demo registration failed for Android com.example.app, iOS ABCDE12345.com.example.app" in result.output, result.output)
    }
}
