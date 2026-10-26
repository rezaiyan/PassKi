package com.alirezaiyan.passki.android

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Everything the Android doctor needs to know about this device and app, gathered up front so
 * [composeAndroidReport] stays pure and host-testable.
 *
 * @property packageName null when PassKi has no Application (not initialised).
 * @property certFingerprints SHA-256 of each current signing certificate (rotated-out keys excluded), colon hex;
 *   empty below API 28 or when unreadable.
 * @property isDeviceSecure whether a PIN/pattern/password lock is set; null when unknown (no Application).
 */
internal class AndroidDoctorFacts(
    val sdkInt: Int,
    val release: String,
    val packageName: String?,
    val hasInternetPermission: Boolean,
    val certFingerprints: List<String>,
    val isDeviceSecure: Boolean? = null,
)

internal fun collectAndroidDoctorFacts(app: Application?): AndroidDoctorFacts {
    val sdkInt = Build.VERSION.SDK_INT
    return AndroidDoctorFacts(
        sdkInt = sdkInt,
        release = Build.VERSION.RELEASE.orEmpty(),
        packageName = app?.packageName,
        hasInternetPermission = app?.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED,
        certFingerprints = app?.let(AppSigning::fingerprints).orEmpty(),
        isDeviceSecure = app?.let { (it.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure },
    )
}
