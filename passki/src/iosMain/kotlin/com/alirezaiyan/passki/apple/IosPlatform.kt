package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.PasskeyError
import com.alirezaiyan.passki.PasskeyException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

@OptIn(ExperimentalForeignApi::class)
internal fun iosAtLeast(major: Int, minor: Int = 0): Boolean =
    NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
        cValue<NSOperatingSystemVersion> {
            majorVersion = major.toLong()
            minorVersion = minor.toLong()
            patchVersion = 0
        },
    )

internal fun requireIos(major: Int, feature: String) {
    if (!iosAtLeast(major)) throw PasskeyException(PasskeyError.NotSupported("$feature requires iOS $major or newer."))
}

/** The window passkey sheets attach to; resolved automatically so callers never pass one. */
internal fun keyWindow(): UIWindow? {
    val windows = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
    return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
}
