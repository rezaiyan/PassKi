package com.alirezaiyan.passki.gradle.check

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CatalogTest {
    private val catalog = Catalog.bundled

    /** The repo's docs/checks/catalog.json, the single source of truth the plugin bundles. */
    private val repoCatalog: Catalog = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .map { File(it, "docs/checks/catalog.json") }
        .first { it.isFile }
        .let { Catalog.parse(it.readText()) }

    @Test
    fun bundledCatalogIsTheRepoCatalog() {
        assertEquals(repoCatalog.entries, catalog.entries)
    }

    @Test
    fun everyPluginIdIsInTheCatalog() {
        val ids = catalog.entries.map { it.id }
        assertEquals(emptyList(), CheckIds.all.filter { it !in ids })
    }

    @Test
    fun everyBuildMachineCatalogEntryIsRunByPasskiCheck() {
        // device/config checks run on the device (PasskeyDoctor); AND-06 is about app startup, also device-only.
        val buildMachine = catalog.entries.filter { it.platform in setOf("android", "ios", "server") && it.id != "AND-06" }.map { it.id }
        assertEquals(emptyList(), buildMachine.filter { it !in CheckIds.all })
    }

    @Test
    fun fillsPlaceholders() {
        val fix = catalog.fix("AND-03", mapOf("fingerprint" to "AA:BB", "package" to "com.example.app"))
        assertTrue("AA:BB" in fix && "com.example.app" in fix && "<" !in fix, fix)
    }

    @Test
    fun entitlementsPathPlaceholderKeepsOneExtension() {
        val fix = catalog.fix("IOS-05", mapOf("path" to "iosApp/iosApp.entitlements"))
        assertTrue("CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements," in fix, fix)
    }

    @Test
    fun unknownPlaceholdersStay() {
        assertTrue("<rpId>" in catalog.fix("SRV-01", emptyMap()))
    }

    @Test
    fun docsLinkPointsAtRepoPage() {
        assertTrue(catalog.docsUrl("IOS-05").endsWith("docs/checks/IOS-05.md"), catalog.docsUrl("IOS-05"))
    }
}
