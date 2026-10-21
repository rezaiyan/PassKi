package com.alirezaiyan.passki.gradle

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class FingerprintsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val keytool: String =
        File(System.getProperty("java.home"), "bin/keytool").takeIf { it.isFile }?.absolutePath ?: "keytool"

    private fun keytool(vararg args: String): String {
        val process = ProcessBuilder(keytool, *args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "keytool failed: $output" }
        return output
    }

    private fun generateKeystore(): File {
        val store = File(tmp.root, "k.jks")
        keytool(
            "-genkeypair", "-keystore", store.absolutePath, "-storetype", "PKCS12",
            "-storepass", "secret123", "-keypass", "secret123", "-alias", "key0",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "1", "-dname", "CN=PassKi Test",
        )
        return store
    }

    @Test
    fun sha256MatchesKeytool() {
        val store = generateKeystore()
        val listing = keytool("-list", "-v", "-keystore", store.absolutePath, "-storepass", "secret123", "-alias", "key0")
        val expected = Regex("""SHA256:\s*([0-9A-F:]+)""").find(listing)?.groupValues?.get(1)
            ?: error("No SHA256 line in keytool output:\n$listing")

        val actual = Fingerprints.sha256(store, "secret123", "key0", "PKCS12")

        assertEquals(expected, actual)
    }

    @Test
    fun sha256IsUppercaseColonHexOf32Bytes() {
        val store = generateKeystore()

        val actual = Fingerprints.sha256(store, "secret123", "key0", null)

        assertTrue(Regex("""^([0-9A-F]{2}:){31}[0-9A-F]{2}$""").matches(actual), actual)
    }

    @Test
    fun wrongPasswordThrowsConfigExceptionNamingKeystore() {
        val store = generateKeystore()

        val error = assertFailsWith<PasskiConfigException> {
            Fingerprints.sha256(store, "wrong-password", "key0", "PKCS12")
        }

        assertTrue(error.message!!.contains(store.absolutePath), error.message)
    }

    @Test
    fun missingAliasThrowsConfigException() {
        val store = generateKeystore()

        val error = assertFailsWith<PasskiConfigException> {
            Fingerprints.sha256(store, "secret123", "nope", "PKCS12")
        }

        assertTrue(error.message!!.contains("nope"), error.message)
    }

    @Test
    fun normalizeUppercasesLowercaseInput() {
        val lower = (0 until 32).joinToString(":") { "%02x".format(it + 0xA0) }

        assertEquals(lower.uppercase(), Fingerprints.normalize(lower))
    }

    @Test
    fun normalizeAddsColonsToBareHex() {
        val bare = "ab".repeat(32)

        assertEquals(List(32) { "AB" }.joinToString(":"), Fingerprints.normalize(bare))
    }

    @Test
    fun androidOriginIsBase64UrlOfDigestWithoutPadding() {
        // 32 bytes 0xFB → "+" / "/" in plain base64, "-" / "_" in base64url; 43 chars, no '='.
        val fingerprint = List(32) { "FB" }.joinToString(":")

        assertEquals("android:apk-key-hash:" + "-_v7".repeat(10) + "-_s", Fingerprints.androidOrigin(fingerprint))
    }

    @Test
    fun normalizeRejectsInvalidInput() {
        assertFailsWith<IllegalArgumentException> { Fingerprints.normalize("AB:CD") }
        assertFailsWith<IllegalArgumentException> { Fingerprints.normalize("ZZ".repeat(32)) }
    }
}
