package com.alirezaiyan.passki

import com.alirezaiyan.passki.android.ActivityTracker
import com.alirezaiyan.passki.android.UrlConnectionGetter
import com.alirezaiyan.passki.android.collectAndroidDoctorFacts
import com.alirezaiyan.passki.android.composeAndroidReport

public actual object PasskeyDoctor {
    public actual suspend fun run(rpId: String): DoctorReport =
        composeAndroidReport(rpId, collectAndroidDoctorFacts(ActivityTracker.application), UrlConnectionGetter)
}
