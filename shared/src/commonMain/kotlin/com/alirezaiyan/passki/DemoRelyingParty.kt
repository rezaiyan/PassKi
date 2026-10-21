package com.alirezaiyan.passki

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The live PassKi demo relying party (passki-web, `/api/demo/...`). */
const val DEMO_RP_URL = "https://passki.alirezaiyan.com"

/** What the demo RP answers after verifying a registration or assertion. */
@Serializable
data class ServerVerdict(
    val verified: Boolean,
    val credentialId: String,
    val signCount: Long,
    val userVerified: Boolean,
    val backupEligible: Boolean = false,
    val backedUp: Boolean = false,
    val aaguid: String? = null,
    val userHandle: String,
    val origin: String,
    val rpId: String,
) {
    override fun toString(): String =
        (if (verified) "verified by server ✓" else "NOT verified by server ✗") +
            " signCount $signCount userVerified $userVerified backedUp $backedUp" +
            "\n  credentialId $credentialId\n  userHandle $userHandle\n  origin $origin" +
            aaguid?.let { "\n  aaguid $it" }.orEmpty()
}

/** `GET /api/demo/server-config`: the RP ID and origins the demo RP accepts (SRV-01/02 reference). */
@Serializable
data class ServerConfig(val rpId: String, val origins: List<String>)

/**
 * Default client: short timeouts plus retries on network errors. Emulators silently drop idle pooled connections,
 * and the next request would otherwise hang until the socket timeout. A retried verify whose first attempt did
 * reach the server fails with "already used challenge", which the log shows as is.
 */
fun demoHttpClient(): HttpClient = HttpClient {
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

/** The demo RP answered with an error (`{"error": …}`), or could not be reached ([status] null). */
class DemoServerException(val status: Int?, message: String, cause: Throwable? = null) : Exception(message, cause)

/** The demo user this app instance registered (or last signed in as); the demo RP keeps it for 24 h. */
data class DemoAccount(val userHandle: String, val credentialIds: List<String>)

/**
 * HTTP client for the PassKi demo relying party. The server creates the challenges, keeps the demo users and
 * verifies every response with SimpleWebAuthn; this sample only moves JSON between it and [Passkeys].
 * Ktor is a dependency of the sample only, never of the library.
 */
class DemoRelyingParty(
    private val baseUrl: String = DEMO_RP_URL,
    private val client: HttpClient = demoHttpClient(),
) {
    /** Set after a server-verified registration or sign-in; used for allowCredentials, PRF and signals. */
    var account: DemoAccount? = null
        private set

    /** `POST /api/demo/register/options` → PublicKeyCredentialCreationOptionsJSON (a fresh demo user each time). */
    suspend fun registrationOptions(username: String? = null): String =
        post("/api/demo/register/options", buildJsonObject { username?.takeIf { it.isNotBlank() }?.let { put("username", it) } })

    /** `POST /api/demo/login/options`; with [userHandle] the server lists that user's credentials, else discoverable. */
    suspend fun authenticationOptions(userHandle: String? = null): String =
        post("/api/demo/login/options", buildJsonObject { userHandle?.let { put("userHandle", it) } })

    /**
     * `POST /api/demo/register/verify`. With [sharePlatform] (android|ios|web|other) the user opted in to storing the
     * raw response as a PassKi contract-test fixture on the demo RP (7 days).
     */
    suspend fun verifyRegistration(responseJson: String, sharePlatform: String? = null): ServerVerdict {
        val body = buildJsonObject {
            put("response", parse(responseJson))
            sharePlatform?.let { put("capture", buildJsonObject { put("platform", it) }) }
        }
        val verdict = json.decodeFromString<ServerVerdict>(post("/api/demo/register/verify", body))
        val known = account?.takeIf { it.userHandle == verdict.userHandle }?.credentialIds.orEmpty()
        account = DemoAccount(verdict.userHandle, known + verdict.credentialId)
        return verdict
    }

    /** `POST /api/demo/login/verify` → verdict with the new signCount. */
    suspend fun verifyAuthentication(responseJson: String): ServerVerdict {
        val verdict = json.decodeFromString<ServerVerdict>(
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

    /** `GET /api/demo/server-config`. */
    suspend fun serverConfig(): ServerConfig = json.decodeFromString(request { client.get(baseUrl + "/api/demo/server-config") })

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
            throw DemoServerException(null, "demo RP unreachable ($baseUrl): ${e.message ?: e::class.simpleName}", e)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val error = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            throw DemoServerException(response.status.value, "demo RP HTTP ${response.status.value}: ${error ?: text.take(200)}")
        }
        return text
    }

    private fun parse(responseJson: String): JsonObject = try {
        json.parseToJsonElement(responseJson).jsonObject
    } catch (e: Exception) {
        throw DemoServerException(null, "response is not a JSON object: ${e.message}", e)
    }

    companion object {
        const val RP_ID = "passki.alirezaiyan.com"
        private val json = Json { ignoreUnknownKeys = true }

        /** Replaces `allowCredentials` in request options, e.g. with an id no provider knows. */
        fun withAllowCredentials(requestOptionsJson: String, ids: List<String>): String {
            val options = json.parseToJsonElement(requestOptionsJson).jsonObject
            val allow = JsonArray(ids.map { JsonObject(mapOf("type" to JsonPrimitive("public-key"), "id" to JsonPrimitive(it))) })
            return JsonObject(options + ("allowCredentials" to allow)).toString()
        }

        /** The fixture platform name the demo RP expects for a [Platform.name]. */
        fun fixturePlatform(platformName: String): String = when {
            platformName.startsWith("Android") -> "android"
            platformName.startsWith("iOS") || platformName.startsWith("iPadOS") -> "ios"
            platformName.startsWith("Java") -> "other"
            else -> "web"
        }
    }
}
