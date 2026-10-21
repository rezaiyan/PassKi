package com.alirezaiyan.passki.gradle

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

    data class Problem(val message: String, val fix: String, val severity: Severity = Severity.ERROR)

    private const val ASSET_LINKS = "assetlinks.json"
    private const val AASA = "apple-app-site-association"
    private const val LOGIN_CREDS = "delegate_permission/common.get_login_creds"
    private const val ASSOCIATED_DOMAINS = "com.apple.developer.associated-domains"

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
        val upload = "Upload build/passki/well-known/$file"
        when {
            fetched.redirected || fetched.status in 300..399 -> {
                problems += Problem("$url must be served without redirects (got HTTP ${fetched.status})", "Serve $file directly at $url with HTTP 200")
                return null
            }
            fetched.status == 404 -> {
                problems += Problem("$file not found at $url (HTTP 404)", fix = upload)
                return null
            }
            fetched.status != 200 -> {
                problems += Problem("$url returned HTTP ${fetched.status}", "Serve $file at $url with HTTP 200")
                return null
            }
        }
        return try {
            JsonSlurper().parseText(fetched.body)
        } catch (e: Exception) {
            problems += Problem("$url is not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}", upload)
            null
        }
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
                problems += Problem("$ASSET_LINKS has no android_app statement for package $packageName", upload)
                return@forEach
            }
            val relations = statements.flatMap { (it["relation"] as? List<*>).orEmpty() }.toSet()
            if (LOGIN_CREDS !in relations) {
                problems += Problem("$ASSET_LINKS statement for $packageName lacks relation $LOGIN_CREDS", upload)
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
            problems += Problem("$AASA webcredentials.apps is missing ${missing.joinToString(", ")}", "Upload build/passki/well-known/$AASA")
        }
        val mime = fetched.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (mime != "application/json") {
            problems += Problem(
                "$AASA is served with Content-Type '${fetched.contentType ?: "(none)"}'",
                "Serve ${url(expected.rpId, AASA)} with Content-Type: application/json",
                Severity.WARNING,
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
            return listOf(Problem("Entitlements file is not a readable plist: ${e.message}", fix))
        }
        val ok = domains.any { it == wanted || it.startsWith("$wanted?") }
        return if (ok) emptyList() else listOf(Problem("Entitlements $ASSOCIATED_DOMAINS does not contain $wanted", fix))
    }

    /** Values of the `com.apple.developer.associated-domains` array in an XML plist. */
    private fun associatedDomains(plist: String): List<String> {
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
