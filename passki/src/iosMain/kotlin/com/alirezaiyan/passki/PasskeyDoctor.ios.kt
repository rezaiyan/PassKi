package com.alirezaiyan.passki

import com.alirezaiyan.passki.apple.NSUrlSessionGetter
import com.alirezaiyan.passki.apple.collectIosDoctorFacts
import com.alirezaiyan.passki.apple.composeIosReport

public actual object PasskeyDoctor {
    public actual suspend fun run(rpId: String): DoctorReport =
        composeIosReport(rpId, collectIosDoctorFacts(), NSUrlSessionGetter)
}
