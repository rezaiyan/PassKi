package com.alirezaiyan.passki

public sealed interface PasskeyResult<out T> {
    public data class Success<out T>(public val value: T) : PasskeyResult<T>
    public data class Failure(public val error: PasskeyError) : PasskeyResult<Nothing>
}

public inline fun <T> PasskeyResult<T>.onSuccess(action: (T) -> Unit): PasskeyResult<T> {
    if (this is PasskeyResult.Success) action(value)
    return this
}

public inline fun <T> PasskeyResult<T>.onFailure(action: (PasskeyError) -> Unit): PasskeyResult<T> {
    if (this is PasskeyResult.Failure) action(error)
    return this
}

public fun <T> PasskeyResult<T>.getOrNull(): T? = (this as? PasskeyResult.Success)?.value

public fun <T> PasskeyResult<T>.getOrThrow(): T = when (this) {
    is PasskeyResult.Success -> value
    is PasskeyResult.Failure -> throw PasskeyException(error)
}

public inline fun <T, R> PasskeyResult<T>.map(transform: (T) -> R): PasskeyResult<R> = when (this) {
    is PasskeyResult.Success -> PasskeyResult.Success(transform(value))
    is PasskeyResult.Failure -> this
}
