package com.alirezaiyan.passki

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSProcessInfo

/** Launch with `-passkiAutorun` (e.g. `xcrun simctl launch --console <device> <bundle> -passkiAutorun`) for an unattended smoke run. */
fun MainViewController() = ComposeUIViewController {
    App(autorun = NSProcessInfo.processInfo.arguments.contains("-passkiAutorun"))
}
