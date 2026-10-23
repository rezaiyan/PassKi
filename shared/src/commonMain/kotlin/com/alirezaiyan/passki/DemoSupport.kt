package com.alirezaiyan.passki

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Sample-only helpers around the demo server (the client itself is `PasskiDemoServer` from passki-demo). */
object DemoSupport {
    private val json = Json { ignoreUnknownKeys = true }

    /** Replaces `allowCredentials` in request options, e.g. with an id no provider knows. */
    fun withAllowCredentials(requestOptionsJson: String, ids: List<String>): String {
        val options = json.parseToJsonElement(requestOptionsJson).jsonObject
        val allow = JsonArray(ids.map { JsonObject(mapOf("type" to JsonPrimitive("public-key"), "id" to JsonPrimitive(it))) })
        return JsonObject(options + ("allowCredentials" to allow)).toString()
    }

    /** The fixture platform name the demo server expects for a [Platform.name]. */
    fun fixturePlatform(platformName: String): String = when {
        platformName.startsWith("Android") -> "android"
        platformName.startsWith("iOS") || platformName.startsWith("iPadOS") -> "ios"
        platformName.startsWith("Java") -> "other"
        else -> "web"
    }
}
