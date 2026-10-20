package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.UnsupportedPasskeys

public actual fun Passkeys(): Passkeys = UnsupportedPasskeys("the browser")
