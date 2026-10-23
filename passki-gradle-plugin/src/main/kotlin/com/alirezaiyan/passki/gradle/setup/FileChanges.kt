package com.alirezaiyan.passki.gradle.setup

import java.io.File

/**
 * Every file `passkiSetup` writes goes through here, so the task can end with one summary of what it did (or, in a
 * preview, what it would do): files created / changed / unchanged / deleted, a line diff for files you own, the
 * network calls it made, and the next steps.
 */
internal class FileChanges(private val preview: Boolean, private val root: File) {
    enum class Kind { CREATED, CHANGED, UNCHANGED, DELETED }

    private class Entry(val file: File, val kind: Kind, val diff: List<String>)

    private val entries = mutableListOf<Entry>()
    private val calls = mutableListOf<Pair<String, String>>()

    /** Writes [text] to [file] unless it already has it (or this is a preview). [showDiff] for files the developer owns. */
    fun write(file: File, text: String, showDiff: Boolean = true): Kind {
        val before = file.takeIf { it.isFile }?.readText()
        val kind = when (before) {
            null -> Kind.CREATED
            text -> Kind.UNCHANGED
            else -> Kind.CHANGED
        }
        if (kind != Kind.UNCHANGED && !preview) {
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
        val diff = if (showDiff && kind != Kind.UNCHANGED) TextDiff.lines(before, text) else emptyList()
        entries += Entry(file, kind, diff)
        return kind
    }

    /** Deletes [file] if it exists (a stale generated file); nothing is recorded otherwise. */
    fun delete(file: File) {
        if (!file.exists()) return
        if (!preview) file.delete()
        entries += Entry(file, Kind.DELETED, emptyList())
    }

    /** Whether anything was (or, in a preview, would be) written, deleted or sent. */
    fun hasChanges(): Boolean = calls.isNotEmpty() || entries.any { it.kind != Kind.UNCHANGED }

    /** Records a network call ([request] as sent, including its body) and its [result]. */
    fun sent(request: String, result: String) {
        calls += request to result
    }

    fun summary(nextSteps: List<String> = emptyList()): String = buildString {
        appendLine(if (preview) "passki setup summary (preview: nothing was written or sent)" else "passki setup summary")
        entries.forEach { entry ->
            appendLine("  ${label(entry.kind)}  ${relative(entry.file)}")
            entry.diff.forEach { appendLine("      $it") }
        }
        if (calls.isNotEmpty()) {
            appendLine(if (preview) "  would send" else "  sent")
            calls.forEach { (request, result) ->
                appendLine("    $request")
                appendLine("      → $result")
            }
        }
        if (nextSteps.isNotEmpty()) {
            appendLine("next steps")
            nextSteps.forEachIndexed { i, step -> appendLine("  ${i + 1}. $step") }
        }
    }.trimEnd()

    private fun label(kind: Kind): String = if (preview) {
        when (kind) {
            Kind.CREATED -> "would create"
            Kind.CHANGED -> "would change"
            Kind.UNCHANGED -> "unchanged   "
            Kind.DELETED -> "would delete"
        }
    } else {
        when (kind) {
            Kind.CREATED -> "created  "
            Kind.CHANGED -> "changed  "
            Kind.UNCHANGED -> "unchanged"
            Kind.DELETED -> "deleted  "
        }
    }

    fun relative(file: File): String {
        val absolute = file.absoluteFile
        return if (absolute.startsWith(root.absoluteFile)) absolute.relativeTo(root.absoluteFile).invariantSeparatorsPath else absolute.path
    }
}

/** Minimal line diff: only the removed (`- `) and added (`+ `) lines, in file order. */
internal object TextDiff {
    fun lines(before: String?, after: String): List<String> {
        val a = before?.lines()?.dropLastWhile { it.isEmpty() }.orEmpty()
        val b = after.lines().dropLastWhile { it.isEmpty() }
        // Longest common subsequence table; the files diffed here are a few dozen lines.
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val out = mutableListOf<String>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> { i++; j++ }
                // Removals first, so a replaced line reads "- old" then "+ new".
                i < a.size && (j == b.size || lcs[i + 1][j] >= lcs[i][j + 1]) -> out += "- ${a[i++]}"
                else -> out += "+ ${b[j++]}"
            }
        }
        return out
    }
}
