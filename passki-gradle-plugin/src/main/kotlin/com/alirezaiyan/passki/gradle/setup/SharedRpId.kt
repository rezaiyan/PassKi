package com.alirezaiyan.passki.gradle.setup

import com.alirezaiyan.passki.gradle.WellKnownVerifier.Fetched
import groovy.json.JsonSlurper

/**
 * Warns when the RP ID is a bare registrable domain whose live well-known files already list other apps: passkeys
 * belong to the RP ID forever, so a company apex shared by several products is usually the wrong choice.
 */
internal object SharedRpId {
    /** Second-level labels under a two-letter country code that act as a public suffix (`example.co.uk`). */
    private val COUNTRY_SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "ac", "edu", "or", "ne", "go")

    /**
     * True when [rpId] looks like a registrable domain with no subdomain: two labels (`example.com`), or three under
     * a country second level (`example.co.uk`). A miss only skips the warning, so no public suffix list is needed.
     */
    fun isBareDomain(rpId: String): Boolean {
        val labels = rpId.split('.')
        return when (labels.size) {
            2 -> true
            3 -> labels[2].length == 2 && labels[1] in COUNTRY_SECOND_LEVEL
            else -> false
        }
    }

    /**
     * Apps the live files list besides the configured [packages] and [appIds]: Android packages from
     * `assetlinks.json`, `<TeamID>.<bundleID>` from `apple-app-site-association`. Unreadable files count as none.
     */
    fun otherApps(packages: Set<String>, appIds: Set<String>, assetLinks: Fetched?, aasa: Fetched?): List<String> {
        val android = (json(assetLinks) as? List<*>).orEmpty().mapNotNull { statement ->
            val target = (statement as? Map<*, *>)?.get("target") as? Map<*, *>
            (target?.get("package_name") as? String).takeIf { target?.get("namespace") == "android_app" }
        }
        val ios = ((((json(aasa) as? Map<*, *>)?.get("webcredentials")) as? Map<*, *>)?.get("apps") as? List<*>)
            .orEmpty().filterIsInstance<String>()
        return (android.filter { it !in packages } + ios.filter { it !in appIds }).distinct().sorted()
    }

    fun warning(rpId: String, others: List<String>): String =
        "passki: warning: https://$rpId/.well-known/ already lists other apps (${others.joinToString()}). Passkeys belong " +
            "to the RP ID and can't move to another one later: changing it orphans every passkey already created. If " +
            "those apps aren't the same product, use a product subdomain such as app.$rpId as the RP ID."

    private fun json(fetched: Fetched?): Any? {
        if (fetched == null || fetched.status != 200 || fetched.redirected) return null
        return runCatching { JsonSlurper().parseText(fetched.body) }.getOrNull()
    }
}
