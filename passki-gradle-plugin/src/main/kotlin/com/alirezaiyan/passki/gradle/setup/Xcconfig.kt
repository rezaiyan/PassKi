package com.alirezaiyan.passki.gradle.setup

/**
 * Line-level edits of `.xcconfig` files. Only plain `KEY = value` lines count: comments, conditional
 * assignments (`KEY[sdk=...]`) and other keys are left exactly as they are.
 */
internal object Xcconfig {
    private fun assignment(key: String) = Regex("^\\s*${Regex.escape(key)}\\s*=(.*)$")

    /** Value of the first plain assignment of [key], without a trailing `//` comment; null when absent. */
    fun get(text: String, key: String): String? {
        val regex = assignment(key)
        return text.lineSequence().firstNotNullOfOrNull { regex.find(it)?.groupValues?.get(1) }
            ?.substringBefore("//")?.trim()
    }

    /** Sets [key] to [value]: replaces the first plain assignment (dropping later duplicates) or appends one. */
    fun set(text: String, key: String, value: String): String {
        val regex = assignment(key)
        val line = "$key = $value"
        val lines = text.split("\n")
        if (lines.none { regex.matches(it) }) {
            val base = if (text.isEmpty() || text.endsWith("\n")) text else "$text\n"
            return "$base$line\n"
        }
        var replaced = false
        val out = lines.mapNotNull { current ->
            when {
                !regex.matches(current) -> current
                replaced -> null
                else -> {
                    replaced = true
                    line
                }
            }
        }
        return out.joinToString("\n")
    }
}
