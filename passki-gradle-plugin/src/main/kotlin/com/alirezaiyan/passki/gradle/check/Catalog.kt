package com.alirezaiyan.passki.gradle.check

import groovy.json.JsonSlurper

/** Catalog IDs `passkiCheck` reports; a test keeps them in sync with `docs/checks/catalog.json`. */
internal object CheckIds {
    const val AND_01 = "AND-01"
    const val AND_02 = "AND-02"
    const val AND_03 = "AND-03"
    const val AND_04 = "AND-04"
    const val AND_05 = "AND-05"
    const val IOS_01 = "IOS-01"
    const val IOS_02 = "IOS-02"
    const val IOS_03 = "IOS-03"
    const val IOS_04 = "IOS-04"
    const val IOS_05 = "IOS-05"
    const val IOS_06 = "IOS-06"
    const val SRV_01 = "SRV-01"
    const val SRV_02 = "SRV-02"
    const val SRV_03 = "SRV-03"

    val all: List<String> = listOf(AND_01, AND_02, AND_03, AND_04, AND_05, IOS_01, IOS_02, IOS_03, IOS_04, IOS_05, IOS_06, SRV_01, SRV_02, SRV_03)
}

/**
 * One entry of the shared setup check catalog. [webPage] is the message and fix for a hosted file that came back as a
 * web page (AND-01, IOS-01), with `<url>` and `<file>` placeholders.
 */
internal data class CatalogEntry(
    val id: String,
    val platform: String,
    val severity: String,
    val title: String,
    val fix: String,
    val webPage: WebPageText? = null,
)

/** The catalog's `html` entry: what to say when a well-known path serves a web page. */
internal data class WebPageText(val message: String, val fix: String) {
    fun message(url: String, file: String): String = message.replace("<url>", url).replace("<file>", file)

    fun fix(url: String, file: String): String = fix.replace("<url>", url).replace("<file>", file)
}

/**
 * The shared setup check catalog (`docs/checks/catalog.json`, bundled into the plugin jar at build time):
 * stable IDs, titles and fix templates with `<placeholder>`s.
 */
internal class Catalog(val version: Int, private val docsBase: String, private val docsRepo: String?, val entries: List<CatalogEntry>) {
    private val byId = entries.associateBy { it.id }

    operator fun get(id: String): CatalogEntry = byId[id] ?: error("Check ID $id is not in the catalog")

    /** [id]'s fix template with `<key>` replaced by [placeholders]; unknown placeholders stay as written. */
    fun fix(id: String, placeholders: Map<String, String>): String {
        var text = get(id).fix
        placeholders["path"]?.takeIf { it.endsWith(".entitlements") }?.let { text = text.replace("<path>.entitlements", it) }
        placeholders.forEach { (key, value) -> text = text.replace("<$key>", value) }
        return text
    }

    /** Docs page for [id]: the repo's `docs/checks/<id>.md` when the catalog names it, else the dashboard. */
    fun docsUrl(id: String): String = if (docsRepo != null) "$docsRepo$id.md" else "$docsBase$id"

    companion object {
        private const val RESOURCE = "/com/alirezaiyan/passki/gradle/catalog.json"

        val bundled: Catalog by lazy {
            val text = Catalog::class.java.getResourceAsStream(RESOURCE)?.use { it.reader().readText() }
                ?: error("PassKi check catalog missing from the plugin jar ($RESOURCE)")
            parse(text)
        }

        fun parse(json: String): Catalog {
            val root = JsonSlurper().parseText(json) as Map<*, *>
            val entries = (root["checks"] as List<*>).map { raw ->
                val e = raw as Map<*, *>
                CatalogEntry(
                    id = e["id"].toString(),
                    platform = e["platform"].toString(),
                    severity = e["severity"].toString(),
                    title = e["title"].toString(),
                    fix = e["fix"].toString(),
                    webPage = (e["html"] as? Map<*, *>)?.let { WebPageText(it["message"].toString(), it["fix"].toString()) },
                )
            }
            return Catalog((root["version"] as Number).toInt(), root["docsBase"].toString(), root["docsRepo"] as String?, entries)
        }
    }
}

internal enum class CheckStatus(val marker: String) {
    PASS("[PASS]"),
    WARN("[WARN]"),
    FAIL("[FAIL]"),
    SKIP("[SKIP]"),
}

/** Outcome of one catalog check; [fix] is set for WARN/FAIL with placeholders filled. */
internal data class CheckResult(val id: String, val status: CheckStatus, val message: String, val fix: String? = null)

/** Builds [CheckResult]s whose fixes come from the catalog with [placeholders] (rpId, package, teamId...) filled. */
internal class ResultFactory(val catalog: Catalog, private val placeholders: Map<String, String>) {
    fun pass(id: String, message: String) = CheckResult(id, CheckStatus.PASS, message)

    fun skip(id: String, message: String) = CheckResult(id, CheckStatus.SKIP, message)

    /**
     * A FAIL, or a WARN when the catalog marks [id] as a warning or [warning] is set. [hint] is a specific
     * extra step appended to the catalog fix; [extra] adds or overrides placeholders.
     */
    fun problem(id: String, message: String, hint: String? = null, warning: Boolean = false, extra: Map<String, String> = emptyMap()): CheckResult {
        val status = if (warning || catalog[id].severity == "warning") CheckStatus.WARN else CheckStatus.FAIL
        val fix = listOfNotNull(catalog.fix(id, placeholders + extra), hint?.takeIf { it.isNotBlank() }).distinct().joinToString("\n")
        return CheckResult(id, status, message, fix)
    }
}
