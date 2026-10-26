package com.alirezaiyan.passki.android

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.alirezaiyan.passki.internal.Base64Url
import java.security.MessageDigest

/**
 * The certificates this app is signed with now, read once per process. Used by the doctor's AND-03 check and by
 * AND-03 error hints, so both name the same fingerprints.
 */
internal object AppSigning {
    @Volatile
    private var cached: List<String>? = null

    /** SHA-256 of each current signing certificate, colon hex; empty below API 28 or when unreadable. */
    fun fingerprints(app: Application): List<String> = cached ?: readFingerprints(app).also { cached = it }

    /** Whether this process runs on an Android emulator. */
    val isEmulator: Boolean by lazy {
        looksLikeEmulator(Build.FINGERPRINT, Build.HARDWARE, Build.PRODUCT, Build.MODEL)
    }
}

/** SHA-256 of [certificate] (DER bytes) as uppercase colon hex, the format assetlinks.json uses. */
internal fun sha256Fingerprint(certificate: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(certificate).joinToString(":") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
    }

/** The origin Android puts in passkey responses for a signing key: `android:apk-key-hash:` + base64url of its SHA-256. */
internal fun androidOrigin(sha256Fingerprint: String): String {
    val bytes = sha256Fingerprint.split(":").map { it.toInt(radix = 16).toByte() }.toByteArray()
    return "android:apk-key-hash:" + Base64Url.encode(bytes)
}

/**
 * Emulator signals from [Build] values. A miss only drops a hint line and a false positive only adds one, so the usual
 * heuristics are enough.
 */
internal fun looksLikeEmulator(fingerprint: String?, hardware: String?, product: String?, model: String?): Boolean {
    val fp = fingerprint.orEmpty().lowercase()
    val hw = hardware.orEmpty().lowercase()
    val prod = product.orEmpty().lowercase()
    return fp.startsWith("generic") || "emulator" in fp || "/sdk_" in fp ||
        hw == "ranchu" || hw == "goldfish" ||
        prod.startsWith("sdk") || "_sdk" in prod || "emulator" in prod ||
        model.orEmpty().contains("Android SDK built for", ignoreCase = true)
}

/** GET_SIGNING_CERTIFICATES and SigningInfo need API 28; older devices can't use passkeys anyway. */
private fun readFingerprints(app: Application): List<String> {
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
