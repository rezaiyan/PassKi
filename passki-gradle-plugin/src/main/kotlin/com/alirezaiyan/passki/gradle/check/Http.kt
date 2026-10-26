package com.alirezaiyan.passki.gradle.check

import com.alirezaiyan.passki.gradle.WellKnownVerifier.Fetched
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Plain GETs that never follow redirects (well-known files must not redirect) and never throw. */
internal class Http(private val timeout: Duration = TIMEOUT) {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(timeout)
        .build()

    fun get(url: String): Fetch = try {
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Accept", "application/json").GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        Fetch(
            url,
            Fetched(
                status = response.statusCode(),
                contentType = response.headers().firstValue("Content-Type").orElse(null),
                body = response.body(),
                redirected = response.statusCode() in 300..399,
            ),
            error = null,
        )
    } catch (e: Exception) {
        Fetch(url, null, e.message ?: e.javaClass.simpleName)
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(15)
    }
}
