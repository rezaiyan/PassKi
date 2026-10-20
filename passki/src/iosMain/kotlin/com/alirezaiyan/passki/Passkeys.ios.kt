package com.alirezaiyan.passki

import com.alirezaiyan.passki.apple.IosPasskeys

public actual fun Passkeys(): Passkeys = IosPasskeys()
