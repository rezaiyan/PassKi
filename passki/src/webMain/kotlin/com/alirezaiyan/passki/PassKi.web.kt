package com.alirezaiyan.passki

internal actual fun platformLogHandler(): PasskiLogHandler = PasskiLogHandler { level, tag, message, throwable ->
    val method = when (level) {
        PasskiLogLevel.Error -> "error"
        PasskiLogLevel.Warn -> "warn"
        PasskiLogLevel.Info -> "info"
        PasskiLogLevel.Debug, PasskiLogLevel.None -> "debug"
    }
    consoleLog(method, "[$tag] " + message.withStackTrace(throwable))
}

/** `console[method](message)`. */
internal expect fun consoleLog(method: String, message: String)
