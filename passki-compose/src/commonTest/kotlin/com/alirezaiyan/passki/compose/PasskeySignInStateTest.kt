package com.alirezaiyan.passki.compose

import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.getOrThrow
import com.alirezaiyan.passki.test.FakePasskeys
import com.alirezaiyan.passki.test.TestOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PasskeySignInStateTest {
    private val rpId = "passki.example"
    private val fake = FakePasskeys(origin = "https://$rpId")

    /** Delegates to [fake] but holds every signIn open until [gate] completes; counts calls. */
    private inner class GatedPasskeys : Passkeys by fake {
        var gate = CompletableDeferred<Unit>()
        var signInCalls = 0

        override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> {
            signInCalls++
            gate.await()
            return fake.signIn(optionsJson, mode)
        }
    }

    private fun state(passkeys: Passkeys = fake, mode: SignInMode = SignInMode.Modal) =
        PasskeySignInState(passkeys, mode) { TestOptions.request(rpId) }

    @Test
    fun successReturnsAssertionAndLeavesNoError() = runTest {
        val registration = fake.register(TestOptions.creation(rpId)).getOrThrow()
        val state = state()

        val result = state.signIn()

        assertEquals(registration.credentialId, assertIs<PasskeyResult.Success<PasskeyAssertion>>(result).value.credentialId)
        assertNull(state.lastError)
        assertFalse(state.isRunning)
    }

    @Test
    fun cancelledIsReturnedButNotStoredAsLastError() = runTest {
        fake.failNextWith(PasskeyError.Cancelled)
        val state = state()

        assertEquals(PasskeyResult.Failure(PasskeyError.Cancelled), state.signIn())
        assertNull(state.lastError)
    }

    @Test
    fun misconfiguredIsStoredAsLastError() = runTest {
        val error = PasskeyError.Misconfigured("no asset links", "publish assetlinks.json")
        fake.failNextWith(error)
        val state = state()

        assertEquals(PasskeyResult.Failure(error), state.signIn())
        assertEquals(error, state.lastError)
    }

    @Test
    fun lastErrorIsClearedWhenANewSignInStarts() = runTest {
        fake.register(TestOptions.creation(rpId)).getOrThrow()
        fake.failNextWith(PasskeyError.Misconfigured("no asset links", "publish assetlinks.json"))
        val passkeys = GatedPasskeys()
        val state = state(passkeys)
        passkeys.gate.complete(Unit)
        state.signIn()
        assertIs<PasskeyError.Misconfigured>(state.lastError)

        passkeys.gate = CompletableDeferred()
        val retry = async { state.signIn() }
        runCurrent()
        assertNull(state.lastError)
        passkeys.gate.complete(Unit)
        assertIs<PasskeyResult.Success<PasskeyAssertion>>(retry.await())
    }

    @Test
    fun isRunningIsTrueOnlyWhileTheRequestIsInFlight() = runTest {
        fake.register(TestOptions.creation(rpId)).getOrThrow()
        val passkeys = GatedPasskeys()
        val state = state(passkeys)
        assertFalse(state.isRunning)

        val call = async { state.signIn() }
        runCurrent()
        assertTrue(state.isRunning)

        passkeys.gate.complete(Unit)
        assertIs<PasskeyResult.Success<PasskeyAssertion>>(call.await())
        assertFalse(state.isRunning)
    }

    @Test
    fun secondCallWhileRunningJoinsTheInFlightRequest() = runTest {
        fake.register(TestOptions.creation(rpId)).getOrThrow()
        val passkeys = GatedPasskeys()
        val state = state(passkeys)

        val first = async { state.signIn() }
        val second = async { state.signIn() }
        runCurrent()
        assertEquals(1, passkeys.signInCalls)

        passkeys.gate.complete(Unit)
        assertSame(first.await(), second.await())
        assertEquals(1, passkeys.signInCalls)
    }

    @Test
    fun cancellingTheOwnerReportsCancelledToJoinedCallers() = runTest {
        val passkeys = GatedPasskeys()
        val state = state(passkeys)

        val owner = async { state.signIn() }
        runCurrent()
        val joiner = async { state.signIn() }
        runCurrent()
        owner.cancel()

        assertEquals(PasskeyResult.Failure(PasskeyError.Cancelled), joiner.await())
        assertFalse(state.isRunning)
        assertNull(state.lastError)
    }

    @Test
    fun requestOptionsFailureBecomesUnknownError() = runTest {
        val state = PasskeySignInState(fake, SignInMode.Modal) { error("server down") }

        val result = state.signIn()

        val error = assertIs<PasskeyError.Unknown>(assertIs<PasskeyResult.Failure>(result).error)
        assertEquals("server down", error.cause?.message)
        assertEquals(error, state.lastError)
        assertFalse(state.isRunning)
    }

    @Test
    fun modeIsPassedToPasskeys() = runTest {
        fake.immediateSignIn = false
        val state = state(mode = SignInMode.ImmediateOnly)

        assertIs<PasskeyError.NotSupported>(assertIs<PasskeyResult.Failure>(state.signIn()).error)
    }
}
