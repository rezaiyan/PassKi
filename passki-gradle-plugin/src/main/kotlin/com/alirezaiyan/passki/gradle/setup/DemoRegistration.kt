package com.alirezaiyan.passki.gradle.setup

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Registers an app with the PassKi demo relying party (`passki { useDemoServer() }`), so its `assetlinks.json` /
 * `apple-app-site-association` list the app for 7 days. Sends only public data: package names, signing SHA-256
 * fingerprints and `TEAM.bundle` IDs, the same values any `.well-known` file publishes.
 */
internal object DemoRegistration {
    data class Request(val label: String, val url: String, val body: String)

    data class Outcome(val ok: Boolean, val message: String)

    fun requests(baseUrl: String, fingerprintsByPackage: Map<String, List<String>>, appleAppIds: List<String>): List<Request> {
        val base = baseUrl.trimEnd('/')
        val android = fingerprintsByPackage.toSortedMap().map { (pkg, fingerprints) ->
            Request("Android $pkg", "$base/api/demo/android-apps", JsonOutput.toJson(linkedMapOf("packageName" to pkg, "fingerprints" to fingerprints)))
        }
        val ios = appleAppIds.sorted().map { appId ->
            val (team, bundle) = appId.split('.', limit = 2)
            Request("iOS $appId", "$base/api/demo/ios-apps", JsonOutput.toJson(linkedMapOf("teamId" to team, "bundleId" to bundle)))
        }
        return android + ios
    }

    /** Interprets a response: [status]/[body] when the request was answered, [error] when it could not be sent. */
    fun outcome(status: Int?, body: String?, error: String?): Outcome {
        if (status == null) return Outcome(false, "not sent: ${error ?: "unknown error"}")
        val json = body?.let { runCatching { JsonSlurper().parseText(it) as? Map<*, *> }.getOrNull() }
        val serverError = json?.get("error")?.toString()
        return when {
            status in 200..299 -> {
                val expires = json?.get("expiresAt")?.toString() ?: "unknown"
                val created = (json?.get("created") as? Number)?.toInt()
                Outcome(true, if (created == 0) "already registered until $expires" else "registered until $expires")
            }
            // The iOS endpoint answers 409 for an app ID that is already listed; re-running setup is fine.
            status == 409 && serverError != null -> Outcome(true, serverError)
            else -> Outcome(false, "HTTP $status: ${serverError ?: body.orEmpty().take(200)}")
        }
    }

    fun send(request: Request): Outcome = try {
        val response = client.send(
            HttpRequest.newBuilder(URI.create(request.url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request.body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        outcome(response.statusCode(), response.body(), null)
    } catch (e: Exception) {
        outcome(null, null, e.message ?: e.javaClass.simpleName)
    }

    private val TIMEOUT: Duration = Duration.ofSeconds(15)
    private val client: HttpClient by lazy {
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(TIMEOUT).build()
    }
}
