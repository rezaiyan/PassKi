package com.alirezaiyan.passki

import android.util.Log

internal actual fun platformLogHandler(): PasskiLogHandler = AndroidLogHandler()

/**
 * Writes to logcat; [write] is `Log.println`, swapped in tests. In local unit tests (an app's `src/test` with
 * FakePasskeys) `android.util.Log` is a stub that throws, so the line goes to standard error instead.
 */
internal class AndroidLogHandler(
    private val write: (priority: Int, tag: String, message: String) -> Unit = { priority, tag, message ->
        Log.println(priority, tag, message)
    },
) : PasskiLogHandler {
    override fun log(level: PasskiLogLevel, tag: String, message: String, throwable: Throwable?) {
        val priority = when (level) {
            PasskiLogLevel.Error -> Log.ERROR
            PasskiLogLevel.Warn -> Log.WARN
            PasskiLogLevel.Info -> Log.INFO
            PasskiLogLevel.Debug, PasskiLogLevel.None -> Log.DEBUG
        }
        try {
            write(priority, tag, message.withStackTrace(throwable))
        } catch (e: RuntimeException) {
            System.err.println(platformLogLine(level, tag, message, throwable))
        }
    }
}
