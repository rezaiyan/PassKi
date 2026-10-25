package com.alirezaiyan.passki

import com.alirezaiyan.passki.web.WebPasskeys

public actual fun Passkeys(): Passkeys = WebPasskeys()
