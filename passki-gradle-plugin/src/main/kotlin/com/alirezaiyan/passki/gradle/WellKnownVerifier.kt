package com.alirezaiyan.passki.gradle

import com.alirezaiyan.passki.gradle.check.Catalog
import com.alirezaiyan.passki.gradle.check.CheckIds
import groovy.json.JsonSlurper
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * Pure checks of hosted `.well-known` files and iOS entitlements against what the build expects.
 * Every [Problem] carries an actionable fix.
 */
internal object WellKnownVerifier {
    /** What the build says should be served for [rpId]. Fingerprints are canonical colon-hex. */
    data class Expected(val rpId: String, val fingerprintsByPackage: Map<String, List<String>>, val appleAppIds: List<String>)

    /** An HTTP response; [redirected] is true for a 3xx (redirects are never followed). */
    data class Fetched(val status: Int, val contentType: String?, val body: String, val redirected: Boolean)

    enum class Severity { ERROR, WARNING }

    /** [id] is the setup check catalog ID (docs/checks/catalog.json) the problem belongs to. */
    data class Problem(val message: String, val fix: String, val severity: Severity = Severity.ERROR, val id: String? = null)

    private const val ASSET_LINKS = "assetlinks.json"
    private const val AASA = "apple-app-site-association"
    private const val LOGIN_CREDS = "delegate_permission/common.get_login_creds"
    const val ASSOCIATED_DOMAINS: String = "com.apple.developer.associated-domains"

    /** `null` inputs were not fetched / not configured and are skipped. */
    fun verify(expected: Expected, assetLinks: Fetched?, aasa: Fetched?, entitlements: String?): List<Problem> {
        val problems = mutableListOf<Problem>()
        assetLinks?.let { problems += checkAssetLinks(expected, it) }
        if (expected.appleAppIds.isNotEmpty()) {
            aasa?.let { problems += checkAasa(expected, it) }
            entitlements?.let { problems += checkEntitlements(expected.rpId, it) }
        }
        return problems
    }

    private fun url(rpId: String, file: String) = "https://$rpId/.well-known/$file"

    /** Status/redirect/JSON checks shared by both files; returns the parsed JSON or null after reporting. */
    private fun parse(expected: Expected, file: String, fetched: Fetched, problems: MutableList<Problem>): Any? {
        val url = url(expected.rpId, file)
        val id = if (file == ASSET_LINKS) CheckIds.AND_01 else CheckIds.IOS_01
        val upload = "Upload build/passki/well-known/$file"
        when {
            fetched.redirected || fetched.status in 300..399 -> {
                problems += Problem("$url must be served without redirects (got HTTP ${fetched.status})", "Serve $file directly at $url with HTTP 200", id = id)
                return null
            }
            fetched.status == 404 -> {
                problems += Problem("$file not found at $url (HTTP 404)", fix = upload, id = id)
                return null
            }
            fetched.status != 200 -> {
                problems += Problem("$url returned HTTP ${fetched.status}", "Serve $file at $url with HTTP 200", id = id)
                return null
            }
        }
        val parsed = runCatching { JsonSlurper().parseText(fetched.body) }
        if (isWebPage(fetched, parsed.isSuccess)) {
            val text = Catalog.bundled[id].webPage ?: error("catalog entry $id has no html text")
            problems += Problem(text.message(url, file), text.fix(url, file), id = id)
            return null
        }
        return parsed.getOrElse { e ->
            problems += Problem("$url is not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}", upload, id = id)
            null
        }
    }

    /**
     * A web page instead of the file (typically a single-page app's catch-all route): the body starts with `<`, or it
     * is served as text/html and isn't JSON. Same rule as PasskeyDoctor and the passki-web verifier.
     */
    private fun isWebPage(fetched: Fetched, isJson: Boolean): Boolean {
        if (fetched.body.trimStart('\uFEFF', ' ', '\t', '\r', '\n').startsWith("<")) return true
        return fetched.contentType.orEmpty().contains("text/html", ignoreCase = true) && !isJson
    }

    private fun checkAssetLinks(expected: Expected, fetched: Fetched): List<Problem> {
        val problems = mutableListOf<Problem>()
        val json = parse(expected, ASSET_LINKS, fetched, problems) ?: return problems
        val upload = "Upload build/passki/well-known/$ASSET_LINKS"
        val all = (json as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        expected.fingerprintsByPackage.toSortedMap().forEach { (packageName, fingerprints) ->
            val statements = all.filter { statement ->
                val target = statement["target"] as? Map<*, *>
                target?.get("namespace") == "android_app" && target["package_name"] == packageName
            }
            if (statements.isEmpty()) {
                problems += Problem("$ASSET_LINKS has no android_app statement for package $packageName", upload, id = CheckIds.AND_02)
                return@forEach
            }
            val relations = statements.flatMap { (it["relation"] as? List<*>).orEmpty() }.toSet()
            if (LOGIN_CREDS !in relations) {
                problems += Problem("$ASSET_LINKS statement for $packageName lacks relation $LOGIN_CREDS", upload, id = CheckIds.AND_02)
            }
            val served = statements
                .flatMap { ((it["target"] as Map<*, *>)["sha256_cert_fingerprints"] as? List<*>).orEmpty() }
                .map { canonical(it.toString()) }
                .toSet()
            val missing = fingerprints.filter { it !in served }
            if (missing.isNotEmpty()) {
                problems += Problem(
                    "$ASSET_LINKS is missing signing fingerprint(s) for $packageName: ${missing.joinToString(", ")}",
                    upload,
                    id = CheckIds.AND_03,
                )
            }
        }
        return problems
    }

    private fun checkAasa(expected: Expected, fetched: Fetched): List<Problem> {
        val problems = mutableListOf<Problem>()
        val json = parse(expected, AASA, fetched, problems) ?: return problems
        val apps = (((json as? Map<*, *>)?.get("webcredentials") as? Map<*, *>)?.get("apps") as? List<*>).orEmpty()
        val missing = expected.appleAppIds.filter { it !in apps }
        if (missing.isNotEmpty()) {
            problems += Problem("$AASA webcredentials.apps is missing ${missing.joinToString(", ")}", "Upload build/passki/well-known/$AASA", id = CheckIds.IOS_02)
        }
        val mime = fetched.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (mime != "application/json") {
            problems += Problem(
                "$AASA is served with Content-Type '${fetched.contentType ?: "(none)"}'",
                "Serve ${url(expected.rpId, AASA)} with Content-Type: application/json",
                Severity.WARNING,
                CheckIds.IOS_01,
            )
        }
        return problems
    }

    private fun checkEntitlements(rpId: String, plist: String): List<Problem> {
        val wanted = "webcredentials:$rpId"
        val fix = "Add <string>$wanted</string> to $ASSOCIATED_DOMAINS in the app's .entitlements"
        val domains = try {
            associatedDomains(plist)
        } catch (e: Exception) {
            return listOf(Problem("Entitlements file is not a readable plist: ${e.message}", fix, id = CheckIds.IOS_04))
        }
        val ok = coversWebcredentials(domains, rpId)
        return if (ok) emptyList() else listOf(Problem("Entitlements $ASSOCIATED_DOMAINS does not contain $wanted", fix, id = CheckIds.IOS_04))
    }

    /** True when [domains] contain `webcredentials:<rpId>`, with or without a `?mode=` suffix. */
    fun coversWebcredentials(domains: List<String>, rpId: String): Boolean {
        val wanted = "webcredentials:$rpId"
        return domains.any { it == wanted || it.startsWith("$wanted?") }
    }

    /** Values of the `com.apple.developer.associated-domains` array in an XML plist; throws if [plist] isn't XML. */
    fun associatedDomains(plist: String): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            // plists declare Apple's DTD; never fetch it, never expand external entities.
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(plist.trim())))
        val keys = document.getElementsByTagName("key")
        for (i in 0 until keys.length) {
            val key = keys.item(i)
            if (key.textContent.trim() != ASSOCIATED_DOMAINS) continue
            var next = key.nextSibling
            while (next != null && next !is Element) next = next.nextSibling
            if (next !is Element || next.tagName != "array") return emptyList()
            val strings = next.getElementsByTagName("string")
            return (0 until strings.length).map { strings.item(it).textContent.trim() }
        }
        return emptyList()
    }

    private fun canonical(fingerprint: String): String = try {
        Fingerprints.normalize(fingerprint)
    } catch (e: IllegalArgumentException) {
        fingerprint.uppercase()
    }
}
