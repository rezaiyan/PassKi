package com.alirezaiyan.passki.gradle

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

internal class WellKnownFilesTest {
    private val fingerprint =
        "C9:64:0F:6A:68:90:AF:6C:26:49:DC:BB:AB:94:79:23:93:5C:FC:10:8B:DF:03:20:78:04:60:5C:5B:79:49:03"

    @Test
    fun assetLinksMatchesDocumentedGolden() {
        // Test working dir is the plugin project; the golden lives in the repo docs.
        val golden = File("../docs/setup/well-known/assetlinks.json").readText()

        assertEquals(golden, WellKnownFiles.assetLinks(mapOf("com.alirezaiyan.passki" to listOf(fingerprint))))
    }

    @Test
    fun assetLinksDeduplicatesAndSortsFingerprints() {
        val a = List(32) { "AA" }.joinToString(":")
        val b = List(32) { "BB" }.joinToString(":")

        val expected = """
            [
              {
                "relation": [
                  "delegate_permission/common.handle_all_urls",
                  "delegate_permission/common.get_login_creds"
                ],
                "target": {
                  "namespace": "android_app",
                  "package_name": "com.example",
                  "sha256_cert_fingerprints": [
                    "$a",
                    "$b"
                  ]
                }
              }
            ]
        """.trimIndent() + "\n"

        assertEquals(expected, WellKnownFiles.assetLinks(mapOf("com.example" to listOf(b, a, b))))
    }

    @Test
    fun assetLinksHasOneStatementPerPackageSortedByPackage() {
        val a = List(32) { "AA" }.joinToString(":")
        val b = List(32) { "BB" }.joinToString(":")

        val json = WellKnownFiles.assetLinks(mapOf("com.example.debug" to listOf(a), "com.example" to listOf(b)))

        @Suppress("UNCHECKED_CAST")
        val statements = groovy.json.JsonSlurper().parseText(json) as List<Map<String, Map<String, Any>>>
        assertEquals(listOf("com.example", "com.example.debug"), statements.map { it.getValue("target")["package_name"] })
        assertEquals(listOf(listOf(b), listOf(a)), statements.map { it.getValue("target")["sha256_cert_fingerprints"] })
    }

    @Test
    fun aasaListsWebcredentialsApps() {
        val expected = """
            {
              "webcredentials": {
                "apps": [
                  "ABCDE12345.com.example"
                ]
              }
            }
        """.trimIndent() + "\n"

        assertEquals(expected, WellKnownFiles.aasa(listOf("ABCDE12345.com.example")))
    }
}
