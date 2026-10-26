package com.alirezaiyan.passki.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSigningTest {
    @Test
    fun androidOriginMatchesPasskiOrigin() {
        // Same vector as the Gradle plugin's FingerprintsTest, so the hint and `passkiOrigin` can't disagree.
        val fingerprint = List(32) { "FB" }.joinToString(":")
        assertEquals("android:apk-key-hash:" + "-_v7".repeat(10) + "-_s", androidOrigin(fingerprint))
    }

    @Test
    fun androidOriginAcceptsLowercaseHex() {
        assertEquals(androidOrigin(List(32) { "AB" }.joinToString(":")), androidOrigin(List(32) { "ab" }.joinToString(":")))
    }

    @Test
    fun googleEmulatorImagesAreEmulators() {
        assertTrue(
            looksLikeEmulator(
                fingerprint = "google/sdk_gphone64_arm64/emu64a:16/BP22.250325.006/13344233:userdebug/dev-keys",
                hardware = "ranchu",
                product = "sdk_gphone64_arm64",
                model = "sdk_gphone64_arm64",
            ),
        )
        assertTrue(looksLikeEmulator("generic/sdk/generic:8.0.0/OSR1/1:userdebug/test-keys", "goldfish", "sdk", "Android SDK built for x86"))
    }

    @Test
    fun physicalDevicesAreNotEmulators() {
        assertFalse(looksLikeEmulator("google/husky/husky:16/BP2A.250605.031/13578911:user/release-keys", "husky", "husky", "Pixel 8 Pro"))
        assertFalse(looksLikeEmulator("samsung/e3qxeea/e3q:15/AP3A.240905.015/S928BXXS4AXK1:user/release-keys", "qcom", "e3qxeea", "SM-S928B"))
    }

    @Test
    fun missingBuildValuesAreNotEmulators() {
        assertFalse(looksLikeEmulator(null, null, null, null))
    }
}
