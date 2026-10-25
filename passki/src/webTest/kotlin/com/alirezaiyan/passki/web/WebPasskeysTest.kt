package com.alirezaiyan.passki.web

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.internal.WebAuthnResponses
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebPasskeysTest {
    /** Answers each op from [answers]; a missing op fails like a browser without that API. */
    private class FakeBrowser(
        private val capabilities: String = ALL_CAPABILITIES,
        private val answers: Map<String, () -> String> = emptyMap(),
    ) : WebAuthnBridge {
        val calls = mutableListOf<Call>()

        override suspend fun call(op: String, json: String, mediation: String?): String {
            if (op == "capabilities") return capabilities
            calls += Call(op, json, mediation)
            val answer = answers[op] ?: throw WebAuthnException("NotSupportedError", "no $op")
            return answer()
        }
    }

    private data class Call(val op: String, val json: String, val mediation: String?)

    private fun failing(name: String, message: String = "browser said no"): () -> String =
        { throw WebAuthnException(name, message) }

    @Test
    fun registerSendsNormalizedOptionsAndParsesTheResponse() = runTest {
        val browser = FakeBrowser(answers = mapOf("create" to { REGISTRATION }))

        val result = WebPasskeys(browser).register("""{"publicKey":$CREATION}""")

        val registration = assertIs<PasskeyResult.Success<*>>(result).value as com.alirezaiyan.passki.PasskeyRegistration
        assertEquals("AQID", registration.credentialId)
        assertEquals(listOf("internal"), registration.transports)
        val call = browser.calls.single()
        assertEquals("create", call.op)
        assertNull(call.mediation)
        // The publicKey envelope is removed and binary fields stay base64url for the JS side to decode.
        assertEquals("localhost", Json.parseToJsonElement(call.json).jsonObject["rp"]!!.jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun registerWithoutRpIdIsAllowedBecauseTheBrowserUsesThePagesHost() = runTest {
        val browser = FakeBrowser(answers = mapOf("create" to { REGISTRATION }))

        val result = WebPasskeys(browser).register(CREATION.replace(""""id":"localhost",""", ""))

        assertIs<PasskeyResult.Success<*>>(result)
    }

    @Test
    fun registerRejectsAMalformedRpIdBeforeCallingTheBrowser() = runTest {
        val browser = FakeBrowser()

        val result = WebPasskeys(browser).register(CREATION.replace("localhost", "https://localhost"))

        assertIs<PasskeyError.InvalidOptions>(assertIs<PasskeyResult.Failure>(result).error)
        assertTrue(browser.calls.isEmpty())
    }

    @Test
    fun invalidOptionsJsonNeverReachesTheBrowser() = runTest {
        val browser = FakeBrowser()

        val result = WebPasskeys(browser).signIn("not json")

        assertIs<PasskeyError.InvalidOptions>(assertIs<PasskeyResult.Failure>(result).error)
        assertTrue(browser.calls.isEmpty())
    }

    @Test
    fun excludedCredentialIsAlreadyRegistered() = runTest {
        val browser = FakeBrowser(answers = mapOf("create" to failing("InvalidStateError")))

        val result = WebPasskeys(browser).register(CREATION)

        assertEquals(PasskeyError.AlreadyRegistered, assertIs<PasskeyResult.Failure>(result).error)
    }

    @Test
    fun signInParsesTheAssertion() = runTest {
        val browser = FakeBrowser(answers = mapOf("get" to { ASSERTION }))

        val result = WebPasskeys(browser).signIn(REQUEST)

        val assertion = assertIs<PasskeyResult.Success<*>>(result).value as com.alirezaiyan.passki.PasskeyAssertion
        assertEquals("AQID", assertion.credentialId)
        assertEquals("BAUG", assertion.userHandle)
        assertNull(browser.calls.single().mediation)
    }

    @Test
    fun dismissedSheetIsCancelled() = runTest {
        val browser = FakeBrowser(answers = mapOf("get" to failing("NotAllowedError")))

        val result = WebPasskeys(browser).signIn(REQUEST)

        assertEquals(PasskeyError.Cancelled, assertIs<PasskeyResult.Failure>(result).error)
    }

    @Test
    fun rpIdThatDoesNotMatchThePageIsMisconfigured() = runTest {
        val browser = FakeBrowser(answers = mapOf("get" to failing("SecurityError", "The relying party ID is not a registrable domain suffix of, nor equal to the current domain.")))

        val result = WebPasskeys(browser).signIn(REQUEST)

        val error = assertIs<PasskeyError.Misconfigured>(assertIs<PasskeyResult.Failure>(result).error)
        assertTrue("registrable domain suffix" in error.message, error.message)
    }

    @Test
    fun unclassifiedBrowserErrorKeepsItsName() = runTest {
        val browser = FakeBrowser(answers = mapOf("get" to failing("ConstraintError", "no UV")))

        val result = WebPasskeys(browser).signIn(REQUEST)

        val error = assertIs<PasskeyError.Unknown>(assertIs<PasskeyResult.Failure>(result).error)
        assertEquals("ConstraintError", error.platformCode)
    }

    @Test
    fun autofillUsesConditionalMediation() = runTest {
        val browser = FakeBrowser(answers = mapOf("get" to { ASSERTION }))

        WebPasskeys(browser).signIn(REQUEST, SignInMode.Autofill)

        assertEquals("conditional", browser.calls.single().mediation)
    }

    @Test
    fun autofillWithoutConditionalMediationIsNotSupported() = runTest {
        val browser = FakeBrowser(capabilities = """{"webauthn":true,"secureContext":true}""")

        val result = WebPasskeys(browser).signIn(REQUEST, SignInMode.Autofill)

        assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(result).error)
        assertTrue(browser.calls.isEmpty())
    }

    @Test
    fun immediateOnlyWithNothingOnTheDeviceIsNoCredentials() = runTest {
        val browser = FakeBrowser(answers = mapOf("get" to failing("NotAllowedError")))

        val result = WebPasskeys(browser).signIn(REQUEST, SignInMode.ImmediateOnly)

        assertEquals("immediate", browser.calls.single().mediation)
        assertEquals(PasskeyError.NoCredentials, assertIs<PasskeyResult.Failure>(result).error)
    }

    @Test
    fun immediateOnlyWithoutBrowserSupportIsNotSupported() = runTest {
        val browser = FakeBrowser(capabilities = """{"webauthn":true,"secureContext":true}""")

        val result = WebPasskeys(browser).signIn(REQUEST, SignInMode.ImmediateOnly)

        assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(result).error)
    }

    @Test
    fun upgradeIsAConditionalCreateAndADeclineIsNotSupported() = runTest {
        val browser = FakeBrowser(answers = mapOf("create" to failing("NotAllowedError")))

        val result = WebPasskeys(browser).upgrade(CREATION)

        assertEquals("conditional", browser.calls.single().mediation)
        assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(result).error)
    }

    @Test
    fun upgradeWithoutConditionalCreateNeverCallsTheBrowser() = runTest {
        val browser = FakeBrowser(capabilities = """{"webauthn":true,"secureContext":true}""")

        val result = WebPasskeys(browser).upgrade(CREATION)

        assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(result).error)
        assertTrue(browser.calls.isEmpty())
    }

    @Test
    fun signInOrPasswordIsNotSupported() = runTest {
        val result = WebPasskeys(FakeBrowser()).signInOrPassword(REQUEST)

        assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(result).error)
    }

    @Test
    fun capabilitiesFollowTheBrowser() = runTest {
        val caps = WebPasskeys(FakeBrowser()).capabilities()

        assertTrue(caps.isSupported)
        assertTrue(caps.autofill)
        assertTrue(caps.conditionalCreate)
        assertTrue(caps.immediateSignIn)
        assertTrue(caps.signals)
        assertFalse(caps.unifiedPasswordSignIn)
    }

    @Test
    fun insecurePageDoesNotSupportPasskeys() = runTest {
        val caps = WebPasskeys(FakeBrowser(capabilities = """{"webauthn":true,"secureContext":false}""")).capabilities()

        assertFalse(caps.isSupported)
    }

    @Test
    fun operationsOnAnUnsupportedPageFailWithoutCallingTheBrowser() = runTest {
        val browser = FakeBrowser(capabilities = """{"webauthn":false,"secureContext":true}""")

        val result = WebPasskeys(browser).register(CREATION)

        assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(result).error)
        assertTrue(browser.calls.isEmpty())
    }

    @Test
    fun signalsCallTheSignalApiWithItsJson() = runTest {
        val browser = FakeBrowser(answers = mapOf("signalUnknownCredential" to { "{}" }))

        val result = WebPasskeys(browser).signals.unknownCredential("localhost", "AQID")

        assertIs<PasskeyResult.Success<*>>(result)
        assertEquals(Call("signalUnknownCredential", """{"rpId":"localhost","credentialId":"AQID"}""", null), browser.calls.single())
    }

    @Test
    fun missingSignalApiIsNotSupported() = runTest {
        val result = WebPasskeys(FakeBrowser()).signals.currentUserDetails("localhost", "BAUG", "alice", "Alice")

        assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(result).error)
    }

    private companion object {
        const val ALL_CAPABILITIES =
            """{"webauthn":true,"secureContext":true,"conditionalGet":true,"conditionalCreate":true,"immediateGet":true,""" +
                """"signalUnknownCredential":true,"signalAllAcceptedCredentials":true,"signalCurrentUserDetails":true}"""

        const val CREATION =
            """{"rp":{"id":"localhost","name":"PassKi demo"},"user":{"id":"BAUG","name":"alice","displayName":"alice"},""" +
                """"challenge":"AAECAw","pubKeyCredParams":[{"type":"public-key","alg":-7}]}"""

        const val REQUEST = """{"challenge":"AAECAw","rpId":"localhost"}"""

        val REGISTRATION: String = WebAuthnResponses.registration(
            credentialId = byteArrayOf(1, 2, 3),
            clientDataJson = byteArrayOf(7),
            attestationObject = byteArrayOf(8),
            transports = listOf("internal"),
            attachment = "platform",
        )

        val ASSERTION: String = WebAuthnResponses.authentication(
            credentialId = byteArrayOf(1, 2, 3),
            clientDataJson = byteArrayOf(7),
            authenticatorData = byteArrayOf(8),
            signature = byteArrayOf(9),
            userHandle = byteArrayOf(4, 5, 6),
            attachment = "platform",
        )
    }
}
