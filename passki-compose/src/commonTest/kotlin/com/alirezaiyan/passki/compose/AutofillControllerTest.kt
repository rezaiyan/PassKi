package com.alirezaiyan.passki.compose

import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import com.alirezaiyan.passki.compose.internal.AutofillController
import com.alirezaiyan.passki.compose.internal.AutofillSignIn
import com.alirezaiyan.passki.compose.internal.NoAutofillSignIn
import com.alirezaiyan.passki.compose.internal.PlainAutofillSignIn
import com.alirezaiyan.passki.getOrThrow
import com.alirezaiyan.passki.test.FakePasskeys
import com.alirezaiyan.passki.test.TestOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AutofillControllerTest {
    private val rpId = "passki.example"
    private val fake = FakePasskeys(origin = "https://$rpId")

    /**
     * Every autofill call suspends until the test completes its deferred in [calls] (like a real autofill offer
     * waiting for the user). Records modes and cancellations.
     */
    private inner class GatedPasskeys : Passkeys by fake {
        val calls = mutableListOf<CompletableDeferred<PasskeyResult<PasskeyAssertion>>>()
        val modes = mutableListOf<SignInMode>()
        var cancellations = 0

        /** When set, calls return this at once instead of waiting. */
        var immediate: PasskeyResult<PasskeyAssertion>? = null

        override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> {
            modes += mode
            immediate?.let { calls += CompletableDeferred(it); return it }
            val call = CompletableDeferred<PasskeyResult<PasskeyAssertion>>()
            calls += call
            try {
                return call.await()
            } catch (e: Throwable) {
                cancellations++
                throw e
            }
        }
    }

    private class Recorder {
        val signedIn = mutableListOf<PasskeyAssertion>()
        val firstSignIn = CompletableDeferred<PasskeyAssertion>()
        val errors = mutableListOf<PasskeyError>()
    }

    private fun TestScope.controller(
        passkeys: Passkeys,
        recorder: Recorder,
        autofill: AutofillSignIn = PlainAutofillSignIn,
        requestOptions: suspend () -> String = { TestOptions.request(rpId) },
    ) = AutofillController(
        passkeys = passkeys,
        requestOptions = requestOptions,
        onSignedIn = {
            recorder.signedIn += it
            recorder.firstSignIn.complete(it)
        },
        onError = { recorder.errors += it },
        timeSource = testTimeSource,
        autofill = autofill,
    )

    private suspend fun assertion(): PasskeyAssertion {
        fake.register(TestOptions.creation(rpId)).getOrThrow()
        return fake.signIn(TestOptions.request(rpId)).getOrThrow()
    }

    @Test
    fun noAutofillCapabilityNeverStartsSignIn() = runTest {
        fake.autofillSupported = false
        val passkeys = GatedPasskeys()
        val controller = controller(passkeys, Recorder())
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        advanceTimeBy(2_000)
        controller.onTap()
        runCurrent()

        assertEquals(0, passkeys.calls.size)
        job.cancel()
    }

    @Test
    fun fieldWithoutAutofillNeverFetchesOptionsOrSignsIn() = runTest {
        val passkeys = GatedPasskeys()
        var fetches = 0
        val controller = controller(passkeys, Recorder(), requestOptions = { fetches++; TestOptions.request(rpId) }, autofill = NoAutofillSignIn)
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(0, fetches)
        assertEquals(0, passkeys.calls.size)
        job.cancel()
    }

    @Test
    fun focusStartsAutofillAndSuccessIsDeliveredOnce() = runTest {
        val passkeys = GatedPasskeys()
        val recorder = Recorder()
        val controller = controller(passkeys, recorder)
        val job = launch { controller.run() }
        runCurrent()
        assertEquals(0, passkeys.calls.size, "not focused yet")

        controller.onFocusChanged(true)
        runCurrent()
        assertEquals(listOf(SignInMode.Autofill), passkeys.modes)

        val assertion = assertion()
        passkeys.calls.single().complete(PasskeyResult.Success(assertion))
        advanceTimeBy(5_000)

        assertEquals(listOf(assertion), recorder.signedIn)
        assertEquals(1, passkeys.calls.size, "no new offer after success until the next focus or tap")
        assertTrue(recorder.errors.isEmpty())
        job.cancel()
    }

    @Test
    fun blurCancelsTheOpenOffer() = runTest {
        val passkeys = GatedPasskeys()
        val controller = controller(passkeys, Recorder())
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        runCurrent()
        controller.onFocusChanged(false)
        runCurrent()

        assertEquals(1, passkeys.cancellations)
        advanceTimeBy(5_000)
        assertEquals(1, passkeys.calls.size)
        job.cancel()
    }

    @Test
    fun disposeCancelsTheOpenOffer() = runTest {
        val passkeys = GatedPasskeys()
        val controller = controller(passkeys, Recorder())
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        runCurrent()
        job.cancel()
        runCurrent()

        assertEquals(1, passkeys.cancellations)
    }

    @Test
    fun tapOnFocusedFieldReArms() = runTest {
        val passkeys = GatedPasskeys()
        val controller = controller(passkeys, Recorder())
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        runCurrent()
        advanceTimeBy(1_000)
        controller.onTap()
        runCurrent()

        assertEquals(2, passkeys.calls.size)
        assertEquals(1, passkeys.cancellations, "the first offer is withdrawn")
        job.cancel()
    }

    @Test
    fun tapThatFocusesTheFieldDoesNotRestart() = runTest {
        val passkeys = GatedPasskeys()
        val controller = controller(passkeys, Recorder())
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        controller.onTap()
        runCurrent()
        advanceTimeBy(100)
        controller.onTap()
        runCurrent()

        assertEquals(1, passkeys.calls.size)
        assertEquals(0, passkeys.cancellations)
        job.cancel()
    }

    @Test
    fun tapWhileUnfocusedDoesNothing() = runTest {
        val passkeys = GatedPasskeys()
        val controller = controller(passkeys, Recorder())
        val job = launch { controller.run() }

        controller.onTap()
        advanceTimeBy(2_000)

        assertEquals(0, passkeys.calls.size)
        job.cancel()
    }

    @Test
    fun dismissalKeepsWaitingWithBackoff() = runTest {
        val passkeys = GatedPasskeys().apply { immediate = PasskeyResult.Failure(PasskeyError.Cancelled) }
        val recorder = Recorder()
        val controller = controller(passkeys, recorder)
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        advanceTimeBy(2_001)

        // Calls at 0, 500, 1000, 1500, 2000 ms: no restart storm.
        assertEquals(5, passkeys.calls.size)
        assertTrue(recorder.errors.isEmpty(), "dismissals are not errors")
        job.cancel()
    }

    @Test
    fun fastTapsAfterDismissalsAreThrottled() = runTest {
        val passkeys = GatedPasskeys()
        val controller = controller(passkeys, Recorder())
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        runCurrent()
        repeat(10) {
            advanceTimeBy(50)
            controller.onTap()
            runCurrent()
        }

        // 500 ms of taps every 50 ms: at most the initial offer plus one re-arm.
        assertTrue(passkeys.calls.size <= 2, "calls=${passkeys.calls.size}")
        job.cancel()
    }

    @Test
    fun notSupportedReportsOnceAndStops() = runTest {
        val passkeys = GatedPasskeys().apply { immediate = PasskeyResult.Failure(PasskeyError.NotSupported("no")) }
        val recorder = Recorder()
        val controller = controller(passkeys, recorder)
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        advanceTimeBy(2_000)
        controller.onTap()
        controller.onFocusChanged(false)
        controller.onFocusChanged(true)
        advanceTimeBy(2_000)

        assertEquals(1, passkeys.calls.size)
        assertIs<PasskeyError.NotSupported>(recorder.errors.single())
        job.cancel()
    }

    @Test
    fun misconfiguredReportsOnceAndStops() = runTest {
        val passkeys = GatedPasskeys().apply {
            immediate = PasskeyResult.Failure(PasskeyError.Misconfigured("no association", fixHint = "AASA"))
        }
        val recorder = Recorder()
        val controller = controller(passkeys, recorder)
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        advanceTimeBy(2_000)
        controller.onFocusChanged(false)
        controller.onFocusChanged(true)
        advanceTimeBy(2_000)

        assertEquals(1, passkeys.calls.size)
        assertIs<PasskeyError.Misconfigured>(recorder.errors.single())
        job.cancel()
    }

    @Test
    fun failingRequestOptionsReportsAndWaitsForNextArm() = runTest {
        val passkeys = GatedPasskeys()
        val recorder = Recorder()
        var fail = true
        val controller = controller(passkeys, recorder) {
            if (fail) error("offline")
            TestOptions.request(rpId)
        }
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        advanceTimeBy(2_000)
        assertEquals(0, passkeys.calls.size)
        assertIs<PasskeyError.Unknown>(recorder.errors.single())

        fail = false
        controller.onTap()
        runCurrent()
        assertEquals(1, passkeys.calls.size, "a tap retries")
        job.cancel()
    }

    /** Counts calls into [FakePasskeys] itself (no gating): its Autofill waits when there is nothing to offer. */
    private inner class CountingFake : Passkeys by fake {
        var calls = 0

        override suspend fun signIn(optionsJson: String, mode: SignInMode): PasskeyResult<PasskeyAssertion> {
            calls++
            return fake.signIn(optionsJson, mode)
        }
    }

    @Test
    fun fakeWithoutPasskeyKeepsOneOfferOpenWithoutSpinning() = runTest {
        val passkeys = CountingFake()
        val recorder = Recorder()
        val controller = controller(passkeys, recorder)
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        advanceTimeBy(60_000)

        assertEquals(1, passkeys.calls, "one open offer, no retry loop")
        assertTrue(recorder.errors.isEmpty())
        assertTrue(recorder.signedIn.isEmpty())
        job.cancel()
    }

    @Test
    fun fakeWithPasskeySignsInLikePickingTheSuggestion() = runTest {
        val registration = fake.register(TestOptions.creation(rpId)).getOrThrow()
        val passkeys = CountingFake()
        val recorder = Recorder()
        val controller = controller(passkeys, recorder)
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        // FakePasskeys signs with real crypto, which on JS completes on the event loop rather than in virtual time:
        // wait for the sign-in itself instead of advancing the clock.
        recorder.firstSignIn.await()

        assertEquals(registration.credentialId, recorder.signedIn.single().credentialId)
        assertEquals(1, passkeys.calls)
        job.cancel()
    }

    @Test
    fun platformAutofillCallIsUsedInsteadOfPlainSignIn() = runTest {
        // On Android, PasskeyUsernameField passes a call anchored to its own View (dialogs are separate windows).
        val passkeys = GatedPasskeys()
        val anchored = mutableListOf<Pair<Passkeys, String>>()
        val controller = AutofillController(
            passkeys = passkeys,
            requestOptions = { "options" },
            onSignedIn = {},
            onError = {},
            timeSource = testTimeSource,
            autofill = { p, options ->
                anchored += p to options
                awaitCancellation()
            },
        )
        val job = launch { controller.run() }

        controller.onFocusChanged(true)
        runCurrent()

        assertEquals(listOf<Pair<Passkeys, String>>(passkeys to "options"), anchored)
        assertEquals(0, passkeys.calls.size, "the plain signIn(…, Autofill) is not used")
        job.cancel()
    }
}
