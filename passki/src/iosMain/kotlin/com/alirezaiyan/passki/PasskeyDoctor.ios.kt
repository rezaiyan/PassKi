package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.doctor.skippedDoctorReport

// Temporary: replaced by the real iOS diagnostics in Plan 2 Task B3.
public actual object PasskeyDoctor {
    public actual suspend fun run(rpId: String): DoctorReport =
        skippedDoctorReport(rpId, "Doctor for iOS lands in a later task")
}
