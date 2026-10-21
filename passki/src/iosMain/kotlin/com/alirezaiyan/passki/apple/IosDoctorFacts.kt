package com.alirezaiyan.passki.apple

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/**
 * Everything the iOS doctor needs to know about this device and app, gathered up front so
 * [composeIosReport] stays pure and testable with any OS version.
 *
 * @property osVersion e.g. `17.2.1`.
 * @property bundleId null when the main bundle has none (e.g. some test hosts).
 * @property deviceAuth how the user can authenticate; null when unknown.
 */
internal class IosDoctorFacts(
    val osMajor: Int,
    val osVersion: String,
    val bundleId: String?,
    val hasKeyWindow: Boolean,
    val deviceAuth: DeviceAuth? = null,
)

/** What LocalAuthentication reports about the device owner's authentication options. */
internal enum class DeviceAuth { Biometrics, PasscodeOnly, None }

@OptIn(ExperimentalForeignApi::class)
private fun deviceAuth(): DeviceAuth {
    val context = LAContext()
    return when {
        context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null) -> DeviceAuth.Biometrics
        context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null) -> DeviceAuth.PasscodeOnly
        else -> DeviceAuth.None
    }
}

/** Reads UIKit state, so it hops to the main thread. */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun collectIosDoctorFacts(): IosDoctorFacts = withContext(Dispatchers.Main) {
    val (major, version) = NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        majorVersion.toInt() to listOf(majorVersion, minorVersion, patchVersion).joinToString(".")
    }
    IosDoctorFacts(
        osMajor = major,
        osVersion = version,
        bundleId = NSBundle.mainBundle.bundleIdentifier,
        hasKeyWindow = keyWindow() != null,
        deviceAuth = deviceAuth(),
    )
}
