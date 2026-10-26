package com.alirezaiyan.passki.internal.doctor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The doctor's [CheckIds] must match the shared catalog (docs/checks/catalog.json), the single source of check IDs. */
class CheckCatalogConsistencyTest {
    private val catalogFile: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .map { File(it, "docs/checks/catalog.json") }
        .first { it.isFile }

    private val catalog = Json.parseToJsonElement(catalogFile.readText()).jsonObject["checks"]!!.jsonArray.map { it.jsonObject }
    private val catalogIds = catalog.map { it["id"]!!.jsonPrimitive.content }

    @Test
    fun everyDoctorIdIsInTheCatalog() {
        val unknown = CheckIds.all.filter { it !in catalogIds }
        assertEquals(emptyList(), unknown, "IDs used by the doctor but missing from $catalogFile")
    }

    @Test
    fun everyDeviceAndConfigCatalogEntryIsCoveredByTheDoctor() {
        // device/config checks can only run on the device; build-machine checks live in the Gradle plugin.
        val deviceOnly = catalog.filter { it["platform"]!!.jsonPrimitive.content in setOf("device", "config") }
            .map { it["id"]!!.jsonPrimitive.content }
        val uncovered = deviceOnly.filter { it !in CheckIds.all }
        assertEquals(emptyList(), uncovered)
    }

    @Test
    fun webPageTextsMatchTheCatalog() {
        for (id in listOf(CheckIds.AND_01, CheckIds.IOS_01)) {
            val html = catalog.first { it["id"]!!.jsonPrimitive.content == id }["html"]!!.jsonObject
            assertEquals(WebPageTexts.MESSAGE, html["message"]!!.jsonPrimitive.content, "$id html.message")
            assertEquals(WebPageTexts.FIX, html["fix"]!!.jsonPrimitive.content, "$id html.fix")
        }
    }

    @Test
    fun catalogIdsAreUniqueAndHaveDocs() {
        assertEquals(catalogIds.size, catalogIds.toSet().size, "duplicate IDs in catalog")
        val docsDir = File(catalogFile.parentFile, "")
        val missingDocs = catalogIds.filter { !File(docsDir, "$it.md").isFile }
        assertTrue(missingDocs.isEmpty(), "missing docs/checks/<ID>.md for $missingDocs")
    }
}
