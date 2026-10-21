package com.alirezaiyan.passki.gradle.setup

import com.alirezaiyan.passki.gradle.PasskiConfigException
import com.alirezaiyan.passki.gradle.WellKnownVerifier

/**
 * Text-level merge of `webcredentials:` domains into an `.entitlements` plist: missing domains are inserted
 * into `com.apple.developer.associated-domains`, every other byte is kept, so diffs stay minimal.
 */
internal object Entitlements {
    private const val KEY = WellKnownVerifier.ASSOCIATED_DOMAINS

    /** `webcredentials:<rpId>`, plus the `?mode=developer` variant (bypasses Apple's CDN on development builds). */
    fun domains(rpId: String, developerMode: Boolean): List<String> =
        listOfNotNull("webcredentials:$rpId", "webcredentials:$rpId?mode=developer".takeIf { developerMode })

    /** [existing] with every one of [domains] present; a new plist when [existing] is null or blank. */
    fun merge(existing: String?, domains: List<String>): String {
        if (existing.isNullOrBlank()) return fresh(domains)
        val present = parse(existing)
        val missing = domains.filter { it !in present }
        if (missing.isEmpty()) return existing

        val keyMatch = Regex("([ \\t]*)<key>\\s*${Regex.escape(KEY)}\\s*</key>").find(existing)
        val merged = if (keyMatch != null) {
            val indent = keyMatch.groupValues[1]
            val inner = indent + unit(indent)
            val after = existing.substring(keyMatch.range.last + 1)
            val emptyArray = Regex("^\\s*<array\\s*/>").find(after)
            val arrayClose = after.indexOf("</array>")
            when {
                emptyArray != null -> {
                    val start = keyMatch.range.last + 1
                    val replacement = "\n$indent<array>" + missing.joinToString("") { "\n$inner<string>$it</string>" } + "\n$indent</array>"
                    existing.substring(0, start) + replacement + existing.substring(start + emptyArray.range.last + 1)
                }
                arrayClose >= 0 -> {
                    val closeAt = keyMatch.range.last + 1 + arrayClose
                    var insertAt = closeAt
                    while (insertAt > 0 && existing[insertAt - 1].isWhitespace()) insertAt--
                    existing.substring(0, insertAt) + missing.joinToString("") { "\n$inner<string>$it</string>" } + existing.substring(insertAt)
                }
                else -> throw PasskiConfigException("$KEY in the entitlements file is not an array")
            }
        } else {
            val emptyDict = Regex("<dict\\s*/>").find(existing)
            val dictClose = existing.lastIndexOf("</dict>")
            when {
                dictClose >= 0 -> {
                    val indent = Regex("\\n([ \\t]*)<key>").find(existing)?.groupValues?.get(1) ?: "\t"
                    var insertAt = dictClose
                    while (insertAt > 0 && existing[insertAt - 1].isWhitespace()) insertAt--
                    existing.substring(0, insertAt) + "\n" + block(indent, missing) + existing.substring(insertAt)
                }
                emptyDict != null -> existing.replaceRange(emptyDict.range, "<dict>\n" + block("\t", missing) + "\n</dict>")
                else -> throw PasskiConfigException("Entitlements file is not a plist <dict>")
            }
        }
        val after = parse(merged)
        if (!domains.all { it in after }) throw PasskiConfigException("Could not add ${missing.joinToString()} to the entitlements file")
        return merged
    }

    private fun parse(plist: String): List<String> = try {
        WellKnownVerifier.associatedDomains(plist)
    } catch (e: Exception) {
        throw PasskiConfigException("Entitlements file is not a readable plist: ${e.message}", e)
    }

    private fun unit(indent: String) = if (indent.startsWith(" ")) " ".repeat(indent.length.coerceAtLeast(2).coerceAtMost(4)) else "\t"

    private fun block(indent: String, domains: List<String>): String {
        val inner = indent + unit(indent)
        return "$indent<key>$KEY</key>\n$indent<array>\n" + domains.joinToString("") { "$inner<string>$it</string>\n" } + "$indent</array>"
    }

    private fun fresh(domains: List<String>): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
        """.trimIndent() + "\n" + block("\t", domains) + "\n</dict>\n</plist>\n"
}
