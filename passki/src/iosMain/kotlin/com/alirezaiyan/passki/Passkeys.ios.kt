package com.alirezaiyan.passki

import com.alirezaiyan.passki.apple.IosPasskeys
import com.alirezaiyan.passki.internal.LoggingPasskeys

public actual fun Passkeys(): Passkeys = LoggingPasskeys(IosPasskeys())
