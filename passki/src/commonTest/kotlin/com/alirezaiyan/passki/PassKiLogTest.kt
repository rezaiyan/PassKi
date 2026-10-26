package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.LoggingPasskeys
import com.alirezaiyan.passki.internal.PasskiLog
import com.alirezaiyan.passki.internal.WebAuthnOptions
import com.alirezaiyan.passki.internal.passkeyCatching
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class PassKiLogTest {
    private val log = RecordingLogHandler()

    @AfterTest
    fun resetPassKi() {
        PassKi.logLevel = PasskiLogLevel.Error
        PassKi.logHandler = PasskiLogHandler.Platform
    }

    private fun logged(error: PasskeyError? = null): Passkeys {
        PassKi.logHandler = log
        return LoggingPasskeys(StubPasskeys(error))
    }

    @Test
    fun theDefaultLevelIsError() {
        assertEquals(PasskiLogLevel.Error, PassKi.logLevel)
        assertSame(PasskiLogHandler.Platform, PassKi.logHandler)
    }

    @Test
    fun aSetupErrorIsLoggedOnceWithItsCheckFixAndDocs() = runTest {
        val passkeys = logged(PasskeyError.Misconfigured("Digital Asset Links don't list this app.", "Add the app.", "AND-03"))

        passkeys.signIn(REQUEST)

        val entry = log.entries.single()
        assertEquals(PasskiLogLevel.Error, entry.level)
        assertEquals("PassKi", entry.tag)
        assertTrue(entry.message.startsWith("signIn failed: Misconfigured [AND-03]"), entry.message)
        assertTrue("Fix: Add the app." in entry.message, entry.message)
        assertTrue("Docs: https://passki.alirezaiyan.com/checks/AND-03" in entry.message, entry.message)
    }

    @Test
    fun theUserClosingTheSheetIsNotLoggedAtError() = runTest {
        for (error in listOf(PasskeyError.Cancelled, PasskeyError.NoCredentials, PasskeyError.AlreadyRegistered)) {
            logged(error).signIn(REQUEST)
        }
        assertEquals(emptyList(), log.entries)
    }

    @Test
    fun unknownIsLoggedAtErrorWithItsCause() = runTest {
        val cause = IllegalStateException("provider crashed")
        logged(PasskeyError.Unknown("Something broke.", "42", cause)).register(CREATION)

        val entry = log.entries.single()
        assertEquals(PasskiLogLevel.Error, entry.level)
        assertTrue("register failed: Unknown: Something broke. (platform code 42)" in entry.message, entry.message)
        assertSame(cause, entry.throwable)
    }

    @Test
    fun noneLogsNothing() = runTest {
        val passkeys = logged(PasskeyError.Unknown("Something broke.", null))
        PassKi.logLevel = PasskiLogLevel.None

        passkeys.signIn(REQUEST)
        passkeys.signals.unknownCredential("example.com", "Y3JlZA")

        assertEquals(emptyList(), log.entries)
    }

    @Test
    fun changingTheLevelAndHandlerTakesEffectForTheNextCall() = runTest {
        val passkeys = logged(PasskeyError.Cancelled)
        passkeys.signIn(REQUEST)
        assertEquals(emptyList(), log.entries)

        PassKi.logLevel = PasskiLogLevel.Info
        passkeys.signIn(REQUEST)
        assertEquals(listOf(PasskiLogLevel.Info), log.entries.map { it.level })

        val other = RecordingLogHandler()
        PassKi.logHandler = other
        passkeys.signIn(REQUEST)
        assertEquals(1, log.entries.size)
        assertEquals(1, other.entries.size)
    }

    @Test
    fun debugTracesTheStartAndOutcomeOfACall() = runTest {
        val passkeys = logged()
        PassKi.logLevel = PasskiLogLevel.Debug

        passkeys.signIn(REQUEST, SignInMode.ImmediateOnly)

        assertEquals(
            listOf(
                "signIn started: mode=ImmediateOnly, needs=immediateSignIn, rpId=example.com, " +
                    "options=<${REQUEST.length} chars>, extensions=[prf]",
                "signIn succeeded: credential=Y3JlZGVu…, json=<2 chars>",
            ),
            log.entries.map { it.message },
        )
        assertEquals(listOf(PasskiLogLevel.Debug, PasskiLogLevel.Info), log.entries.map { it.level })
    }

    @Test
    fun debugShowsWhichPlatformErrorAFailureWasMappedFrom() = runTest {
        PassKi.logHandler = log
        PassKi.logLevel = PasskiLogLevel.Debug

        passkeyCatching({ PasskeyError.Cancelled }) { throw IllegalStateException("user closed it") }

        assertEquals("platform error IllegalStateException: user closed it -> Cancelled", log.entries.single().message)
    }

    @Test
    fun interruptedAndNotSupportedAreWarnings() {
        assertEquals(PasskiLogLevel.Warn, PasskiLog.levelOf(PasskeyError.Interrupted))
        assertEquals(PasskiLogLevel.Warn, PasskiLog.levelOf(PasskeyError.NotSupported("old OS")))
        assertEquals(PasskiLogLevel.Error, PasskiLog.levelOf(PasskeyError.InvalidOptions("bad")))
    }

    @Test
    fun skippedSignalsAreLoggedAtInfoOnly() = runTest {
        val passkeys = logged()
        passkeys.signals.unknownCredential("example.com", "Y3JlZA")
        assertEquals(emptyList(), log.entries)

        PassKi.logLevel = PasskiLogLevel.Info
        passkeys.signals.syncAccount("example.com", "dXNlcg", listOf("Y3JlZA"), "alice", "Alice")
        assertEquals("signals.syncAccount succeeded: skipped (no provider)", log.entries.single().message)
    }

    @Test
    fun credentialIdsAreShortened() {
        assertEquals("abcdefgh…", PasskiLog.shortId("abcdefghijklmnop"))
        assertEquals("abc…", PasskiLog.shortId("abc"))
        assertEquals("<empty>", PasskiLog.shortId(""))
        assertEquals("<12 chars>", PasskiLog.lengthOf("{\"a\":\"bcde\"}"))
    }

    @Test
    fun invalidJsonOptionsDoNotRepeatTheOptionsInTheError() {
        val options = """{"challenge":"c2VjcmV0LWNoYWxsZW5nZQ","rpId":"example.com",}"""
        try {
            WebAuthnOptions.parseRequest(options)
            fail("expected InvalidOptions")
        } catch (e: PasskeyException) {
            assertTrue(e.error is PasskeyError.InvalidOptions, e.error.toString())
            assertFalse("c2VjcmV0" in e.error.message, e.error.message)
        }
    }

    @Test
    fun platformLogLinesNameTheTagAndLevel() {
        assertEquals("PassKi E signIn failed", platformLogLine(PasskiLogLevel.Error, "PassKi", "signIn failed", null))
        val line = platformLogLine(PasskiLogLevel.Debug, "PassKi", "x", IllegalStateException("boom"))
        assertTrue(line.startsWith("PassKi D x\n"), line)
        assertTrue("boom" in line, line)
    }

    private companion object {
        const val REQUEST = """{"publicKey":{"challenge":"Y2hhbGxlbmdl","rpId":"example.com","extensions":{"prf":{}}}}"""
        const val CREATION = """{"challenge":"Y2hhbGxlbmdl","rp":{"id":"example.com","name":"Example"}}"""
    }
}

class RecordingLogHandler : PasskiLogHandler {
    class Entry(val level: PasskiLogLevel, val tag: String, val message: String, val throwable: Throwable?)

    val entries: MutableList<Entry> = mutableListOf()

    override fun log(level: PasskiLogLevel, tag: String, message: String, throwable: Throwable?) {
        entries += Entry(level, tag, message, throwable)
    }
}

/** Fails every ceremony with [error], or succeeds with fixed results; signals are always skipped. */
private class StubPasskeys(private val error: PasskeyError?) : Passkeys {
    private val assertion = PasskeyAssertion(json = "{}", credentialId = "Y3JlZGVudGlhbA", userHandle = null, rpId = "example.com")

    private fun <T> outcome(value: T): PasskeyResult<T> = error?.let { PasskeyResult.Failure(it) } ?: PasskeyResult.Success(value)

    override suspend fun capabilities(): PasskeyCapabilities = throw UnsupportedOperationException()

    override suspend fun register(optionsJson: String): PasskeyResult<PasskeyRegistration> =
        outcome(PasskeyRegistration("{}", "Y3JlZGVudGlhbA", emptyList(), null, rpId = "example.com"))

    override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> = outcome(assertion)

    override suspend fun signInOrPassword(optionsJson: String): PasskeyResult<SignInCredential> =
        outcome(SignInCredential.Passkey(assertion))

    override suspend fun upgrade(optionsJson: String): PasskeyResult<PasskeyRegistration> = register(optionsJson)

    override val signals: PasskeySignals = object : PasskeySignals {
        private val skipped = PasskeyResult.Success(SignalOutcome.Skipped("no provider"))

        override suspend fun unknownCredential(rpId: String, credentialId: String) = skipped

        override suspend fun acceptedCredentials(rpId: String, userHandle: String, credentialIds: List<String>) = skipped

        override suspend fun currentUserDetails(rpId: String, userHandle: String, name: String, displayName: String) =
            skipped
    }
}
