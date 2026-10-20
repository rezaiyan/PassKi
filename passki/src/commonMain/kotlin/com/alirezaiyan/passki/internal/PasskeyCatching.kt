package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block]; typed [PasskeyException]s keep their error, other throwables go through [mapError].
 * Coroutine cancellation is never swallowed, and neither are [Error]s (e.g. out-of-memory).
 * Platform-level cancellation that is not coroutine cancellation (e.g. a cancelled platform future)
 * surfaces as CancellationException and is rethrown.
 */
internal inline fun <T> passkeyCatching(
    mapError: (Throwable) -> PasskeyError,
    block: () -> T,
): PasskeyResult<T> = try {
    PasskeyResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Error) {
    throw e
} catch (e: PasskeyException) {
    PasskeyResult.Failure(e.error)
} catch (e: Throwable) {
    PasskeyResult.Failure(mapError(e))
}
