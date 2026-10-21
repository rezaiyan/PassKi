package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.doctor.skippedDoctorReport

// Temporary: replaced by the real Android diagnostics in Plan 2 Task B2.
public actual object PasskeyDoctor {
    public actual suspend fun run(rpId: String): DoctorReport =
        skippedDoctorReport(rpId, "Doctor for Android lands in a later task")
}
