package com.alirezaiyan.passki.internal.doctor

import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.internal.PasskiJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetLinksCheckTest {
    private val url = "https://example.com/.well-known/assetlinks.json"
    private val pkg = "com.example.app"
    private val fp1 = "A0:A1:A2:A3:A4:A5:A6:A7:A8:A9:AA:AB:AC:AD:AE:AF:B0:B1:B2:B3:B4:B5:B6:B7:B8:B9:BA:BB:BC:BD:BE:BF"
    private val fp2 = "00:01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:10:11:12:13:14:15:16:17:18:19:1A:1B:1C:1D:1E:1F"

    private fun statement(
        pkg: String = this.pkg,
        fingerprints: List<String> = listOf(fp1),
        relation: String = "delegate_permission/common.get_login_creds",
    ) = """
        {"relation":["$relation"],
         "target":{"namespace":"android_app","package_name":"$pkg","sha256_cert_fingerprints":[${fingerprints.joinToString { "\"$it\"" }}]}}
    """.trimIndent()

    private fun ok(body: String, contentType: String? = "application/json") =
        HttpResult(url = url, status = 200, contentType = contentType, body = body, redirectedTo = null, error = null)

    private fun evaluate(result: HttpResult, fingerprints: List<String> = listOf(fp1)) =
        AssetLinksCheck.evaluate(result, pkg, fingerprints)

    @Test
    fun passesWhenStatementCoversPackageAndFingerprints() {
        val check = evaluate(ok("[${statement()}]"))
        assertEquals(DoctorStatus.Pass, check.status)
        assertEquals("assetlinks.json", check.name)
        assertNull(check.fixHint)
    }

    @Test
    fun fingerprintComparisonIgnoresCase() {
        val check = evaluate(ok("[${statement(fingerprints = listOf(fp1.lowercase()))}]"))
        assertEquals(DoctorStatus.Pass, check.status)
    }

    @Test
    fun fingerprintsMayBeSpreadAcrossStatements() {
        val body = "[${statement(fingerprints = listOf(fp1))},${statement(fingerprints = listOf(fp2))}]"
        assertEquals(DoctorStatus.Pass, evaluate(ok(body), listOf(fp1, fp2)).status)
    }

    @Test
    fun acceptsJsonContentTypeWithCharset() {
        val check = evaluate(ok("[${statement()}]", contentType = "application/json; charset=utf-8"))
        assertEquals(DoctorStatus.Pass, check.status)
    }

    @Test
    fun failsWhenFetchErrored() {
        val result = HttpResult(url, status = -1, contentType = null, body = null, redirectedTo = null, error = "timeout")
        val check = evaluate(result)
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("timeout" in check.message)
        assertNotNull(check.fixHint)
    }

    @Test
    fun failsWhenFileIsMissing() {
        val result = HttpResult(url, status = 404, contentType = "text/html", body = "nope", redirectedTo = null, error = null)
        val check = evaluate(result)
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("404" in check.message)
        assertTrue(url in check.fixHint!!)
    }

    @Test
    fun failsWhenRedirected() {
        val result = HttpResult(
            url, status = 301, contentType = null, body = null,
            redirectedTo = "https://www.example.com/.well-known/assetlinks.json", error = null,
        )
        val check = evaluate(result)
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("redirect" in check.message)
        assertTrue("https://www.example.com/.well-known/assetlinks.json" in check.message)
    }

    @Test
    fun failsWhenBodyIsNotJson() {
        val check = evaluate(ok("<html>"))
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("JSON" in check.message)
    }

    @Test
    fun failsWhenBodyIsJsonButNotAnArray() {
        assertEquals(DoctorStatus.Fail, evaluate(ok("""{"relation":[]}""")).status)
    }

    @Test
    fun failsWhenBodyIsEmpty() {
        assertEquals(DoctorStatus.Fail, evaluate(ok("")).status)
    }

    @Test
    fun failsWhenNoStatementHasLoginCredsRelation() {
        val check = evaluate(ok("[${statement(relation = "delegate_permission/common.handle_all_urls")}]"))
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("get_login_creds" in check.message)
    }

    @Test
    fun failsWhenPackageIsMissingAndHintCarriesStatement() {
        val check = evaluate(ok("[${statement(pkg = "com.other.app")}]"))
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue(pkg in check.message)
        val hinted = hintedStatement(check.fixHint!!)
        assertEquals(pkg, hinted["target"]!!.jsonObject["package_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun failsWhenFingerprintIsMissingAndHintCarriesExactStatement() {
        val check = evaluate(ok("[${statement(fingerprints = listOf(fp2))}]"))
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue(fp1 in check.message)
        val hint = check.fixHint!!
        assertTrue(url in hint)
        val hinted = hintedStatement(hint)
        assertEquals(
            listOf("delegate_permission/common.get_login_creds"),
            hinted["relation"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        val target = hinted["target"]!!.jsonObject
        assertEquals("android_app", target["namespace"]!!.jsonPrimitive.content)
        assertEquals(pkg, target["package_name"]!!.jsonPrimitive.content)
        assertEquals(listOf(fp1), target["sha256_cert_fingerprints"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun failsWhenNoSigningFingerprintsKnown() {
        assertEquals(DoctorStatus.Fail, evaluate(ok("[${statement()}]"), emptyList()).status)
    }

    @Test
    fun warnsWhenContentTypeIsNotJson() {
        val check = evaluate(ok("[${statement()}]", contentType = "text/plain"))
        assertEquals(DoctorStatus.Warn, check.status)
        assertTrue("text/plain" in check.message)
        assertNotNull(check.fixHint)
    }

    @Test
    fun warnsWhenContentTypeIsAbsent() {
        assertEquals(DoctorStatus.Warn, evaluate(ok("[${statement()}]", contentType = null)).status)
    }

    @Test
    fun androidOriginEncodesFingerprintBytesAsBase64Url() {
        assertEquals("android:apk-key-hash:oKGio6SlpqeoqaqrrK2ur7CxsrO0tba3uLm6u7y9vr8", androidOrigin(fp1))
        assertEquals(androidOrigin(fp1), androidOrigin(fp1.lowercase()))
    }

    @Test
    fun androidOriginRejectsMalformedFingerprint() {
        assertFailsWith<IllegalArgumentException> { androidOrigin("ZZ:01") }
        assertFailsWith<IllegalArgumentException> { androidOrigin("A0:A1") }
    }

    /** The hint embeds a JSON statement after "statement:"; parse it back. */
    private fun hintedStatement(hint: String): JsonObject {
        val json = hint.substring(hint.indexOf('{'))
        val element = PasskiJson.parseToJsonElement(json)
        return (element as? JsonObject) ?: (element as JsonArray).first().jsonObject
    }

    @Test
    fun androidOriginMatchesRealDebugKeyObservedOnDevice() {
        val debugKey = "C9:64:0F:6A:68:90:AF:6C:26:49:DC:BB:AB:94:79:23:93:5C:FC:10:8B:DF:03:20:78:04:60:5C:5B:79:49:03"
        assertEquals("android:apk-key-hash:yWQPamiQr2wmSdy7q5R5I5Nc_BCL3wMgeARgXFt5SQM", androidOrigin(debugKey))
    }

    @Test
    fun includeOnlyFileWarnsInsteadOfFailing() {
        val check = evaluate(ok("""[{"include":"https://example.com/other-assetlinks.json"}]"""))
        assertEquals(DoctorStatus.Warn, check.status)
        assertTrue("include statements are not followed by the doctor" in check.message, check.message)
        assertTrue("https://example.com/other-assetlinks.json" in check.message)
    }

    @Test
    fun includeWithMissingFingerprintWarns() {
        val body = """[${statement(fingerprints = listOf(fp2))},{"include":"https://example.com/more.json"}]"""
        assertEquals(DoctorStatus.Warn, evaluate(ok(body)).status)
    }

    @Test
    fun includeAlongsideCompleteStatementPasses() {
        val body = """[${statement()},{"include":"https://example.com/more.json"}]"""
        assertEquals(DoctorStatus.Pass, evaluate(ok(body)).status)
    }

    @Test
    fun truncatedBodyReportsSizeNotJsonSyntax() {
        val result = HttpResult(url, 200, "application/json", "[{\"rel", redirectedTo = null, error = null, truncated = true)
        val check = evaluate(result)
        assertEquals(DoctorStatus.Fail, check.status)
        assertTrue("larger than 1 MiB" in check.message, check.message)
    }
}
