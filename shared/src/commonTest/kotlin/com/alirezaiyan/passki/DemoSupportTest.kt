package com.alirezaiyan.passki

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoSupportTest {
    @Test
    fun withAllowCredentials_replacesTheList() {
        val edited = DemoSupport.withAllowCredentials("""{"challenge":"c","allowCredentials":[]}""", listOf("AA"))
        val allow = Json.parseToJsonElement(edited).jsonObject["allowCredentials"]!!.jsonArray
        assertEquals("AA", allow.single().jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun fixturePlatform_mapsPlatformNames() {
        assertEquals("android", DemoSupport.fixturePlatform("Android 36"))
        assertEquals("ios", DemoSupport.fixturePlatform("iOS 26.3"))
        assertEquals("other", DemoSupport.fixturePlatform("Java 21"))
        assertEquals("web", DemoSupport.fixturePlatform("Web with Kotlin/Wasm"))
    }
}
