package com.alirezaiyan.passki.apple

import com.alirezaiyan.passki.DoctorCheck
import com.alirezaiyan.passki.DoctorReport
import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.internal.doctor.AasaCheck
import com.alirezaiyan.passki.internal.doctor.CheckIds
import com.alirezaiyan.passki.internal.doctor.HttpGetter
import com.alirezaiyan.passki.internal.doctor.PLATFORM_CHECK
import com.alirezaiyan.passki.internal.doctor.rpIdCheck
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal const val KEY_WINDOW_CHECK: String = "Key window"
internal const val ENTITLEMENT_CHECK: String = "Associated Domains entitlement"
internal const val DEVICE_AUTH_CHECK: String = "Device authentication"

private const val MIN_PASSKEY_IOS = 16

/**
 * Builds the iOS doctor report from [facts], fetching the AASA file directly and from Apple's CDN (concurrently)
 * through [getter].
 * Stops early on iOS < 16, where nothing else matters.
 */
internal suspend fun composeIosReport(rpId: String, facts: IosDoctorFacts, getter: HttpGetter): DoctorReport {
    rpIdCheck(rpId)?.let { return DoctorReport(rpId, listOf(it)) }
    val checks = mutableListOf<DoctorCheck>()
    val version = "iOS ${facts.osVersion}"
    if (facts.osMajor < MIN_PASSKEY_IOS) {
        checks += DoctorCheck(
            PLATFORM_CHECK,
            DoctorStatus.Fail,
            "$version; passkeys need iOS 16 or newer",
            fixHint = "run on an iOS 16+ device or simulator, and hide passkey UI when Passkeys.capabilities().isSupported is false",
            id = CheckIds.DEV_03,
        )
        return DoctorReport(rpId, checks)
    }
    checks += DoctorCheck(PLATFORM_CHECK, DoctorStatus.Pass, version, fixHint = null, id = CheckIds.DEV_03)
    deviceAuthCheck(facts.deviceAuth, rpId)?.let { checks += it }

    checks += if (facts.hasKeyWindow) {
        DoctorCheck(KEY_WINDOW_CHECK, DoctorStatus.Pass, "foreground window available for passkey sheets", fixHint = null)
    } else {
        DoctorCheck(
            KEY_WINDOW_CHECK,
            DoctorStatus.Warn,
            "no window found; passkey sheets need one when Passkeys.create/get is called",
            fixHint = "call passkey APIs while a scene is in the foreground",
        )
    }

    checks += DoctorCheck(
        ENTITLEMENT_CHECK,
        DoctorStatus.Skipped,
        "iOS apps can't read their own entitlements at runtime",
        fixHint = "make sure the app's entitlements list webcredentials:$rpId; " +
            "the PassKi Gradle plugin's passkiCheck (or passkiVerify) task checks the .entitlements file and its Xcode wiring",
        id = CheckIds.IOS_04,
    )

    val bundleId = facts.bundleId
    if (bundleId == null) {
        checks += DoctorCheck(
            AasaCheck.NAME,
            DoctorStatus.Fail,
            "the main bundle has no bundle identifier, so the AASA entry can't be checked",
            fixHint = "set PRODUCT_BUNDLE_IDENTIFIER for the app target",
            id = CheckIds.IOS_02,
        )
        return DoctorReport(rpId, checks)
    }
    val (direct, cdn) = coroutineScope {
        val direct = async { getter.get("https://$rpId/.well-known/apple-app-site-association") }
        val cdn = async { getter.get("https://app-site-association.cdn-apple.com/a/v1/$rpId") }
        direct.await() to cdn.await()
    }
    checks += AasaCheck.evaluate(direct, cdn, bundleId)
    return DoctorReport(rpId, checks)
}

/** DEV-02: passkeys need a device passcode; the Simulator additionally needs enrolled Face ID to show the sheet. */
private fun deviceAuthCheck(auth: DeviceAuth?, rpId: String): DoctorCheck? = when (auth) {
    null -> null
    DeviceAuth.Biometrics -> DoctorCheck(DEVICE_AUTH_CHECK, DoctorStatus.Pass, "Face ID / Touch ID enrolled", fixHint = null, id = CheckIds.DEV_02)
    DeviceAuth.PasscodeOnly -> DoctorCheck(
        DEVICE_AUTH_CHECK,
        DoctorStatus.Pass,
        "device passcode set (no biometrics enrolled)",
        fixHint = null,
        id = CheckIds.DEV_02,
    )
    DeviceAuth.None -> DoctorCheck(
        DEVICE_AUTH_CHECK,
        DoctorStatus.Warn,
        "no passcode or biometrics; passkey sheets can't authenticate",
        fixHint = "set a passcode and enrol Face ID/Touch ID (Simulator: Features > Face ID > Enrolled); " +
            "for webcredentials:$rpId?mode=developer also enable Settings > Developer > Associated Domains Development",
        id = CheckIds.DEV_02,
    )
}
