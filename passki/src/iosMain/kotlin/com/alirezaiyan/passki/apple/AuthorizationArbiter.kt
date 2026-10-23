package com.alirezaiyan.passki.apple

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Keeps AutoFill-assisted requests out of the way of modal ones. AuthenticationServices runs one authorization at a
 * time: starting a modal request cancels a pending AutoFill request (code 1001), and an AutoFill request (re)started
 * while a modal sheet is up competes with that sheet. So a modal request first cancels every pending AutoFill
 * request, and an AutoFill request only starts once no modal request is running.
 *
 * One instance is shared by all [IosPasskeys]: a screen's username field and its sign-in button usually hold
 * different ones. Main thread only.
 */
internal class AuthorizationArbiter {
    private val modalRequests = MutableStateFlow(0)
    private val pendingAutoFill = mutableListOf<AutoFillRequest>()

    private class AutoFillRequest(val cancel: () -> Unit)

    /**
     * A modal request is starting: cancels (and forgets) pending AutoFill requests. Returns the idempotent call that
     * ends it.
     */
    fun beginModal(): () -> Unit {
        modalRequests.value += 1
        val cancelled = pendingAutoFill.toList()
        pendingAutoFill.clear()
        cancelled.forEach { it.cancel() }
        var ended = false
        return {
            if (!ended) {
                ended = true
                modalRequests.value -= 1
            }
        }
    }

    /**
     * Suspends until no modal request is running. Rechecked after every resume, so on return no modal request runs;
     * the caller must start its AutoFill request without suspending in between.
     */
    suspend fun awaitNoModal() {
        while (modalRequests.value != 0) modalRequests.first { it == 0 }
    }

    /**
     * An AutoFill request is starting; [cancel] stops it if a modal request begins meanwhile. Returns the idempotent
     * call that unregisters it.
     */
    fun beginAutoFill(cancel: () -> Unit): () -> Unit {
        val request = AutoFillRequest(cancel)
        pendingAutoFill += request
        return { pendingAutoFill.remove(request) }
    }
}
