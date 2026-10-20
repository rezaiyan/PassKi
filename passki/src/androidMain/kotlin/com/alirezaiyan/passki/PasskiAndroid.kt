package com.alirezaiyan.passki

import android.app.Application
import com.alirezaiyan.passki.android.ActivityTracker

/** Manual initialisation for apps that disabled androidx.startup. */
public object PasskiAndroid {
    /** Only needed if you removed PassKi's androidx.startup initializer. Call from Application.onCreate(). */
    public fun init(application: Application) {
        ActivityTracker.install(application)
    }
}
