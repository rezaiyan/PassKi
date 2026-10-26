package com.alirezaiyan.passki

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide PassKi settings. Changes take effect for the next call, from any thread.
 *
 * ```kotlin
 * PassKi.logLevel = if (BuildConfig.DEBUG) PasskiLogLevel.Debug else PasskiLogLevel.Error
 * PassKi.logHandler = PasskiLogHandler { level, tag, message, throwable -> Timber.tag(tag).d(throwable, message) }
 * ```
 */
public object PassKi {
    private val level = MutableStateFlow(PasskiLogLevel.Error)
    private val handler = MutableStateFlow(PasskiLogHandler.Platform)

    /**
     * How much PassKi logs. Default [PasskiLogLevel.Error]: setup problems only, so a misconfigured release build
     * still says why passkeys fail. Set [PasskiLogLevel.None] if your app already logs PassKi errors itself.
     */
    public var logLevel: PasskiLogLevel
        get() = level.value
        set(value) {
            level.value = value
        }

    /**
     * Where log entries go. Default [PasskiLogHandler.Platform]: logcat on Android, `NSLog` on iOS, the browser
     * console on the web and standard error on the JVM. Set your own to route entries to Timber, Kermit or a crash
     * reporter; set [PasskiLogHandler.Platform] again to go back.
     */
    public var logHandler: PasskiLogHandler
        get() = handler.value
        set(value) {
            handler.value = value
        }
}

/**
 * How much PassKi logs; each level includes the ones above it.
 *
 * Closed: there are exactly these five levels; a `when` over it needs no `else` branch.
 */
public enum class PasskiLogLevel {
    /** Nothing. */
    None,

    /** Setup and options problems (errors with a [PasskeyError.fixHint]) and [PasskeyError.Unknown]. The default. */
    Error,

    /** Also [PasskeyError.Interrupted] and [PasskeyError.NotSupported] failures. */
    Warn,

    /** Also one line per finished call: its outcome, including the user closing the sheet. */
    Info,

    /** Also when each call starts (mode, RP ID, extensions) and which platform error each failure was mapped from. */
    Debug,
}

/**
 * Receives PassKi's log entries. [tag] is always `PassKi`; [message] never contains challenges, user handles,
 * options or response JSON, passwords, PRF outputs or large-blob data, and credential IDs are shortened.
 * [throwable] is the platform error behind a [PasskeyError.Unknown], if there is one.
 */
public fun interface PasskiLogHandler {
    public fun log(level: PasskiLogLevel, tag: String, message: String, throwable: Throwable?)

    public companion object {
        /** The platform log: logcat (`android.util.Log`), `NSLog`, the browser console or standard error. */
        public val Platform: PasskiLogHandler = platformLogHandler()
    }
}

internal expect fun platformLogHandler(): PasskiLogHandler

/** One line for logs without levels of their own (`NSLog`, standard error), e.g. `PassKi E signIn failed: ...`. */
internal fun platformLogLine(level: PasskiLogLevel, tag: String, message: String, throwable: Throwable?): String =
    "$tag ${level.name.first()} " + message.withStackTrace(throwable)

internal fun String.withStackTrace(throwable: Throwable?): String =
    if (throwable == null) this else this + "\n" + throwable.stackTraceToString()
