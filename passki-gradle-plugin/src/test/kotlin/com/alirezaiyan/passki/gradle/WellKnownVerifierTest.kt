package com.alirezaiyan.passki.gradle

import com.alirezaiyan.passki.gradle.WellKnownVerifier.Expected
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Fetched
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Problem
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class WellKnownVerifierTest {
    private val fpA = List(32) { "AA" }.joinToString(":")
    private val fpB = List(32) { "BB" }.joinToString(":")
    private val expected = Expected(
        rpId = "example.com",
        packageName = "com.example.app",
        fingerprints = listOf(fpA, fpB),
        appleAppIds = listOf("ABCDE12345.com.example.app"),
    )

    private fun json(body: String, contentType: String? = "application/json") = Fetched(200, contentType, body, redirected = false)

    private val goodAssetLinks = json(WellKnownFiles.assetLinks("com.example.app", listOf(fpA, fpB)))
    private val goodAasa = json(WellKnownFiles.aasa(listOf("ABCDE12345.com.example.app")))

    private fun entitlements(vararg domains: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>aps-environment</key>
            <string>development</string>
            <key>com.apple.developer.associated-domains</key>
            <array>
                ${domains.joinToString("\n") { "<string>$it</string>" }}
            </array>
        </dict>
        </plist>
    """.trimIndent()

    private fun verify(
        assetLinks: Fetched? = goodAssetLinks,
        aasa: Fetched? = goodAasa,
        entitlements: String? = entitlements("webcredentials:example.com"),
    ): List<Problem> = WellKnownVerifier.verify(expected, assetLinks, aasa, entitlements)

    private fun List<Problem>.single(containing: String): Problem {
        val matches = filter { it.message.contains(containing) }
        assertEquals(1, matches.size, "expected one problem containing '$containing' in $this")
        return matches.single()
    }

    @Test
    fun allGoodHasNoProblems() {
        assertEquals(emptyList(), verify())
    }

    @Test
    fun missingAssetLinksPointsAtGeneratedFile() {
        val problems = verify(assetLinks = Fetched(404, "text/html", "Not Found", redirected = false))

        assertEquals(
            listOf(
                Problem(
                    "assetlinks.json not found at https://example.com/.well-known/assetlinks.json (HTTP 404)",
                    fix = "Upload build/passki/well-known/assetlinks.json",
                ),
            ),
            problems,
        )
    }

    @Test
    fun redirectIsAnError() {
        val problem = verify(assetLinks = Fetched(301, null, "", redirected = true)).single("without redirects")

        assertEquals(Severity.ERROR, problem.severity)
        assertTrue(problem.message.contains("assetlinks.json"))
    }

    @Test
    fun nonJsonBodyIsAnError() {
        val problem = verify(assetLinks = json("<html>hi</html>")).single("not valid JSON")

        assertEquals(Severity.ERROR, problem.severity)
    }

    @Test
    fun missingGetLoginCredsRelation() {
        val body = goodAssetLinks.body.replace("\"delegate_permission/common.get_login_creds\"", "\"x\"")

        verify(assetLinks = json(body)).single("delegate_permission/common.get_login_creds")
    }

    @Test
    fun missingPackage() {
        val body = WellKnownFiles.assetLinks("com.other", listOf(fpA, fpB))

        verify(assetLinks = json(body)).single("com.example.app")
    }

    @Test
    fun listsExactlyTheMissingFingerprints() {
        val body = WellKnownFiles.assetLinks("com.example.app", listOf(fpA)).replace(fpA, fpA.lowercase())

        val problem = verify(assetLinks = json(body)).single("fingerprint")

        assertTrue(problem.message.contains(fpB), problem.message)
        assertTrue(!problem.message.contains(fpA), "present (case-insensitive) fingerprint must not be listed: ${problem.message}")
    }

    @Test
    fun aasaMissingAppId() {
        val problem = verify(aasa = json(WellKnownFiles.aasa(listOf("OTHER.com.example.app")))).single("ABCDE12345.com.example.app")

        assertEquals(Severity.ERROR, problem.severity)
    }

    @Test
    fun aasaWrongContentTypeIsOnlyAWarning() {
        val problems = verify(aasa = goodAasa.copy(contentType = "application/octet-stream"))

        assertEquals(1, problems.size, problems.toString())
        assertEquals(Severity.WARNING, problems.single().severity)
        assertTrue(problems.single().message.contains("Content-Type"))
    }

    @Test
    fun aasaJsonContentTypeWithCharsetIsFine() {
        assertEquals(emptyList(), verify(aasa = goodAasa.copy(contentType = "application/json; charset=utf-8")))
    }

    @Test
    fun aasaNotFound() {
        verify(aasa = Fetched(404, null, "", redirected = false)).single("apple-app-site-association not found")
    }

    @Test
    fun entitlementsWithoutWebcredentials() {
        val problem = verify(entitlements = entitlements("applinks:example.com")).single("webcredentials:example.com")

        assertEquals(Severity.ERROR, problem.severity)
    }

    @Test
    fun entitlementsWithDeveloperModeAreAccepted() {
        assertEquals(emptyList(), verify(entitlements = entitlements("webcredentials:example.com?mode=developer")))
    }

    @Test
    fun entitlementsForAnotherDomainAreAnError() {
        verify(entitlements = entitlements("webcredentials:other.com")).single("webcredentials:example.com")
    }

    @Test
    fun skippedInputsProduceNoProblems() {
        assertEquals(emptyList(), WellKnownVerifier.verify(expected.copy(appleAppIds = emptyList()), goodAssetLinks, null, null))
    }
}
