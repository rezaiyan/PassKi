package com.alirezaiyan.passki.android

import com.alirezaiyan.passki.DoctorCheck
import com.alirezaiyan.passki.DoctorReport
import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.internal.doctor.AssetLinksCheck
import com.alirezaiyan.passki.internal.doctor.CheckIds
import com.alirezaiyan.passki.internal.doctor.HttpGetter
import com.alirezaiyan.passki.internal.doctor.PLATFORM_CHECK
import com.alirezaiyan.passki.internal.doctor.androidOrigin
import com.alirezaiyan.passki.internal.doctor.rpIdCheck

internal const val PASSKI_INIT_CHECK: String = "PassKi initialised"
internal const val INTERNET_CHECK: String = "INTERNET permission"
internal const val ORIGIN_CHECK: String = "Android origin"
internal const val SCREEN_LOCK_CHECK: String = "Screen lock"

private const val MIN_PASSKEY_SDK = 28

/**
 * Builds the Android doctor report from [facts], fetching assetlinks.json through [getter].
 * Stops early when later checks can't produce meaningful results (old Android, PassKi not initialised).
 */
internal suspend fun composeAndroidReport(rpId: String, facts: AndroidDoctorFacts, getter: HttpGetter): DoctorReport {
    rpIdCheck(rpId)?.let { return DoctorReport(rpId, listOf(it)) }
    val checks = mutableListOf<DoctorCheck>()
    val version = "Android ${facts.release} (API ${facts.sdkInt})"
    if (facts.sdkInt < MIN_PASSKEY_SDK) {
        checks += DoctorCheck(
            PLATFORM_CHECK,
            DoctorStatus.Fail,
            "$version; passkeys need Android 9 (API 28) or newer",
            fixHint = "run on an Android 9+ device or emulator, and hide passkey UI when Passkeys.capabilities().isSupported is false",
            id = CheckIds.DEV_03,
        )
        return DoctorReport(rpId, checks)
    }
    checks += DoctorCheck(PLATFORM_CHECK, DoctorStatus.Pass, version, fixHint = null, id = CheckIds.DEV_03)
    screenLockCheck(facts.isDeviceSecure)?.let { checks += it }

    val packageName = facts.packageName
    if (packageName == null) {
        checks += DoctorCheck(PASSKI_INIT_CHECK, DoctorStatus.Fail, "PassKi has no Application", INIT_HINT, CheckIds.AND_06)
        return DoctorReport(rpId, checks)
    }
    checks += DoctorCheck(PASSKI_INIT_CHECK, DoctorStatus.Pass, "package $packageName", fixHint = null, id = CheckIds.AND_06)

    val assetLinksUrl = "https://$rpId/.well-known/assetlinks.json"
    if (facts.hasInternetPermission) {
        checks += DoctorCheck(INTERNET_CHECK, DoctorStatus.Pass, "granted", fixHint = null)
        checks += AssetLinksCheck.evaluate(getter.get(assetLinksUrl), packageName, facts.certFingerprints)
    } else {
        checks += DoctorCheck(
            INTERNET_CHECK,
            DoctorStatus.Warn,
            "not declared; network checks skipped",
            fixHint = "add <uses-permission android:name=\"android.permission.INTERNET\" /> to a debug AndroidManifest.xml " +
                "to let the doctor fetch $assetLinksUrl",
        )
        checks += DoctorCheck(AssetLinksCheck.NAME, DoctorStatus.Skipped, "needs the INTERNET permission", fixHint = null, id = CheckIds.AND_01)
    }

    checks += originCheck(facts.certFingerprints)
    return DoctorReport(rpId, checks)
}

/** DEV-01: Credential Manager providers (Google Password Manager) refuse to store passkeys without a screen lock. */
private fun screenLockCheck(isDeviceSecure: Boolean?): DoctorCheck? = when (isDeviceSecure) {
    null -> null
    true -> DoctorCheck(SCREEN_LOCK_CHECK, DoctorStatus.Pass, "device has a secure screen lock", fixHint = null, id = CheckIds.DEV_01)
    false -> DoctorCheck(
        SCREEN_LOCK_CHECK,
        DoctorStatus.Warn,
        "no secure screen lock; credential providers won't create passkeys (the create call looks like it hangs or fails)",
        fixHint = "set a PIN, pattern or password screen lock in Settings > Security, and sign in to a Google account " +
            "(or enable another credential provider)",
        id = CheckIds.DEV_01,
    )
}

private fun originCheck(fingerprints: List<String>): DoctorCheck {
    if (fingerprints.isEmpty()) {
        return DoctorCheck(ORIGIN_CHECK, DoctorStatus.Skipped, "no signing certificate found", fixHint = null)
    }
    val origins = fingerprints.map(::androidOrigin)
    return DoctorCheck(
        ORIGIN_CHECK,
        DoctorStatus.Pass,
        "allow ${if (origins.size == 1) "this origin" else "these origins"} (current signing " +
            "${if (origins.size == 1) "key" else "keys"}) on your server: ${origins.joinToString()}",
        fixHint = null,
    )
}
