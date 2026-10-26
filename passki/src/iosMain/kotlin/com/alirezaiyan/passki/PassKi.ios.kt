package com.alirezaiyan.passki

import platform.Foundation.NSLog

// The line is the format itself, with `%` escaped: Kotlin strings passed as NSLog's variadic arguments crash.
internal actual fun platformLogHandler(): PasskiLogHandler = PasskiLogHandler { level, tag, message, throwable ->
    NSLog(platformLogLine(level, tag, message, throwable).replace("%", "%%"))
}
