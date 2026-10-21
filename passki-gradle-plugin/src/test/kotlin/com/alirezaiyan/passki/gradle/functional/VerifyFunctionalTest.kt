package com.alirezaiyan.passki.gradle.functional

import com.alirezaiyan.passki.gradle.Fingerprints
import com.alirezaiyan.passki.gradle.WellKnownFiles
import com.sun.net.httpserver.HttpServer
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

internal class VerifyFunctionalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val extra = List(32) { "AB" }.joinToString(":")
    private val served = mutableMapOf<String, String>()
    private lateinit var server: HttpServer

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/.well-known/") { exchange ->
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

    private fun project(): AndroidTestProject = AndroidTestProject(tmp.root).apply {
        generateKeystore()
        File(dir, "app.entitlements").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0"><dict>
                <key>com.apple.developer.associated-domains</key>
                <array><string>webcredentials:example.com</string></array>
            </dict></plist>
            """.trimIndent(),
        )
        writeBuild(
            """
            passki {
                rpId = "example.com"
                android { extraFingerprints.add("$extra") }
                apple {
                    teamId = "ABCDE12345"
                    bundleId = "com.example.app"
                    entitlementsFile = file("app.entitlements")
                }
            }
            """.trimIndent(),
        )
    }

    private fun serve(fingerprints: List<String>) {
        served["/.well-known/assetlinks.json"] = WellKnownFiles.assetLinks("com.example.app", fingerprints)
        served["/.well-known/apple-app-site-association"] = WellKnownFiles.aasa(listOf("ABCDE12345.com.example.app"))
    }

    private fun localFingerprints(project: AndroidTestProject): List<String> =
        listOfNotNull(
            Fingerprints.sha256(project.keystore, "secret123", "key0", "PKCS12"),
            AndroidTestProject.debugKeystore?.let { Fingerprints.sha256(it, "android", "androiddebugkey", null) },
        )

    private val baseUrlArg get() = "-Ppasski.verify.baseUrl=http://127.0.0.1:${server.address.port}"

    @Test
    fun goodFilesPass() {
        val project = project()
        serve(localFingerprints(project) + extra)

        val result = project.build("passkiVerify", baseUrlArg, "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, result.task(":passkiVerify")?.outcome)
        assertTrue(result.output.contains("passki: well-known files OK"), result.output)
    }

    @Test
    fun missingFingerprintFailsAndNamesIt() {
        val project = project()
        val local = localFingerprints(project)
        val release = local.first()
        serve(local.drop(1) + extra)

        val result = project.buildAndFail("passkiVerify", baseUrlArg, "--configuration-cache")

        assertEquals(TaskOutcome.FAILED, result.task(":passkiVerify")?.outcome)
        assertTrue(result.output.contains(release), result.output)
    }
}
