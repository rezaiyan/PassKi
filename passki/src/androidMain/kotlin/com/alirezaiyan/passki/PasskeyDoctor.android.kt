package com.alirezaiyan.passki

import com.alirezaiyan.passki.android.ActivityTracker
import com.alirezaiyan.passki.android.UrlConnectionGetter
import com.alirezaiyan.passki.android.collectAndroidDoctorFacts
import com.alirezaiyan.passki.android.composeAndroidReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public actual object PasskeyDoctor {
    public actual suspend fun run(rpId: String): DoctorReport {
        // PackageManager lookups are binder IPC; keep them off the caller's (often main) thread.
        val facts = withContext(Dispatchers.IO) { collectAndroidDoctorFacts(ActivityTracker.application) }
        return composeAndroidReport(rpId, facts, UrlConnectionGetter)
    }
}
