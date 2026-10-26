package com.alirezaiyan.passki.gradle.functional

import com.alirezaiyan.passki.gradle.WellKnownFiles
import com.sun.net.httpserver.HttpServer
import org.gradle.testkit.runner.TaskOutcome
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** passkiSetup's warning for an RP ID whose live well-known files already list other apps. */
internal class SharedRpIdFunctionalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val served = mutableMapOf<String, String>()
    private lateinit var server: HttpServer

    private val checkOn = "-Ppasski.setup.sharedRpIdCheck=true"
    private val noXcodebuild = "-Ppasski.xcodebuild=/nonexistent/xcodebuild"
    private val url get() = "http://127.0.0.1:${server.address.port}"

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
        val fp = List(32) { "AA" }.joinToString(":")
        served["/.well-known/assetlinks.json"] = WellKnownFiles.assetLinks(mapOf("com.example.other" to listOf(fp)))
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun project(rpId: String): AndroidTestProject =
        AndroidTestProject(tmp.root).apply {
            generateKeystore()
            writeBuild("passki {\n rpId = \"$rpId\"\n}")
        }

    @Test
    fun warnsWhenTheApexListsAnotherApp() {
        val result = project("example.com").build("passkiSetup", checkOn, "-Ppasski.check.baseUrl=$url", noXcodebuild)

        assertEquals(TaskOutcome.SUCCESS, result.task(":passkiSetup")?.outcome)
        assertTrue("already lists other apps (com.example.other)" in result.output, result.output)
        assertTrue("app.example.com" in result.output, result.output)
    }

    @Test
    fun subdomainRpIdIsNotChecked() {
        val result = project("app.example.com").build("passkiSetup", checkOn, "-Ppasski.check.baseUrl=$url", noXcodebuild)

        assertEquals(TaskOutcome.SUCCESS, result.task(":passkiSetup")?.outcome)
        assertFalse("already lists other apps" in result.output, result.output)
    }

    @Test
    fun unreachableHostSkipsTheWarning() {
        val closedPort = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }

        val result = project("example.com").build("passkiSetup", checkOn, "-Ppasski.check.baseUrl=http://127.0.0.1:$closedPort", noXcodebuild)

        assertEquals(TaskOutcome.SUCCESS, result.task(":passkiSetup")?.outcome)
        assertFalse("already lists other apps" in result.output, result.output)
    }

    @Test
    fun offlineBuildsSkipTheCheck() {
        val result = project("example.com").build("passkiSetup", checkOn, "-Ppasski.check.baseUrl=$url", noXcodebuild, "--offline")

        assertEquals(TaskOutcome.SUCCESS, result.task(":passkiSetup")?.outcome)
        assertFalse("already lists other apps" in result.output, result.output)
    }
}
