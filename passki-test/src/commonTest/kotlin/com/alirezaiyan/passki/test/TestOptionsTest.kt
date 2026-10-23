package com.alirezaiyan.passki.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class TestOptionsTest {
    @Test
    fun creation_defaultsGeneratedConcurrently_haveUniqueUserIds() = runTest {
        val ids = (1..1000).map {
            async(Dispatchers.Default) {
                val json = Json.parseToJsonElement(TestOptions.creation("example.com")).jsonObject
                json.getValue("user").jsonObject.getValue("id").jsonPrimitive.content
            }
        }.awaitAll()
        assertEquals(1000, ids.toSet().size)
    }
}
