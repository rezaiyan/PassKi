package com.alirezaiyan.passki.demo

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Client for the hosted PassKi demo relying party, so you can try passkeys in your app before you have a domain or
 * a WebAuthn server. **For evaluation only:** demo users expire after 24 hours and the domain is shared.
 *
 * The server creates every challenge and verifies every response with SimpleWebAuthn. This class only moves JSON
 * between it and PassKi, exactly as your own server API would:
 *
 * ```
 * val demo = PasskiDemoServer()
 * passkeys.register(demo.registrationOptions("alice"))
 *     .onSuccess { println(demo.verifyRegistration(it.json)) } // "verified by server ✓ signCount 0 …"
 * passkeys.signIn(demo.authenticationOptions())
 *     .onSuccess { println(demo.verifyAuthentication(it.json)) }
 * ```
 *
 * The app must be associated with [RP_ID]: apply the Gradle plugin with `passki { useDemoServer() }` and run
 * `./gradlew passkiSetup`, which registers your package, signing key and iOS app ID with the demo server.
 *
 * Every call throws [DemoServerException] when the server is unreachable or answers with an error.
 */
public class PasskiDemoServer internal constructor(
    private val baseUrl: String,
    private val client: HttpClient,
) {
    /** A client for the demo server at [baseUrl] (default: the hosted [URL]). */
    public constructor(baseUrl: String = URL) : this(baseUrl.trimEnd('/'), defaultClient())

    /** The demo user from the last server-verified registration or sign-in; null before that. */
    public var account: DemoAccount? = null
        private set

    /** `POST /api/demo/register/options`: WebAuthn creation options for a new demo user, ready for `Passkeys.register`. */
    public suspend fun registrationOptions(username: String? = null): String =
        post("/api/demo/register/options", buildJsonObject { username?.takeIf { it.isNotBlank() }?.let { put("username", it) } })

    /**
     * `POST /api/demo/login/options`: WebAuthn request options, ready for `Passkeys.signIn`. With [userHandle] the
     * server lists that user's passkeys in `allowCredentials`; without it any discoverable passkey is accepted.
     */
    public suspend fun authenticationOptions(userHandle: String? = null): String =
        post("/api/demo/login/options", buildJsonObject { userHandle?.let { put("userHandle", it) } })

    /**
     * `POST /api/demo/register/verify` with `PasskeyRegistration.json`; returns the server's verdict and remembers
     * the [account]. With [shareFixtureAs] (`android`, `ios`, `web` or `other`) the raw response is also stored on
     * the demo server for 7 days as a PassKi contract-test fixture; leave it null unless you want to share it.
     */
    public suspend fun verifyRegistration(responseJson: String, shareFixtureAs: String? = null): DemoVerdict {
        val body = buildJsonObject {
            put("response", parse(responseJson))
            shareFixtureAs?.let { put("capture", buildJsonObject { put("platform", it) }) }
        }
        val verdict = json.decodeFromString<DemoVerdict>(post("/api/demo/register/verify", body))
        val known = account?.takeIf { it.userHandle == verdict.userHandle }?.credentialIds.orEmpty()
        account = DemoAccount(verdict.userHandle, known + verdict.credentialId)
        return verdict
    }

    /** `POST /api/demo/login/verify` with `PasskeyAssertion.json`; returns the verdict with the new sign count. */
    public suspend fun verifyAuthentication(responseJson: String): DemoVerdict {
        val verdict = json.decodeFromString<DemoVerdict>(
            post("/api/demo/login/verify", buildJsonObject { put("response", parse(responseJson)) }),
        )
        val current = account
        account = if (current?.userHandle == verdict.userHandle) {
            if (verdict.credentialId in current.credentialIds) current else current.copy(credentialIds = current.credentialIds + verdict.credentialId)
        } else {
            DemoAccount(verdict.userHandle, listOf(verdict.credentialId))
        }
        return verdict
    }

    /** `GET /api/demo/server-config`: the RP ID and every origin the demo server accepts, registered apps included. */
    public suspend fun serverConfig(): DemoServerConfig =
        json.decodeFromString(request { client.get("$baseUrl/api/demo/server-config") })

    private suspend fun post(path: String, body: JsonObject): String = request {
        client.post(baseUrl + path) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }

    private suspend fun request(call: suspend () -> HttpResponse): String {
        val response = try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw DemoServerException(null, "demo server unreachable ($baseUrl): ${e.message ?: e::class.simpleName}", e)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val error = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            throw DemoServerException(response.status.value, "demo server HTTP ${response.status.value}: ${error ?: text.take(200)}")
        }
        return text
    }

    private fun parse(responseJson: String): JsonObject = try {
        json.parseToJsonElement(responseJson).jsonObject
    } catch (e: Exception) {
        throw DemoServerException(null, "response is not a JSON object: ${e.message}", e)
    }

    public companion object {
        /** The demo relying party's RP ID; your app's associated domain in demo mode. */
        public const val RP_ID: String = "passki.alirezaiyan.com"

        /** The hosted demo server. */
        public const val URL: String = "https://$RP_ID"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Short timeouts plus retries on network errors. Emulators silently drop idle pooled connections, and the
         * next request would otherwise hang until the socket timeout. A retried verify whose first attempt did reach
         * the server fails with "already used challenge", which is reported as is.
         */
        private fun defaultClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 8_000
                requestTimeoutMillis = 20_000
            }
            install(HttpRequestRetry) {
                retryOnException(maxRetries = 2, retryOnTimeout = true)
                exponentialDelay(base = 2.0, maxDelayMs = 2_000)
            }
        }
    }
}
