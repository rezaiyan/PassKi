package com.alirezaiyan.passki

import com.alirezaiyan.passki.android.AndroidPasskeys

public actual fun Passkeys(): Passkeys = AndroidPasskeys()
