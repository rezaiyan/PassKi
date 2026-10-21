package com.alirezaiyan.passki.gradle

import groovy.json.JsonOutput

/**
 * Builds the `.well-known` documents served by the relying party.
 *
 * Output is pretty-printed with 2-space indentation and a trailing newline, keys in a fixed order,
 * so generated files are stable and diff-friendly.
 */
internal object WellKnownFiles {
    private val RELATIONS = listOf(
        "delegate_permission/common.handle_all_urls",
        "delegate_permission/common.get_login_creds",
    )

    /** `assetlinks.json` granting [packageName] (signed by any of [fingerprints]) passkey access. */
    fun assetLinks(packageName: String, fingerprints: List<String>): String {
        val target = linkedMapOf<String, Any>(
            "namespace" to "android_app",
            "package_name" to packageName,
            "sha256_cert_fingerprints" to fingerprints.distinct().sorted(),
        )
        val statement = linkedMapOf<String, Any>(
            "relation" to RELATIONS,
            "target" to target,
        )
        return render(listOf(statement))
    }

    /** `apple-app-site-association` listing [appIds] (`<TeamID>.<bundleId>`) under `webcredentials`. */
    fun aasa(appIds: List<String>): String {
        val webcredentials = linkedMapOf<String, Any>("apps" to appIds.distinct().sorted())
        return render(linkedMapOf<String, Any>("webcredentials" to webcredentials))
    }

    private fun render(value: Any): String {
        val pretty = JsonOutput.prettyPrint(JsonOutput.toJson(value))
        // Groovy indents by 4 spaces; halve leading whitespace to get 2.
        return pretty.lineSequence().joinToString("\n", postfix = "\n") { line ->
            val indent = line.length - line.trimStart(' ').length
            " ".repeat(indent / 2) + line.substring(indent)
        }
    }
}
