package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebAuthnOptionsTest {
    private val creation = """
        {"rp":{"id":"example.com","name":"Example"},
         "user":{"id":"AQID","name":"alice","displayName":"Alice"},
         "challenge":"AAEC",
         "pubKeyCredParams":[{"type":"public-key","alg":-7}],
         "excludeCredentials":[{"id":"BAUG","type":"public-key"}],
         "authenticatorSelection":{"residentKey":"required","userVerification":"preferred"},
         "extensions":{"credProps":true},
         "unknownField":true}
    """.trimIndent()

    @Test
    fun parsesCreationOptions() {
        val parsed = WebAuthnOptions.parseCreation(creation)
        assertEquals("example.com", parsed.model.rp.id)
        assertEquals("alice", parsed.model.user.name)
        assertEquals("Alice", parsed.model.user.displayName)
        assertEquals(listOf("BAUG"), parsed.model.excludeCredentials.map { it.id })
        assertEquals("preferred", parsed.model.authenticatorSelection?.userVerification)
    }

    @Test
    fun keepsUnknownFieldsAndExtensionsInNormalizedJson() {
        val json = WebAuthnOptions.parseCreation(creation).json
        assertTrue("unknownField" in json)
        assertTrue("credProps" in json)
    }

    @Test
    fun unwrapsPublicKeyEnvelope() {
        val parsed = WebAuthnOptions.parseCreation("""{"publicKey":$creation}""")
        assertEquals("AAEC", parsed.model.challenge)
        assertFalse("publicKey" in parsed.json)
    }

    @Test
    fun parsesRequestOptions() {
        val parsed = WebAuthnOptions.parseRequest(
            """{"challenge":"AAEC","rpId":"example.com",
               "allowCredentials":[{"id":"BAUG","type":"public-key","transports":["internal"]}],
               "userVerification":"required"}""",
        )
        assertEquals("example.com", parsed.model.rpId)
        assertEquals(listOf("internal"), parsed.model.allowCredentials.single().transports)
        assertEquals("required", parsed.model.userVerification)
    }

    @Test
    fun missingChallengeIsInvalidOptions() {
        val e = assertFailsWith<PasskeyException> {
            WebAuthnOptions.parseCreation("""{"rp":{"name":"x"},"user":{"id":"AQID","name":"a"}}""")
        }
        assertIs<PasskeyError.InvalidOptions>(e.error)
    }

    @Test
    fun nonJsonIsInvalidOptions() {
        val e = assertFailsWith<PasskeyException> { WebAuthnOptions.parseRequest("not json") }
        assertIs<PasskeyError.InvalidOptions>(e.error)
    }

    @Test
    fun nonBase64ChallengeIsInvalidOptions() {
        val e = assertFailsWith<PasskeyException> { WebAuthnOptions.parseRequest("""{"challenge":"***"}""") }
        assertIs<PasskeyError.InvalidOptions>(e.error)
    }

    private fun invalidMessage(block: () -> Unit): String {
        val e = assertFailsWith<PasskeyException> { block() }
        return assertIs<PasskeyError.InvalidOptions>(e.error).message
    }

    @Test
    fun creationBinaryFieldsAreCanonicalized() {
        val parsed = WebAuthnOptions.parseCreation(
            """{"rp":{"name":"x"},"user":{"id":"AQID/w==","name":"a"},"challenge":"AQID/w==",
               "excludeCredentials":[{"id":"AQID/w==","type":"public-key"}],"unknownField":1}""",
        )
        assertTrue("AQID_w" in parsed.json)
        assertFalse("/" in parsed.json)
        assertFalse("=" in parsed.json)
        assertTrue("unknownField" in parsed.json)
        assertEquals("AQID_w", parsed.model.challenge)
        assertEquals("AQID_w", parsed.model.user.id)
        assertEquals("AQID_w", parsed.model.excludeCredentials.single().id)
    }

    @Test
    fun requestBinaryFieldsAreCanonicalized() {
        val parsed = WebAuthnOptions.parseRequest(
            """{"challenge":"AQID/w==","allowCredentials":[{"id":"AQID/w==","type":"public-key"}]}""",
        )
        assertTrue("AQID_w" in parsed.json)
        assertFalse("/" in parsed.json)
        assertEquals("AQID_w", parsed.model.challenge)
        assertEquals("AQID_w", parsed.model.allowCredentials.single().id)
    }

    @Test
    fun emptyChallengeIsRejected() {
        assertTrue("challenge" in invalidMessage { WebAuthnOptions.parseRequest("""{"challenge":""}""") })
        assertTrue(
            "challenge" in invalidMessage {
                WebAuthnOptions.parseCreation("""{"rp":{"name":"x"},"user":{"id":"AQID","name":"a"},"challenge":""}""")
            },
        )
    }

    @Test
    fun emptyUserIdIsRejected() {
        assertTrue(
            "user.id" in invalidMessage {
                WebAuthnOptions.parseCreation("""{"rp":{"name":"x"},"user":{"id":"","name":"a"},"challenge":"AAEC"}""")
            },
        )
    }

    @Test
    fun userIdOver64BytesIsRejected() {
        val id = Base64Url.encode(ByteArray(65))
        assertTrue(
            "user.id" in invalidMessage {
                WebAuthnOptions.parseCreation("""{"rp":{"name":"x"},"user":{"id":"$id","name":"a"},"challenge":"AAEC"}""")
            },
        )
        val ok = Base64Url.encode(ByteArray(64))
        WebAuthnOptions.parseCreation("""{"rp":{"name":"x"},"user":{"id":"$ok","name":"a"},"challenge":"AAEC"}""")
    }

    @Test
    fun invalidUserIdAndCredentialIdAreRejected() {
        invalidMessage {
            WebAuthnOptions.parseCreation("""{"rp":{"name":"x"},"user":{"id":"***","name":"a"},"challenge":"AAEC"}""")
        }
        invalidMessage {
            WebAuthnOptions.parseCreation(
                """{"rp":{"name":"x"},"user":{"id":"AQID","name":"a"},"challenge":"AAEC","excludeCredentials":[{"id":"***"}]}""",
            )
        }
    }

    @Test
    fun nonObjectRootIsInvalid() {
        invalidMessage { WebAuthnOptions.parseRequest("[]") }
        invalidMessage { WebAuthnOptions.parseRequest("null") }
    }

    @Test
    fun nonObjectPublicKeyIsNotAnEnvelope() {
        invalidMessage { WebAuthnOptions.parseRequest("""{"publicKey":5}""") }
    }

    @Test
    fun unknownFieldsPreservedInsideEnvelope() {
        val parsed = WebAuthnOptions.parseCreation("""{"publicKey":$creation}""")
        assertTrue("unknownField" in parsed.json)
        assertTrue("credProps" in parsed.json)
    }
}
