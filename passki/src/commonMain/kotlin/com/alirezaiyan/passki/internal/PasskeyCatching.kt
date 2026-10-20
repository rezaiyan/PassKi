package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import com.alirezaiyan.passki.PasskeyResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block]; typed [PasskeyException]s keep their error, other throwables go through [mapError].
 * Coroutine cancellation is never swallowed.
 */
internal inline fun <T> passkeyCatching(
    mapError: (Throwable) -> PasskeyError,
    block: () -> T,
): PasskeyResult<T> = try {
    PasskeyResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: PasskeyException) {
    PasskeyResult.Failure(e.error)
} catch (e: Throwable) {
    PasskeyResult.Failure(mapError(e))
}
