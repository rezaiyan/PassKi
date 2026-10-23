package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyExtensions
import com.alirezaiyan.passki.PrfResults
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionsJsonTest {
    private fun obj(json: String) = PasskiJson.parseToJsonElement(json).jsonObject

    // --- inputs -------------------------------------------------------------------------------------------------

    @Test
    fun prfInputIsNullWithoutPrf() {
        assertNull(ExtensionsJson.prfInput(null))
        assertNull(ExtensionsJson.prfInput(obj("""{"credProps":true}""")))
    }

    @Test
    fun prfInputWithoutEvalOnlyChecksSupport() {
        val input = ExtensionsJson.prfInput(obj("""{"prf":{}}"""))!!
        assertNull(input.eval)
        assertTrue(input.evalByCredential.isEmpty())
    }

    @Test
    fun prfInputDecodesEvalAndEvalByCredential() {
        val input = ExtensionsJson.prfInput(
            obj("""{"prf":{"eval":{"first":"AQ","second":"Ag=="},"evalByCredential":{"Aw":{"first":"BA"}}}}"""),
        )!!
        assertContentEquals(byteArrayOf(1), input.eval!!.first)
        assertContentEquals(byteArrayOf(2), input.eval.second)
        assertContentEquals(byteArrayOf(4), input.evalByCredential.getValue("Aw").first)
        assertNull(input.evalByCredential.getValue("Aw").second)
    }

    @Test
    fun prfInputWithMalformedSaltIsInvalidOptions() {
        val e = assertFailsWith<PasskeyException> { ExtensionsJson.prfInput(obj("""{"prf":{"eval":{"first":"%%"}}}""")) }
        assertIs<PasskeyError.InvalidOptions>(e.error)
        assertFailsWith<PasskeyException> { ExtensionsJson.prfInput(obj("""{"prf":{"eval":{}}}""")) }
    }

    @Test
    fun jsonNullInputsAreTreatedAsAbsent() {
        // Server-side WebAuthn libraries often serialise unset optional members as null.
        val input = ExtensionsJson.prfInput(obj("""{"prf":{"eval":{"first":"AQ","second":null},"evalByCredential":null}}"""))!!
        assertContentEquals(byteArrayOf(1), input.eval!!.first)
        assertNull(input.eval.second)
        assertTrue(input.evalByCredential.isEmpty())
        assertNull(ExtensionsJson.prfInput(obj("""{"prf":{"eval":null}}"""))!!.eval)
        assertIs<ExtensionsJson.LargeBlobAssertion.Read>(
            ExtensionsJson.largeBlobAssertion(obj("""{"largeBlob":{"read":true,"write":null}}""")),
        )
    }

    @Test
    fun largeBlobRegistrationSupportDefaultsToPreferred() {
        assertNull(ExtensionsJson.largeBlobSupport(obj("{}")))
        assertEquals("preferred", ExtensionsJson.largeBlobSupport(obj("""{"largeBlob":{}}""")))
        assertEquals("required", ExtensionsJson.largeBlobSupport(obj("""{"largeBlob":{"support":"required"}}""")))
    }

    @Test
    fun largeBlobAssertionInputReadOrWrite() {
        assertNull(ExtensionsJson.largeBlobAssertion(obj("""{"largeBlob":{}}""")))
        assertIs<ExtensionsJson.LargeBlobAssertion.Read>(
            ExtensionsJson.largeBlobAssertion(obj("""{"largeBlob":{"read":true}}""")),
        )
        assertNull(ExtensionsJson.largeBlobAssertion(obj("""{"largeBlob":{"read":false}}""")))
        val write = assertIs<ExtensionsJson.LargeBlobAssertion.Write>(
            ExtensionsJson.largeBlobAssertion(obj("""{"largeBlob":{"write":"AQID"}}""")),
        )
        assertContentEquals(byteArrayOf(1, 2, 3), write.blob)
    }

    @Test
    fun builtOptionsRoundTripThroughInputParsers() {
        val request = PasskeyExtensions.withLargeBlobWrite(
            PasskeyExtensions.withPrf("""{"challenge":"AQ"}""", byteArrayOf(7), byteArrayOf(8)),
            byteArrayOf(9),
        )
        val ext = WebAuthnOptions.parseRequest(request).model.extensions
        assertContentEquals(byteArrayOf(8), ExtensionsJson.prfInput(ext)!!.eval!!.second)
        assertContentEquals(
            byteArrayOf(9),
            assertIs<ExtensionsJson.LargeBlobAssertion.Write>(ExtensionsJson.largeBlobAssertion(ext)).blob,
        )
    }

    @Test
    fun extensionsSurviveNormalization() {
        val json = """{"publicKey":{"challenge":"AQ","extensions":{"prf":{"eval":{"first":"AQ"}},"largeBlob":{"read":true}}}}"""
        val normalized = WebAuthnOptions.parseRequest(json)
        val ext = obj(normalized.json)["extensions"]!!.jsonObject
        assertEquals(obj("""{"prf":{"eval":{"first":"AQ"}},"largeBlob":{"read":true}}"""), ext)
        assertEquals(ext, normalized.model.extensions)
    }

    // --- outputs ------------------------------------------------------------------------------------------------

    @Test
    fun registrationOutputsUseW3cShape() {
        val out = ExtensionsJson.registrationOutputs(
            prfEnabled = true,
            prf = ExtensionsJson.PrfValues(byteArrayOf(1), null),
            largeBlobSupported = false,
        )
        assertEquals(obj("""{"prf":{"enabled":true,"results":{"first":"AQ"}},"largeBlob":{"supported":false}}"""), out)
        assertEquals(JsonObject(emptyMap()), ExtensionsJson.registrationOutputs(null, null, null))
    }

    @Test
    fun assertionOutputsUseW3cShape() {
        val out = ExtensionsJson.assertionOutputs(
            prf = ExtensionsJson.PrfValues(byteArrayOf(1), byteArrayOf(2)),
            largeBlob = byteArrayOf(3),
            largeBlobWritten = null,
        )
        assertEquals(obj("""{"prf":{"results":{"first":"AQ","second":"Ag"}},"largeBlob":{"blob":"Aw"}}"""), out)
        assertEquals(
            obj("""{"largeBlob":{"written":true}}"""),
            ExtensionsJson.assertionOutputs(prf = null, largeBlob = null, largeBlobWritten = true),
        )
    }

    // --- typed results ------------------------------------------------------------------------------------------

    private fun registration(ext: JsonObject) = parseRegistration(
        WebAuthnResponses.registration(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), emptyList(), null, ext),
    )

    private fun assertion(ext: JsonObject) = parseAssertion(
        WebAuthnResponses.authentication(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(4), null, null, ext),
    )

    @Test
    fun resultsWithoutExtensionsHaveNullOutputs() {
        val r = registration(JsonObject(emptyMap()))
        assertNull(r.prfEnabled)
        assertNull(r.prf)
        assertNull(r.largeBlobSupported)
        val a = assertion(JsonObject(emptyMap()))
        assertNull(a.prf)
        assertNull(a.largeBlob)
        assertNull(a.largeBlobWritten)
    }

    @Test
    fun registrationParsesPrfAndLargeBlob() {
        val r = registration(ExtensionsJson.registrationOutputs(true, ExtensionsJson.PrfValues(byteArrayOf(5), null), true))
        assertEquals(true, r.prfEnabled)
        assertEquals(PrfResults(byteArrayOf(5), null), r.prf)
        assertEquals(true, r.largeBlobSupported)
    }

    @Test
    fun assertionParsesPrfAndLargeBlob() {
        val a = assertion(obj("""{"prf":{"results":{"first":"AQ","second":"Ag"}},"largeBlob":{"blob":"Aw","written":false}}"""))
        assertEquals(PrfResults(byteArrayOf(1), byteArrayOf(2)), a.prf)
        assertContentEquals(byteArrayOf(3), a.largeBlob)
        assertEquals(false, a.largeBlobWritten)
    }

    @Test
    fun parsesGooglePasswordManagerAssertionOutput() {
        // Shape returned by Google Password Manager through Credential Manager (authenticationResponseJson).
        val first = ByteArray(32) { (it * 7).toByte() }
        val json = """{"id":"AQ","rawId":"AQ","type":"public-key","authenticatorAttachment":"platform",""" +
            """"response":{"clientDataJSON":"e30","authenticatorData":"AQ","signature":"AQ","userHandle":"AQ"},""" +
            """"clientExtensionResults":{"prf":{"results":{"first":"${Base64Url.encode(first)}"}}}}"""
        val a = parseAssertion(json)
        assertContentEquals(first, a.prf!!.first)
        assertNull(a.prf.second)
        assertEquals(32, a.prf.first.size)
    }

    @Test
    fun parsesGooglePasswordManagerRegistrationOutput() {
        val json = """{"id":"AQ","rawId":"AQ","type":"public-key","response":{"clientDataJSON":"e30",""" +
            """"attestationObject":"AQ","transports":["internal","hybrid"]},""" +
            """"clientExtensionResults":{"credProps":{"rk":true},"prf":{"enabled":true}}}"""
        val r = parseRegistration(json)
        assertEquals(true, r.prfEnabled)
        assertNull(r.prf)
    }

    @Test
    fun prfResultsWithJsonNullSecondKeepFirst() {
        val a = assertion(obj("""{"prf":{"results":{"first":"AQ","second":null}}}"""))
        assertEquals(PrfResults(byteArrayOf(1), null), a.prf)
        val r = registration(obj("""{"prf":{"enabled":true,"results":{"first":"AQ","second":null}}}"""))
        assertEquals(PrfResults(byteArrayOf(1), null), r.prf)
    }

    @Test
    fun malformedExtensionOutputsAreIgnored() {
        val a = assertion(obj("""{"prf":{"results":{"first":"%%"}},"largeBlob":{"blob":7,"written":"yes"}}"""))
        assertNull(a.prf)
        assertNull(a.largeBlob)
        assertNull(a.largeBlobWritten)
        val r = registration(obj("""{"prf":"x","largeBlob":{"supported":"maybe"}}"""))
        assertNull(r.prfEnabled)
        assertNull(r.largeBlobSupported)
    }
}
