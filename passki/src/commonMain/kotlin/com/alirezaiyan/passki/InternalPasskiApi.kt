package com.alirezaiyan.passki

/**
 * Marks declarations shared between PassKi modules. Not part of the stable API.
 */
@RequiresOptIn(
    message = "Internal PassKi API. It may change without notice.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class InternalPasskiApi
