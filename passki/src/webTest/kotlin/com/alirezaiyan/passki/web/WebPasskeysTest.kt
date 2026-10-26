package com.alirezaiyan.passki.web

import com.alirezaiyan.passki.PassKi
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskiLogHandler
import com.alirezaiyan.passki.PasskiLogLevel
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.SignalOutcome
import com.alirezaiyan.passki.internal.WebAuthnResponses
import com.alirezaiyan.passki.internal.LoggingPasskeys
import com.alirezaiyan.passki.internal.ModalGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WebPasskeysTest {
    /** Answers each op from [answers]; a missing op fails like a browser without that API. */
    private class FakeBrowser(
        private val capabilities: String = ALL_CAPABILITIES,
        private val answers: Map<String, () -> String> = emptyMap(),
    ) : WebAuthnBridge {
        val calls = mutableListOf<Call>()

        override fun pageHost(): String = PAGE_HOST

        override suspend fun call(op: String, json: String, mediation: String?): String {
            if (op == "capabilities") return capabilities
            calls += Call(op, json, mediation)
            val answer = answers[op] ?: throw WebAuthnException("NotSupportedError", "no $op")
            return answer()
        }
    }

    private data class Call(val op: String, val json: String, val mediation: String?)

    /** Keeps every create / get pending until [answer] completes, like a browser showing its sheet. */
    private class SheetBrowser : WebAuthnBridge {
        val calls = mutableListOf<Call>()

        override fun pageHost(): String = PAGE_HOST
        val answer = CompletableDeferred<String>()

        override suspend fun call(op: String, json: String, mediation: String?): String {
            if (op == "capabilities") return ALL_CAPABILITIES
            calls += Call(op, json, mediation)
            return answer.await()
        }
    }

    @Test
    fun registerSendsNormalizedOptionsAndParsesTheResponse() = runTest {
        val browser = FakeBrowser(answers = mapOf("create" to { REGISTRATION }))

        val result = WebPasskeys(browser).register("""{"publicKey":$CREATION}""")

        val registration = assertIs<PasskeyResult.Success<*>>(result).value as com.alirezaiyan.passki.PasskeyRegistration
        assertEquals("AQID", registration.credentialId)
        assertEquals(listOf("internal"), registration.transports)
        assertEquals("localhost", registration.rpId)
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

        val registration = assertIs<PasskeyResult.Success<*>>(result).value as com.alirezaiyan.passki.PasskeyRegistration
        assertEquals(PAGE_HOST, registration.rpId)
    }

    @Test
    fun signInWithoutRpIdReportsThePagesHost() = runTest {
        val browser = FakeBrowser(answers = mapOf("get" to { ASSERTION }))

        val result = WebPasskeys(browser).signIn("""{"challenge":"AAECAw"}""")

        val assertion = assertIs<PasskeyResult.Success<*>>(result).value as com.alirezaiyan.passki.PasskeyAssertion
        assertEquals(PAGE_HOST, assertion.rpId)
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
        assertEquals("localhost", assertion.rpId)
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
    fun immediateOnlyThatTheBrowserRejectsAsAnUnknownMediationIsNotSupportedFromThenOn() = runTest {
        // Chromium can report immediateGet and still reject the enum value.
        val browser = FakeBrowser(answers = mapOf("get" to failing("TypeError", UNKNOWN_IMMEDIATE)))
        val passkeys = WebPasskeys(browser)

        val first = passkeys.signIn(REQUEST, SignInMode.ImmediateOnly)

        val error = assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(first).error)
        assertTrue("CredentialMediationRequirement" in error.message, error.message)
        assertFalse(passkeys.capabilities().immediateSignIn)
        val second = passkeys.signIn(REQUEST, SignInMode.ImmediateOnly)
        assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(second).error)
        assertEquals(1, browser.calls.size, "The second attempt never reaches the browser.")
    }

    @Test
    fun immediateOnlyWithOptionsTheBrowserRejectsIsStillInvalidOptions() = runTest {
        val browser = FakeBrowser(
            answers = mapOf(
                "get" to failing("TypeError", "Failed to read the 'challenge' property from 'PublicKeyCredentialRequestOptions': Required member is undefined."),
            ),
        )
        val passkeys = WebPasskeys(browser)

        val result = passkeys.signIn(REQUEST, SignInMode.ImmediateOnly)

        assertIs<PasskeyError.InvalidOptions>(assertIs<PasskeyResult.Failure>(result).error)
        assertTrue(passkeys.capabilities().immediateSignIn)
    }

    @Test
    fun modalSignInIsUnaffectedByAnImmediateRejection() = runTest {
        var immediateFails = true
        val browser = FakeBrowser(
            answers = mapOf(
                "get" to { if (immediateFails) throw WebAuthnException("TypeError", UNKNOWN_IMMEDIATE) else ASSERTION },
            ),
        )
        val passkeys = WebPasskeys(browser)

        passkeys.signIn(REQUEST, SignInMode.ImmediateOnly)
        immediateFails = false
        val result = passkeys.signIn(REQUEST, SignInMode.Modal)

        assertIs<PasskeyResult.Success<*>>(result)
        assertNull(browser.calls.last().mediation)
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

        assertEquals(SignalOutcome.Applied, assertIs<PasskeyResult.Success<SignalOutcome>>(result).value)
        assertEquals(Call("signalUnknownCredential", """{"rpId":"localhost","credentialId":"AQID"}""", null), browser.calls.single())
    }

    @Test
    fun missingSignalApiIsSkipped() = runTest {
        val result = WebPasskeys(FakeBrowser()).signals.currentUserDetails("localhost", "BAUG", "alice", "Alice")

        val skipped = assertIs<SignalOutcome.Skipped>(assertIs<PasskeyResult.Success<SignalOutcome>>(result).value)
        assertEquals("no signalCurrentUserDetails", skipped.reason)
    }

    @Test
    fun aSecondModalRequestFailsWithInterruptedWhileTheFirstIsShowing() = runTest(timeout = 1.seconds) {
        val browser = SheetBrowser()
        val passkeys = WebPasskeys(browser, ModalGate())
        val first = async { passkeys.register(CREATION) }
        runCurrent()
        for (second in listOf(
            passkeys.register(CREATION),
            passkeys.upgrade(CREATION),
            passkeys.signIn(REQUEST, SignInMode.Modal),
            passkeys.signIn(REQUEST, SignInMode.ImmediateOnly),
        )) {
            assertEquals(PasskeyError.Interrupted, assertIs<PasskeyResult.Failure>(second).error)
        }
        assertEquals(1, browser.calls.size, "refused requests never reach the browser")

        browser.answer.complete(REGISTRATION)
        assertIs<PasskeyResult.Success<*>>(first.await())
    }

    @Test
    fun autofillWaitsForAModalRequestToFinish() = runTest(timeout = 1.seconds) {
        val browser = SheetBrowser()
        val passkeys = WebPasskeys(browser, ModalGate())
        val modal = async { passkeys.register(CREATION) }
        runCurrent()

        val autofill = async { passkeys.signIn(REQUEST, SignInMode.Autofill) }
        runCurrent()
        assertEquals(listOf("create"), browser.calls.map { it.op }, "autofill hasn't started yet")

        browser.answer.complete(REGISTRATION)
        modal.await()
        runCurrent()
        assertEquals(Call("get", browser.calls.last().json, "conditional"), browser.calls.last())
        autofill.cancel()
    }

    @Test
    fun cancellingAModalRequestLetsTheNextOneRun() = runTest(timeout = 1.seconds) {
        val browser = SheetBrowser()
        val passkeys = WebPasskeys(browser, ModalGate())
        val first = async { passkeys.register(CREATION) }
        runCurrent()

        first.cancel()
        assertFailsWith<CancellationException> { first.await() }

        val next = async { passkeys.signIn(REQUEST, SignInMode.Modal) }
        runCurrent()
        assertEquals(2, browser.calls.size, "the next request reached the browser")
        next.cancel()
    }

    @Test
    fun theDebugLogShowsHowABrowserErrorWasMapped() = runTest {
        val lines = mutableListOf<Pair<PasskiLogLevel, String>>()
        PassKi.logLevel = PasskiLogLevel.Debug
        PassKi.logHandler = PasskiLogHandler { level, _, message, _ -> lines += level to message }
        try {
            val browser = FakeBrowser(answers = mapOf("get" to failing("NotAllowedError")))
            LoggingPasskeys(WebPasskeys(browser)).signIn(REQUEST)
        } finally {
            PassKi.logLevel = PasskiLogLevel.Error
            PassKi.logHandler = PasskiLogHandler.Platform
        }

        assertEquals(3, lines.size, lines.toString())
        assertEquals(PasskiLogLevel.Debug to "signIn started: mode=Modal, rpId=localhost, options=<${REQUEST.length} chars>", lines[0])
        assertTrue(lines[1].second.startsWith("platform error WebAuthnException: ") && lines[1].second.endsWith(" -> Cancelled"), lines[1].second)
        assertEquals(PasskiLogLevel.Info to "signIn failed: Cancelled: The user cancelled the passkey request.", lines[2])
    }

    private fun failing(name: String, message: String = "browser said no"): () -> String =
        { throw WebAuthnException(name, message) }

    private companion object {
        const val ALL_CAPABILITIES =
            """{"webauthn":true,"secureContext":true,"conditionalGet":true,"conditionalCreate":true,"immediateGet":true,""" +
                """"signalUnknownCredential":true,"signalAllAcceptedCredentials":true,"signalCurrentUserDetails":true}"""

        const val CREATION =
            """{"rp":{"id":"localhost","name":"PassKi demo"},"user":{"id":"BAUG","name":"alice","displayName":"alice"},""" +
                """"challenge":"AAECAw","pubKeyCredParams":[{"type":"public-key","alg":-7}]}"""

        const val REQUEST = """{"challenge":"AAECAw","rpId":"localhost"}"""

        const val PAGE_HOST = "app.example.com"

        const val UNKNOWN_IMMEDIATE =
            "Failed to read the 'mediation' property from 'CredentialRequestOptions': The provided value 'immediate' " +
                "is not a valid enum value of type CredentialMediationRequirement."

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
