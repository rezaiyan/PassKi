package com.alirezaiyan.passki.android

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

/**
 * Installs PassKi's foreground-Activity tracker automatically through androidx.startup.
 *
 * Public only because androidx.startup instantiates it reflectively; do not use it directly.
 */
public class PasskiInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        ActivityTracker.install(context.applicationContext as Application)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
