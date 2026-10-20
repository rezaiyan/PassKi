package com.alirezaiyan.passki

/** Outcome of a passkey operation: either [Success] with a value or [Failure] with a typed [PasskeyError]. */
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
