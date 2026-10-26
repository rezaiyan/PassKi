package com.alirezaiyan.passki.gradle

import com.alirezaiyan.passki.gradle.WellKnownVerifier.Expected
import com.alirezaiyan.passki.gradle.check.Catalog
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
        fingerprintsByPackage = mapOf("com.example.app" to listOf(fpA, fpB), "com.example.app.debug" to listOf(fpB)),
        appleAppIds = listOf("ABCDE12345.com.example.app"),
    )

    private fun json(body: String, contentType: String? = "application/json") = Fetched(200, contentType, body, redirected = false)

    private val goodAssetLinks = json(WellKnownFiles.assetLinks(expected.fingerprintsByPackage))
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
                    id = "AND-01",
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
        val problem = verify(assetLinks = json("""[{"relation":""")).single("not valid JSON")

        assertEquals(Severity.ERROR, problem.severity)
    }

    @Test
    fun missingGetLoginCredsRelation() {
        val body = goodAssetLinks.body.replace("\"delegate_permission/common.get_login_creds\"", "\"x\"")

        verify(assetLinks = json(body)).single("statement for com.example.app lacks relation delegate_permission/common.get_login_creds")
    }

    @Test
    fun missingPackage() {
        val body = WellKnownFiles.assetLinks(mapOf("com.other" to listOf(fpA, fpB), "com.example.app.debug" to listOf(fpB)))

        verify(assetLinks = json(body)).single("package com.example.app")
    }

    @Test
    fun missingSuffixedPackageIsReportedOnItsOwn() {
        val body = WellKnownFiles.assetLinks(mapOf("com.example.app" to listOf(fpA, fpB)))

        val problem = verify(assetLinks = json(body)).single("com.example.app.debug")

        assertTrue(problem.message.contains("no android_app statement"), problem.message)
    }

    @Test
    fun fingerprintsAreCheckedPerPackage() {
        // fpB is served, but only under the base package; the debug package must list it too.
        val body = WellKnownFiles.assetLinks(mapOf("com.example.app" to listOf(fpA, fpB), "com.example.app.debug" to listOf(fpA)))

        val problem = verify(assetLinks = json(body)).single("fingerprint")

        assertTrue(problem.message.contains("com.example.app.debug: $fpB"), problem.message)
    }

    @Test
    fun listsExactlyTheMissingFingerprints() {
        val body = WellKnownFiles.assetLinks(mapOf("com.example.app" to listOf(fpA), "com.example.app.debug" to listOf(fpB))).replace(fpA, fpA.lowercase())

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

    @Test
    fun everyProblemCarriesItsCatalogId() {
        fun ids(problems: List<Problem>) = problems.map { it.id }
        assertEquals(listOf("AND-01"), ids(verify(assetLinks = Fetched(301, null, "", redirected = true))))
        assertEquals(listOf("AND-01"), ids(verify(assetLinks = json("<html>"))))
        assertEquals(
            listOf("AND-02", "AND-02"),
            ids(verify(assetLinks = json(WellKnownFiles.assetLinks(mapOf("com.other" to listOf(fpA)))))),
        )
        assertEquals(
            listOf("AND-03"),
            ids(verify(assetLinks = json(WellKnownFiles.assetLinks(mapOf("com.example.app" to listOf(fpA), "com.example.app.debug" to listOf(fpB)))))),
        )
        assertEquals(listOf("IOS-01"), ids(verify(aasa = Fetched(404, null, "", false))))
        assertEquals(listOf("IOS-02"), ids(verify(aasa = json(WellKnownFiles.aasa(listOf("X.y"))))))
        assertEquals(listOf("IOS-01"), ids(verify(aasa = json(WellKnownFiles.aasa(listOf("ABCDE12345.com.example.app")), contentType = "text/plain"))))
        assertEquals(listOf("IOS-04"), ids(verify(entitlements = entitlements("webcredentials:other.com"))))
    }

    @Test
    fun webPageAtAssetLinksIsAnd01WithHostingFix() {
        val problem = verify(assetLinks = json(SPA_INDEX_HTML, "text/html; charset=utf-8"))
            .single("served a web page, not assetlinks.json")

        assertEquals("AND-01", problem.id)
        assertEquals("https://example.com/.well-known/assetlinks.json served a web page, not assetlinks.json", problem.message)
        assertTrue("catch-all route" in problem.fix, problem.fix)
        assertEquals(Severity.ERROR, problem.severity)
    }

    @Test
    fun webPageAtAasaIsIos01() {
        val problem = verify(aasa = json(SPA_INDEX_HTML, "text/html")).single("served a web page")

        assertEquals("IOS-01", problem.id)
        assertTrue(problem.message.endsWith("not apple-app-site-association"), problem.message)
    }

    @Test
    fun nonJsonTextHtmlIsAWebPage() {
        verify(assetLinks = json("Not here", "text/html")).single("served a web page")
    }

    @Test
    fun jsonServedAsTextHtmlIsCheckedAsJson() {
        val problems = verify(aasa = json(WellKnownFiles.aasa(listOf("ABCDE12345.com.example.app")), "text/html"))

        assertTrue(problems.none { "web page" in it.message }, problems.toString())
        problems.single("Content-Type")
    }

    @Test
    fun webPageTextComesFromTheCatalog() {
        val problem = verify(assetLinks = json(SPA_INDEX_HTML)).single("served a web page")
        val text = Catalog.bundled["AND-01"].webPage!!
        assertEquals(text.fix("https://example.com/.well-known/assetlinks.json", "assetlinks.json"), problem.fix)
    }

    private companion object {
        /** Same body as passki's commonTest `SPA_INDEX_HTML` and passki-web's fixture: one response for all three checkers. */
        const val SPA_INDEX_HTML: String =
            "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"><title>App</title></head>" +
                "<body><div id=\"root\"></div><script src=\"/assets/index.js\"></script></body></html>\n"
    }
}
