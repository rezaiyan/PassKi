package com.alirezaiyan.passki.gradle.check

import com.alirezaiyan.passki.gradle.WellKnownFiles
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Expected
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Fetched
import com.alirezaiyan.passki.gradle.setup.XcodeSettings
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class IosChecksTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val appId = "ABCDE12345.com.example.app"
    private val expected = Expected("example.com", emptyMap(), listOf(appId))
    private val f = ResultFactory(
        Catalog.bundled,
        mapOf("rpId" to "example.com", "teamId" to "ABCDE12345", "bundleId" to "com.example.app", "path" to "iosApp/iosApp.entitlements"),
    )
    private val aasaUrl = "https://example.com/.well-known/apple-app-site-association"

    private fun ok(body: String, type: String? = "application/json") = Fetch(aasaUrl, Fetched(200, type, body, false), null)

    private fun plist(vararg domains: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <plist version="1.0"><dict>
        <key>com.apple.developer.associated-domains</key>
        <array>${domains.joinToString("") { "<string>$it</string>" }}</array>
        </dict></plist>
    """.trimIndent()

    @Test
    fun goodAasaPassesIos01And02() {
        val results = IosChecks.wellKnown(f, expected, ok(WellKnownFiles.aasa(listOf(appId))))
        assertEquals(listOf("IOS-01" to CheckStatus.PASS, "IOS-02" to CheckStatus.PASS), results.map { it.id to it.status })
    }

    @Test
    fun wrongContentTypeWarnsIos01() {
        val results = IosChecks.wellKnown(f, expected, ok(WellKnownFiles.aasa(listOf(appId)), type = "text/plain"))
        assertEquals(CheckStatus.WARN, results.single { it.id == "IOS-01" }.status)
        assertEquals(CheckStatus.PASS, results.single { it.id == "IOS-02" }.status)
    }

    @Test
    fun missingAppFailsIos02WithTeamAndBundleInFix() {
        val result = IosChecks.wellKnown(f, expected, ok(WellKnownFiles.aasa(listOf("OTHER.com.other")))).single { it.id == "IOS-02" }
        assertEquals(CheckStatus.FAIL, result.status)
        assertTrue("\"ABCDE12345.com.example.app\"" in result.fix!!, result.fix)
    }

    @Test
    fun unreachableAasaSkipsIos02() {
        val results = IosChecks.wellKnown(f, expected, Fetch(aasaUrl, null, "dns"))
        assertEquals(listOf(CheckStatus.FAIL, CheckStatus.SKIP), results.map { it.status })
    }

    @Test
    fun cdn() {
        val cdnUrl = "https://app-site-association.cdn-apple.com/a/v1/example.com"
        assertEquals(CheckStatus.PASS, IosChecks.cdn(f, listOf(appId), Fetch(cdnUrl, Fetched(200, "application/json", WellKnownFiles.aasa(listOf(appId)), false), null)).status)
        assertEquals(CheckStatus.WARN, IosChecks.cdn(f, listOf(appId), Fetch(cdnUrl, Fetched(404, null, "", false), null)).status)
        assertEquals(CheckStatus.WARN, IosChecks.cdn(f, listOf(appId), Fetch(cdnUrl, Fetched(200, null, "{}", false), null)).status)
    }

    @Test
    fun entitlementsFile() {
        val file = tmp.newFile("app.entitlements")
        file.writeText(plist("webcredentials:example.com?mode=developer"))
        assertEquals(CheckStatus.PASS, IosChecks.entitlements(f, "example.com", file).status)
        file.writeText(plist("webcredentials:other.com"))
        assertEquals(CheckStatus.FAIL, IosChecks.entitlements(f, "example.com", file).status)
        assertEquals(CheckStatus.FAIL, IosChecks.entitlements(f, "example.com", File(tmp.root, "missing.entitlements")).status)
        assertEquals(CheckStatus.SKIP, IosChecks.entitlements(f, "example.com", null).status)
    }

    private fun settings(entitlements: String?) = XcodeSettings(
        target = "iosApp",
        values = mapOf("PROJECT_DIR" to tmp.root.absolutePath) + (entitlements?.let { mapOf("CODE_SIGN_ENTITLEMENTS" to it) } ?: emptyMap()),
    )

    @Test
    fun wiringFailsWhenCodeSignEntitlementsIsUnset() {
        val result = IosChecks.wiring(f, "example.com", XcodeProbe.Found(settings(null)))
        assertEquals(CheckStatus.FAIL, result.status)
        assertTrue("CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements" in result.fix!!, result.fix)
    }

    @Test
    fun wiringPassesWhenResolvedFileListsDomain() {
        File(tmp.root, "iosApp").mkdirs()
        File(tmp.root, "iosApp/iosApp.entitlements").writeText(plist("webcredentials:example.com"))
        val result = IosChecks.wiring(f, "example.com", XcodeProbe.Found(settings("iosApp/iosApp.entitlements")))
        assertEquals(CheckStatus.PASS, result.status, result.message)
    }

    @Test
    fun wiringFailsWhenResolvedFileIsMissingOrLacksDomain() {
        assertEquals(CheckStatus.FAIL, IosChecks.wiring(f, "example.com", XcodeProbe.Found(settings("nope.entitlements"))).status)
        File(tmp.root, "x.entitlements").writeText(plist("webcredentials:other.com"))
        assertEquals(CheckStatus.FAIL, IosChecks.wiring(f, "example.com", XcodeProbe.Found(settings("x.entitlements"))).status)
    }

    @Test
    fun wiringSkipsWithoutXcodebuild() {
        val result = IosChecks.wiring(f, "example.com", XcodeProbe.Unavailable("xcodebuild not found"))
        assertEquals(CheckStatus.SKIP, result.status)
        assertTrue("xcodebuild" in result.message)
    }

    @Test
    fun builtApp() {
        assertEquals(CheckStatus.SKIP, IosChecks.builtApp(f, "example.com", null).status)
        val good = BuiltApp("/d/PassKi.app", "PassKi.app-Simulated.xcent", plist("webcredentials:example.com"), stale = false)
        assertEquals(CheckStatus.PASS, IosChecks.builtApp(f, "example.com", good).status)
        assertEquals(CheckStatus.WARN, IosChecks.builtApp(f, "example.com", good.copy(stale = true)).status)
        assertEquals(CheckStatus.FAIL, IosChecks.builtApp(f, "example.com", good.copy(entitlements = plist())).status)
        assertEquals(CheckStatus.FAIL, IosChecks.builtApp(f, "example.com", good.copy(entitlements = null)).status)
    }
}
