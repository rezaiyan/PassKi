package com.alirezaiyan.passki.compose.internal

import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One `signIn(options, SignInMode.Autofill)` call. Android anchors it to the field's own View, see
 * [com.alirezaiyan.passki.compose.rememberAutofillSignIn].
 */
internal typealias AutofillSignIn = suspend (passkeys: Passkeys, optionsJson: String) -> PasskeyResult<PasskeyAssertion>

internal val PlainAutofillSignIn: AutofillSignIn = { passkeys, options -> passkeys.signIn(options, SignInMode.Autofill) }

/**
 * Decides when a username field offers passkeys through [SignInMode.Autofill]. Pure logic, no UI.
 *
 * Feed it [onFocusChanged] and [onTap] from the field and keep [run] running for as long as the field is composed.
 * - While the field is focused (and the platform supports autofill) it keeps one `signIn(…, Autofill)` call open.
 * - A dismissal ([PasskeyError.Cancelled], [PasskeyError.NoCredentials], [PasskeyError.Interrupted]) starts the next
 *   call; consecutive calls start at least [minInterval] apart, so a provider that fails instantly can't spin.
 * - A tap on the already focused field re-arms (cancels the open call and starts a new one). On Android the
 *   suggestion chip is used up after a dismissal and only a new call brings it back. Taps within [minInterval] of
 *   the last arm are ignored, so the tap that focuses the field doesn't restart the call it just started.
 * - Losing focus cancels the open call, which withdraws the offer.
 * - Success calls `onSignedIn` and waits for the next focus or tap.
 * - [PasskeyError.NotSupported], [PasskeyError.Misconfigured] and [PasskeyError.InvalidOptions] call `onError` once and
 *   stop for good: retrying can't fix them. Any other error (for example `requestOptions` throwing) calls `onError`
 *   and waits for the next focus or tap.
 *
 * Each offer is one [autofill] call.
 */
internal class AutofillController(
    private val passkeys: Passkeys,
    private val requestOptions: suspend () -> String,
    private val onSignedIn: (PasskeyAssertion) -> Unit,
    private val onError: (PasskeyError) -> Unit,
    private val minInterval: Duration = 500.milliseconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val autofill: AutofillSignIn = PlainAutofillSignIn,
) {
    private data class Arm(val focused: Boolean, val generation: Int)

    private val arm = MutableStateFlow(Arm(focused = false, generation = 0))
    private var lastArm: TimeMark? = null
    private var lastStart: TimeMark? = null
    private var stopped = false

    /** The field gained or lost focus. */
    fun onFocusChanged(focused: Boolean) {
        arm.update { current ->
            if (current.focused == focused) {
                current
            } else {
                if (focused) lastArm = timeSource.markNow()
                Arm(focused, current.generation + 1)
            }
        }
    }

    /** The user tapped the field (or otherwise asked for the keyboard again). Re-arms if the field is focused. */
    fun onTap() {
        arm.update { current ->
            val recent = lastArm?.let { it.elapsedNow() < minInterval } ?: false
            if (!current.focused || recent) {
                current
            } else {
                lastArm = timeSource.markNow()
                current.copy(generation = current.generation + 1)
            }
        }
    }

    /** Runs until cancelled. Returns at once if the platform has no autofill. */
    suspend fun run() {
        if (stopped || !passkeys.capabilities().autofill) return
        arm.collectLatest { state ->
            if (state.focused && !stopped) offer()
        }
    }

    private suspend fun offer() {
        while (true) {
            throttle()
            when (val result = attempt()) {
                is PasskeyResult.Success -> {
                    onSignedIn(result.value)
                    return
                }
                is PasskeyResult.Failure -> when (val error = result.error) {
                    PasskeyError.Cancelled, PasskeyError.NoCredentials, PasskeyError.Interrupted -> Unit
                    is PasskeyError.NotSupported, is PasskeyError.Misconfigured, is PasskeyError.InvalidOptions -> {
                        stopped = true
                        onError(error)
                        return
                    }
                    else -> {
                        onError(error)
                        return
                    }
                }
            }
        }
    }

    private suspend fun attempt(): PasskeyResult<PasskeyAssertion> {
        val options = try {
            requestOptions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return PasskeyResult.Failure(
                PasskeyError.Unknown(
                    message = "Fetching passkey request options failed: ${e.message ?: e::class.simpleName}",
                    platformCode = null,
                    cause = e,
                ),
            )
        }
        return autofill(passkeys, options)
    }

    private suspend fun throttle() {
        lastStart?.elapsedNow()?.let { elapsed -> if (elapsed < minInterval) delay(minInterval - elapsed) }
        lastStart = timeSource.markNow()
    }
}
