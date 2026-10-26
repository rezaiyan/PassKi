package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Lets one modal passkey request run at a time. A modal request (a sheet, or a silent create) that starts while
 * another one is pending fails with [PasskeyError.Interrupted] instead of competing with it; autofill requests wait
 * with [awaitIdle]. Safe to use from any thread.
 *
 * [shared] is the process-wide gate the platform implementations use: a screen's sign-in button and its username
 * field usually hold different [com.alirezaiyan.passki.Passkeys] instances.
 */
internal class ModalGate {
    private val held = MutableStateFlow(false)

    /** True when no modal request is pending. */
    val isIdle: Boolean get() = !held.value

    /** Starts a modal request: returns the idempotent call that ends it, or null if another one is pending. */
    fun tryBegin(): (() -> Unit)? {
        if (!held.compareAndSet(expect = false, update = true)) return null
        val ended = MutableStateFlow(false)
        return { if (ended.compareAndSet(expect = false, update = true)) held.value = false }
    }

    /** Suspends until no modal request is pending. */
    suspend fun awaitIdle() {
        held.first { !it }
    }

    companion object {
        val shared: ModalGate = ModalGate()
    }
}

/** Runs [block] as the only modal request, or throws [PasskeyException] with [PasskeyError.Interrupted]. */
internal inline fun <T> ModalGate.exclusive(block: () -> T): T {
    val end = tryBegin() ?: throw PasskeyException(PasskeyError.Interrupted)
    try {
        return block()
    } finally {
        end()
    }
}
