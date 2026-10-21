package com.alirezaiyan.passki.gradle.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class XcodeSettingsTest {
    private val json = """
        [
          {"action":"build","target":"Widget","buildSettings":{"WRAPPER_EXTENSION":"appex","PRODUCT_BUNDLE_IDENTIFIER":"com.example.app.widget"}},
          {"action":"build","target":"iosApp","buildSettings":{
            "WRAPPER_EXTENSION":"app","PRODUCT_TYPE":"com.apple.product-type.application",
            "DEVELOPMENT_TEAM":"ABCDE12345","PRODUCT_BUNDLE_IDENTIFIER":"com.example.app",
            "PROJECT_DIR":"/p/iosApp","CODE_SIGN_ENTITLEMENTS":"iosApp/iosApp.entitlements"}}
        ]
    """.trimIndent()

    @Test
    fun picksTheApplicationTarget() {
        val settings = XcodeSettings.parse(json)!!
        assertEquals("iosApp", settings.target)
        assertEquals("ABCDE12345", settings["DEVELOPMENT_TEAM"])
        assertEquals("com.example.app", settings["PRODUCT_BUNDLE_IDENTIFIER"])
        assertEquals("/p/iosApp/iosApp/iosApp.entitlements", settings.entitlementsFile()?.path)
    }

    @Test
    fun toleratesLeadingNoiseAndBlankValues() {
        val settings = XcodeSettings.parse("note: Using new build system\n" + json.replace("iosApp/iosApp.entitlements", ""))!!
        assertNull(settings.entitlementsFile())
    }

    @Test
    fun invalidJsonIsNull() {
        assertNull(XcodeSettings.parse("xcodebuild: error: no scheme"))
        assertNull(XcodeSettings.parse("[]"))
    }
}
