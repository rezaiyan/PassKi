package com.alirezaiyan.passki.android

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

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
        certFingerprints = app?.let(::signingFingerprints).orEmpty(),
        isDeviceSecure = app?.let { (it.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure },
    )
}

/** SHA-256 of [certificate] (DER bytes) as uppercase colon hex, the format assetlinks.json uses. */
internal fun sha256Fingerprint(certificate: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(certificate).joinToString(":") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
    }

/** GET_SIGNING_CERTIFICATES and SigningInfo need API 28; older devices can't use passkeys anyway. */
private fun signingFingerprints(app: Application): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
    val info = try {
        @Suppress("DEPRECATION") // The PackageInfoFlags overload needs API 33; the Int one works everywhere.
        app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    } catch (_: PackageManager.NameNotFoundException) {
        return emptyList()
    }
    val signing = info.signingInfo ?: return emptyList()
    val signers = currentSigningCertificates(
        hasMultipleSigners = signing.hasMultipleSigners(),
        apkContentsSigners = signing.apkContentsSigners.orEmpty().toList(),
        history = signing.signingCertificateHistory.orEmpty().toList(),
    )
    return signers.map { sha256Fingerprint(it.toByteArray()) }.distinct()
}

/**
 * The certificates the app is signed with *now*. With a single signer, [history] lists every rotated-out key
 * (oldest first) and the current one last; requiring the old keys in assetlinks.json would be a false failure.
 * With multiple signers there's no rotation, so every APK contents signer counts.
 */
internal fun <T> currentSigningCertificates(hasMultipleSigners: Boolean, apkContentsSigners: List<T>, history: List<T>): List<T> =
    when {
        hasMultipleSigners -> apkContentsSigners
        history.isNotEmpty() -> listOf(history.last())
        else -> apkContentsSigners
    }
