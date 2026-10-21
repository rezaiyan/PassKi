package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.doctor.skippedDoctorReport

public actual object PasskeyDoctor {
    public actual suspend fun run(rpId: String): DoctorReport =
        skippedDoctorReport(rpId, "Doctor supports Android and iOS; this is JVM desktop")
}
