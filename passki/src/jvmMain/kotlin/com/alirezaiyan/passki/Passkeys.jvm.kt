package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.LoggingPasskeys
import com.alirezaiyan.passki.internal.UnsupportedPasskeys

public actual fun Passkeys(): Passkeys = LoggingPasskeys(UnsupportedPasskeys("JVM desktop"))
