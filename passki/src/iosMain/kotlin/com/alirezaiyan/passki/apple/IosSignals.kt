package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyResult
import com.alirezaiyan.passki.PasskeySignals
import com.alirezaiyan.passki.apple.swift.passki_callback
import com.alirezaiyan.passki.apple.swift.passki_report_all_accepted
import com.alirezaiyan.passki.apple.swift.passki_report_details
import com.alirezaiyan.passki.apple.swift.passki_report_unknown
import com.alirezaiyan.passki.internal.Base64Url
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/** Status codes reported by the Swift bridge (passki-ios-bridge/PassKiSignals.swift). */
internal object SignalStatus {
    const val OK: Int = 0
    const val NOT_AVAILABLE: Int = 1
    const val ERROR: Int = 2
}

/** Maps a bridge callback to a [PasskeyResult]. */
internal fun signalResult(status: Int, message: String?): PasskeyResult<Unit> = when (status) {
    SignalStatus.OK -> PasskeyResult.Success(Unit)
    SignalStatus.NOT_AVAILABLE ->
        PasskeyResult.Failure(PasskeyError.NotSupported(message ?: "The Signal API requires iOS 26 or newer."))
    else -> PasskeyResult.Failure(PasskeyError.Unknown(message ?: "ASCredentialUpdater failed.", "ASCredentialUpdater"))
}

/** WebAuthn Signal API on iOS 26+, through ASCredentialUpdater (Swift-only) via the C bridge. */
@OptIn(ExperimentalForeignApi::class)
internal object IosSignals : PasskeySignals {
    override suspend fun unknownCredential(rpId: String, credentialId: String): PasskeyResult<Unit> {
        val id = decode(credentialId, "credentialId").getOrElse { return it }
        return call { cb, ctx ->
            memScoped { passki_report_unknown(rpId, bytes(id), id.size, cb, ctx) }
        }
    }

    override suspend fun acceptedCredentials(
        rpId: String,
        userId: String,
        credentialIds: List<String>,
    ): PasskeyResult<Unit> {
        val user = decode(userId, "userId").getOrElse { return it }
        val ids = credentialIds.map { decode(it, "credentialIds").getOrElse { failure -> return failure } }
        return call { cb, ctx ->
            memScoped {
                val pointers = allocArray<CPointerVar<UByteVar>>(ids.size.coerceAtLeast(1))
                val lengths = allocArray<IntVar>(ids.size.coerceAtLeast(1))
                ids.forEachIndexed { i, id ->
                    pointers[i] = bytes(id)
                    lengths[i] = id.size
                }
                passki_report_all_accepted(rpId, bytes(user), user.size, pointers, lengths, ids.size, cb, ctx)
            }
        }
    }

    override suspend fun currentUserDetails(
        rpId: String,
        userId: String,
        name: String,
        displayName: String,
    ): PasskeyResult<Unit> {
        val user = decode(userId, "userId").getOrElse { return it }
        // ASCredentialUpdater only takes one name: the account name the passkey is saved under.
        return call { cb, ctx ->
            memScoped { passki_report_details(rpId, bytes(user), user.size, name, cb, ctx) }
        }
    }

    private fun decode(value: String, field: String): Decoded = try {
        Decoded.Bytes(Base64Url.decode(value))
    } catch (e: IllegalArgumentException) {
        Decoded.Invalid(PasskeyResult.Failure(PasskeyError.InvalidOptions("$field must be base64url: ${e.message}")))
    }

    private sealed interface Decoded {
        class Bytes(val value: ByteArray) : Decoded
        class Invalid(val failure: PasskeyResult.Failure) : Decoded
    }

    private inline fun Decoded.getOrElse(onInvalid: (PasskeyResult.Failure) -> Nothing): ByteArray = when (this) {
        is Decoded.Bytes -> value
        is Decoded.Invalid -> onInvalid(failure)
    }

    private fun MemScope.bytes(value: ByteArray): CPointer<UByteVar> =
        allocArrayOf(value).reinterpret()

    private suspend fun call(start: (passki_callback, COpaquePointer) -> Unit): PasskeyResult<Unit> =
        suspendCancellableCoroutine { continuation ->
            // The Swift side always calls back exactly once (resume after cancellation is ignored); the ref is
            // disposed in the callback.
            val ref = StableRef.create(continuation)
            start(callback, ref.asCPointer())
        }

    private val callback: passki_callback = staticCFunction { status, message, ctx ->
        val ref = ctx!!.asStableRef<Continuation<PasskeyResult<Unit>>>()
        val continuation = ref.get()
        ref.dispose()
        continuation.resume(signalResult(status, message?.toKString()))
    }
}
