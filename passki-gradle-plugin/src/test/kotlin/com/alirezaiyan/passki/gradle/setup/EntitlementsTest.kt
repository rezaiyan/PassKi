package com.alirezaiyan.passki.gradle.setup

import com.alirezaiyan.passki.gradle.PasskiConfigException
import com.alirezaiyan.passki.gradle.WellKnownVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class EntitlementsTest {
    private val header = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    """.trimIndent()

    private val sample = header + "\n" + """
        <plist version="1.0">
        <dict>
        	<key>com.apple.developer.associated-domains</key>
        	<array>
        		<string>webcredentials:example.com?mode=developer</string>
        	</array>
        </dict>
        </plist>
    """.trimIndent() + "\n"

    private val domains = Entitlements.domains("example.com", developerMode = true)

    @Test
    fun domainsIncludeDeveloperModeOnlyWhenAsked() {
        assertEquals(listOf("webcredentials:example.com", "webcredentials:example.com?mode=developer"), domains)
        assertEquals(listOf("webcredentials:example.com"), Entitlements.domains("example.com", developerMode = false))
    }

    @Test
    fun createsFreshFile() {
        val created = Entitlements.merge(null, domains)
        assertTrue(created.startsWith("<?xml"))
        assertEquals(domains, WellKnownVerifier.associatedDomains(created))
    }

    @Test
    fun addsMissingDomainIntoExistingArrayWithMatchingIndent() {
        val merged = Entitlements.merge(sample, domains)
        assertEquals(
            header + "\n" + """
                <plist version="1.0">
                <dict>
                	<key>com.apple.developer.associated-domains</key>
                	<array>
                		<string>webcredentials:example.com?mode=developer</string>
                		<string>webcredentials:example.com</string>
                	</array>
                </dict>
                </plist>
            """.trimIndent() + "\n",
            merged,
        )
    }

    @Test
    fun isIdempotent() {
        val once = Entitlements.merge(sample, domains)
        assertEquals(once, Entitlements.merge(once, domains))
    }

    @Test
    fun keepsOtherKeysAndAddsTheArrayWhenAbsent() {
        val other = header + "\n<plist version=\"1.0\">\n<dict>\n    <key>aps-environment</key>\n    <string>development</string>\n</dict>\n</plist>\n"
        val merged = Entitlements.merge(other, listOf("webcredentials:example.com"))
        assertTrue(merged.contains("    <key>aps-environment</key>\n    <string>development</string>\n"), merged)
        assertTrue(
            merged.contains(
                "    <key>com.apple.developer.associated-domains</key>\n    <array>\n        <string>webcredentials:example.com</string>\n    </array>\n</dict>",
            ),
            merged,
        )
        assertEquals(listOf("webcredentials:example.com"), WellKnownVerifier.associatedDomains(merged))
    }

    @Test
    fun fillsEmptyArrayAndEmptyDict() {
        val emptyArray = header + "\n<plist version=\"1.0\">\n<dict>\n\t<key>com.apple.developer.associated-domains</key>\n\t<array/>\n</dict>\n</plist>\n"
        assertEquals(listOf("webcredentials:example.com"), WellKnownVerifier.associatedDomains(Entitlements.merge(emptyArray, listOf("webcredentials:example.com"))))
        val emptyDict = header + "\n<plist version=\"1.0\">\n<dict/>\n</plist>\n"
        assertEquals(listOf("webcredentials:example.com"), WellKnownVerifier.associatedDomains(Entitlements.merge(emptyDict, listOf("webcredentials:example.com"))))
    }

    @Test
    fun rejectsNonPlist() {
        assertFailsWith<PasskiConfigException> { Entitlements.merge("not xml", domains) }
    }
}
