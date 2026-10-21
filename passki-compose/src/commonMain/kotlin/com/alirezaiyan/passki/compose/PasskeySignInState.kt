package com.alirezaiyan.passki.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.alirezaiyan.passki.PasskeyAssertion
import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.Passkeys
import com.alirezaiyan.passki.SignInMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Headless passkey sign-in state for custom UI. Create it with [rememberPasskeySignIn].
 *
 * Call [signIn] from a coroutine (for example one launched from `rememberCoroutineScope()` in a click handler)
 * and drive your UI from [isRunning] and [lastError], which are Compose state and trigger recomposition.
 *
 * Double taps are safe: a [signIn] call made while another one is running does not start a second system
 * sheet; it waits for the running request and returns the same result.
 */
@Stable
public class PasskeySignInState internal constructor(
    private val passkeys: Passkeys,
    private val mode: SignInMode,
    private val requestOptions: suspend () -> String,
) {
    /** True while a sign-in request (fetching options plus the system sheet) is in flight. */
    public var isRunning: Boolean by mutableStateOf(false)
        private set

    /**
     * The error of the most recent finished [signIn], or null if it succeeded or the user cancelled.
     *
     * [PasskeyError.Cancelled] is never stored here: dismissing the sheet is not something to show an error for.
     * Cleared when a new [signIn] starts.
     */
    public var lastError: PasskeyError? by mutableStateOf(null)
        private set

    private val lock = Mutex()
    private var inFlight: CompletableDeferred<PasskeyResult<PasskeyAssertion>>? = null

    /**
     * Fetches the request options from your server, then shows the system passkey sheet.
     *
     * If a sign-in is already running, this joins it and returns its result instead of starting another.
     * If the coroutine that started the running request is cancelled, joined callers receive
     * [PasskeyError.Cancelled].
     *
     * An exception thrown by the `requestOptions` lambda (for example a network failure) is returned as
     * [PasskeyError.Unknown] with the exception as [PasskeyError.Unknown.cause]; it is not rethrown.
     *
     * @return the assertion to send to your server, or a typed failure.
     */
    public suspend fun signIn(): PasskeyResult<PasskeyAssertion> {
        val (request, owner) = lock.withLock {
            inFlight?.let { return@withLock it to false }
            val request = CompletableDeferred<PasskeyResult<PasskeyAssertion>>()
            inFlight = request
            lastError = null
            isRunning = true
            request to true
        }
        if (!owner) return request.await()

        val result = try {
            passkeys.signIn(requestOptions(), mode)
        } catch (e: CancellationException) {
            finish(request, PasskeyResult.Failure(PasskeyError.Cancelled))
            throw e
        } catch (e: Throwable) {
            PasskeyResult.Failure(
                PasskeyError.Unknown(
                    message = "Fetching passkey request options failed: ${e.message ?: e::class.simpleName}",
                    platformCode = null,
                    cause = e,
                ),
            )
        }
        finish(request, result)
        return result
    }

    private fun finish(
        request: CompletableDeferred<PasskeyResult<PasskeyAssertion>>,
        result: PasskeyResult<PasskeyAssertion>,
    ) {
        val error = (result as? PasskeyResult.Failure)?.error
        lastError = error?.takeUnless { it == PasskeyError.Cancelled }
        inFlight = null
        isRunning = false
        request.complete(result)
    }
}

/**
 * Remembers a [PasskeySignInState] for a sign-in button or custom passkey UI.
 *
 * @param passkeys the passkeys implementation; defaults to the platform one from [rememberPasskeys].
 *   Pass a fake (for example passki-test's `FakePasskeys`) in previews and tests.
 * @param mode how the system sheet presents itself.
 * @param requestOptions fetches fresh WebAuthn request options JSON from your server. Called once per sign-in
 *   attempt; the latest lambda is always used, so it may capture changing values such as a typed username.
 */
@Composable
public fun rememberPasskeySignIn(
    passkeys: Passkeys = rememberPasskeys(),
    mode: SignInMode = SignInMode.Modal,
    requestOptions: suspend () -> String,
): PasskeySignInState {
    val currentRequestOptions by rememberUpdatedState(requestOptions)
    return remember(passkeys, mode) {
        PasskeySignInState(passkeys, mode) { currentRequestOptions() }
    }
}
