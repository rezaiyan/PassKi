package com.alirezaiyan.passki

import com.alirezaiyan.passki.android.AndroidPasskeys
import com.alirezaiyan.passki.internal.LoggingPasskeys

public actual fun Passkeys(): Passkeys = LoggingPasskeys(AndroidPasskeys())
