package com.alirezaiyan.passki.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.alirezaiyan.passki.Passkeys

/**
 * Remembers the platform [Passkeys] implementation across recompositions.
 *
 * Equivalent to `remember { Passkeys() }`. No platform handles are needed; on Android the current activity
 * is tracked by PassKi itself.
 */
@Composable
public fun rememberPasskeys(): Passkeys = remember { Passkeys() }
