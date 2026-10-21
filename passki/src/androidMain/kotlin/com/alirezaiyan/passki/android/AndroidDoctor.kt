package com.alirezaiyan.passki.android

import com.alirezaiyan.passki.DoctorCheck
import com.alirezaiyan.passki.DoctorReport
import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.internal.doctor.AssetLinksCheck
import com.alirezaiyan.passki.internal.doctor.HttpGetter
import com.alirezaiyan.passki.internal.doctor.PLATFORM_CHECK
import com.alirezaiyan.passki.internal.doctor.androidOrigin
import com.alirezaiyan.passki.internal.doctor.rpIdCheck

internal const val PASSKI_INIT_CHECK: String = "PassKi initialised"
internal const val INTERNET_CHECK: String = "INTERNET permission"
internal const val ORIGIN_CHECK: String = "Android origin"

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
        )
        return DoctorReport(rpId, checks)
    }
    checks += DoctorCheck(PLATFORM_CHECK, DoctorStatus.Pass, version, fixHint = null)

    val packageName = facts.packageName
    if (packageName == null) {
        checks += DoctorCheck(PASSKI_INIT_CHECK, DoctorStatus.Fail, "PassKi has no Application", INIT_HINT)
        return DoctorReport(rpId, checks)
    }
    checks += DoctorCheck(PASSKI_INIT_CHECK, DoctorStatus.Pass, "package $packageName", fixHint = null)

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
        checks += DoctorCheck(AssetLinksCheck.NAME, DoctorStatus.Skipped, "needs the INTERNET permission", fixHint = null)
    }

    checks += originCheck(facts.certFingerprints)
    return DoctorReport(rpId, checks)
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
