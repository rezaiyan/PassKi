package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.PasskiJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasskeyExtensionsTest {
    private val creation =
        """{"rp":{"id":"example.com","name":"Ex"},"user":{"id":"AQ","name":"a"},"challenge":"AQID",""" +
            """"pubKeyCredParams":[{"type":"public-key","alg":-7}],"extensions":{"credProps":true}}"""
    private val request = """{"challenge":"AQID","rpId":"example.com","extensions":{"appid":"x"}}"""

    private fun ext(json: String): JsonObject {
        val root = PasskiJson.parseToJsonElement(json).jsonObject
        return (root["publicKey"]?.jsonObject ?: root)["extensions"]!!.jsonObject
    }

    @Test
    fun withPrfOnRequestAddsEvalSaltsAsBase64UrlAndKeepsOtherExtensions() {
        val out = PasskeyExtensions.withPrf(request, first = byteArrayOf(-5, -1), second = byteArrayOf(1))
        val eval = ext(out)["prf"]!!.jsonObject["eval"]!!.jsonObject
        assertEquals("-_8", eval["first"]!!.jsonPrimitive.content)
        assertEquals("AQ", eval["second"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive("x"), ext(out)["appid"])
        assertEquals("AQID", PasskiJson.parseToJsonElement(out).jsonObject["challenge"]!!.jsonPrimitive.content)
    }

    @Test
    fun withPrfOmitsSecondWhenNull() {
        val eval = ext(PasskeyExtensions.withPrf(request, byteArrayOf(1)))["prf"]!!.jsonObject["eval"]!!.jsonObject
        assertEquals(setOf("first"), eval.keys)
    }

    @Test
    fun withPrfOnCreationAddsEval() {
        val out = PasskeyExtensions.withPrf(creation, byteArrayOf(1))
        assertEquals("AQ", ext(out)["prf"]!!.jsonObject["eval"]!!.jsonObject["first"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(true), ext(out)["credProps"])
    }

    @Test
    fun withPrfWithoutSaltsAsksWhetherPrfIsEnabled() {
        val out = PasskeyExtensions.withPrf(creation)
        assertEquals(JsonObject(emptyMap()), ext(out)["prf"])
    }

    @Test
    fun withPrfWithoutSaltsKeepsExistingEval() {
        val withEval = PasskeyExtensions.withPrf(creation, byteArrayOf(1))
        assertEquals(ext(withEval)["prf"], ext(PasskeyExtensions.withPrf(withEval))["prf"])
    }

    @Test
    fun withPrfWithoutSaltsRejectsRequestOptions() {
        assertFailsWith<IllegalArgumentException> { PasskeyExtensions.withPrf(request) }
    }

    @Test
    fun withPrfOnRequestKeepsEvalByCredential() {
        val json = """{"challenge":"AQ","extensions":{"prf":{"evalByCredential":{"AQ":{"first":"Ag"}}}}}"""
        val prf = ext(PasskeyExtensions.withPrf(json, byteArrayOf(3)))["prf"]!!.jsonObject
        assertEquals(setOf("evalByCredential", "eval"), prf.keys)
    }

    @Test
    fun builderKeepsPublicKeyEnvelope() {
        val out = PasskeyExtensions.withPrf("""{"publicKey":$request}""", byteArrayOf(1))
        val root = PasskiJson.parseToJsonElement(out).jsonObject
        assertEquals(setOf("publicKey"), root.keys)
        assertTrue("prf" in ext(out))
    }

    @Test
    fun builderAddsExtensionsObjectWhenMissing() {
        val out = PasskeyExtensions.withLargeBlobRead("""{"challenge":"AQ"}""")
        assertTrue(ext(out)["largeBlob"]!!.jsonObject["read"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun withLargeBlobSetsSupport() {
        assertEquals(
            "required",
            ext(PasskeyExtensions.withLargeBlob(creation, LargeBlobSupport.Required))["largeBlob"]!!
                .jsonObject["support"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "preferred",
            ext(PasskeyExtensions.withLargeBlob(creation))["largeBlob"]!!.jsonObject["support"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun withLargeBlobWriteReplacesRead() {
        val out = PasskeyExtensions.withLargeBlobWrite(PasskeyExtensions.withLargeBlobRead(request), byteArrayOf(1, 2, 3))
        val largeBlob = ext(out)["largeBlob"]!!.jsonObject
        assertEquals(setOf("write"), largeBlob.keys)
        assertEquals("AQID", largeBlob["write"]!!.jsonPrimitive.content)
    }

    @Test
    fun largeBlobBuildersRejectTheWrongOptionsKind() {
        assertFailsWith<IllegalArgumentException> { PasskeyExtensions.withLargeBlob(request) }
        assertFailsWith<IllegalArgumentException> { PasskeyExtensions.withLargeBlobRead(creation) }
        assertFailsWith<IllegalArgumentException> { PasskeyExtensions.withLargeBlobWrite(creation, byteArrayOf(1)) }
    }

    @Test
    fun buildersRejectMalformedJson() {
        assertFailsWith<IllegalArgumentException> { PasskeyExtensions.withPrf("not json", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { PasskeyExtensions.withLargeBlobRead("[1]") }
        assertFailsWith<IllegalArgumentException> {
            PasskeyExtensions.withLargeBlobRead("""{"challenge":"AQ","extensions":5}""")
        }
    }

    @Test
    fun prfResultsCompareByContentAndHideBytes() {
        val a = PrfResults(byteArrayOf(1, 2), null)
        val b = PrfResults(byteArrayOf(1, 2), null)
        val c = PrfResults(byteArrayOf(1, 2), byteArrayOf(3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertEquals("PrfResults(first=<2 bytes>, second=null)", a.toString())
        assertEquals("PrfResults(first=<2 bytes>, second=<1 bytes>)", c.toString())
        assertFalse(c.toString().contains("[1"))
        assertNull(a.second)
    }
}
