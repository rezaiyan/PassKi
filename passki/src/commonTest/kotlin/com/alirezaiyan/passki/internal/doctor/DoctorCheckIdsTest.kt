package com.alirezaiyan.passki.internal.doctor

import com.alirezaiyan.passki.DoctorStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each doctor outcome carries the catalog ID of the check it speaks to. */
class DoctorCheckIdsTest {
    private val pkg = "com.example.app"
    private val fp = List(32) { "AB" }.joinToString(":")
    private val linksUrl = "https://example.com/.well-known/assetlinks.json"
    private val aasaUrl = "https://example.com/.well-known/apple-app-site-association"
    private val cdnUrl = "https://app-site-association.cdn-apple.com/a/v1/example.com"

    private fun ok(url: String, body: String, contentType: String? = "application/json") =
        HttpResult(url, 200, contentType, body, redirectedTo = null, error = null)

    private fun statement(pkg: String = this.pkg, relation: String = AssetLinksCheck.LOGIN_CREDS_RELATION, fps: List<String> = listOf(fp)) =
        """[{"relation":["$relation"],"target":{"namespace":"android_app","package_name":"$pkg",""" +
            """"sha256_cert_fingerprints":[${fps.joinToString { "\"$it\"" }}]}}]"""

    private fun links(result: HttpResult) = AssetLinksCheck.evaluate(result, pkg, listOf(fp))

    @Test
    fun assetLinksIds() {
        assertEquals(CheckIds.AND_01, links(HttpResult(linksUrl, 404, null, "x", null, null)).id)
        assertEquals(CheckIds.AND_01, links(ok(linksUrl, "<html>")).id)
        assertEquals(CheckIds.AND_02, links(ok(linksUrl, statement(relation = "other"))).id)
        assertEquals(CheckIds.AND_02, links(ok(linksUrl, statement(pkg = "com.other"))).id)
        assertEquals(CheckIds.AND_03, links(ok(linksUrl, statement(fps = listOf(List(32) { "CD" }.joinToString(":"))))).id)
        val wrongType = links(ok(linksUrl, statement(), contentType = "text/plain"))
        assertEquals(DoctorStatus.Warn, wrongType.status)
        assertEquals(CheckIds.AND_01, wrongType.id)
        val pass = links(ok(linksUrl, statement()))
        assertEquals(DoctorStatus.Pass, pass.status)
        assertEquals(CheckIds.AND_03, pass.id)
    }

    @Test
    fun aasaIds() {
        val good = """{"webcredentials":{"apps":["ABCDE12345.$pkg"]}}"""
        assertEquals(CheckIds.IOS_01, AasaCheck.evaluate(HttpResult(aasaUrl, 404, null, "x", null, null), ok(cdnUrl, good), pkg).id)
        assertEquals(CheckIds.IOS_01, AasaCheck.evaluate(ok(aasaUrl, "<html>"), ok(cdnUrl, good), pkg).id)
        assertEquals(CheckIds.IOS_02, AasaCheck.evaluate(ok(aasaUrl, """{"webcredentials":{"apps":[]}}"""), ok(cdnUrl, good), pkg).id)
        assertEquals(CheckIds.IOS_03, AasaCheck.evaluate(ok(aasaUrl, good), ok(cdnUrl, "{}"), pkg).id)
        assertEquals(CheckIds.IOS_03, AasaCheck.evaluate(ok(aasaUrl, good), ok(cdnUrl, good), pkg).id)
    }

    @Test
    fun rpIdCheckId() {
        assertEquals(CheckIds.CFG_01, rpIdCheck("https://example.com")?.id)
        assertEquals(CheckIds.CFG_01, rpIdCheck("")?.id)
    }

    @Test
    fun skippedPlatformReportHasDev03() {
        assertEquals(CheckIds.DEV_03, skippedDoctorReport("example.com", "unsupported").checks.single().id)
    }
}
