package com.alirezaiyan.passki.gradle

import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

/** Thrown when the passki configuration or a referenced keystore is unusable. */
public class PasskiConfigException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** SHA-256 signing-certificate fingerprints in the colon-hex form `assetlinks.json` expects. */
internal object Fingerprints {
    /** Returns the SHA-256 of [keyAlias]'s certificate in [storeFile], as uppercase colon-hex. */
    fun sha256(storeFile: File, storePassword: String, keyAlias: String, storeType: String?): String {
        val keyStore = try {
            KeyStore.getInstance(storeType ?: KeyStore.getDefaultType()).apply {
                storeFile.inputStream().use { load(it, storePassword.toCharArray()) }
            }
        } catch (e: Exception) {
            throw PasskiConfigException("Cannot open keystore ${storeFile.absolutePath}: ${e.message}", e)
        }
        val certificate = keyStore.getCertificate(keyAlias)
            ?: throw PasskiConfigException("Alias '$keyAlias' not found in ${storeFile.absolutePath}")
        return format(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))
    }

    /** Formats [digest] as uppercase colon-separated hex. */
    fun format(digest: ByteArray): String = digest.joinToString(":") { "%02X".format(it) }

    /** Accepts "ab:cd…" or "abcd…"; returns canonical uppercase colon-hex. */
    fun normalize(value: String): String {
        val hex = value.replace(":", "").uppercase()
        require(hex.length == 64 && hex.all { it in "0123456789ABCDEF" }) { "Not a SHA-256 fingerprint: $value" }
        return hex.chunked(2).joinToString(":")
    }
}
