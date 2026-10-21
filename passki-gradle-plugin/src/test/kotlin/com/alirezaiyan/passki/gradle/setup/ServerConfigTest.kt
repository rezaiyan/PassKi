package com.alirezaiyan.passki.gradle.setup

import com.alirezaiyan.passki.gradle.Fingerprints
import groovy.json.JsonSlurper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ServerConfigTest {
    private val fp = List(32) { "AB" }.joinToString(":")
    private val config = ServerConfig.of("example.com", mapOf("com.example.app" to listOf(fp)), listOf("ABCDE12345.com.example.app"))

    @Test
    fun originsAreWebOriginThenEveryApkKeyHash() {
        assertEquals(listOf("https://example.com", Fingerprints.androidOrigin(fp)), config.origins)
    }

    @Test
    fun jsonFollowsTheServerConfigContract() {
        val json = JsonSlurper().parseText(config.json()) as Map<*, *>
        assertEquals("example.com", json["rpId"])
        assertEquals(config.origins, json["origins"])
        assertEquals(listOf("ABCDE12345.com.example.app"), json["appleAppIds"])
    }

    @Test
    fun everySnippetNamesRpIdAndAllOrigins() {
        val snippets = config.snippets()
        assertEquals(setOf("simplewebauthn.ts", "webauthn4j.kt", "java-webauthn-server.kt", "py_webauthn.py"), snippets.keys)
        snippets.forEach { (name, text) ->
            assertTrue("example.com" in text, name)
            config.origins.forEach { assertTrue(it in text, "$name lacks $it") }
        }
    }
}
