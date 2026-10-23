package com.alirezaiyan.passki.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class RpIdTest {
    @Test
    fun acceptsBareDomains() {
        listOf("example.com", "login.example.co.uk", "passki.alirezaiyan.com", "a-b.example.io", "xn--bcher-kva.example").forEach {
            assertEquals(it, RpId.validate(it))
        }
    }

    @Test
    fun suggestsTheBareDomainForUrlsPathsAndPorts() {
        mapOf(
            "https://example.com" to "example.com",
            "https://example.com/" to "example.com",
            "http://example.com/login" to "example.com",
            "example.com/" to "example.com",
            "example.com:443" to "example.com",
            "Example.COM" to "example.com",
            " example.com " to "example.com",
            "example.com." to "example.com",
        ).forEach { (input, suggestion) ->
            val e = assertFailsWith<PasskiConfigException>(input) { RpId.validate(input) }
            assertTrue("use \"$suggestion\"" in e.message!!, "$input → ${e.message}")
            assertTrue("passki { rpId" in e.message!!, e.message)
        }
    }

    @Test
    fun rejectsValuesThatAreNotDomains() {
        listOf("", "localhost", "192.168.1.10", "example", "-bad.example.com", "exa mple.com", "*.example.com").forEach { input ->
            val e = assertFailsWith<PasskiConfigException>(input) { RpId.validate(input) }
            assertTrue("bare domain" in e.message!!, "$input → ${e.message}")
        }
    }
}
