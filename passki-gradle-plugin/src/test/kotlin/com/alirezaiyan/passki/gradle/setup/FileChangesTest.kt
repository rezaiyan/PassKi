package com.alirezaiyan.passki.gradle.setup

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FileChangesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun recordsCreatedChangedAndUnchangedFiles() {
        val created = File(tmp.root, "out/new.json")
        val changed = File(tmp.root, "Config.xcconfig").apply { writeText("A=1\nB=2\n") }
        val same = File(tmp.root, "same.txt").apply { writeText("x\n") }
        val changes = FileChanges(preview = false, root = tmp.root)

        assertEquals(FileChanges.Kind.CREATED, changes.write(created, "{}\n"))
        assertEquals(FileChanges.Kind.CHANGED, changes.write(changed, "A=1\nB=3\n"))
        assertEquals(FileChanges.Kind.UNCHANGED, changes.write(same, "x\n"))

        assertEquals("{}\n", created.readText())
        assertEquals("A=1\nB=3\n", changed.readText())
        val summary = changes.summary()
        assertTrue("created    out/new.json" in summary, summary)
        assertTrue("changed    Config.xcconfig" in summary, summary)
        assertTrue("unchanged  same.txt" in summary, summary)
        assertTrue("- B=2" in summary && "+ B=3" in summary, summary)
    }

    @Test
    fun previewWritesNothingButShowsTheSameChanges() {
        val created = File(tmp.root, "App.entitlements")
        val changed = File(tmp.root, "Config.xcconfig").apply { writeText("A=1\n") }
        val stale = File(tmp.root, "old.json").apply { writeText("{}") }
        val changes = FileChanges(preview = true, root = tmp.root)

        changes.write(created, "<plist/>\n")
        changes.write(changed, "A=1\nCODE_SIGN_ENTITLEMENTS = App.entitlements\n")
        changes.delete(stale)

        assertFalse(created.exists())
        assertEquals("A=1\n", changed.readText())
        assertTrue(stale.exists())
        val summary = changes.summary()
        assertTrue("preview: nothing was written" in summary, summary)
        assertTrue("would create  App.entitlements" in summary, summary)
        assertTrue("+ <plist/>" in summary, summary)
        assertTrue("would change  Config.xcconfig" in summary, summary)
        assertTrue("+ CODE_SIGN_ENTITLEMENTS = App.entitlements" in summary, summary)
        assertFalse("- A=1" in summary, summary)
        assertTrue("would delete  old.json" in summary, summary)
    }

    @Test
    fun generatedFilesAreListedWithoutDiffs() {
        val generated = File(tmp.root, "build/passki/server-config.json")
        val changes = FileChanges(preview = false, root = tmp.root)

        changes.write(generated, "{\n  \"rpId\": \"example.com\"\n}\n", showDiff = false)

        assertFalse("rpId" in changes.summary(), changes.summary())
    }

    @Test
    fun includesNetworkCallsAndNextSteps() {
        val changes = FileChanges(preview = false, root = tmp.root)
        changes.sent("POST https://demo/api/demo/ios-apps {\"teamId\":\"ABCDE12345\"}", "registered until 2026-10-30")

        val summary = changes.summary(listOf("Run ./gradlew passkiCheck"))

        assertTrue("sent" in summary && "POST https://demo/api/demo/ios-apps" in summary, summary)
        assertTrue("registered until 2026-10-30" in summary, summary)
        assertTrue("1. Run ./gradlew passkiCheck" in summary, summary)
    }

    @Test
    fun deleteOfMissingFileIsNotReported() {
        val changes = FileChanges(preview = false, root = tmp.root)
        changes.delete(File(tmp.root, "never-existed"))
        assertFalse("never-existed" in changes.summary())
    }

    @Test
    fun diffKeepsOrderAroundInsertions() {
        assertEquals(
            listOf("+ <string>webcredentials:example.com</string>"),
            TextDiff.lines("<array>\n</array>\n", "<array>\n<string>webcredentials:example.com</string>\n</array>\n"),
        )
        assertEquals(listOf("+ a", "+ b"), TextDiff.lines(null, "a\nb\n"))
        assertEquals(listOf("- old", "+ new"), TextDiff.lines("keep\nold\n", "keep\nnew\n"))
    }
}
