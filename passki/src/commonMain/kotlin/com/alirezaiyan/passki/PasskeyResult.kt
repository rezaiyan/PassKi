package com.alirezaiyan.passki

/**
 * Outcome of a passkey operation: either [Success] with a value or [Failure] with a typed [PasskeyError].
 *
 * Closed: it is always one of these two; a `when` over it needs no `else` branch.
 *
 * @sample com.alirezaiyan.passki.samples.foldIntoYourOwnType
 */
public sealed interface PasskeyResult<out T> {
    /** The operation succeeded; [value] is the result. */
    public data class Success<out T>(public val value: T) : PasskeyResult<T>

    /** The operation failed; [error] says why. */
    public data class Failure(public val error: PasskeyError) : PasskeyResult<Nothing>
}

/** Runs [action] with the value if this is a [PasskeyResult.Success]; returns this result unchanged. */
public inline fun <T> PasskeyResult<T>.onSuccess(action: (T) -> Unit): PasskeyResult<T> {
    if (this is PasskeyResult.Success) action(value)
    return this
}

/** Runs [action] with the error if this is a [PasskeyResult.Failure]; returns this result unchanged. */
public inline fun <T> PasskeyResult<T>.onFailure(action: (PasskeyError) -> Unit): PasskeyResult<T> {
    if (this is PasskeyResult.Failure) action(error)
    return this
}

/** Returns the value on success, or null on failure. */
public fun <T> PasskeyResult<T>.getOrNull(): T? = (this as? PasskeyResult.Success)?.value

/** Returns the value on success, or throws a [PasskeyException] carrying the error. */
public fun <T> PasskeyResult<T>.getOrThrow(): T = when (this) {
    is PasskeyResult.Success -> value
    is PasskeyResult.Failure -> throw PasskeyException(error)
}

/** Transforms the value on success; failures pass through unchanged. */
public inline fun <T, R> PasskeyResult<T>.map(transform: (T) -> R): PasskeyResult<R> = when (this) {
    is PasskeyResult.Success -> PasskeyResult.Success(transform(value))
    is PasskeyResult.Failure -> this
}

/**
 * Returns [onSuccess] of the value or [onFailure] of the error; exactly one of them runs. The usual way to turn a
 * result into your own type:
 * ```kotlin
 * passkeys.signIn(options).fold(
 *     onSuccess = { AuthResult.SignedIn(api.finishSignIn(it.json)) },
 *     onFailure = { if (it.isUserCancellation) AuthResult.Idle else AuthResult.Error(it) },
 * )
 * ```
 */
public inline fun <T, R> PasskeyResult<T>.fold(onSuccess: (T) -> R, onFailure: (PasskeyError) -> R): R = when (this) {
    is PasskeyResult.Success -> onSuccess(value)
    is PasskeyResult.Failure -> onFailure(error)
}

/** Transforms the error on failure, e.g. into another [PasskeyError]; successes pass through unchanged. */
public inline fun <T> PasskeyResult<T>.mapError(transform: (PasskeyError) -> PasskeyError): PasskeyResult<T> =
    when (this) {
        is PasskeyResult.Success -> this
        is PasskeyResult.Failure -> PasskeyResult.Failure(transform(error))
    }

/**
 * Turns any failure into a success with [transform]'s value; successes pass through unchanged. It hides setup errors
 * too, so log them in [transform] (or use [fold] when failures need their own handling):
 * ```kotlin
 * val usedPasskey: PasskeyResult<Boolean> = passkeys.signIn(options, SignInMode.ImmediateOnly)
 *     .map { true }
 *     .recover { error -> if (error.category == PasskeyErrorCategory.Setup) log(error); false } // show the password form
 * ```
 */
public inline fun <T> PasskeyResult<T>.recover(transform: (PasskeyError) -> T): PasskeyResult<T> = when (this) {
    is PasskeyResult.Success -> this
    is PasskeyResult.Failure -> PasskeyResult.Success(transform(error))
}

/** True for [PasskeyResult.Success]. */
public val PasskeyResult<*>.isSuccess: Boolean get() = this is PasskeyResult.Success

/** True for [PasskeyResult.Failure]. */
public val PasskeyResult<*>.isFailure: Boolean get() = this is PasskeyResult.Failure

/** Returns the error on failure, or null on success. */
public fun PasskeyResult<*>.errorOrNull(): PasskeyError? = (this as? PasskeyResult.Failure)?.error

/**
 * The same outcome as a `kotlin.Result`; a failure holds a [PasskeyException] whose `error` is the original error.
 * For code built around `Result` (e.g. `runCatching` chains).
 */
public fun <T> PasskeyResult<T>.toResult(): Result<T> = when (this) {
    is PasskeyResult.Success -> Result.success(value)
    is PasskeyResult.Failure -> Result.failure(PasskeyException(error))
}
