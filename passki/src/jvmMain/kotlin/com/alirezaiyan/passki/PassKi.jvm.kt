package com.alirezaiyan.passki

internal actual fun platformLogHandler(): PasskiLogHandler = PasskiLogHandler { level, tag, message, throwable ->
    System.err.println(platformLogLine(level, tag, message, throwable))
}
