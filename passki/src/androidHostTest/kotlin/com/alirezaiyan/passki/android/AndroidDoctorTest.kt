package com.alirezaiyan.passki.android

import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.internal.doctor.AssetLinksCheck
import com.alirezaiyan.passki.internal.doctor.HttpGetter
import com.alirezaiyan.passki.internal.doctor.HttpResult
import com.alirezaiyan.passki.internal.doctor.PLATFORM_CHECK
import com.alirezaiyan.passki.internal.doctor.androidOrigin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidDoctorTest {
    private val fingerprint = (0 until 32).joinToString(":") { "AB" }
    private val pkg = "com.example.app"

    private fun facts(
        sdkInt: Int = 34,
        packageName: String? = pkg,
        internet: Boolean = true,
        fingerprints: List<String> = listOf(fingerprint),
        deviceSecure: Boolean? = null,
    ) = AndroidDoctorFacts(
        sdkInt = sdkInt,
        release = "14",
        packageName = packageName,
        hasInternetPermission = internet,
        certFingerprints = fingerprints,
        isDeviceSecure = deviceSecure,
    )

    private val validAssetLinks = """
        [{"relation":["delegate_permission/common.get_login_creds"],
          "target":{"namespace":"android_app","package_name":"$pkg","sha256_cert_fingerprints":["$fingerprint"]}}]
    """.trimIndent()

    private class RecordingGetter(private val respond: (String) -> HttpResult) : HttpGetter {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String): HttpResult {
            urls += url
            return respond(url)
        }
    }

    private fun ok(url: String, body: String) = HttpResult(url, 200, "application/json", body, redirectedTo = null, error = null)

    @Test
    fun healthySetupPassesEveryCheck() = runTest {
        val getter = RecordingGetter { ok(it, validAssetLinks) }
        val report = composeAndroidReport("example.com", facts(), getter)

        assertEquals(listOf("https://example.com/.well-known/assetlinks.json"), getter.urls)
        assertEquals(
            listOf(PLATFORM_CHECK, PASSKI_INIT_CHECK, INTERNET_CHECK, AssetLinksCheck.NAME, ORIGIN_CHECK),
            report.checks.map { it.name },
        )
        assertTrue(report.checks.all { it.status == DoctorStatus.Pass }, report.toString())
        assertTrue(report.isHealthy)
        assertTrue("API 34" in report.checks[0].message)
        assertTrue(androidOrigin(fingerprint) in report.checks.last().message)
    }

    @Test
    fun missingFingerprintFailsAssetLinks() = runTest {
        val other = (0 until 32).joinToString(":") { "CD" }
        val report = composeAndroidReport("example.com", facts(fingerprints = listOf(other)), RecordingGetter { ok(it, validAssetLinks) })

        val assetLinks = report.checks.single { it.name == AssetLinksCheck.NAME }
        assertEquals(DoctorStatus.Fail, assetLinks.status)
        assertTrue(other in assetLinks.message)
        assertFalse(report.isHealthy)
    }

    @Test
    fun redirectFailsAssetLinks() = runTest {
        val getter = RecordingGetter {
            HttpResult(it, 301, null, null, redirectedTo = "https://www.example.com/.well-known/assetlinks.json", error = null)
        }
        val assetLinks = composeAndroidReport("example.com", facts(), getter).checks.single { it.name == AssetLinksCheck.NAME }
        assertEquals(DoctorStatus.Fail, assetLinks.status)
        assertTrue("www.example.com" in assetLinks.message)
    }

    @Test
    fun missingInternetPermissionWarnsAndSkipsNetwork() = runTest {
        val getter = RecordingGetter { error("must not fetch") }
        val report = composeAndroidReport("example.com", facts(internet = false), getter)

        assertTrue(getter.urls.isEmpty())
        assertEquals(DoctorStatus.Warn, report.checks.single { it.name == INTERNET_CHECK }.status)
        assertEquals(DoctorStatus.Skipped, report.checks.single { it.name == AssetLinksCheck.NAME }.status)
        assertEquals(DoctorStatus.Pass, report.checks.single { it.name == ORIGIN_CHECK }.status)
        assertTrue(report.isHealthy)
    }

    @Test
    fun notInitialisedFailsAndStops() = runTest {
        val getter = RecordingGetter { error("must not fetch") }
        val report = composeAndroidReport("example.com", facts(packageName = null), getter)

        assertEquals(listOf(PLATFORM_CHECK, PASSKI_INIT_CHECK), report.checks.map { it.name })
        val init = report.checks.last()
        assertEquals(DoctorStatus.Fail, init.status)
        assertTrue("PasskiAndroid.init" in init.fixHint.orEmpty())
        assertTrue(getter.urls.isEmpty())
    }

    @Test
    fun oldAndroidFailsPlatformAndStops() = runTest {
        val getter = RecordingGetter { error("must not fetch") }
        val report = composeAndroidReport("example.com", facts(sdkInt = 27), getter)

        val platform = report.checks.single()
        assertEquals(PLATFORM_CHECK, platform.name)
        assertEquals(DoctorStatus.Fail, platform.status)
        assertTrue("API 27" in platform.message)
        assertTrue(getter.urls.isEmpty())
    }

    @Test
    fun noSigningCertificateSkipsOrigin() = runTest {
        val report = composeAndroidReport("example.com", facts(fingerprints = emptyList()), RecordingGetter { ok(it, validAssetLinks) })
        assertEquals(DoctorStatus.Skipped, report.checks.single { it.name == ORIGIN_CHECK }.status)
    }

    @Test
    fun multipleSignersListEveryOrigin() = runTest {
        val second = (0 until 32).joinToString(":") { "01" }
        val report = composeAndroidReport(
            "example.com",
            facts(fingerprints = listOf(fingerprint, second)),
            RecordingGetter { ok(it, validAssetLinks) },
        )
        val origin = report.checks.single { it.name == ORIGIN_CHECK }
        assertTrue(androidOrigin(fingerprint) in origin.message)
        assertTrue(androidOrigin(second) in origin.message)
    }

    @Test
    fun certificateBytesFormatAsUppercaseColonHexSha256() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals(
            "BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD",
            sha256Fingerprint("abc".encodeToByteArray()),
        )
    }

    @Test
    fun rotatedSingleSignerUsesOnlyCurrentCertificate() {
        val old = "old".encodeToByteArray()
        val current = "current".encodeToByteArray()
        val selected = currentSigningCertificates(hasMultipleSigners = false, apkContentsSigners = listOf(current), history = listOf(old, current))
        assertEquals(listOf(current), selected)
    }

    @Test
    fun multipleSignersUseApkContentsSigners() {
        val a = "a".encodeToByteArray()
        val b = "b".encodeToByteArray()
        assertEquals(listOf(a, b), currentSigningCertificates(hasMultipleSigners = true, apkContentsSigners = listOf(a, b), history = emptyList()))
    }

    @Test
    fun singleSignerWithoutHistoryFallsBackToContentsSigners() {
        val a = "a".encodeToByteArray()
        assertEquals(listOf(a), currentSigningCertificates(hasMultipleSigners = false, apkContentsSigners = listOf(a), history = emptyList()))
    }

    @Test
    fun urlRpIdFailsWithoutFetching() = runTest {
        val getter = RecordingGetter { ok(it, validAssetLinks) }
        val report = composeAndroidReport("https://example.com/x", facts(), getter)
        assertFalse(report.isHealthy)
        assertEquals("rpId must be a bare domain (got https://example.com/x); use example.com", report.failures.single().message)
        assertTrue(getter.urls.isEmpty())
    }

    @Test
    fun checksCarryCatalogIds() = runTest {
        val report = composeAndroidReport("example.com", facts(deviceSecure = true), RecordingGetter { ok(it, validAssetLinks) })
        val ids = report.checks.associate { it.name to it.id }
        assertEquals("DEV-03", ids[PLATFORM_CHECK])
        assertEquals("AND-06", ids[PASSKI_INIT_CHECK])
        assertEquals("AND-03", ids[AssetLinksCheck.NAME])
        assertEquals("DEV-01", ids[SCREEN_LOCK_CHECK])
        assertTrue("[PASS] DEV-03 $PLATFORM_CHECK" in report.toString(), report.toString())
    }

    @Test
    fun noScreenLockWarnsDev01() = runTest {
        val report = composeAndroidReport("example.com", facts(deviceSecure = false), RecordingGetter { ok(it, validAssetLinks) })
        val check = report.checks.single { it.id == "DEV-01" }
        assertEquals(DoctorStatus.Warn, check.status)
        assertTrue("screen lock" in check.fixHint.orEmpty(), check.fixHint)
        assertTrue(report.isHealthy)
    }

    @Test
    fun unknownScreenLockStateOmitsDev01() = runTest {
        val report = composeAndroidReport("example.com", facts(deviceSecure = null), RecordingGetter { ok(it, validAssetLinks) })
        assertTrue(report.checks.none { it.id == "DEV-01" })
    }

    @Test
    fun urlRpIdCarriesCfg01() = runTest {
        val report = composeAndroidReport("https://example.com", facts(), RecordingGetter { ok(it, validAssetLinks) })
        assertEquals("CFG-01", report.checks.single().id)
    }
}
