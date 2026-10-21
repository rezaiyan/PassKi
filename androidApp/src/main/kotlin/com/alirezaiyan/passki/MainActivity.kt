package com.alirezaiyan.passki

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.security.MessageDigest

/** Start with `adb shell am start -n com.alirezaiyan.passki/.MainActivity --ez passkiAutorun true` for an unattended smoke run. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val autorun = intent.getBooleanExtra("passkiAutorun", false)
        val origin = apkKeyHashOrigin()

        setContent {
            App(autorun = autorun, appOrigin = origin)
        }
    }

    /** The WebAuthn origin Credential Manager reports for this app: `android:apk-key-hash:<base64url SHA-256 of the signing cert>`. */
    @Suppress("DEPRECATION")
    private fun apkKeyHashOrigin(): String? = runCatching {
        val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return null
        val hash = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
        "android:apk-key-hash:" + Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }.getOrNull()
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
